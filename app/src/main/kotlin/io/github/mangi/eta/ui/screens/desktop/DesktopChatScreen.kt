package io.github.mangi.eta.ui.screens.desktop

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
        inputSync++
    }

    /** 发送。[steer] 为插队：不停下当前工作，这一步结束就送达；[interrupt] 为先停止再发送。 */
    fun submit(text: String, interrupt: Boolean = false, steer: Boolean = false) {
        if (readOnly) {
            context.toast(readOnlyText)
            setInput(text)
            return
        }
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
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AdaptiveTopAppBar(
                title = state.title?.takeIf { it.isNotBlank() } ?: listTitle?.takeIf { it.isNotBlank() } ?: stringResource(R.string.desktop_untitled),
                compact = true,
                navigationIcon = { MiuixBackButton(onClick = onBack) },
                actions = {
                    // 这个会话会不会提醒你：铃铛一眼可见，点一下切换。
                    if (!isChild) {
                        val remind = linkPrefs.reminds(sessionId)
                        EtaIconButton(onClick = { DesktopLinkSettings.setSessionReminders(sessionId, !remind) }) {
                            Icon(
                                if (remind) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                                stringResource(R.string.desktop_session_remind),
                                Modifier.size(22.dp),
                                tint = if (remind) EtaTheme.colors.accent else EtaTheme.colors.textTertiary,
                            )
                        }
                    }
                    if (!isChild && state.subagents.isNotEmpty()) {
                        Box {
                            EtaIconButton(onClick = ::openSubagents) {
                                Icon(Icons.Outlined.Hub, stringResource(R.string.desktop_subagents), Modifier.size(22.dp))
                            }
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
                    if (!isChild) {
                        Box(Modifier.padding(end = 8.dp)) {
                            ModelPill(picker, locked = false, iconOnly = true) { showSettings = true }
                        }
                    } else {
                        Box(Modifier.padding(end = 16.dp)) { DesktopRunningDot(state.running) }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            // 标题下一行小字：这是电脑上的会话，在哪个工作区。
            Text(
                text = listOfNotNull(stringResource(R.string.desktop_title), workspaceTitle?.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = EtaTheme.type.caption,
                color = EtaTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
            )
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
                return@Column
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
                return@Column
            }
            val remoteComposer = remember(sessionId, isChild) {
                RemoteComposer(
                    workingHint = resources.getString(R.string.desktop_working_hint),
                    idleHint = resources.getString(R.string.desktop_idle_hint),
                    onDraftChange = { text -> DesktopBridge.setDraft(sessionId, text) },
                    aboveInput = { aboveInput.value() },
                )
            }
            CompositionLocalProvider(LocalRemoteComposer provides remoteComposer, LocalRemoteImageLoader provides DesktopBridge::image) {
            AgentChatBody(
                isBranching = false,
                onBranchMessage = {},  // 电脑会话不支持分支
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
                messageEdit = null,
                onReasoningEffortChange = { effort ->
                    (state.model ?: catalog?.default)?.let { selection ->
                        run { DesktopBridge.selectModel(sessionId, selection.provider, selection.model, effort.wireValue) }
                    }
                },
                onCompactContext = {},
                canCompactContext = false,
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
                    // 桌面历史不可改写：把原文放回输入框，改完作为新消息发送。
                    (messages.firstOrNull { it.id == id } as? UserMessageUi)?.let { setInput(it.content) }
                        ?: (messages.firstOrNull { it.id == id } as? AgentMessageUi)?.let { setInput(it.content) }
                },
                onCancelMessageEdit = {},
                onDeleteMessage = { context.toast(unsupported) },
                onRegenerateMessage = { context.toast(unsupported) },
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
