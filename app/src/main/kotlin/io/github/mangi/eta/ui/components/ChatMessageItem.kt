package io.github.mangi.eta.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import io.github.mangi.eta.ui.theme.EtaMotion

import android.graphics.BitmapFactory
import android.util.Base64
import io.github.mangi.eta.ui.components.EtaAnimatedVisibility as AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.produceState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.theme.EtaTheme
import io.github.mangi.eta.ui.model.TaskVisualStatus
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Hub
import io.github.mangi.eta.ui.voice.SpeechReadAloudMenuItem
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.foundation.layout.BoxWithConstraints
import io.github.mangi.eta.agent.model.AgentFileReferencePromptCodec
import io.github.mangi.eta.agent.runtime.RunPerformanceSummary
import io.github.mangi.eta.agent.overlay.toolDisplayName
import io.github.mangi.eta.ui.markdown.MarkdownTone
import io.github.mangi.eta.ui.markdown.StaticMarkdown
import io.github.mangi.eta.ui.markdown.StreamingMarkdown
import io.github.mangi.eta.ui.markdown.StreamingMarkdownState
import io.github.mangi.eta.ui.markdown.THINKING_LINE_HEIGHT_SP
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.RunTraceMessageUi
import io.github.mangi.eta.ui.model.SuggestionChipsMessageUi
import io.github.mangi.eta.ui.model.SystemNoticeCode
import io.github.mangi.eta.ui.model.SystemNoticeMessageUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.ToolSummaryMessageUi
import io.github.mangi.eta.ui.model.UserMessageUi
import io.github.mangi.eta.ui.voice.SpeechReadAloudButton
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.RichTooltip
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipAnchorPosition
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.TooltipDefaults
import top.yukonga.miuix.kmp.basic.rememberTooltipState
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun rememberDataUrlBitmap(dataUrl: String): ImageBitmap? {
    val loader = LocalRemoteImageLoader.current
    if (loader == null || !dataUrl.startsWith("dsh-image:")) return remember(dataUrl) { decodeDataUrlBitmap(dataUrl) }
    // 电脑会话的图片：按需向电脑取图，在后台线程解码。
    val bitmap by produceState<ImageBitmap?>(null, dataUrl) {
        value = loader(dataUrl)?.let { url -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { decodeDataUrlBitmap(url) } }
    }
    return bitmap
}

/** 电脑会话提供的取图函数：把 `dsh-image:` 地址换成 data URL。本机会话为空。 */
internal val LocalRemoteImageLoader = androidx.compose.runtime.staticCompositionLocalOf<(suspend (String) -> String?)?> { null }

