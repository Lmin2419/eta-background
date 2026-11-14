package io.github.mangi.eta.ui.components

import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.animation.animateContentSize
import io.github.mangi.eta.ui.theme.EtaMotion

import io.github.mangi.eta.ui.voice.rememberSpeechPermission
import io.github.mangi.eta.ui.theme.EtaTheme
import io.github.mangi.eta.ui.theme.EtaElevation
import io.github.mangi.eta.ui.theme.etaElevation
import androidx.compose.foundation.layout.heightIn
import io.github.mangi.eta.ui.voice.SpeechInputFeedback
import io.github.mangi.eta.ui.voice.rememberSpeechInput
import androidx.compose.ui.text.TextRange
import io.github.mangi.eta.ui.components.EtaAnimatedContent as AnimatedContent
import io.github.mangi.eta.ui.components.EtaAnimatedVisibility as AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import com.itos.coui.menu.couiMenuAnchor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.R
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.AutoAwesome
import io.github.mangi.eta.data.model.ReasoningEffort
import io.github.mangi.eta.data.repository.SpeechSettingsRepository
import io.github.mangi.eta.ui.model.AgentContextUsageUi
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.PendingFileReferenceUi
import io.github.mangi.eta.ui.model.PendingImageUi
import io.github.mangi.eta.ui.model.QueuedMessageUi
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

private val SendButtonVisualSize = ChatInputActionIconSize
private val SendIconSize = 16.dp
private val StopIconSize = 10.dp
private val ThinkingIconSize = 21.dp
private val InputContainerShape = RoundedCornerShape(28.dp)

/**
 * 电脑会话对输入器的定制：执行中的占位文字，以及发送时直接插队的入口。
 * 本机会话不提供，输入器保持原样。
 */
internal data class RemoteComposer(
    val workingHint: String,
    /** 空闲时的占位文字（「给电脑发消息…」），不再显示本机的「给胖小胖发消息」。 */
    val idleHint: String? = null,
    val steerLabel: String = "",
    /** 发送时直接插队的入口；为空时不显示（插队改在排队条上点）。 */
    val onSteerSubmit: ((String) -> Unit)? = null,
    /** 输入内容每次变化时回调，用来按会话保存草稿。 */
    val onDraftChange: (String) -> Unit = {},
    /** 输入框上方的内嵌内容：运行状态条、审批与提问卡片。 */
    val aboveInput: (@Composable () -> Unit)? = null,
)

internal val LocalRemoteComposer = androidx.compose.runtime.staticCompositionLocalOf<RemoteComposer?> { null }

