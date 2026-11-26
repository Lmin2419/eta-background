package io.github.mangi.eta.ui.screens.desktop

import io.github.mangi.eta.ui.components.coui.rememberEtaBackdrop
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.style.TextOverflow
import io.github.mangi.eta.agent.remote.DesktopApproval
import top.yukonga.miuix.kmp.basic.Icon
import kotlinx.coroutines.delay
import io.github.mangi.eta.ui.components.ModelPill
import io.github.mangi.eta.ui.components.EtaIconButton
import io.github.mangi.eta.agent.remote.DesktopSubagent
import io.github.mangi.eta.agent.remote.DesktopItem
import androidx.compose.runtime.produceState
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.media.AgentImageCodec
import io.github.mangi.eta.agent.remote.DesktopBridge
import io.github.mangi.eta.ui.components.LocalRemoteComposer
import io.github.mangi.eta.ui.components.RemoteComposer
import io.github.mangi.eta.agent.remote.DesktopLinkSettings
import io.github.mangi.eta.agent.remote.DesktopBridgeClient
import io.github.mangi.eta.agent.remote.DesktopBridgeException
import io.github.mangi.eta.agent.remote.DesktopImage
import io.github.mangi.eta.ui.components.AdaptiveTopAppBar
import io.github.mangi.eta.ui.components.AgentChatBody
import io.github.mangi.eta.ui.components.MiuixBackButton
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.ConversationHistoryLoadState
import io.github.mangi.eta.ui.model.PendingImageUi
import io.github.mangi.eta.ui.model.PendingFileReferenceUi
import io.github.mangi.eta.agent.model.AgentFileReference
import io.github.mangi.eta.agent.model.AgentFileReferenceKind
import io.github.mangi.eta.agent.remote.DesktopFile
import io.github.mangi.eta.ui.components.LocalRemoteImageLoader
import io.github.mangi.eta.ui.model.UserMessageUi
import io.github.mangi.eta.ui.theme.EtaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.UUID

/**
 * 电脑端会话：消息流、输入栏、队列条与模型选择全部复用胖小胖原有聊天组件，
 * 只是动作改为发给桌面 DSH 执行。
 */