internal fun decodeDataUrlBitmap(dataUrl: String): ImageBitmap? {
    val base64 = dataUrl.substringAfter("base64,", "")
    if (base64.isBlank()) return null
    return runCatching {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/**
 * 等待首个文本片段时的轻量反馈。
 */
@Composable
fun AITypingIndicator(modifier: Modifier = Modifier) {
    // 等首个字时用胖小胖猫头的「思考」呼吸，取代三个跳动的点。
    EtaThinkingMark(modifier.padding(vertical = 2.dp), size = 20.dp)
}

/**
 * 只有正在执行的状态才持有无限动画。历史思考和工具条目保持静态，避免长会话里
 * 每个已完成节点都持续产生帧时钟与状态更新。
 */
@Composable
internal fun rememberActivePulse(
    active: Boolean,
    label: String,
): Float {
    if (!active || io.github.mangi.eta.ui.theme.EtaTheme.motion.reduceMotion) return 1f
    val transition = rememberInfiniteTransition(label = label)
    val alpha by transition.animateFloat(
        initialValue = 0.58f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(820, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "${label}_alpha",
    )
    return alpha
}

@Composable
internal fun ChatMessageItem(
    message: AgentChatMessageUi,
    onSuggestionClick: (String) -> Unit,
    onRunTraceClick: () -> Unit,
    onOpenBrowser: () -> Unit,
    showBrowserShortcut: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    assistantOverlay: Boolean = false,
    retainedStreamingState: StreamingMarkdownState? = null,
    showCopyAction: Boolean = true,
    showMessageActions: Boolean = false,
    showBranchAction: Boolean = false,
    messageActionsEnabled: Boolean = true,
    isEditing: Boolean = false,
    onEditMessage: (String) -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
    onRegenerateMessage: (String) -> Unit = {},
    onBranchMessage: (String) -> Unit = {},
    onSelectReplyCandidate: (String, Int) -> Unit = { _, _ -> },
) {
    when (message) {
        is UserMessageUi -> UserMessageBubble(
            message = message,
            assistantOverlay = assistantOverlay,
            actionsEnabled = messageActionsEnabled,
            isEditing = isEditing,
            onEdit = { onEditMessage(message.id) },
            onDelete = { onDeleteMessage(message.id) },
            modifier = modifier,
        )
        is AgentMessageUi -> AgentMessageBlock(
            message = message,
            allowSpeech = true,
            assistantOverlay = assistantOverlay,
            retainedStreamingState = retainedStreamingState,
            showCopyAction = showCopyAction,
            showMessageActions = showMessageActions,
            showBranchAction = showBranchAction,
            onBranch = { onBranchMessage(message.id) },
            messageActionsEnabled = messageActionsEnabled,
            onDelete = { onDeleteMessage(message.id) },
            onRegenerate = { onRegenerateMessage(message.id) },
            onEdit = { onEditMessage(message.id) },
            onSelectCandidate = { onSelectReplyCandidate(message.id, it) },
            modifier = modifier,
        )
        is SystemNoticeMessageUi -> if (message.code == SystemNoticeCode.ContextCompaction) {
            ContextCompactionMarker(message = message, modifier = modifier)
        } else if (message.code == SystemNoticeCode.RunPerformance) {
            RunPerformanceMarker(message = message, modifier = modifier)
        } else if (message.code == SystemNoticeCode.Marker) {
            InfoMarker(message = message, modifier = modifier)
        } else {
            AgentMessageBlock(
                message = AgentMessageUi(
                    id = message.id,
                    content = buildString {
                        append(
                            stringResource(
                                when (message.code) {
                                    SystemNoticeCode.Stopped -> R.string.system_notice_stopped
                                    SystemNoticeCode.EmptyResult -> R.string.system_notice_empty_result
                                    SystemNoticeCode.ContextCompaction -> R.string.context_compaction
                                    SystemNoticeCode.ModelRetry -> R.string.system_notice_model_retry
                                    SystemNoticeCode.RuntimeFailed -> R.string.system_notice_runtime_failed
                                    SystemNoticeCode.Interrupted -> R.string.system_notice_interrupted
                                    SystemNoticeCode.RunPerformance, SystemNoticeCode.Marker -> R.string.system_notice_run_performance
                                },
                            ),
                        )
                        message.detail?.takeIf(String::isNotBlank)?.let { detail ->
                            append("\n\n")
                            append(detail)
                        }
                    },
                    renderMarkdown = false,
                ),
                assistantOverlay = false,
                retainedStreamingState = null,
                showCopyAction = showCopyAction,
                showMessageActions = showMessageActions,
                messageActionsEnabled = messageActionsEnabled,
                onDelete = { onDeleteMessage(message.id) },
                onRegenerate = { onRegenerateMessage(message.id) },
                modifier = modifier,
            )
        }
        is ThinkingMessageUi -> ThinkingRow(
            message = message,
            retainedStreamingState = retainedStreamingState,
            modifier = modifier,
            compact = compact,
        )
        is RunTraceMessageUi -> RunTraceRow(message = message, onClick = onRunTraceClick, modifier = modifier)
        is ToolActivityMessageUi -> ToolActivityInline(
            message = message,
            onOpenBrowser = onOpenBrowser,
            showBrowserShortcut = showBrowserShortcut,
            modifier = modifier,
            compact = compact,
        )
        is ToolSummaryMessageUi -> ToolSummaryInline(message = message, modifier = modifier, compact = compact)
        is SuggestionChipsMessageUi -> SuggestionChipsRow(message = message, onSuggestionClick = onSuggestionClick, modifier = modifier)
    }
}

// ── 用户消息：轻盈美观气泡 ──────────────────────────────────────────────

@Composable
private fun UserMessageBubble(
    message: UserMessageUi,
    assistantOverlay: Boolean,
    actionsEnabled: Boolean,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var menu by remember(message.id) { mutableStateOf(false) }
    var menuAnchor by remember(message.id) { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    // 菜单打开时被按的气泡轻轻放大，提示「操作的是这一条」。
    val pressScale = androidx.compose.animation.core.animateFloatAsState(if (menu) 1.03f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 500f), label = "userBubbleMenu")
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(actionsEnabled) { if (!actionsEnabled) menu = false }
    val visiblePrompt = remember(message.content) { AgentFileReferencePromptCodec.parse(message.content) }
    val editor = LocalChatInlineEditor.current?.takeIf { it.messageId == message.id }
    // 用户消息：统一 18dp 圆角、不带尾角，字号略小于回复正文，退到次要层级。
    val shape = RoundedCornerShape(18.dp)
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = if (assistantOverlay) 16.dp else 20.dp, vertical = if (assistantOverlay) 4.dp else 0.dp)) {
        Column(Modifier.align(Alignment.CenterEnd).widthIn(max = maxWidth * 0.85f), horizontalAlignment = Alignment.End) {
            if (message.images.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 点图片在原位放大到全屏，可左右翻页、双指缩放，下滑关闭。
                val previewer = LocalChatImagePreviewer.current
                val imageBounds = remember(message.id) { mutableMapOf<Int, androidx.compose.ui.geometry.Rect>() }
                val viewLabel = stringResource(R.string.chat_view_image)
                message.images.forEachIndexed { index, url -> rememberDataUrlBitmap(url)?.let { bitmap ->
                    Image(bitmap, stringResource(R.string.chat_image_attachment), Modifier.size(120.dp)
                        .onGloballyPositioned { imageBounds[index] = it.boundsInWindow() }
                        .graphicsLayer { alpha = if (previewer?.isPresenting(message.id, index) == true) 0f else 1f }
                        .clip(EtaTheme.shapes.radiusMd)
                        .clickable(enabled = previewer != null, onClickLabel = viewLabel) {
                            previewer?.open(message.images, index, message.id, { imageBounds[it] }, 12.dp)
                        }, contentScale = ContentScale.Crop)
                } }
            }
            Column(Modifier.onGloballyPositioned { menuAnchor = it.boundsInWindow() }
                .graphicsLayer { val s = pressScale.value; scaleX = s; scaleY = s }
                .background(EtaTheme.colors.surfaceSunken, shape)
                .then(if (isEditing) Modifier.border(1.dp, EtaTheme.colors.accent, shape) else Modifier)
                .combinedClickable(enabled = actionsEnabled, onClick = {}, onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true
                }).padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (visiblePrompt.references.isNotEmpty()) SentFileReferenceFlow(visiblePrompt.references,
                    Modifier.padding(bottom = if (visiblePrompt.request.isNotBlank()) 8.dp else 0.dp))
                if (isEditing && editor != null) InlineUserMessageEditor(editor, message.images.isNotEmpty() || visiblePrompt.references.isNotEmpty())
                else if (visiblePrompt.request.isNotBlank()) Text(visiblePrompt.request, style = EtaTheme.type.body.copy(fontSize = 15.sp, lineHeight = 24.sp), color = EtaTheme.colors.textPrimary)
                if (message.isEdited) Text(stringResource(R.string.ui_edited_c36776), style = EtaTheme.type.caption,
                    color = EtaTheme.colors.textTertiary, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    EtaContextMenu(menu, anchor = menuAnchor, onDismiss = { menu = false }, alignEnd = true) {
        EtaMenuItem(stringResource(R.string.ui_copy_4edd1d), onClick = {
            clipboardManager.setText(AnnotatedString(message.content)); menu = false
        })
        EtaMenuItem(stringResource(R.string.ui_edit_a7f814), onClick = { menu = false; onEdit() })
        EtaMenuItem(stringResource(R.string.ui_delete_3755f5), onClick = { menu = false; onDelete() }, destructive = true)
    }
}

@Composable
private fun InlineUserMessageEditor(editor: ChatInlineEditor, hasAttachments: Boolean) {
    var text by rememberSaveable(editor.messageId) { mutableStateOf(editor.initialText) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(editor.messageId) { focus.requestFocus(); keyboard?.show() }
    Column {
        top.yukonga.miuix.kmp.basic.TextField(text, { text = it }, modifier = Modifier.fillMaxWidth().focusRequester(focus),
            minLines = 2, maxLines = 8, textStyle = EtaTheme.type.body)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EtaButton(stringResource(R.string.action_cancel), editor.onCancel, size = EtaButtonSize.Small, kind = EtaButtonKind.Secondary)
            EtaButton(stringResource(if (editor.preserveFollowing) R.string.chat_save_message else R.string.eta_edit_resend),
                { editor.onSubmit(text) }, enabled = text.isNotBlank() || hasAttachments, size = EtaButtonSize.Small)
        }
    }
}

@Composable
private fun MessageTooltipAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            // 下方文字已经命名该操作，图标不再重复朗读。
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

// ── 上下文压缩：时间线中的轻量胶囊标记 ─────────────────────────────────

/**
 * 压缩不是一轮对话结果，而是上下文维护事件；用居中胶囊标记与助手正文区分，
 * 进行中通过图标脉冲反馈，结束后保留压缩前后的 token 信息。
 */
@Composable
private fun ContextCompactionMarker(
    message: SystemNoticeMessageUi,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.background(EtaTheme.colors.surfaceSunken, EtaTheme.shapes.radiusFull).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (message.running) StatusRing(TaskVisualStatus.Running, size = 12.dp)
            else Icon(Icons.Outlined.Compress, null, Modifier.size(16.dp), tint = EtaTheme.colors.textTertiary)
            Text(message.detail?.takeIf(String::isNotBlank) ?: stringResource(R.string.context_compaction),
                style = EtaTheme.type.caption, color = EtaTheme.colors.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Agent 结果 ───────────────────────────────────────────────────────

@Composable
private fun RunPerformanceMarker(message: SystemNoticeMessageUi, modifier: Modifier = Modifier) {
    val summary = remember(message.detail) { RunPerformanceSummary.fromJson(message.detail) } ?: return
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    val chinese = stringResource(R.string.system_notice_run_performance).any { it.code in 0x4e00..0x9fff }
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.clip(EtaTheme.shapes.radiusFull).background(EtaTheme.colors.surfaceSunken)
                .clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Speed, null, Modifier.padding(end = 6.dp).size(16.dp), tint = EtaTheme.colors.textTertiary)
            Text(summary.title(chinese), style = EtaTheme.type.caption,
                color = EtaTheme.colors.textTertiary, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                modifier = Modifier.size(16.dp).graphicsLayer(rotationZ = if (expanded) 90f else 0f),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        AnimatedVisibility(visible = expanded) {
            SelectionContainer {
                Text(summary.details(chinese), style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
    }
}

/** 通用小胶囊：标题一行，点开展开详情；与执行统计同一视觉。 */
@Composable
private fun InfoMarker(message: SystemNoticeMessageUi, modifier: Modifier = Modifier) {
    val parsed = remember(message.detail) {
        runCatching { org.json.JSONObject(message.detail.orEmpty()) }.getOrNull()
    } ?: return
    val title = parsed.optString("title")
    val details = parsed.optString("details")
    val icon = when (parsed.optString("icon")) {
        "usage" -> Icons.Outlined.Speed
        "subagent" -> Icons.Outlined.Hub
        "schedule" -> Icons.Outlined.Schedule
        else -> Icons.Outlined.Bolt
    }
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.clip(EtaTheme.shapes.radiusFull).background(EtaTheme.colors.surfaceSunken)
                .clickable(enabled = details.isNotBlank()) { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.padding(end = 6.dp).size(16.dp), tint = EtaTheme.colors.textTertiary)
            Text(title, style = EtaTheme.type.caption, color = EtaTheme.colors.textTertiary,
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            if (details.isNotBlank()) Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                modifier = Modifier.size(16.dp).graphicsLayer(rotationZ = if (expanded) 90f else 0f),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        AnimatedVisibility(visible = expanded) {
            SelectionContainer {
                Text(details, style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun AgentMessageBlock(
    message: AgentMessageUi,
    allowSpeech: Boolean = false,
    assistantOverlay: Boolean = false,
    retainedStreamingState: StreamingMarkdownState?,
    showCopyAction: Boolean,
    showMessageActions: Boolean,
    showBranchAction: Boolean = false,
    onBranch: () -> Unit = {},
    messageActionsEnabled: Boolean,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit = {},
    onSelectCandidate: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var menu by remember(message.id) { mutableStateOf(false) }
    var selectingText by remember(message.id) { mutableStateOf(false) }
    val export = LocalChatHomeShortcuts.current.onExportConversation
    var copied by remember(message.id) { mutableStateOf(false) }
    val keepStreamingMarkdown = remember(message.id) { message.isStreaming }
    DisposableEffect(retainedStreamingState) {
        retainedStreamingState?.attachContent()
        onDispose { retainedStreamingState?.detachContent() }
    }
    var streamingRevealComplete by remember(message.id) {
        mutableStateOf(!keepStreamingMarkdown)
    }
    // 渲染会话由列表层按 message.id 持有，item 滚出视口被销毁后滑回时复用同一
    // 会话；没有外部持有者时（如嵌套条目）退回组合内 remember，行为与之前一致。
    val streamingState = if (keepStreamingMarkdown) {
        retainedStreamingState ?: remember(message.id) { StreamingMarkdownState() }
    } else {
        null
    }
    val completedDocument = (streamingState ?: retainedStreamingState)
        ?.snapshot?.completedDocumentFor(message.content)
    val revealComplete = streamingRevealComplete && !message.isStreaming &&
        (streamingState == null || completedDocument != null)
    LaunchedEffect(retainedStreamingState, revealComplete, message.content) {
        retainedStreamingState?.revealedContent = message.content.takeIf { revealComplete }
    }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1_400)
            copied = false
        }
    }

    val character = LocalRoleplayChat.current.takeUnless { assistantOverlay }
    val isGreeting = character != null && message.id.startsWith(GreetingMessagePrefix)
    var menuAnchor by remember(message.id) { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { menuAnchor = it.boundsInWindow() }
            .combinedClickable(enabled = showMessageActions && messageActionsEnabled && revealComplete,
                onClick = {}, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); selectingText = false; menu = true })
            .padding(
                horizontal = if (assistantOverlay) 16.dp else 20.dp,
                vertical = if (assistantOverlay) 8.dp else 0.dp,
            ),
    ) {
        if (character != null) {
            CharacterReplyHeader(character = character, isGreeting = isGreeting)
        }
        when {
            message.content.isBlank() && message.isStreaming -> {
                AITypingIndicator(
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            streamingState != null && !revealComplete -> {
                StreamingMarkdown(
                    state = streamingState,
                    content = message.content,
                    isStreaming = message.isStreaming,
                    tone = MarkdownTone.Answer,
                    onRevealCompleteChange = { streamingRevealComplete = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            message.renderMarkdown -> {
                AnswerTextSelection(enabled = !showMessageActions) {
                    StaticMarkdown(
                        content = message.content,
                        tone = MarkdownTone.Answer,
                        parsed = completedDocument,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            message.content.isNotBlank() -> {
                AnswerTextSelection(enabled = !showMessageActions) {
                    Text(
                        text = message.content,
                        style = if (assistantOverlay) EtaTheme.type.body.copy(fontSize = 15.sp, lineHeight = 24.sp) else MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        message.searchSuggestionsHtml.forEach { html ->
            SearchSuggestionsView(html = html, modifier = Modifier.padding(top = 8.dp))
        }

        // 回答显现完成后才出现操作行；淡入展开避免贴底滚动时整页突然跳动一行高度。
        AnimatedVisibility(
            visible = showCopyAction &&
                !message.isStreaming &&
                message.content.isNotBlank() &&
                revealComplete,
            enter = fadeIn(tween(EtaMotion.Default.short, easing = EtaMotion.Default.standard)) + expandVertically(tween(EtaMotion.Default.short, easing = EtaMotion.Default.standard)),
            exit = fadeOut(tween(EtaMotion.Default.exitDuration(260), easing = EtaMotion.Default.accelerate)) + shrinkVertically(tween(EtaMotion.Default.exitDuration(260), easing = EtaMotion.Default.accelerate)),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (message.characterEditable && message.candidateCount > 1) CandidateSwitcher(
                    message.selectedCandidate, message.candidateCount, messageActionsEnabled, onSelectCandidate)
                AnswerActionButton(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                    stringResource(if (copied) R.string.copy_copied else R.string.copy_answer), {
                        clipboardManager.setText(AnnotatedString(message.content)); copied = true
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    }, tint = if (copied) EtaTheme.colors.accent else null)
                if (showMessageActions) AnswerActionButton(Icons.Outlined.Refresh, stringResource(R.string.ui_regenerate_reply_84a7d9), onRegenerate, messageActionsEnabled)
                if (allowSpeech) SpeechReadAloudButton(message.id, message.content)
                if (showMessageActions) AnswerActionButton(Icons.Outlined.MoreHoriz, stringResource(R.string.eta_message_actions), { selectingText = false; menu = true }, messageActionsEnabled)
                // 上游 3.4.1：本轮性能统计（步数、首字时间、生成速度、用量），点开看详情。
                if (!assistantOverlay && message.usage != null && message.usage.hasPerformanceStats) {
                    TurnStatsPill(usage = message.usage, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
    // 选择文字仍用底部面板（需要整段可选的大区域）；其余操作是贴着回答弹出的气泡菜单。
    EtaSheet(menu && selectingText, title = stringResource(R.string.eta_select_text), onDismissRequest = { menu = false; selectingText = false }) {
        SelectionContainer {
            Text(message.content, style = EtaTheme.type.body, modifier = Modifier.fillMaxWidth()
                .heightIn(max = 420.dp).verticalScroll(rememberScrollState()))
        }
    }
    EtaContextMenu(menu && !selectingText, anchor = menuAnchor, onDismiss = { menu = false }) {
        EtaMenuItem(stringResource(R.string.eta_select_text), onClick = { selectingText = true })
        EtaMenuItem(stringResource(R.string.copy_answer), onClick = {
            clipboardManager.setText(AnnotatedString(message.content)); copied = true; menu = false
        })
        if (allowSpeech) SpeechReadAloudMenuItem(message.id, message.content) { menu = false }
        EtaMenuItem(stringResource(R.string.ui_regenerate_reply_84a7d9), enabled = messageActionsEnabled,
            onClick = { menu = false; onRegenerate() })
        if (message.characterEditable) EtaMenuItem(stringResource(R.string.chat_edit_character_reply), onClick = { menu = false; onEdit() })
        // 上游 3.4.1：从这条已完成的回复另开一个独立对话，原对话不受影响。
        if (showBranchAction) EtaMenuItem(stringResource(R.string.conversation_branch), enabled = messageActionsEnabled,
            onClick = { menu = false; onBranch() })
        EtaMenuItem(stringResource(R.string.action_export), onClick = { menu = false; export() })
        EtaMenuItem(stringResource(R.string.ui_delete_3755f5), onClick = { menu = false; onDelete() }, destructive = true)
    }
}

/** 开场白消息由开始对话时写入，标识前缀与写入方保持一致。 */
private const val GreetingMessagePrefix = "greeting-"

/**
 * 角色回复的署名行：小头像加角色名，开场白额外标注“故事开始”。
 * 正文保持与普通回答同宽，避免为头像缩进后长段落在窄屏上过于拥挤。
 */
@Composable
private fun CharacterReplyHeader(character: RoleplayChatUi, isGreeting: Boolean) {
    Row(
        modifier = Modifier.padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CharacterAvatar(
            name = character.characterName,
            avatarPath = character.avatarPath,
            size = 28.dp,
            shape = CircleShape,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = character.characterName,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (isGreeting) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.chat_character_story_start),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** 思考卡片与运行记录等全宽描边卡片共用的圆角与描边强度，相邻时轮廓一致。 */
private val ChatCardCorner = 12.dp
private const val ChatCardOutlineAlpha = 0.5f

/** 回答操作按钮的触控尺寸；行整体左移半个尺寸减去图标半宽，使图标落在工作过程图标的中心线上。 */
internal val AnswerActionSize = 40.dp
private val AnswerActionCenterOffset = 11.dp

/** 回答下方的轻量操作：图标显式着色，因此禁用态需要自己换成禁用色，否则看起来仍可点。 */
@Composable
internal fun AnswerActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(AnswerActionSize)
            .clip(RoundedCornerShape(8.dp))
            .etaPressScale(interactionSource, enabled, pressedScale = 0.88f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 图标切换（如复制 → 对勾）带一点缩放淡入，不是生硬替换。
        val iconTint = when {
            !enabled -> MiuixTheme.colorScheme.disabledOnSurface
            tint != null -> tint
            else -> EtaTheme.colors.textTertiary
        }
        androidx.compose.animation.AnimatedContent(
            targetState = icon,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(tween(160)) + androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 700f), initialScale = 0.6f))
                    .togetherWith(androidx.compose.animation.fadeOut(tween(100)) + androidx.compose.animation.scaleOut(tween(100), targetScale = 0.6f))
            },
            label = "answerActionIcon",
        ) { current ->
            Icon(imageVector = current, contentDescription = contentDescription, modifier = Modifier.size(18.dp), tint = iconTint)
        }
    }
}

@Composable
private fun CandidateSwitcher(
    selected: Int,
    count: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(EtaTheme.colors.surfaceSunken)
            .padding(horizontal = 2.dp),
    ) {
        AnswerActionButton(
            icon = Icons.Outlined.ChevronLeft,
            contentDescription = stringResource(R.string.chat_previous_candidate),
            onClick = { onSelect(selected - 1) },
            enabled = enabled && selected > 0,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = "${selected + 1}/$count",
            style = EtaTheme.type.caption.copy(fontFeatureSettings = "tnum"),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 28.dp),
        )
        AnswerActionButton(
            icon = Icons.Outlined.ChevronRight,
            contentDescription = stringResource(R.string.chat_next_candidate),
            onClick = { onSelect(selected + 1) },
            enabled = enabled && selected < count - 1,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

// ── 思考过程 ─────────────────────────────────────────────────────────

@Composable
internal fun ThinkingRow(
    message: ThinkingMessageUi,
    retainedStreamingState: StreamingMarkdownState?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    DisposableEffect(retainedStreamingState) {
        retainedStreamingState?.attachContent()
        onDispose { retainedStreamingState?.detachContent() }
    }
    // null 表示跟随思考生命周期；用户点过之后只认手动选择。
    var manualExpanded by rememberSaveable(message.id) { mutableStateOf<Boolean?>(null) }
    val keepStreamingMarkdown = remember(message.id) { message.isStreaming }
    val streamingState = if (keepStreamingMarkdown) {
        retainedStreamingState ?: remember(message.id) { StreamingMarkdownState() }
    } else {
        null
    }
    val completedDocument = (streamingState ?: retainedStreamingState)
        ?.snapshot?.completedDocumentFor(message.content)
    // 展开态必须在组合期同步推导：若经 LaunchedEffect 晚一帧收起，思考结束的那一帧尾部窗口
    // 已解除而正文仍展开，会以全文高度闪现一帧，再从该高度执行收起动画。
    val expanded = manualExpanded ?: message.isStreaming
    // 自动模式下高度上限覆盖到收起动画结束，退出过程不会先撑开再收拢。
    val tailWindow = manualExpanded == null

    val pulseAlpha = rememberActivePulse(
        active = message.isStreaming,
        label = "thinking_pulse",
    )

    // compact 模式渲染在工作过程时间线内，节点徽章已承担图标，这里只保留文字与展开控制。
    val containerModifier = if (compact) {
        modifier.fillMaxWidth()
    } else {
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .squircleSurface(
                color = MiuixTheme.colorScheme.surface,
                cornerRadius = ChatCardCorner,
            )
            .squircleBorder(
                width = 0.5.dp,
                color = MiuixTheme.colorScheme.outline.copy(alpha = ChatCardOutlineAlpha),
                cornerRadius = ChatCardCorner,
            )
    }

    Column(modifier = containerModifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    // 尾部窗口下点击视为要看全文，保持展开并放开高度限制。
                    manualExpanded = (tailWindow && expanded) || !expanded
                }
                .then(
                    if (compact) {
                        Modifier.heightIn(min = WorkRowHeight)
                    } else {
                        Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!compact) {
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier
                        .size(15.dp)
                        .graphicsLayer(alpha = if (message.isStreaming) pulseAlpha else 1f),
                    tint = if (message.isStreaming) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            ShimmerText(
                active = message.isStreaming,
                text = if (message.isStreaming) {
                    stringResource(R.string.reasoning_in_progress)
                } else {
                    message.elapsedSeconds?.takeIf { it > 0 }?.let { seconds ->
                        pluralStringResource(
                            R.plurals.reasoning_completed_seconds,
                            seconds,
                            seconds,
                        )
                    } ?: stringResource(R.string.reasoning_completed)
                },
                style = MiuixTheme.textStyles.body2,
                color = if (message.isStreaming) {
                    MiuixTheme.colorScheme.onSurface
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
                modifier = Modifier.weight(1f, fill = !compact),
            )
            ExpandChevron(
                expanded = expanded,
                contentDescription = stringResource(
                    if (expanded) R.string.reasoning_collapse else R.string.reasoning_expand,
                ),
                modifier = Modifier.padding(start = 4.dp, end = if (compact) 8.dp else 0.dp),
            )
        }

        AnimatedVisibility(visible = expanded && message.content.isNotBlank()) {
            Column {
                if (!compact) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(0.5.dp)
                            .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    )
                }
                val contentModifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (compact) 0.dp else 12.dp,
                        end = if (compact) 8.dp else 12.dp,
                        top = if (compact) 0.dp else 8.dp,
                        bottom = if (compact) 8.dp else 12.dp,
                    )
                if (streamingState != null &&
                    (message.isStreaming || completedDocument == null || tailWindow)
                ) {
                    ThinkingTailWindow(
                        enabled = tailWindow,
                        modifier = contentModifier,
                    ) {
                        StreamingMarkdown(
                            state = streamingState,
                            content = message.content,
                            isStreaming = message.isStreaming,
                            tone = MarkdownTone.Thinking,
                        )
                    }
                } else {
                    StaticMarkdown(
                        content = message.content,
                        tone = MarkdownTone.Thinking,
                        parsed = completedDocument,
                        modifier = contentModifier,
                    )
                }
            }
        }
    }
}

/**
 * 进行中的思考只露出最近几行：内容按底部对齐超出部分向上裁掉，顶部渐隐提示还有更早内容。
 * 高度封顶后增量不再推高工作过程卡片，列表也不必随每次思考快照重新跟底。
 */
@Composable
private fun ThinkingTailWindow(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier) { content() }
        return
    }
    val maxHeight = with(LocalDensity.current) {
        (THINKING_TAIL_WINDOW_LINES * THINKING_LINE_HEIGHT_SP).sp.toDp()
    }
    Box(
        modifier = modifier
            .heightIn(max = maxHeight)
            .clipToBounds()
            // 离屏合成只覆盖这块有界区域，用于让渐隐遮罩作用在文字 alpha 上。
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (size.height >= maxHeight.toPx() - 1f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to Color.Black,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Bottom, unbounded = true),
        ) {
            content()
        }
    }
}

private const val THINKING_TAIL_WINDOW_LINES = 4

// ── Run trace：轻量入口行 ─────────────────────────────────────────────

@Composable
private fun RunTraceRow(
    message: RunTraceMessageUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .squircleSurface(color = MiuixTheme.colorScheme.surface, cornerRadius = ChatCardCorner)
            .squircleBorder(
                width = 0.5.dp,
                color = MiuixTheme.colorScheme.outline.copy(alpha = ChatCardOutlineAlpha),
                cornerRadius = ChatCardCorner,
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MiuixTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.ui_available_capacity_743337),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f),
        )
    }
}

// ── 工具摘要 ──────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolSummaryInline(
    message: ToolSummaryMessageUi,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 10.dp else 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        message.tools.forEach { tool ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MiuixTheme.colorScheme.surface)
                    .border(
                        0.5.dp,
                        MiuixTheme.colorScheme.outline.copy(alpha = 0.5f),
                        RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = iconForTool(tool),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MiuixTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = toolDisplayName(tool),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}

// ── 建议语 ────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionChipsRow(
    message: SuggestionChipsMessageUi,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        message.prompts.forEach { prompt ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MiuixTheme.colorScheme.surface)
                    .border(
                        0.5.dp,
                        MiuixTheme.colorScheme.outline.copy(alpha = 0.55f),
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onSuggestionClick(prompt) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MiuixTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = prompt,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AnswerTextSelection(enabled: Boolean, content: @Composable () -> Unit) {
    if (enabled) SelectionContainer(content = content) else content()
}
