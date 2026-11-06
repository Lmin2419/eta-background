package io.github.mangi.eta.agent.voice

import io.github.mangi.eta.ui.theme.EtaMotion


import io.github.mangi.eta.ui.components.EtaAnimatedContent as AnimatedContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import io.github.mangi.eta.ui.components.EtaIconButton
import io.github.mangi.eta.ui.theme.EtaTheme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.R
import kotlinx.coroutines.flow.first
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun AssistantComposer(
    state: EtaVoiceUiState,
    input: String,
    speech: EtaSpeechState,
    recognitionEnabled: Boolean,
    inputFocusRequestKey: Int,
    onInputFocus: () -> Unit,
    onMicrophone: (holdToTalk: Boolean) -> Unit,
    onCancelSpeech: () -> Unit,
    onFinishSpeech: () -> Unit,
    onDownloadModel: () -> Unit,
    onOpenSpeechSettings: () -> Unit,
    onKeyboard: () -> Unit,
    keyboardVisible: Boolean,
    colors: EtaVoicePanelColors,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    speechLevel: () -> Float = { 0f },
    canOpenConversation: Boolean = false,
    onOpenConversation: () -> Unit = {},
) {
    Column(modifier = modifier) {
        if (recognitionEnabled) EtaSpeechFeedback(speech, onDownloadModel, onOpenSpeechSettings)
        AnimatedContent(
            // 按住说话期间保持输入栏：切换布局会移除正被按住的按钮，松手事件随之丢失。
            targetState = recognitionEnabled && speech.active && !speech.holdToTalk && state.messages.isEmpty(),
            transitionSpec = {
                fadeIn(tween(EtaMotion.Default.medium, easing = EtaMotion.Default.emphasizedDecelerate)) togetherWith fadeOut(tween(EtaMotion.Default.exitDuration(260), easing = EtaMotion.Default.accelerate)) using
                    SizeTransform(clip = false)
            },
            contentAlignment = Alignment.BottomCenter,
            label = "assistant_input_mode",
        ) { voiceMode ->
            if (voiceMode) {
                AssistantVoiceEntry(speech, input, onKeyboard, onFinishSpeech, onCancelSpeech, speechLevel, canOpenConversation, onOpenConversation)
            } else {
                AssistantInputBar(
                    state = state,
                    input = input,
                    speech = speech,
                    recognitionEnabled = recognitionEnabled,
                    inputFocusRequestKey = inputFocusRequestKey,
                    onInputFocus = onInputFocus,
                    onMicrophone = onMicrophone,
                    onCancelSpeech = onCancelSpeech,
                    onFinishSpeech = onFinishSpeech,
                    keyboardVisible = keyboardVisible,
                    colors = colors,
                    onInputChange = onInputChange,
                    onSubmit = onSubmit,
                    onStop = onStop,
                    onKeyboard = onKeyboard,
                    speechLevel = speechLevel,
                    canOpenConversation = canOpenConversation,
                    onOpenConversation = onOpenConversation,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AssistantInputBar(
    state: EtaVoiceUiState,
    input: String,
    speech: EtaSpeechState,
    recognitionEnabled: Boolean,
    inputFocusRequestKey: Int,
    onInputFocus: () -> Unit,
    onMicrophone: (holdToTalk: Boolean) -> Unit,
    onCancelSpeech: () -> Unit,
    onFinishSpeech: () -> Unit,
    keyboardVisible: Boolean,
    colors: EtaVoicePanelColors,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    onKeyboard: () -> Unit,
    speechLevel: () -> Float = { 0f },
    canOpenConversation: Boolean = false,
    onOpenConversation: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val themeColors = EtaTheme.colors
    val accent = themeColors.accent
    val onAccent = themeColors.onAccent
    val canSubmit = input.isNotBlank() && state.phase != EtaVoicePhase.PROCESSING
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val windowInfo = LocalWindowInfo.current
    val inputEnabled = state.phase != EtaVoicePhase.PROCESSING && !speech.active
    LaunchedEffect(inputFocusRequestKey, inputEnabled) {
        if (inputFocusRequestKey < 0 || !inputEnabled) return@LaunchedEffect
        // 输入栏挂载且浮窗取得焦点后再连接 IME，避免动画切换时请求落在尚不存在的输入框上。
        withFrameNanos { }
        snapshotFlow { windowInfo.isWindowFocused }.first { it }
        focusRequester.requestFocus()
        withFrameNanos { }
        keyboard?.show()
    }
    var textFocused by remember { mutableStateOf(false) }
    val inputHighlighted = textFocused || keyboardVisible
    val inputBorder by animateColorAsState(
        targetValue = themeColors.outlineStrong.copy(alpha = if (inputHighlighted) 1f else 0.55f),
        animationSpec = tween(EtaMotion.Default.short, easing = EtaMotion.Default.standard),
        label = "assistant_input_border",
    )
    val inputShape = RoundedCornerShape(20.dp)
    val trailingIcon = when {
        speech.active || state.phase == EtaVoicePhase.PROCESSING -> Icons.Rounded.Stop
        canSubmit || !recognitionEnabled -> Icons.Rounded.Send
        else -> Icons.Rounded.Mic
    }
    val trailingDescription = when {
        speech.phase == EtaSpeechPhase.RECOGNIZING -> R.string.action_stop
        speech.active -> R.string.voice_finish_speech
        state.phase == EtaVoicePhase.PROCESSING -> R.string.action_stop
        canSubmit || !recognitionEnabled -> R.string.voice_send
        else -> R.string.voice_tap_to_speak
    }
    val trailingAction = when {
        speech.phase == EtaSpeechPhase.RECOGNIZING -> onCancelSpeech
        speech.active -> onFinishSpeech
        state.phase == EtaVoicePhase.PROCESSING -> onStop
        canSubmit || !recognitionEnabled -> onSubmit
        else -> { { onMicrophone(false) } }
    }
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(inputShape)
                .background(themeColors.surface)
                .border(1.dp, inputBorder, inputShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (it.isFocused && !textFocused) onInputFocus()
                        textFocused = it.isFocused
                    }
                    .focusRequester(focusRequester),
                enabled = inputEnabled,
                textStyle = TextStyle(
                    color = themeColors.textPrimary,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                ),
                cursorBrush = SolidColor(themeColors.textPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSubmit) onSubmit() }),
                maxLines = 4,
                minLines = 1,
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (input.isEmpty()) {
                            Text(
                                text = stringResource(
                                    if (speech.holdToTalk) R.string.voice_release_to_send else R.string.voice_input_hint,
                                ),
                                color = themeColors.textTertiary,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EtaIconButton(
                onClick = onKeyboard,
                modifier = Modifier.size(44.dp),
                enabled = true,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Keyboard,
                    contentDescription = stringResource(R.string.voice_use_keyboard),
                    tint = themeColors.textPrimary,
                )
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                EtaAssistantWave(speech.active, speechLevel, Modifier.size(80.dp), ring = true)
                AssistantTrailingButton(
                    modifier = Modifier.background(accent, CircleShape),
                    iconRes = trailingIcon,
                    contentDescription = stringResource(trailingDescription),
                    tint = onAccent,
                    enabled = speech.active || recognitionEnabled || canSubmit || state.phase == EtaVoicePhase.PROCESSING,
                    holdToTalkAvailable = recognitionEnabled && !speech.active && !canSubmit && state.phase != EtaVoicePhase.PROCESSING,
                    onClick = trailingAction,
                    onHoldStart = { onMicrophone(true) },
                    onHoldEnd = onFinishSpeech,
                    onHoldCancel = onCancelSpeech,
                )
            }
            EtaIconButton(
                onClick = onOpenConversation,
                modifier = Modifier.size(44.dp),
                enabled = canOpenConversation,
            ) {
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = stringResource(R.string.overlay_open_conversation),
                    tint = themeColors.textPrimary,
                )
            }
        }
    }
}


/**
 * 输入栏尾部按钮。空闲时长按进入按住说话：松手发送，手指移出按钮取消；
 * 长按触发后不再派发点击，避免松手时又按旧状态开始一次新的录音。
 */
@Composable
private fun AssistantTrailingButton(
    iconRes: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    enabled: Boolean,
    holdToTalkAvailable: Boolean,
    onClick: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    onHoldCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val currentClick by rememberUpdatedState(onClick)
    val currentHoldAvailable by rememberUpdatedState(holdToTalkAvailable)
    val currentHoldStart by rememberUpdatedState(onHoldStart)
    val currentHoldEnd by rememberUpdatedState(onHoldEnd)
    val currentHoldCancel by rememberUpdatedState(onHoldCancel)
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(64.dp)
            .graphicsLayer {
                val scale = if (pressed) 0.9f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            // 手势检测不产生无障碍动作；读屏用户只需要点击语义，按住说话依赖触摸时长不适合读屏。
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (!enabled) disabled()
                onClick { if (enabled) currentClick(); enabled }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    var holding = false
                    try {
                        val down = awaitFirstDown()
                        down.consume()
                        pressed = true
                        // Pair distinguishes an actual cancellation (null Up) from timeout.
                        val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation() to true
                        }
                        if (early != null) {
                            early.first?.let { it.consume(); currentClick() }
                        } else {
                            if (currentHoldAvailable) {
                                holding = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentHoldStart()
                            }
                            val up = waitForUpOrCancellation()
                            if (up != null && holding) {
                                up.consume()
                                holding = false
                                currentHoldEnd()
                            }
                        }
                    } finally {
                        pressed = false
                        // Native CANCEL, an out-of-bounds move, disable or detach never sends.
                        if (holding) currentHoldCancel()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = iconRes,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = if (enabled) tint else tint.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun AssistantVoiceEntry(
    speech: EtaSpeechState, input: String, onKeyboard: () -> Unit,
    onFinishSpeech: () -> Unit, onCancelSpeech: () -> Unit, speechLevel: () -> Float,
    canOpenConversation: Boolean, onOpenConversation: () -> Unit,
) {
    val controls = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        controls.animateTo(1f, tween(EtaMotion.Default.short, easing = EtaMotion.Default.standard))
    }
    val colors = EtaTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Text(input.ifBlank { stringResource(if (speech.phase == EtaSpeechPhase.RECOGNIZING)
            R.string.voice_recognizing else R.string.voice_listening) },
            color = colors.textPrimary, fontSize = 15.sp, lineHeight = 24.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            EtaIconButton(onKeyboard, enabled = controls.value >= 0.5f) {
                Icon(Icons.Rounded.Keyboard, stringResource(R.string.voice_use_keyboard), tint = colors.textPrimary)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EtaAssistantWave(speech.active, speechLevel, Modifier.size(80.dp), ring = true)
                EtaIconButton(
                    onClick = if (speech.phase == EtaSpeechPhase.RECOGNIZING) onCancelSpeech else onFinishSpeech,
                    enabled = controls.value >= 0.5f,
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.accent)
                        .graphicsLayer { alpha = controls.value },
                ) {
                    Icon(Icons.Rounded.Stop, stringResource(if (speech.phase == EtaSpeechPhase.RECOGNIZING)
                        R.string.action_stop else R.string.voice_finish_speech), tint = colors.onAccent)
                }
            }
            EtaIconButton(onOpenConversation, enabled = canOpenConversation && controls.value >= 0.5f) {
                Icon(Icons.Rounded.OpenInNew, stringResource(R.string.overlay_open_conversation), tint = colors.textPrimary)
            }
        }
    }
}