@Composable
internal fun DesktopChatScreen(
    sessionId: String,
    onBack: () -> Unit,
    parentId: String? = null,
    mode: String? = null,
    onOpenSubagent: (DesktopSubagent) -> Unit = {},
    /** 顶部任务胶囊 / 完成提示条里点手机对话、别的电脑会话、「查看全部」时打开。 */
    onOpenConversation: (String) -> Unit = {},
    onOpenDesktopSession: (String) -> Unit = {},
    onOpenTasks: () -> Unit = {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val isChild = parentId != null
    val readOnly = isChild && mode != "continuable"
    DisposableEffect(sessionId) {
        DesktopBridge.acquire(context)
        DesktopBridge.follow(sessionId, parentId, mode)
        onDispose {
            DesktopBridge.unfollow(sessionId)
            DesktopBridge.release()
        }
    }
    val state by DesktopBridge.sessionState(sessionId).collectAsState()
    val catalog by DesktopBridge.catalog.collectAsState()
    val connection by DesktopBridge.client.state.collectAsState()
    val approvals by DesktopBridge.approvals.collectAsState()
    val liveQuestions by DesktopBridge.questions.collectAsState()
    val linkPrefs by DesktopLinkSettings.state.collectAsState()
    val scope = rememberCoroutineScope()
    // 正在等待的提问优先；前台等待已结束的限时提问也能在这里补答。
    val asks = remember(liveQuestions, state.pendingQuestions, sessionId) {
        val live = liveQuestions.filter { it.sessionId == sessionId }
            .map { DesktopAsk("q:${it.id}", sessionId, it.items, questionId = it.id, callId = it.callId) }
        val liveCalls = live.mapNotNullTo(HashSet()) { it.callId }
        val later = state.pendingQuestions.filter { it.continued && it.callId !in liveCalls }
            .map { DesktopAsk("c:${it.callId}", sessionId, it.items, callId = it.callId) }
        live + later
    }
    var openAsk by remember(sessionId) { mutableStateOf<String?>(null) }
    // 提问不再自动弹出：输入框上方出现卡片，点「回答」才打开作答面板。
    LaunchedEffect(asks) {
        if (openAsk != null && asks.none { it.key == openAsk }) openAsk = null
    }
    val sessionApprovals = remember(approvals, sessionId) { approvals.filter { it.sessionId == sessionId } }
    val sessionsList by DesktopBridge.sessions.collectAsState()
    val workspaceTitle = remember(sessionsList, sessionId) {
        val row = sessionsList.rows.firstOrNull { it.id == sessionId }
        row?.workspaceId?.let { id -> sessionsList.workspaces.firstOrNull { it.id == id }?.title }
    }

    val labels = rememberDesktopLabels()
    // 已写进记录的部分只在记录变化时重算；流式输出每来一小段字，只重算末尾正在生成的部分。
    val committed = remember(state.items, labels) { DesktopChatProjector.committed(state.items, labels) }
    val messages = remember(committed, state.echoes, state.live, state.queue, state.handoff) { DesktopChatProjector.merge(committed, state) }
    val queued = remember(state.queue, state.echoes) { DesktopChatProjector.queued(state) }
    // 快照到达前先用列表里的标题，不显示成「新对话」。
    val listTitle = remember(sessionId) { DesktopBridge.sessions.value.rows.firstOrNull { it.id == sessionId }?.title }
    val picker = remember(catalog, state.model) { DesktopChatProjector.modelPicker(catalog, state) }
    val efforts = remember(catalog, state.model) { DesktopChatProjector.reasoningEfforts(catalog, state) }
    var input by remember(sessionId) { mutableStateOf(DesktopBridge.draft(sessionId)) }
    var inputSync by remember(sessionId) { mutableIntStateOf(0) }
    var pendingImages by remember(sessionId) { mutableStateOf(emptyList<PendingImageUi>()) }
    var pendingFiles by remember(sessionId) { mutableStateOf(emptyList<PendingFileReferenceUi>()) }
    val server by DesktopBridge.server.collectAsState()
    val filesSupported = server?.supports("files") == true
    // 分支、重新回答、编辑重发都按 DSH 事件序号切分支（插件 0.6.0 起）；子代理会话不能分支。
    val canFork = !isChild && server?.supports("fork") == true
    val points = remember(state.items) { DesktopChatProjector.branchPoints(state.items) }
    var branching by remember(sessionId) { mutableStateOf(false) }
    var retrying by remember(sessionId) { mutableStateOf(false) }
    var editing by remember(sessionId) { mutableStateOf<io.github.mangi.eta.ui.model.MessageEditUiState?>(null) }
    var draftText by remember(sessionId) { mutableStateOf(input) }
    var commands by remember(sessionId) { mutableStateOf(emptyList<io.github.mangi.eta.agent.remote.DesktopCommand>()) }
    val commandsSupported = !isChild && server?.supports("commands") == true
    LaunchedEffect(sessionId, commandsSupported, connection is DesktopBridgeClient.State.Connected) {
        if (commandsSupported && connection is DesktopBridgeClient.State.Connected) {
            commands = try { DesktopBridge.commands(sessionId) } catch (_: DesktopBridgeException) { emptyList() }
        }
    }
    val pluginUpdate = stringResource(R.string.desktop_plugin_update_needed)
    val branchedText = stringResource(R.string.desktop_branched)
    val filesNotResent = stringResource(R.string.desktop_branch_files_not_resent)
    val reaskUnavailable = stringResource(R.string.desktop_reask_unavailable)
    val retryResent = stringResource(R.string.desktop_retry_resent)
    val commandFailed = stringResource(R.string.desktop_command_failed)
    val unsupported = stringResource(R.string.desktop_action_unsupported)
    val readOnlyText = stringResource(R.string.desktop_subagent_readonly)
    var showSettings by remember(sessionId) { mutableStateOf(false) }
    var subagentDirectory by remember(sessionId) { mutableStateOf<List<DesktopSubagent>?>(null) }
    var showSubagents by remember(sessionId) { mutableStateOf(false) }
    // 运行中的本轮已用时长，每秒刷新（对应桌面「深度求索中，用时 …」）。
    val turnStartedAt = remember(state.items, state.running) {
        if (!state.running) null else {
            val lastStart = state.items.lastOrNull { it is DesktopItem.TurnStart }
            val lastEnd = state.items.lastOrNull { it is DesktopItem.TurnEnd }
            lastStart?.takeIf { lastEnd == null || it.seq > lastEnd.seq }?.time
        }
    }
    val runningForMs by produceState<Long?>(null, turnStartedAt) {
        if (turnStartedAt == null) { value = null; return@produceState }
        while (true) {
            value = (System.currentTimeMillis() - turnStartedAt).coerceAtLeast(0)
            delay(1_000)
        }
    }
    val attachmentsUnsupported = stringResource(R.string.desktop_attachments_unsupported)

    // 导出：先把电脑上更早的记录都取回来（最多 30 页），再按手机对话的格式写成 Markdown。
    val latestTitle = rememberUpdatedState(state.title?.takeIf { it.isNotBlank() } ?: listTitle?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.desktop_untitled))
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                repeat(30) {
                    if (!DesktopBridge.sessionState(sessionId).value.hasMore) return@repeat
                    DesktopBridge.loadOlder(sessionId)
                }
                val all = DesktopChatProjector.messages(DesktopBridge.sessionState(sessionId).value, labels)
                val markdown = io.github.mangi.eta.ui.app.ConversationMarkdownExporter.export(latestTitle.value, all,
                    io.github.mangi.eta.ui.app.ConversationMarkdownExporter.labels(context))
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(markdown.toByteArray(Charsets.UTF_8)) } ?: error("no output")
                }
            }.isSuccess
            context.toast(resources.getString(if (ok) R.string.conversation_exported else R.string.conversation_export_failed))
        }
    }
    val exportDefaultName = stringResource(R.string.conversation_export_default_name)
    fun exportConversation() {
        exportLauncher.launch(io.github.mangi.eta.ui.app.ConversationMarkdownExporter.defaultFileName(latestTitle.value, exportDefaultName))
    }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (error: DesktopBridgeException) {
                context.toast(error.display(resources))
            }
        }
    }

    fun setInput(text: String) {
        input = text
        draftText = text
        inputSync++
    }

    /** 电脑上存好的图片附件：取回原图，重发时原样带上。 */
    suspend fun imagesOf(refs: List<kotlinx.serialization.json.JsonObject>): List<DesktopImage> =
        refs.mapNotNull { ref -> DesktopBridge.image(DesktopBridge.imageAddress(ref))?.let(::desktopImageOf) }

    /**
     * 切出分支并打开它：[atSeq] 是切点（为空时切在最近一轮结束处），[fresh] 时不切而是在同一工作区新建会话
     * （前面没有可保留的内容）。[text] 不为空时在新分支里把这段内容（连同原来的图片）发出去。
     */
    fun branchFrom(
        atSeq: Long?,
        fresh: Boolean = false,
        text: String? = null,
        imageRefs: List<kotlinx.serialization.json.JsonObject> = emptyList(),
        files: List<String> = emptyList(),
    ) {
        if (branching) return
        if (!canFork) { context.toast(pluginUpdate); return }
        branching = true
        scope.launch {
            try {
                val row = DesktopBridge.sessions.value.rows.firstOrNull { it.id == sessionId }
                val newId = if (fresh) DesktopBridge.createSession(row?.workspaceId, row?.cwd) else DesktopBridge.fork(sessionId, atSeq)
                if (text != null) {
                    val images = imagesOf(imageRefs)
                    if (text.isNotBlank() || images.isNotEmpty()) {
                        DesktopBridge.prompt(newId, text, images = images, previews = imageRefs.map(DesktopBridge::imageAddress))
                    } else {
                        DesktopBridge.setDraft(newId, text)
                    }
                    if (files.isNotEmpty()) context.toast(filesNotResent)
                } else {
                    context.toast(branchedText)
                }
                onOpenDesktopSession(newId)
            } catch (error: DesktopBridgeException) {
                context.toast(error.display(resources))
            } finally {
                branching = false
            }
        }
    }

    fun reask(rewind: DesktopRewind?) {
        if (rewind == null) { context.toast(reaskUnavailable); return }
        branchFrom(rewind.atSeq, fresh = rewind.atSeq == null, text = rewind.prompt.text, imageRefs = rewind.prompt.imageRefs, files = rewind.prompt.files)
    }

    /** 上一轮出错：先请电脑原地重试；它的 DSH 不支持时重发开启这一轮的消息。 */
    fun retryFailed(prompt: DesktopPrompt) {
        if (retrying) return
        retrying = true
        scope.launch {
            try {
                try {
                    DesktopBridge.retryTurn(sessionId)
                } catch (error: DesktopBridgeException) {
                    if (error.code != "unsupported" && error.code != "unknown-op") throw error
                    DesktopBridge.prompt(sessionId, prompt.text, images = imagesOf(prompt.imageRefs), previews = prompt.imageRefs.map(DesktopBridge::imageAddress))
                    context.toast(retryResent)
                }
            } catch (error: DesktopBridgeException) {
                context.toast(error.display(resources))
            } finally {
                retrying = false
            }
        }
    }

    /**
     * 在电脑上执行斜杠命令；命令不存在时（[fallback] 不为空）把原文当普通消息发出。
     * 没执行成（断线等）时把 [restore] 放回输入框，不让用户重打一遍。
     */
    fun runSlash(line: String, restore: String? = null, fallback: (() -> Unit)? = null) {
        scope.launch {
            try {
                val result = DesktopBridge.runCommand(sessionId, line)
                if (!result.success) context.toast(commandFailed.format(result.text.orEmpty().take(160)))
            } catch (error: DesktopBridgeException) {
                if (error.code == "unknown-command" && fallback != null) fallback()
                else {
                    context.toast(error.display(resources))
                    if (restore != null && DesktopBridge.draft(sessionId).isEmpty()) setInput(restore)
                }
            }
        }
    }

    /** 把消息（连同待发送的图片、文件）发给电脑。 */
    fun sendPrompt(text: String, interrupt: Boolean, steer: Boolean) {
        val images = pendingImages
        val files = pendingFiles
        pendingImages = emptyList()
        pendingFiles = emptyList()
        input = ""
        DesktopBridge.setDraft(sessionId, "")
        scope.launch {
            try {
                val encoded = withContext(Dispatchers.IO) { images.mapNotNull { encodeImage(context, it) } }
                val payload = withContext(Dispatchers.IO) { files.mapNotNull { readFile(context, it.reference) } }
                if (payload.sumOf { it.base64.length } > MAX_FILE_BASE64) throw DesktopBridgeException("too-large", resources.getString(R.string.desktop_files_too_large))
                DesktopBridge.prompt(sessionId, text, images = encoded, interrupt = interrupt, steer = steer, files = payload,
                    previews = images.map { it.dataUrl.ifBlank { it.uri } })
            } catch (error: DesktopBridgeException) {
                // 没发出去（多半是断线）：文字、图片和文件放回输入框，不让用户重选一遍。
                context.toast(error.display(resources))
                if (DesktopBridge.draft(sessionId).isEmpty()) setInput(text)
                if (pendingImages.isEmpty()) pendingImages = images
                if (pendingFiles.isEmpty()) pendingFiles = files
            }
        }
    }

    /** 发送。[steer] 为插队：不停下当前工作，这一步结束就送达；[interrupt] 为先停止再发送。 */
    fun submit(text: String, interrupt: Boolean = false, steer: Boolean = false) {
        if (readOnly) {
            context.toast(readOnlyText)
            setInput(text)
            return
        }
        // 编辑历史消息：电脑的记录不能改写，改为从这条消息之前切出分支，在分支里发改好的内容。
        editing?.let { edit ->
            editing = null
            setInput(edit.previousInput)
            DesktopBridge.setDraft(sessionId, edit.previousInput)
            val rewind = points.edit[edit.targetMessageId] ?: return
            branchFrom(rewind.atSeq, fresh = rewind.atSeq == null, text = text, imageRefs = rewind.prompt.imageRefs, files = rewind.prompt.files)
            return
        }
        // 「/命令」在电脑上执行（和桌面输入框一样不发给模型）；电脑没有这个命令时按普通消息发。
        val line = text.trim()
        val commandName = line.removePrefix("/").substringBefore(' ').substringBefore('\n').lowercase()
        if (commandsSupported && line.startsWith("/") && commandName.isNotEmpty() && pendingImages.isEmpty() && pendingFiles.isEmpty() &&
            (commands.isEmpty() || commands.any { it.name == commandName })) {
            input = ""
            draftText = ""
            DesktopBridge.setDraft(sessionId, "")
            runSlash(line, restore = text) { sendPrompt(text, interrupt, steer) }
            return
        }
        sendPrompt(text, interrupt, steer)
    }

    fun openSubagents() {
        showSubagents = true
        subagentDirectory = null
        scope.launch {
            subagentDirectory = try {
                DesktopBridge.subagentDirectory(sessionId)
            } catch (error: DesktopBridgeException) {
                context.toast(error.display(resources))
                emptyList()
            }
        }
    }

    fun answerApproval(approval: DesktopApproval, allow: Boolean) = run { DesktopBridge.answerApproval(approval.id, allow) }

    // 输入框上方：运行状态条、审批卡、提问卡。用最新状态绘制（RemoteComposer 本身只创建一次）。
    val aboveInput = rememberUpdatedState<@Composable () -> Unit> {
        Column {
            // 输入「/」：列出电脑上这个会话能用的命令。
            val slash = draftText.takeIf { it.startsWith("/") && ' ' !in it && '\n' !in it }?.drop(1)?.lowercase()
            val matches = if (slash != null) commands.filter { it.name.startsWith(slash) }.take(6) else emptyList()
            AnimatedVisibility(
                visible = matches.isNotEmpty(),
                enter = fadeIn(tween(EtaTheme.motion.short)) + expandVertically(tween(EtaTheme.motion.medium, easing = EtaTheme.motion.emphasizedDecelerate)),
                exit = fadeOut(tween(EtaTheme.motion.exitDuration(EtaTheme.motion.short))) + shrinkVertically(tween(EtaTheme.motion.exitDuration(EtaTheme.motion.medium))),
            ) {
                DesktopCommandSuggestions(matches) { command -> setInput("/${command.name} ") }
            }
            AnimatedVisibility(
                visible = state.running && !readOnly,
                enter = fadeIn(tween(EtaTheme.motion.short)) + expandVertically(tween(EtaTheme.motion.medium, easing = EtaTheme.motion.emphasizedDecelerate)),
                exit = fadeOut(tween(EtaTheme.motion.exitDuration(EtaTheme.motion.short))) + shrinkVertically(tween(EtaTheme.motion.exitDuration(EtaTheme.motion.medium))),
            ) {
                DesktopRunStrip(runningForMs, onStop = { run { DesktopBridge.cancel(sessionId) } }, onDetails = { showSettings = true })
            }
            sessionApprovals.firstOrNull()?.let { approval ->
                DesktopApprovalCard(approval) { allow -> answerApproval(approval, allow) }
            }
            if (asks.isNotEmpty()) {
                DesktopAskCard(asks.size, plan = asks.first().isPlanReview) { openAsk = asks.first().key }
            }
            val failed = points.failedPrompt
            if (!isChild && failed != null && !state.running && state.echoes.isEmpty() && state.queue.isEmpty() && editing == null) {
                val lastError = (state.items.lastOrNull { it is DesktopItem.TurnEnd } as? DesktopItem.TurnEnd)?.message
                DesktopRetryCard(lastError, busy = retrying) { retryFailed(failed) }
            }
        }
    }

    // 和手机对话首页一样：消息延伸到顶部控件下面，控件和输入框共用消息区的 COUI 采样源（液态玻璃透出背后的消息），
    // 顶部是同一条渐隐 + 轻微渐进模糊（ChatScrollEdge），不再是一条实底的顶栏。见 DesktopChatFrame。
    // 与手机对话同一套顶部：左边玻璃返回钮，中间玻璃胶囊（上行会话标题、下行「电脑 · 工作区」，
    // 点一下打开会话设置，模型也在那里选），右边玻璃铃铛和子代理。
    val title = state.title?.takeIf { it.isNotBlank() } ?: listTitle?.takeIf { it.isNotBlank() } ?: stringResource(R.string.desktop_untitled)
    val subtitle = listOfNotNull(stringResource(R.string.desktop_title), workspaceTitle?.takeIf { it.isNotBlank() }).joinToString(" · ")
    DesktopChatFrame(
        title = title,
        subtitle = subtitle,
        onBack = onBack,
        onTitle = if (!isChild) ({ showSettings = true }) else null,
        // 连着电脑、会话已加载时消息延伸到顶部控件下面；断线横幅、加载中仍从顶栏下方开始。
        underTopBar = connection is DesktopBridgeClient.State.Connected && state.loaded,
        sessionId = sessionId,
        onOpenConversation = onOpenConversation,
        onOpenDesktopSession = onOpenDesktopSession,
        onOpenTasks = onOpenTasks,
        trailing = {
                if (!isChild) {
                    val remind = linkPrefs.reminds(sessionId)
                    io.github.mangi.eta.ui.components.CircleIconButton(
                        if (remind) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                        stringResource(R.string.desktop_session_remind),
                        onClick = { DesktopLinkSettings.setSessionReminders(sessionId, !remind) },
                        tint = if (remind) EtaTheme.colors.accent else EtaTheme.colors.textTertiary,
                    )
                }
                if (!isChild && state.subagents.isNotEmpty()) {
                    Box {
                        io.github.mangi.eta.ui.components.CircleIconButton(Icons.Outlined.Hub,
                            stringResource(R.string.desktop_subagents), onClick = ::openSubagents)
                        Text(
                            state.subagents.size.toString(),
                            style = EtaTheme.type.caption,
                            color = EtaTheme.colors.onAccent,
                            modifier = Modifier.align(Alignment.TopEnd)
                                .background(EtaTheme.colors.accent, EtaTheme.shapes.radiusFull)
                                .padding(horizontal = 4.dp),
                        )
                    }
                }
                if (isChild) Box(Modifier.padding(horizontal = 12.dp)) { DesktopRunningDot(state.running) }
        },
    ) {
            if (connection !is DesktopBridgeClient.State.Connected) {
                DesktopConnectionBanner(connection, configured = true, onRetry = DesktopBridge::retry)
                if (state.loaded) {
                    Text(
                        text = stringResource(R.string.desktop_offline_hint),
                        style = MiuixTheme.textStyles.footnote2,
                        color = EtaTheme.colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            if (!state.loaded && state.error == null) {
                // 打开会话时用占位条代替转圈，内容到了直接替换。
                io.github.mangi.eta.ui.components.EtaSkeletonChat(Modifier.weight(1f))
                return@DesktopChatFrame
            }
            if (!state.loaded) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error ?: stringResource(R.string.desktop_loading),
                            style = MiuixTheme.textStyles.footnote1,
                            color = EtaTheme.colors.textSecondary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                return@DesktopChatFrame
            }
            val remoteComposer = remember(sessionId, isChild) {
                RemoteComposer(
                    workingHint = resources.getString(R.string.desktop_working_hint),
                    idleHint = resources.getString(R.string.desktop_idle_hint),
                    onDraftChange = { text -> draftText = text; DesktopBridge.setDraft(sessionId, text) },
                    aboveInput = { aboveInput.value() },
                )
            }
            // 电脑会话的多选是自己的一份，不和首页对话共用（09 第五期 5.1）。
            val desktopSelection = androidx.compose.runtime.remember(sessionId) { io.github.mangi.eta.ui.components.MessageSelectionState() }
            CompositionLocalProvider(LocalRemoteComposer provides remoteComposer, LocalRemoteImageLoader provides DesktopBridge::image,
                io.github.mangi.eta.ui.components.LocalMessageSelection provides desktopSelection,
                // 分支按钮只出现在桌面也能分支的回复上；历史不能删；「导出」导出这个电脑会话。
                io.github.mangi.eta.ui.components.LocalBranchableMessageIds provides (if (canFork) points.branchAt.keys else emptySet()),
                io.github.mangi.eta.ui.components.LocalMessageDeleteSupported provides false,
                io.github.mangi.eta.ui.components.LocalChatHomeShortcuts provides
                    io.github.mangi.eta.ui.components.LocalChatHomeShortcuts.current.copy(onExportConversation = { exportConversation() })) {
            io.github.mangi.eta.ui.components.ChatImagePreviewHost {
            AgentChatBody(
                isBranching = branching,
                onBranchMessage = { id -> points.branchAt[id]?.let { seq -> branchFrom(seq) } },
                messages = messages,
                modelPickerState = picker,
                isCompacting = false,
                input = input,
                isStreaming = state.running,
                reasoningEffort = efforts.second,
                availableReasoningEfforts = efforts.first,
                pendingImages = pendingImages,
                pendingFileReferences = pendingFiles,
                queuedMessages = queued,
                inputSyncVersion = inputSync,
                messageEdit = editing,
                onReasoningEffortChange = { effort ->
                    (state.model ?: catalog?.default)?.let { selection ->
                        run { DesktopBridge.selectModel(sessionId, selection.provider, selection.model, effort.wireValue) }
                    }
                },
                onCompactContext = { runSlash("/compact") },
                canCompactContext = !state.running && commands.any { it.name == "compact" },
                onModelSelected = { id ->
                    DesktopChatProjector.parseOptionId(id)?.let { (provider, model) ->
                        run { DesktopBridge.selectModel(sessionId, provider, model, null) }
                    }
                },
                onSubmit = { text -> submit(text) },
                onInterruptAndSubmit = { text -> submit(text, interrupt = true) },
                // 排队条上的操作对刚发出、电脑还没回执的消息同样有效，并且立即在界面上体现。
                onSteerQueuedMessage = { id -> run { DesktopBridge.steer(sessionId, id) } },
                onRecallQueuedMessage = { id, draft ->
                    val recalled = queued.firstOrNull { it.id == id }?.content.orEmpty()
                    scope.launch {
                        try {
                            DesktopBridge.removeQueued(sessionId, id)
                            setInput(listOf(recalled, draft).filter { it.isNotBlank() }.joinToString("\n"))
                        } catch (error: DesktopBridgeException) {
                            context.toast(error.display(resources))
                        }
                    }
                },
                onRemoveQueuedMessage = { id -> run { DesktopBridge.removeQueued(sessionId, id) } },
                onStop = { run { DesktopBridge.cancel(sessionId) } },
                onAttachImage = { uri ->
                    scope.launch {
                        val pending = withContext(Dispatchers.IO) { loadPendingImage(context, uri) }
                        if (pending != null) pendingImages = pendingImages + pending
                    }
                },
                onRemoveImage = { id -> pendingImages = pendingImages.filterNot { it.id == id } },
                onAttachFiles = { uris ->
                    if (!filesSupported) context.toast(attachmentsUnsupported)
                    else pendingFiles = (pendingFiles + uris.map { uri ->
                        PendingFileReferenceUi("file-${UUID.randomUUID()}", AgentFileReference(displayNameOf(context, uri), uri, AgentFileReferenceKind.File))
                    }).take(8)
                },
                onAttachFolder = { context.toast(attachmentsUnsupported) },
                onAttachFilePath = { context.toast(attachmentsUnsupported) },
                onRemoveFileReference = { id -> pendingFiles = pendingFiles.filterNot { it.id == id } },
                onEditMessage = { id ->
                    val rewind = points.edit[id]
                    if (canFork && rewind != null) {
                        // 和手机对话一样就地编辑；点「编辑重发」时从这条消息之前切出分支，在分支里发改好的内容。
                        editing = io.github.mangi.eta.ui.model.MessageEditUiState(
                            targetMessageId = id, previousInput = input, previousImages = emptyList(),
                            previousFileReferences = emptyList(), hasLaterTurns = true,
                        )
                        setInput(rewind.prompt.text)
                    } else {
                        // 插件太旧不能分支：把原文放回输入框，改完作为新消息发送。
                        (messages.firstOrNull { it.id == id } as? UserMessageUi)?.let { setInput(it.content) }
                            ?: (messages.firstOrNull { it.id == id } as? AgentMessageUi)?.let { setInput(it.content) }
                    }
                },
                onCancelMessageEdit = {
                    editing?.let { setInput(it.previousInput) }
                    editing = null
                },
                onDeleteMessage = { context.toast(unsupported) },
                onRegenerateMessage = { id -> if (canFork) reask(points.reask[id]) else context.toast(pluginUpdate) },
                onSelectReplyCandidate = { _, _ -> },
                onSuggestionClick = {},
                onRunTraceClick = {},
                onOpenBrowser = {},
                hasEarlierMessages = state.hasMore,
                historyLoadState = if (state.loadingOlder) ConversationHistoryLoadState.Loading else ConversationHistoryLoadState.Idle,
                onLoadEarlierMessages = { scope.launch { DesktopBridge.loadOlder(sessionId) } },
                modifier = Modifier.weight(1f),
            )
            }
            }
    }
    DesktopQuestionSheet(
        ask = asks.firstOrNull { it.key == openAsk },
        onDismiss = { openAsk = null },
    )
    DesktopSessionSheet(
        show = showSettings,
        state = state,
        picker = picker,
        efforts = efforts.first,
        effort = efforts.second,
        allowModelChange = !isChild,
        busy = state.running,
        remindOn = if (isChild) null else linkPrefs.reminds(sessionId),
        onRemind = { on -> DesktopLinkSettings.setSessionReminders(sessionId, on) },
        onDismiss = { showSettings = false },
        onModel = { id ->
            showSettings = false
            DesktopChatProjector.parseOptionId(id)?.let { (provider, model) ->
                run { DesktopBridge.selectModel(sessionId, provider, model, null) }
            }
        },
        onEffort = { effort ->
            (state.model ?: catalog?.default)?.let { selection ->
                run { DesktopBridge.selectModel(sessionId, selection.provider, selection.model, effort.wireValue) }
            }
        },
        onBranch = if (canFork) ({ showSettings = false; branchFrom(null) }) else null,
        onCompact = if (commands.any { it.name == "compact" }) ({ showSettings = false; runSlash("/compact") }) else null,
        onExport = if (!isChild) ({ showSettings = false; exportConversation() }) else null,
    )
    DesktopSubagentSheet(
        show = showSubagents,
        children = subagentDirectory,
        onDismiss = { showSubagents = false },
        onOpen = { child ->
            showSubagents = false
            onOpenSubagent(child)
        },
    )
}

/** 一次最多发约 10 MB（base64 后），插件单条消息上限 16 MB。 */
private const val MAX_FILE_BASE64 = 14_000_000

private fun displayNameOf(context: Context, uri: String): String = runCatching {
    context.contentResolver.query(android.net.Uri.parse(uri), arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull() ?: uri.substringAfterLast('/').ifBlank { "file" }

private fun readFile(context: Context, reference: AgentFileReference): DesktopFile? = runCatching {
    val bytes = context.contentResolver.openInputStream(android.net.Uri.parse(reference.absolutePath))?.use { it.readBytes() } ?: return null
    DesktopFile(reference.displayName, android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
}.getOrNull()

private fun Context.toast(text: String) {
    io.github.mangi.eta.ui.components.EtaToastCenter.show(this, text, Toast.LENGTH_SHORT)
}

private fun loadPendingImage(context: Context, uri: String): PendingImageUi? {
    val image = AgentImageCodec.fromReference(context, uri, source = "desktop_attach") ?: return null
    val preview = AgentImageCodec.previewFromReference(context, image) ?: image
    return PendingImageUi(
        id = "img-${UUID.randomUUID()}",
        uri = image.reference,
        dataUrl = preview.reference,
        mimeType = image.mimeType,
    )
}

/** 待发送图片的稳定引用是 data URL；取出 base64 直接交给桌面。 */
private fun encodeImage(context: Context, pending: PendingImageUi): DesktopImage? {
    val reference = pending.uri.takeIf { it.startsWith("data:") }
        ?: AgentImageCodec.fromReference(context, pending.uri, source = "desktop_attach")?.reference
        ?: return null
    val header = reference.substringBefore(",")
    val base64 = reference.substringAfter("base64,", "")
    if (base64.isEmpty()) return null
    val mediaType = header.removePrefix("data:").substringBefore(";").ifBlank { pending.mimeType }
    return DesktopImage(mediaType, base64)
}

/**
 * 电脑会话页的外框，与手机对话首页同一套做法：顶部是浮在消息上的玻璃控件（[GlassPageTopBar]），
 * 下方一条从页面底色到透明的渐隐叠轻微渐进模糊（[ChatScrollEdge]）；消息延伸到控件下面（[underTopBar]），
 * 列表自己留出顶栏高度（LocalTopBarInset）；顶部控件和输入框共用消息区的 COUI 采样源（LocalChatCouiSource）。
 */
@Composable
internal fun DesktopChatFrame(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onTitle: (() -> Unit)?,
    underTopBar: Boolean,
    trailing: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    sessionId: String? = null,
    onOpenConversation: (String) -> Unit = {},
    onOpenDesktopSession: (String) -> Unit = {},
    onOpenTasks: () -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val edgeBackdrop = io.github.mangi.eta.ui.components.trackRenderSource("电脑会话顶部渐隐(Miuix)",
        io.github.mangi.eta.ui.components.rememberTopBarBackdrop())
    val pageSource = io.github.mangi.eta.ui.components.trackRenderSource("电脑会话页(COUI)",
        if (io.github.mangi.eta.ui.app.LocalBlurEnabled.current && io.github.mangi.eta.ui.components.EtaCouiSupport.shaders)
            rememberEtaBackdrop(diagnosticName = "desktop-page") else null)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = EtaTheme.colors.canvas,
        topBar = {
            io.github.mangi.eta.ui.components.ChatScrollEdge(edgeBackdrop) {
                CompositionLocalProvider(io.github.mangi.eta.ui.components.coui.LocalEtaCouiBackdrop provides pageSource) {
                    io.github.mangi.eta.ui.components.GlassPageTopBar(
                        title = title, subtitle = subtitle, onBack = onBack, onTitle = onTitle,
                        sourceName = "电脑会话顶部(COUI)", ownBackdrop = false, trailing = trailing,
                    )
                    // 与手机对话首页一样：控件下方是任务胶囊（手机上在跑的任务、别的电脑会话）和「已完成 · 查看」提示条。
                    // 正在看的这个电脑会话不进胶囊、不弹提示。
                    io.github.mangi.eta.ui.components.CappedFontScale {
                        androidx.compose.foundation.layout.Column {
                            io.github.mangi.eta.ui.components.HomeDoneNotice(null, onOpenConversation, onOpenDesktopSession,
                                currentDesktopSessionId = sessionId)
                            io.github.mangi.eta.ui.components.HomeTaskCapsule(null, onOpenTasks, onOpenConversation, onOpenDesktopSession,
                                currentDesktopSessionId = sessionId)
                        }
                    }
                }
            }
        },
    ) { padding ->
        val topPadding = padding.calculateTopPadding()
        Column(Modifier.fillMaxSize().then(io.github.mangi.eta.ui.components.captureForTopBarModifier(edgeBackdrop))
            .padding(top = if (underTopBar) 0.dp else topPadding)) {
            CompositionLocalProvider(
                io.github.mangi.eta.ui.components.coui.LocalChatCouiSource provides pageSource,
                io.github.mangi.eta.ui.components.LocalTopBarInset provides
                    if (underTopBar) (topPadding - io.github.mangi.eta.ui.components.ChatScrollEdgeTail).coerceAtLeast(0.dp) else 0.dp,
            ) { content() }
        }
    }
}

/** data URL → 发给电脑的图片（重发电脑上已有的图片时用）。 */
private fun desktopImageOf(dataUrl: String): DesktopImage? {
    val header = dataUrl.substringBefore(",")
    val base64 = dataUrl.substringAfter("base64,", "")
    if (!dataUrl.startsWith("data:") || base64.isEmpty()) return null
    return DesktopImage(header.removePrefix("data:").substringBefore(";").ifBlank { "image/png" }, base64)
}
