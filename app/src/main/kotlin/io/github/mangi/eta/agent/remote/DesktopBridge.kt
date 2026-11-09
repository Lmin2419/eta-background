package io.github.mangi.eta.agent.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import io.github.mangi.eta.agent.model.AgentHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 随消息发送的一张图片：已编码的字节以 base64 传给桌面。 */
internal data class DesktopImage(val mediaType: String, val base64: String)

internal data class DesktopSessionsState(
    val rows: List<DesktopSessionRow> = emptyList(),
    val workspaces: List<DesktopWorkspace> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    /** 列表来自上次的本地缓存，还没和电脑核对过。 */
    val cached: Boolean = false,
)

/**
 * 进程内唯一的电脑端镜像：一条连接、会话列表、已打开会话的状态缓存与待审批列表。
 *
 * 界面进入时 [acquire]，离开时 [release]；最后一个界面离开 45 秒后才断开，
 * 切到别的 App 再回来不用重连。重连成功后自动按各会话的 cursor 续订，只补差量。
 */
internal object DesktopBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val client = DesktopBridgeClient(AgentHttpClient.client, scope)

    private val _sessions = MutableStateFlow(DesktopSessionsState())
    val sessions: StateFlow<DesktopSessionsState> = _sessions.asStateFlow()

    private val _approvals = MutableStateFlow<List<DesktopApproval>>(emptyList())
    val approvals: StateFlow<List<DesktopApproval>> = _approvals.asStateFlow()

    private val _catalog = MutableStateFlow<DesktopModelCatalog?>(null)
    val catalog: StateFlow<DesktopModelCatalog?> = _catalog.asStateFlow()

    private val _config = MutableStateFlow<DesktopBridgeConfig?>(null)
    val config: StateFlow<DesktopBridgeConfig?> = _config.asStateFlow()

    private val _questions = MutableStateFlow<List<DesktopQuestion>>(emptyList())
    /** Agent 正在等待回答的提问（含计划审阅），手机和桌面先答者生效。 */
    val questions: StateFlow<List<DesktopQuestion>> = _questions.asStateFlow()

    private val _server = MutableStateFlow<DesktopServerInfo?>(null)
    val server: StateFlow<DesktopServerInfo?> = _server.asStateFlow()

    private val _turns = MutableSharedFlow<DesktopTurnEvent>(extraBufferCapacity = 32)
    /** 任一会话一轮结束（用于完成提醒）。 */
    val turns: SharedFlow<DesktopTurnEvent> = _turns.asSharedFlow()

    private val _unread = MutableStateFlow<Set<String>>(emptySet())
    /** 在手机上没看着时跑完的会话（与桌面的「完成未读」一致），打开后清除。 */
    val unread: StateFlow<Set<String>> = _unread.asStateFlow()

    private val _errors = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 16)
    /** 轮次外的会话错误：(sessionId, message)。 */
    val errors: SharedFlow<Pair<String, String>> = _errors.asSharedFlow()

    /** App 在后台：暂停逐字推送（退订正在看的会话），心跳放宽到 60 秒。 */
    @Volatile var background: Boolean = false
        private set
    private var cacheFile: File? = null

    private val states = ConcurrentHashMap<String, MutableStateFlow<DesktopSessionState>>()

    /** 正在看的会话；子代理会话记着主会话 id 与模式，重连后按同一地址续订。 */
    private data class FollowTarget(val parentId: String?, val mode: String?)
    private val followed = ConcurrentHashMap<String, FollowTarget>()
    private val targets = ConcurrentHashMap<String, FollowTarget>()

    private var users = 0
    private var releaseJob: Job? = null
    private var store: DesktopBridgeConfigStore? = null
    private var connectivity: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        scope.launch { client.pushes.collect(::dispatch) }
        scope.launch { client.connections.collect(::onConnected) }
    }

    @Synchronized
    fun acquire(context: Context) {
        users++
        releaseJob?.cancel()
        releaseJob = null
        val appContext = context.applicationContext
        DesktopLinkSettings.init(appContext)
        if (cacheFile == null) {
            cacheFile = File(appContext.cacheDir, SESSIONS_CACHE)
            if (!_sessions.value.loaded) loadCachedSessions()
        }
        if (store == null) store = DesktopBridgeConfigStore(appContext)
        if (_config.value == null) _config.value = runCatching { store?.load() }.getOrNull()
        registerNetworkCallback(appContext)
        val config = _config.value
        if (config != null && config.isComplete && DesktopLinkSettings.state.value.enabled &&
            client.state.value is DesktopBridgeClient.State.Idle) client.start(config)
    }

    @Synchronized
    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users > 0) return
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(IDLE_DISCONNECT_MS)
            synchronized(this@DesktopBridge) {
                if (users == 0) {
                    client.stop()
                    unregisterNetworkCallback()
                }
            }
        }
    }

    /** 保存新配置并立即用它重连。 */
    fun configure(context: Context, config: DesktopBridgeConfig) {
        val target = store ?: DesktopBridgeConfigStore(context.applicationContext).also { store = it }
        target.save(config)
        _config.value = config
        _sessions.value = DesktopSessionsState()
        _approvals.value = emptyList()
        _questions.value = emptyList()
        runCatching { cacheFile?.delete() }
        states.clear()
        if (config.isComplete && DesktopLinkSettings.state.value.enabled) client.start(config) else client.stop()
        DesktopLinkController.sync(context)
    }

    fun clearConfig(context: Context) {
        (store ?: DesktopBridgeConfigStore(context.applicationContext)).clear()
        _config.value = null
        client.stop()
        _sessions.value = DesktopSessionsState()
        _questions.value = emptyList()
        runCatching { cacheFile?.delete() }
        states.clear()
        DesktopLinkController.sync(context)
    }

    fun retry() {
        val config = _config.value ?: return
        if (!DesktopLinkSettings.state.value.enabled) return
        if (client.state.value is DesktopBridgeClient.State.Unauthorized || client.state.value is DesktopBridgeClient.State.Idle) {
            client.start(config)
        } else {
            client.reconnectNow(dropCurrent = false)
        }
    }

    // ---- 会话 --------------------------------------------------------------

    fun sessionState(sessionId: String): StateFlow<DesktopSessionState> = stateFlow(sessionId).asStateFlow()

    private fun stateFlow(sessionId: String) =
        states.getOrPut(sessionId) { MutableStateFlow(DesktopSessionState(sessionId)) }

    /** 打开会话：已有缓存时立刻可见，并按 cursor 续订只补差量。子代理对话传入主会话 id。 */
    fun follow(sessionId: String, parentId: String? = null, mode: String? = null) {
        val target = FollowTarget(parentId, mode)
        followed[sessionId] = target
        _unread.update { it - sessionId }
        targets[sessionId] = target
        stateFlow(sessionId)
        if (client.isConnected && !background) scope.launch { sendFollow(sessionId) }
    }

    /**
     * 前后台切换。后台时退订所有正在看的会话（不再收逐字输出）并放宽心跳，
     * 只保留状态、审批、提问和完成推送；回到前台按 cursor 续订，只补差量。
     */
    fun setBackground(value: Boolean) {
        if (background == value) return
        background = value
        client.setHeartbeat(if (value) BACKGROUND_HEARTBEAT_MS else FOREGROUND_HEARTBEAT_MS)
        if (!client.isConnected) return
        val sessions = followed.keys.toList()
        scope.launch {
            for (sessionId in sessions) {
                if (value) runCatching { client.call("sessions.unfollow", mapOf("sessionId" to JsonPrimitive(sessionId))) }
                else sendFollow(sessionId)
            }
        }
    }

    fun isFollowing(sessionId: String): Boolean = followed.containsKey(sessionId)

    fun unfollow(sessionId: String) {
        followed.remove(sessionId)
        if (client.isConnected) scope.launch {
            runCatching { client.call("sessions.unfollow", mapOf("sessionId" to JsonPrimitive(sessionId))) }
        }
        trimCache()
    }

    private fun MutableMap<String, JsonElement>.putAddress(sessionId: String) {
        put("sessionId", JsonPrimitive(sessionId))
        targets[sessionId]?.parentId?.let { parent ->
            put("parentId", JsonPrimitive(parent))
            targets[sessionId]?.mode?.let { put("mode", JsonPrimitive(it)) }
        }
    }

    private suspend fun sendFollow(sessionId: String) {
        val state = stateFlow(sessionId).value
        val params = buildMap<String, JsonElement> {
            putAddress(sessionId)
            if (state.loaded && state.cursor >= 0) put("afterSeq", JsonPrimitive(state.cursor))
        }
        try {
            client.call("sessions.follow", params)
        } catch (error: DesktopBridgeException) {
            stateFlow(sessionId).update { it.copy(error = error.message) }
        }
    }

    suspend fun refreshSessions() {
        _sessions.update { it.copy(loading = true) }
        try {
            val data = client.call("sessions.list").jsonObject
            val rows = (data["sessions"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(DesktopBridgeProtocol::parseSessionRow) }
            val workspaces = (data["workspaces"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(DesktopBridgeProtocol::parseWorkspace) }
            _sessions.value = DesktopSessionsState(rows = rows, workspaces = workspaces, loaded = true)
            saveCachedSessions(data)
        } catch (error: DesktopBridgeException) {
            _sessions.update { it.copy(loading = false, error = error.message) }
        }
    }

    suspend fun refreshCatalog() {
        runCatching { client.call("models.list").jsonObject }.getOrNull()?.let { _catalog.value = DesktopBridgeProtocol.parseCatalog(it) }
    }

    suspend fun createSession(workspaceId: String?, cwd: String?): String {
        val params = buildMap<String, JsonElement> {
            workspaceId?.let { put("workspaceId", JsonPrimitive(it)) }
            if (workspaceId == null) cwd?.let { put("cwd", JsonPrimitive(it)) }
        }
        return client.call("sessions.create", params).jsonObject.string("sessionId")
            ?: throw DesktopBridgeException("bad-response", "电脑没有返回会话")
    }

    /**
     * 发送一条消息。[interrupt] 对应胖小胖的“打断并发送”：先停掉当前轮次再排队，
     * 桌面会在停稳后立刻开始新一轮。
     */
    suspend fun prompt(
        sessionId: String,
        text: String,
        images: List<DesktopImage> = emptyList(),
        interrupt: Boolean = false,
        steer: Boolean = false,
    ) {
        val requestId = UUID.randomUUID().toString()
        val flow = stateFlow(sessionId)
        val running = flow.value.running
        if (targets[sessionId]?.parentId == null) DesktopLinkSettings.markWatched(sessionId)
        flow.update { it.addEcho(DesktopEcho(requestId, text, queued = running && !interrupt && !steer)) }
        try {
            if (interrupt && running) runCatching { cancel(sessionId) }
            if (targets[sessionId]?.parentId != null) {
                // 子代理对话：经 subagents 服务投递（只有可继续的子代理接受）。
                client.call(
                    "subagents.prompt",
                    mapOf(
                        "childSessionId" to JsonPrimitive(sessionId),
                        "text" to JsonPrimitive(text),
                        "mode" to JsonPrimitive(if (steer) "steer" else "queue"),
                        "requestId" to JsonPrimitive(requestId),
                        "timeZone" to JsonPrimitive(TimeZone.getDefault().id),
                    ),
                )
                return
            }
            val params = buildMap<String, JsonElement> {
                put("sessionId", JsonPrimitive(sessionId))
                put("text", JsonPrimitive(text))
                put("mode", JsonPrimitive(if (steer) "steer" else "queue"))
                put("requestId", JsonPrimitive(requestId))
                put("timeZone", JsonPrimitive(TimeZone.getDefault().id))
                if (images.isNotEmpty()) {
                    put("images", JsonArray(images.map { image ->
                        JsonObject(mapOf("mediaType" to JsonPrimitive(image.mediaType), "data" to JsonPrimitive(image.base64)))
                    }))
                }
            }
            client.call("sessions.prompt", params, timeoutMs = if (images.isEmpty()) 20_000 else 60_000)
        } catch (error: DesktopBridgeException) {
            flow.update { it.removeEcho(requestId) }
            throw error
        }
    }

    suspend fun cancel(sessionId: String) {
        if (targets[sessionId]?.parentId != null) {
            client.call("subagents.interrupt", mapOf("childSessionId" to JsonPrimitive(sessionId)))
        } else {
            client.call("sessions.cancel", mapOf("sessionId" to JsonPrimitive(sessionId)))
        }
    }

    /** 插队：先在本地把消息挪进对话（与桌面一致显示为已发出），再请电脑执行。 */
    suspend fun steer(sessionId: String, itemId: String) {
        val flow = stateFlow(sessionId)
        val before = flow.value.queue
        flow.update { it.markSteering(itemId) }
        try {
            queueAction(sessionId, itemId, JsonPrimitive("steer"))
        } catch (error: DesktopBridgeException) {
            flow.update { it.copy(queue = before) }
            throw error
        }
    }

    /** 主会话下的子代理目录（状态、用量、耗时），与桌面页头的子代理菜单同源。 */
    suspend fun subagentDirectory(sessionId: String): List<DesktopSubagent> {
        val data = client.call("sessions.subagents", mapOf("sessionId" to JsonPrimitive(sessionId))).jsonObject
        return (data["children"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(DesktopBridgeProtocol::parseSubagent) }
    }

    suspend fun queueAction(sessionId: String, itemId: String, action: JsonElement) {
        client.call(
            "sessions.queue",
            mapOf("sessionId" to JsonPrimitive(sessionId), "itemId" to JsonPrimitive(itemId), "action" to action),
        )
    }

    suspend fun loadOlder(sessionId: String) {
        val flow = stateFlow(sessionId)
        val current = flow.value
        val before = current.firstSeq ?: return
        if (current.loadingOlder || !current.hasMore) return
        flow.update { it.copy(loadingOlder = true) }
        try {
            val params = buildMap<String, JsonElement> {
                putAddress(sessionId)
                put("beforeSeq", JsonPrimitive(before))
            }
            val data = client.call("sessions.older", params).jsonObject
            flow.update { it.prependOlder(DesktopBridgeProtocol.parseItems(data["items"]), data.bool("hasMore") == true) }
        } catch (error: DesktopBridgeException) {
            flow.update { it.copy(loadingOlder = false, error = error.message) }
        }
    }

    suspend fun selectModel(sessionId: String, provider: String, model: String, reasoningEffort: String?) {
        val params = buildMap<String, JsonElement> {
            put("sessionId", JsonPrimitive(sessionId))
            put("provider", JsonPrimitive(provider))
            put("model", JsonPrimitive(model))
            reasoningEffort?.let { put("reasoningEffort", JsonPrimitive(it)) }
        }
        client.call("sessions.selectModel", params)
        stateFlow(sessionId).update { it.copy(model = DesktopModelSelection(provider, model, reasoningEffort)) }
    }

    suspend fun answerApproval(approvalId: String, allow: Boolean) {
        client.call(
            "approval.answer",
            mapOf("approvalId" to JsonPrimitive(approvalId), "outcome" to JsonPrimitive(if (allow) "allowed-once" else "rejected")),
        )
        _approvals.update { list -> list.filterNot { it.id == approvalId } }
    }

    /** 回答一个正在等待的提问；未作答的问题按跳过处理。 */
    suspend fun answerQuestion(questionId: String, answers: List<DesktopQuestionAnswer>): Boolean {
        val data = client.call(
            "question.answer",
            mapOf("questionId" to JsonPrimitive(questionId), "answers" to DesktopBridgeProtocol.answersJson(answers)),
        ).jsonObject
        _questions.update { list -> list.filterNot { it.id == questionId } }
        return data.bool("accepted") == true
    }

    /** 回答已转为「稍后回答」的限时提问：答案会作为一条新消息送给 Agent。 */
    suspend fun replyQuestion(sessionId: String, callId: String, answers: List<DesktopQuestionAnswer>): Boolean {
        val data = client.call(
            "question.reply",
            mapOf(
                "sessionId" to JsonPrimitive(sessionId),
                "callId" to JsonPrimitive(callId),
                "answers" to DesktopBridgeProtocol.answersJson(answers),
            ),
        ).jsonObject
        return data.bool("accepted") == true
    }

    suspend fun setArchived(sessionId: String, archived: Boolean) =
        setFlag(sessionId, if (archived) "sessions.archive" else "sessions.unarchive")

    suspend fun setPinned(sessionId: String, pinned: Boolean) =
        setFlag(sessionId, if (pinned) "sessions.pin" else "sessions.unpin")

    private suspend fun setFlag(sessionId: String, op: String) {
        val data = client.call(op, mapOf("sessionId" to JsonPrimitive(sessionId))).jsonObject
        applyFlags(sessionId, data.bool("archived") == true, data.bool("pinned") == true)
    }

    suspend fun rename(sessionId: String, title: String) {
        client.call("sessions.rename", mapOf("sessionId" to JsonPrimitive(sessionId), "title" to JsonPrimitive(title)))
        updateRow(sessionId) { it.copy(title = title) }
        stateFlow(sessionId).update { it.copy(title = title) }
    }

    /** 搜索电脑上的会话内容；返回 (sessionId, 片段)。 */
    suspend fun search(query: String): List<Pair<String, String>> {
        val data = client.call("sessions.search", mapOf("query" to JsonPrimitive(query))).jsonObject
        return (data["items"] as? JsonArray).orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item.string("sessionId") ?: return@mapNotNull null
            id to item.string("snippet").orEmpty()
        }
    }

    private fun applyFlags(sessionId: String, archived: Boolean, pinned: Boolean) =
        updateRow(sessionId) { it.copy(archived = archived, pinned = pinned) }

    // ---- 推送分发 ----------------------------------------------------------

    private suspend fun onConnected(hello: JsonObject) {
        _approvals.value = (hello["approvals"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(DesktopBridgeProtocol::parseApproval) }
        _questions.value = (hello["questions"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(DesktopBridgeProtocol::parseQuestion) }
        _server.value = DesktopBridgeProtocol.parseServerInfo(hello)
        if (!background) for (sessionId in followed.keys) sendFollow(sessionId)
        refreshSessions()
        refreshCatalog()
    }

    private fun dispatch(push: JsonObject) {
        val type = push.string("t") ?: return
        val sessionId = push.string("sessionId")
        when (type) {
            "snapshot", "item", "live", "projection", "follow-error" -> {
                sessionId?.let { id -> states[id]?.update { it.reduce(push) } }
                if (type == "projection") push.string("title")?.let { title -> updateRow(sessionId) { it.copy(title = title) } }
            }
            "follow-end" -> sessionId?.takeIf { followed.containsKey(it) }?.let { id -> scope.launch { delay(500); sendFollow(id) } }
            "status" -> {
                val running = push.bool("running") == true
                if (sessionId != null) {
                    val wasRunning = _sessions.value.rows.firstOrNull { it.id == sessionId }?.running == true
                    if (running) _unread.update { it - sessionId }
                    else if (wasRunning && !(isFollowing(sessionId) && !background)) _unread.update { it + sessionId }
                }
                sessionId?.let { id -> states[id]?.update { it.reduce(push) } }
                updateRow(sessionId) { it.copy(running = running) }
            }
            "activity" -> updateRow(sessionId) { it.copy(updatedAt = push.long("updatedAt") ?: it.updatedAt, blank = false) }
            "session" -> (push["session"] as? JsonObject)?.let(DesktopBridgeProtocol::parseSessionRow)?.let(::upsertRow)
            "approval" -> (push["approval"] as? JsonObject)?.let(DesktopBridgeProtocol::parseApproval)?.let { approval ->
                _approvals.update { list -> list.filterNot { it.id == approval.id } + approval }
            }
            "approval-done" -> push.string("approvalId")?.let { id -> _approvals.update { list -> list.filterNot { it.id == id } } }
            "question" -> (push["question"] as? JsonObject)?.let(DesktopBridgeProtocol::parseQuestion)?.let { question ->
                _questions.update { list -> list.filterNot { it.id == question.id } + question }
            }
            "question-done" -> push.string("questionId")?.let { id -> _questions.update { list -> list.filterNot { it.id == id } } }
            "turn" -> DesktopBridgeProtocol.parseTurn(push)?.let { _turns.tryEmit(it) }
            "error" -> if (sessionId != null) _errors.tryEmit(sessionId to push.string("message").orEmpty())
            "session-flags" -> sessionId?.let { applyFlags(it, push.bool("archived") == true, push.bool("pinned") == true) }
        }
    }

    private fun updateRow(sessionId: String?, transform: (DesktopSessionRow) -> DesktopSessionRow) {
        sessionId ?: return
        _sessions.update { state ->
            if (state.rows.none { it.id == sessionId }) state
            else state.copy(rows = state.rows.map { if (it.id == sessionId) transform(it) else it })
        }
    }

    private fun upsertRow(row: DesktopSessionRow) {
        _sessions.update { state ->
            val existing = state.rows.firstOrNull { it.id == row.id }
            val merged = if (existing == null) row else row.copy(
                title = row.title ?: existing.title,
                workspaceId = row.workspaceId ?: existing.workspaceId,
                pinned = existing.pinned,
                archived = existing.archived,
            )
            state.copy(rows = listOf(merged) + state.rows.filterNot { it.id == row.id })
        }
    }

    /** 上次的会话列表：冷启动时先显示，连上电脑后立即被真实列表替换。 */
    private fun loadCachedSessions() {
        val file = cacheFile ?: return
        val data = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(file.readText()).jsonObject }.getOrNull() ?: return
        val rows = (data["sessions"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.let(DesktopBridgeProtocol::parseSessionRow) }
            .map { it.copy(running = false) }
        val workspaces = (data["workspaces"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(DesktopBridgeProtocol::parseWorkspace) }
        if (rows.isNotEmpty()) _sessions.value = DesktopSessionsState(rows = rows, workspaces = workspaces, loaded = true, cached = true)
    }

    private fun saveCachedSessions(data: JsonObject) {
        val file = cacheFile ?: return
        scope.launch(Dispatchers.IO) { runCatching { file.writeText(data.toString()) } }
    }

    /** 只保留正在看的会话和最近几个缓存，控制内存。 */
    private fun trimCache() {
        if (states.size <= MAX_CACHED_SESSIONS) return
        states.keys.filterNot { followed.containsKey(it) }.take(states.size - MAX_CACHED_SESSIONS).forEach(states::remove)
    }

    // ---- 网络切换 ----------------------------------------------------------

    private fun registerNetworkCallback(context: Context) {
        if (networkCallback != null) return
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            private var current: Network? = null

            override fun onAvailable(network: Network) {
                val previous = current
                current = network
                // 默认网络换了（Wi-Fi ↔ 蜂窝），旧 TCP 连接基本已失效：立刻重连而不是等 ping 超时。
                if (previous != null && previous != network) client.reconnectNow(dropCurrent = true)
                else if (!client.isConnected) client.reconnectNow(dropCurrent = false)
            }

            override fun onLost(network: Network) {
                if (current == network) current = null
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }.onSuccess {
            connectivity = manager
            networkCallback = callback
        }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
        networkCallback = null
    }

    private const val IDLE_DISCONNECT_MS = 45_000L
    private const val FOREGROUND_HEARTBEAT_MS = 15_000L
    private const val BACKGROUND_HEARTBEAT_MS = 60_000L
    private const val SESSIONS_CACHE = "desktop-sessions.json"
    private const val MAX_CACHED_SESSIONS = 8
}

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