/**
 * Agent 输入器始终保持同一空间结构，聚焦、输入和执行过程只改变状态，不搬动操作入口。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AgentChatInputBar(
    input: String,
    modelPickerState: AgentModelPickerUiState,
    isCompacting: Boolean,
    contextUsage: AgentContextUsageUi,
    showContextUsage: Boolean,
    isStreaming: Boolean,
    reasoningEffort: ReasoningEffort,
    availableReasoningEfforts: List<ReasoningEffort>,
    pendingImages: List<PendingImageUi>,
    pendingFileReferences: List<PendingFileReferenceUi>,
    queuedMessages: List<QueuedMessageUi> = emptyList(),
    inputSyncVersion: Int = 0,
    focusInputRequest: String? = null,
    onInputFocusHandled: () -> Unit = {},
    // 外部写入草稿（如系统分享）时可不弹键盘。
    focusInputOnSync: Boolean = true,
    isEditingMessage: Boolean,
    editHasLaterTurns: Boolean,
    preserveFollowingMessages: Boolean,
    onReasoningEffortChange: (ReasoningEffort) -> Unit,
    onCompactContext: () -> Unit,
    canCompactContext: Boolean,
    onModelSelected: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onInterruptAndSubmit: (String) -> Unit = onSubmit,
    onSteerQueuedMessage: (String) -> Unit = {},
    onRecallQueuedMessage: (String, String) -> Unit = { _, _ -> },
    onRemoveQueuedMessage: (String) -> Unit = {},
    onStop: () -> Unit,
    onAttachImage: (String) -> Unit,
    onRemoveImage: (String) -> Unit,
    onAttachFiles: (List<String>) -> Unit,
    onAttachFolder: (String) -> Unit,
    onAttachFilePath: (String) -> Unit,
    onRemoveFileReference: (String) -> Unit,
    onCancelMessageEdit: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundCompacting: Boolean = false,
) {
    val remoteComposer = LocalRemoteComposer.current
    val keyboard = LocalSoftwareKeyboardController.current
    val hapticFeedback = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }
    val windowFocused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focusInputRequest, windowFocused) {
        if (focusInputRequest != null && windowFocused) {
            focusRequester.requestFocus()
            keyboard?.show()
            onInputFocusHandled()
        }
    }
    var sendSweep by remember { mutableIntStateOf(0) }
    val sweepProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(sendSweep) {
        if (sendSweep > 0 && !EtaMotion.Default.reduceMotion) {
            sweepProgress.snapTo(0f)
            sweepProgress.animateTo(1f, androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            sweepProgress.snapTo(0f)
        }
    }
    val textFieldState = rememberTextFieldState(initialText = input)
    if (remoteComposer != null) {
        val onDraftChange by rememberUpdatedState(remoteComposer.onDraftChange)
        LaunchedEffect(textFieldState) {
            snapshotFlow { textFieldState.text.toString() }.collect { onDraftChange(it) }
        }
    }
    val speechSettings by remember { SpeechSettingsRepository.settingsFlow() }.collectAsState(initial = null)
    val recognitionEnabled = speechSettings?.recognitionEnabled == true
    val dictation = rememberSpeechInput { recognized ->
        textFieldState.edit {
            val range = selection
            replace(range.min, range.max, recognized)
            selection = TextRange(range.min + recognized.length)
        }
    }
    LaunchedEffect(isEditingMessage, recognitionEnabled) { dictation.cancel() }
    val draftRequest = LocalChatDraftRequest.current?.value
    LaunchedEffect(draftRequest) {
        draftRequest?.let {
            textFieldState.setTextAndPlaceCursorAtEnd(it.text)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    val shortcuts = LocalChatHomeShortcuts.current
    val speechState by dictation.state.collectAsState()
    var microphoneHold by remember { mutableStateOf(false) }
    var microphoneTapRequest by remember { mutableStateOf(false) }
    val requestMicrophone = rememberSpeechPermission {
        if (microphoneHold) dictation.start(holdToTalk = true)
        else if (microphoneTapRequest) { microphoneTapRequest = false; dictation.start() }
    }
    var interruptArmed by remember { mutableStateOf(false) }
    var wasEditingMessage by remember { mutableStateOf(isEditingMessage) }
    var syncedVersion by remember { mutableIntStateOf(inputSyncVersion) }
    val canSend = textFieldState.text.isNotBlank() ||
        pendingImages.isNotEmpty() ||
        pendingFileReferences.isNotEmpty()
    val density = LocalDensity.current
    val statusBarTopPx = WindowInsets.statusBars.getTop(density)
    var inputContainerTopPx by remember { mutableIntStateOf(0) }
    val thinkingPopupMaxHeight = with(density) {
        (inputContainerTopPx - statusBarTopPx).coerceAtLeast(0).toDp()
    }.minus(ChatInputPopupMargin * 2)
        .coerceAtLeast(ListPopupDefaults.MinPopupHeight)

    LaunchedEffect(isEditingMessage, inputSyncVersion) {
        // 编辑态与排队消息撤回由外部业务状态驱动；普通输入只保留在本地，避免每个字符把聊天舞台
        // 的消息流、滚动和 Markdown 一起带入重组。
        val versionChanged = inputSyncVersion != syncedVersion
        if (isEditingMessage || wasEditingMessage || versionChanged) {
            textFieldState.setTextAndPlaceCursorAtEnd(input)
        }
        if (isEditingMessage || (versionChanged && input.isNotEmpty() && focusInputOnSync)) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
        wasEditingMessage = isEditingMessage
        syncedVersion = inputSyncVersion
    }

    Column(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        ConversationLiveTasks(shortcuts.conversationId, shortcuts.onOpenTasks)
        remoteComposer?.aboveInput?.invoke()
        if (recognitionEnabled) SpeechInputFeedback(dictation)
        AnimatedVisibility(
            visible = queuedMessages.isNotEmpty(),
            enter = fadeIn(tween(EtaMotion.Default.short, easing = EtaMotion.Default.standard)),
            exit = fadeOut(tween(EtaMotion.Default.exitDuration(260), easing = EtaMotion.Default.accelerate)) + shrinkVertically(tween(EtaMotion.Default.exitDuration(260), easing = EtaMotion.Default.accelerate)),
        ) {
            AgentChatQueueStrip(
                queuedMessages = queuedMessages,
                isStreaming = isStreaming,
                onSteer = onSteerQueuedMessage,
                onRecall = { id -> onRecallQueuedMessage(id, textFieldState.text.toString()) },
                onRemove = onRemoveQueuedMessage,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        AnimatedVisibility(
            visible = isEditingMessage,
            enter = fadeIn(tween(EtaMotion.Default.short, easing = EtaMotion.Default.standard)),
            exit = fadeOut(tween(EtaMotion.Default.exitDuration(260), easing = EtaMotion.Default.accelerate)) + shrinkVertically(tween(EtaMotion.Default.exitDuration(260), easing = EtaMotion.Default.accelerate)),
        ) {
            Text(
                text = if (preserveFollowingMessages) {
                    stringResource(R.string.chat_edit_preserve_following)
                } else if (editHasLaterTurns) {
                    stringResource(R.string.chat_edit_replace_later)
                } else {
                    stringResource(R.string.chat_edit_replace_message)
                },
                style = MiuixTheme.textStyles.footnote1,
                color = EtaTheme.colors.textTertiary,
                // 与输入框内正文同一起点（容器 10dp + 文本 8dp）。
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
            )
        }

        if (interruptArmed) Text(stringResource(R.string.eta_interrupt_release),
            style = EtaTheme.type.footnote, color = EtaTheme.colors.onAccentSoft,
            modifier = Modifier.background(EtaTheme.colors.accentSoft, EtaTheme.shapes.radiusFull)
                .padding(horizontal = 12.dp, vertical = 6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    inputContainerTopPx = coordinates.positionInWindow().y.roundToInt()
                },
        ) {
            // 输入胶囊：上游 COUI 的流体液态玻璃（背后的消息被折射、模糊透出），不支持时退回胖小胖的玻璃。
            io.github.mangi.eta.ui.components.coui.EtaFloatingSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = InputContainerShape,
                capsule = true,
                onClick = null,
                wrapContent = true,
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 发送后一道光从左到右扫过玻璃（画在内容下面，不盖文字）。
                    .drawWithCache {
                        val outline = InputContainerShape.createOutline(size, layoutDirection, this)
                        onDrawBehind {
                            val p = sweepProgress.value
                            if (p > 0.001f && p < 0.999f) {
                                val w = size.width
                                val x = -0.3f * w + 1.6f * w * p
                                drawOutline(outline, androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                                    start = androidx.compose.ui.geometry.Offset(x - 0.22f * w, 0f),
                                    end = androidx.compose.ui.geometry.Offset(x + 0.22f * w, size.height)))
                            }
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
        AnimatedVisibility(
            visible = pendingFileReferences.isNotEmpty(),
            enter = fadeIn(tween(EtaMotion.Default.short, easing = EtaMotion.Default.standard)),
            exit = fadeOut(tween(EtaMotion.Default.exitDuration(260), easing = EtaMotion.Default.accelerate)) + shrinkVertically(tween(EtaMotion.Default.exitDuration(260), easing = EtaMotion.Default.accelerate)),
        ) {
            PendingFileReferenceStrip(
                references = pendingFileReferences,
                onRemoveReference = onRemoveFileReference,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        AnimatedVisibility(
            visible = pendingImages.isNotEmpty(),
            enter = fadeIn(tween(EtaMotion.Default.short, easing = EtaMotion.Default.standard)),
            exit = fadeOut(tween(EtaMotion.Default.exitDuration(260), easing = EtaMotion.Default.accelerate)) + shrinkVertically(tween(EtaMotion.Default.exitDuration(260), easing = EtaMotion.Default.accelerate)),
        ) {
            PendingImageStrip(
                images = pendingImages,
                onRemoveImage = onRemoveImage,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 40.dp)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    // 占位文字与输入正文共用同一行高，首字输入时基线不跳动。
                    val inputTextStyle = EtaTheme.type.body
                    val character = LocalRoleplayChat.current
                    if (textFieldState.text.isBlank()) {
                        Text(
                            text = when {
                                // 角色对话的占位文字跟随角色，避免“交给 Eta”打断沉浸感。
                                character != null && isStreaming ->
                                    stringResource(R.string.chat_character_typing, character.characterName)
                                character != null -> stringResource(R.string.chat_character_input_hint, character.characterName)
                                // 电脑会话：执行的是电脑上的 Agent，不是胖小胖本机。
                                remoteComposer != null && isStreaming -> remoteComposer.workingHint
                                remoteComposer?.idleHint != null -> remoteComposer.idleHint
                                isStreaming -> stringResource(R.string.chat_eta_working)
                                else -> stringResource(R.string.eta_input_placeholder)
                            },
                            style = inputTextStyle,
                            color = EtaTheme.colors.textTertiary,
                        )
                    }
                    BasicTextField(
                        state = textFieldState,
                        modifier = Modifier
                            .fillMaxWidth()
                            // 多行输入时高度用弹簧平滑变化，不跳。
                            .animateContentSize(androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 500f))
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        textStyle = inputTextStyle.copy(color = MiuixTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(EtaTheme.colors.accent),
                        lineLimits = TextFieldLineLimits.MultiLine(
                            minHeightInLines = 1,
                            maxHeightInLines = 8,
                        ),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                        if (isEditingMessage) {
                            IconButton(
                                onClick = onCancelMessageEdit,
                                minWidth = ChatInputActionSize,
                                minHeight = ChatInputActionSize,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.ui_cancel_edit_c698df),
                                    modifier = Modifier.size(ChatInputActionIconSize),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        } else {
                            AgentAttachmentPickerButton(
                                popupAnchorTopPx = inputContainerTopPx,
                                popupMaxHeight = thinkingPopupMaxHeight,
                                onAttachImage = onAttachImage,
                                onAttachFiles = onAttachFiles,
                                onAttachFolder = onAttachFolder,
                                onAttachFilePath = onAttachFilePath,
                            )

                            Spacer(modifier = Modifier.width(2.dp))

                            if (availableReasoningEfforts.any { it != ReasoningEffort.OFF }) {
                                ThinkingEffortChip(
                                    effort = reasoningEffort,
                                    options = availableReasoningEfforts,
                                    enabled = !isStreaming,
                                    popupAnchorTopPx = inputContainerTopPx,
                                    popupMaxHeight = thinkingPopupMaxHeight,
                                    onEffortChange = onReasoningEffortChange,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // 上下文圆环：用量 ≥ 30% 才出现；后台整理时旁边有呼吸小点。
                        if (showContextUsage && remoteComposer == null &&
                            ((contextUsage.progress ?: 0f) >= ContextRingThreshold || backgroundCompacting)) {
                            AgentContextUsageButton(contextUsage, onCompactContext, canCompactContext && !isStreaming && !isCompacting,
                                backgroundCompacting = backgroundCompacting)
                        }

                        val action = resolveComposerAction(isStreaming, isCompacting, canSend)
                        val submitCurrentDraft: (Boolean) -> Unit = { interrupt ->
                            if (canSend && !isCompacting) {
                                dictation.cancel()
                                val submittedText = textFieldState.text.toString()
                                textFieldState.clearText()
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                sendSweep++
                                if (interrupt) onInterruptAndSubmit(submittedText) else onSubmit(submittedText)
                            }
                        }
                        val beginDictation = {
                            if (!recognitionEnabled) shortcuts.onOpenSpeechSettings()
                            else if (speechState.active) dictation.finish()
                            else { microphoneTapRequest = true; requestMicrophone() }
                        }
                        // 电脑会话执行中：主按钮是「加入排队」，旁边这个闪电直接插队，当前这一步结束就送达。
                        val steerSubmit = remoteComposer?.onSteerSubmit
                        if (remoteComposer != null && steerSubmit != null && action == ComposerAction.Queue) {
                            EtaIconButton(
                                onClick = {
                                    if (canSend && !isCompacting) {
                                        dictation.cancel()
                                        val submittedText = textFieldState.text.toString()
                                        textFieldState.clearText()
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        steerSubmit(submittedText)
                                    }
                                },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Outlined.Bolt, contentDescription = remoteComposer.steerLabel, tint = EtaTheme.colors.accent, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        // 话筒：点按听写，按住说话；与发送键分开，发送键只负责发送 / 停止 / 排队。
                        ComposerActionButton(
                            action = ComposerAction.Microphone,
                            label = stringResource(if (speechState.active) R.string.speech_dictation_finish else R.string.speech_dictation),
                            longPressLabel = stringResource(R.string.eta_hold_to_talk),
                            onClick = { beginDictation() },
                            onHoldStart = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (recognitionEnabled) { microphoneTapRequest = false; microphoneHold = true; requestMicrophone() }
                            },
                            onHoldEnd = { released ->
                                if (microphoneHold) { microphoneHold = false; if (released) dictation.finish() else dictation.cancel() }
                            },
                            onAccessibleLongClick = { beginDictation() },
                            plain = true,
                        )
                        val mainAction = if (action == ComposerAction.Microphone) ComposerAction.Disabled else action
                        ComposerActionButton(
                            action = mainAction,
                            label = when (mainAction) {
                                ComposerAction.Stop -> stringResource(R.string.chat_stop)
                                ComposerAction.Queue -> stringResource(R.string.eta_queue_message)
                                ComposerAction.Microphone -> stringResource(if (speechState.active) R.string.speech_dictation_finish else R.string.speech_dictation)
                                ComposerAction.Disabled -> stringResource(if (isCompacting) R.string.context_compaction else R.string.chat_send)
                                else -> stringResource(if (isEditingMessage && preserveFollowingMessages) R.string.chat_save_message else if (isEditingMessage) R.string.eta_edit_resend else R.string.chat_send)
                            },
                            longPressLabel = stringResource(if (mainAction == ComposerAction.Queue) R.string.eta_interrupt_send else R.string.eta_hold_to_talk),
                            onClick = {
                                when (mainAction) {
                                    ComposerAction.Stop -> onStop()
                                    ComposerAction.Queue, ComposerAction.Send -> submitCurrentDraft(false)
                                    ComposerAction.Microphone -> beginDictation()
                                    ComposerAction.Disabled -> Unit
                                }
                            },
                            onHoldStart = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (action == ComposerAction.Queue) interruptArmed = true
                                else if (recognitionEnabled) { microphoneTapRequest = false; microphoneHold = true; requestMicrophone() }
                            },
                            onHoldEnd = { released ->
                                if (interruptArmed) { interruptArmed = false; if (released) submitCurrentDraft(true) }
                                if (microphoneHold) { microphoneHold = false; if (released) dictation.finish() else dictation.cancel() }
                            },
                            onAccessibleLongClick = {
                                if (action == ComposerAction.Queue) submitCurrentDraft(true) else beginDictation()
                            },
                        )
                }
            }
            }
        }
    }

}

/**
 * 输入胶囊里的思考等级：一段文字「思考 · 标准」，点一下从这里展开一枚玻璃小窗选等级。
 * 只有一个可选项或正在生成时不可点。
 */
