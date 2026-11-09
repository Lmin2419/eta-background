package io.github.mangi.eta.ui.screens.desktop

import android.content.Context
import top.yukonga.miuix.kmp.basic.Icon
import kotlinx.coroutines.delay
import io.github.mangi.eta.ui.components.ModelPill
import io.github.mangi.eta.ui.components.EtaIconButton
import io.github.mangi.eta.agent.remote.DesktopSubagent
import io.github.mangi.eta.agent.remote.DesktopItem
import androidx.compose.runtime.produceState
import androidx.compose.material.icons.rounded.Hub
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
import androidx.compose.runtime.DisposableEffect
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
import io.github.mangi.eta.agent.remote.DesktopBridgeClient
import io.github.mangi.eta.agent.remote.DesktopBridgeException
import io.github.mangi.eta.agent.remote.DesktopImage
import io.github.mangi.eta.ui.components.AdaptiveTopAppBar
import io.github.mangi.eta.ui.components.AgentChatBody
import io.github.mangi.eta.ui.components.MiuixBackButton
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.ConversationHistoryLoadState
import io.github.mangi.eta.ui.model.PendingImageUi
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
    val scope = rememberCoroutineScope()

    val labels = rememberDesktopLabels()
    val messages = remember(state, labels) { DesktopChatProjector.messages(state, labels) }
    val queued = remember(state) { DesktopChatProjector.queued(state) }
    val picker = remember(catalog, state.model) { DesktopChatProjector.modelPicker(catalog, state) }
    val efforts = remember(catalog, state.model) { DesktopChatProjector.reasoningEfforts(catalog, state) }
    var input by remember(sessionId) { mutableStateOf("") }
    var inputSync by remember(sessionId) { mutableIntStateOf(0) }
    var pendingImages by remember(sessionId) { mutableStateOf(emptyList<PendingImageUi>()) }
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
                context.toast(resources.getString(R.string.desktop_failed, error.message))
            }
        }
    }

    fun submit(text: String, interrupt: Boolean) {
        if (readOnly) {
            context.toast(readOnlyText)
            input = text
            inputSync++
            return
        }
        val images = pendingImages
        pendingImages = emptyList()
        input = ""
        run {
            val encoded = withContext(Dispatchers.IO) { images.mapNotNull { encodeImage(context, it) } }
            DesktopBridge.prompt(sessionId, text, images = encoded, interrupt = interrupt)
        }
    }

    fun setInput(text: String) {
        input = text
        inputSync++
    }

    fun openSubagents() {
        showSubagents = true
        subagentDirectory = null
        scope.launch {
            subagentDirectory = try {
                DesktopBridge.subagentDirectory(sessionId)
            } catch (error: DesktopBridgeException) {
                context.toast(resources.getString(R.string.desktop_failed, error.message))
                emptyList()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AdaptiveTopAppBar(
                title = state.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.desktop_untitled),
                compact = true,
                navigationIcon = { MiuixBackButton(onClick = onBack) },
                actions = {
                    if (!isChild && state.subagents.isNotEmpty()) {
                        Box {
                            EtaIconButton(onClick = ::openSubagents) {
                                Icon(Icons.Rounded.Hub, stringResource(R.string.desktop_subagents), Modifier.size(22.dp))
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
            if (state.loaded) DesktopStatsLine(state, runningForMs) { showSettings = true }
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
            if (!state.loaded) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        InfiniteProgressIndicator()
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
            AgentChatBody(
                messages = messages,
                modelPickerState = picker,
                isCompacting = false,
                input = input,
                isStreaming = state.running,
                reasoningEffort = efforts.second,
                availableReasoningEfforts = efforts.first,
                pendingImages = pendingImages,
                pendingFileReferences = emptyList(),
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
                onSubmit = { text -> submit(text, interrupt = false) },
                onInterruptAndSubmit = { text -> submit(text, interrupt = true) },
                onSteerQueuedMessage = { id ->
                    if (!id.startsWith("echo-")) run { DesktopBridge.steer(sessionId, id) }
                },
                onRecallQueuedMessage = { id, draft ->
                    val recalled = queued.firstOrNull { it.id == id }?.content.orEmpty()
                    if (!id.startsWith("echo-")) run { DesktopBridge.queueAction(sessionId, id, JsonPrimitive("remove")) }
                    setInput(listOf(recalled, draft).filter { it.isNotBlank() }.joinToString("\n"))
                },
                onRemoveQueuedMessage = { id ->
                    if (!id.startsWith("echo-")) run { DesktopBridge.queueAction(sessionId, id, JsonPrimitive("remove")) }
                },
                onStop = { run { DesktopBridge.cancel(sessionId) } },
                onAttachImage = { uri ->
                    scope.launch {
                        val pending = withContext(Dispatchers.IO) { loadPendingImage(context, uri) }
                        if (pending != null) pendingImages = pendingImages + pending
                    }
                },
                onRemoveImage = { id -> pendingImages = pendingImages.filterNot { it.id == id } },
                onAttachFiles = { context.toast(attachmentsUnsupported) },
                onAttachFolder = { context.toast(attachmentsUnsupported) },
                onAttachFilePath = { context.toast(attachmentsUnsupported) },
                onRemoveFileReference = {},
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
    DesktopApprovalHost(approvals, sessionId)
    DesktopSessionSheet(
        show = showSettings,
        state = state,
        picker = picker,
        efforts = efforts.first,
        effort = efforts.second,
        allowModelChange = !isChild,
        busy = state.running,
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

private fun Context.toast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
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
