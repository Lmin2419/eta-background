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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
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

    private val states = ConcurrentHashMap<String, MutableStateFlow<DesktopSessionState>>()
    private val followed = ConcurrentHashMap.newKeySet<String>()

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
        if (store == null) store = DesktopBridgeConfigStore(appContext)
        if (_config.value == null) _config.value = runCatching { store?.load() }.getOrNull()
        registerNetworkCallback(appContext)
        val config = _config.value
        if (config != null && config.isComplete && client.state.value is DesktopBridgeClient.State.Idle) client.start(config)
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
        states.clear()
        if (config.isComplete) client.start(config) else client.stop()
    }

    fun clearConfig(context: Context) {
        (store ?: DesktopBridgeConfigStore(context.applicationContext)).clear()
        _config.value = null
        client.stop()
        _sessions.value = DesktopSessionsState()
        states.clear()
    }

    fun retry() {
        val config = _config.value ?: return
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

    /** 打开会话：已有缓存时立刻可见，并按 cursor 续订只补差量。 */
    fun follow(sessionId: String) {
        followed += sessionId
        stateFlow(sessionId)
        if (client.isConnected) scope.launch { sendFollow(sessionId) }
    }

    fun unfollow(sessionId: String) {
        followed -= sessionId
        if (client.isConnected) scope.launch {
            runCatching { client.call("sessions.unfollow", mapOf("sessionId" to JsonPrimitive(sessionId))) }
        }
        trimCache()
    }

    private suspend fun sendFollow(sessionId: String) {
        val state = stateFlow(sessionId).value
        val params = buildMap<String, JsonElement> {
            put("sessionId", JsonPrimitive(sessionId))
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
        flow.update { it.addEcho(DesktopEcho(requestId, text, queued = running && !interrupt && !steer)) }
        try {
            if (interrupt && running) runCatching { cancel(sessionId) }
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
        client.call("sessions.cancel", mapOf("sessionId" to JsonPrimitive(sessionId)))
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
            val data = client.call("sessions.older", mapOf("sessionId" to JsonPrimitive(sessionId), "beforeSeq" to JsonPrimitive(before))).jsonObject
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

    // ---- 推送分发 ----------------------------------------------------------

    private suspend fun onConnected(hello: JsonObject) {
        _approvals.value = (hello["approvals"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(DesktopBridgeProtocol::parseApproval) }
        for (sessionId in followed) sendFollow(sessionId)
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
            "follow-end" -> sessionId?.takeIf { it in followed }?.let { id -> scope.launch { delay(500); sendFollow(id) } }
            "status" -> {
                val running = push.bool("running") == true
                sessionId?.let { id -> states[id]?.update { it.reduce(push) } }
                updateRow(sessionId) { it.copy(running = running) }
            }
            "activity" -> updateRow(sessionId) { it.copy(updatedAt = push.long("updatedAt") ?: it.updatedAt, blank = false) }
            "session" -> (push["session"] as? JsonObject)?.let(DesktopBridgeProtocol::parseSessionRow)?.let(::upsertRow)
            "approval" -> (push["approval"] as? JsonObject)?.let(DesktopBridgeProtocol::parseApproval)?.let { approval ->
                _approvals.update { list -> list.filterNot { it.id == approval.id } + approval }
            }
            "approval-done" -> push.string("approvalId")?.let { id -> _approvals.update { list -> list.filterNot { it.id == id } } }
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
            val merged = if (existing == null) row else row.copy(title = row.title ?: existing.title, workspaceId = row.workspaceId ?: existing.workspaceId, pinned = existing.pinned)
            state.copy(rows = listOf(merged) + state.rows.filterNot { it.id == row.id })
        }
    }

    /** 只保留正在看的会话和最近几个缓存，控制内存。 */
    private fun trimCache() {
        if (states.size <= MAX_CACHED_SESSIONS) return
        states.keys.filterNot { it in followed }.take(states.size - MAX_CACHED_SESSIONS).forEach(states::remove)
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
    private const val MAX_CACHED_SESSIONS = 8
}

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