@Composable
private fun ThinkingEffortChip(
    effort: ReasoningEffort,
    options: List<ReasoningEffort>,
    enabled: Boolean,
    @Suppress("UNUSED_PARAMETER") popupAnchorTopPx: Int,
    @Suppress("UNUSED_PARAMETER") popupMaxHeight: Dp,
    onEffortChange: (ReasoningEffort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPopup by remember { mutableStateOf(false) }
    val anchor = com.itos.coui.menu.rememberCouiMenuAnchor()
    val menuEnabled = enabled && options.size > 1
    LaunchedEffect(menuEnabled) {
        if (!menuEnabled) showPopup = false
    }
    val level = stringResource(when (effort) {
        ReasoningEffort.OFF -> R.string.eta_effort_off
        ReasoningEffort.MINIMAL, ReasoningEffort.LOW -> R.string.eta_effort_quick
        ReasoningEffort.HIGH, ReasoningEffort.XHIGH, ReasoningEffort.MAX -> R.string.eta_effort_deep
        else -> R.string.eta_effort_standard
    })
    val label = stringResource(R.string.eta_effort_label, level)
    val colors = EtaTheme.colors
    Row(
        modifier
            .heightIn(min = ChatInputActionSize)
            .couiMenuAnchor(anchor)
            .clip(EtaTheme.shapes.radiusFull)
            .clickable(enabled = menuEnabled, role = androidx.compose.ui.semantics.Role.Button) { anchor.open { showPopup = true } }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = EtaTheme.type.footnote, color = if (menuEnabled) colors.textSecondary else colors.textQuiet,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        if (menuEnabled) Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = colors.textQuiet)
    }
    EtaContextMenu(showPopup && menuEnabled, onDismiss = { showPopup = false }, couiAnchor = anchor) {
        options.forEach { option ->
            EtaMenuItem(option.displayName, onClick = { onEffortChange(option); showPopup = false }, selected = option == effort)
        }
    }
}

/**
 * 横向跟随 Chip，竖向则避开整个输入面板；默认下拉定位只会避开 Chip 自身。
 */
@Composable
private fun PendingImageStrip(
    images: List<PendingImageUi>,
    onRemoveImage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var previousCount by remember { mutableIntStateOf(images.size) }
    LaunchedEffect(images.size) {
        if (images.size > previousCount && images.isNotEmpty()) {
            listState.animateScrollToItem(images.lastIndex)
        }
        previousCount = images.size
    }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(
            items = images,
            key = { it.id },
        ) { image ->
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(EtaTheme.shapes.radiusMd)
                    .background(MiuixTheme.colorScheme.surfaceContainer),
            ) {
                rememberDataUrlBitmap(image.dataUrl)?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                // 可见圆点保持小巧，触控区放大到右上角 32dp，避免误触缩略图。
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(32.dp)
                        .clickable(interactionSource = null, indication = null) { onRemoveImage(image.id) },
                    contentAlignment = Alignment.TopEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(EtaTheme.colors.toastBackground),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.ui_remove_image_089db3),
                            modifier = Modifier.size(11.dp),
                            tint = EtaTheme.colors.toastForeground,
                        )
                    }
                }
            }
        }
    }
}
