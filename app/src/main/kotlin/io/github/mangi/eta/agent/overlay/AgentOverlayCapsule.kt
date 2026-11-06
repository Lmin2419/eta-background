package io.github.mangi.eta.agent.overlay

import io.github.mangi.eta.ui.theme.EtaMotion

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.mangi.eta.ui.components.StatusRing
import io.github.mangi.eta.ui.components.label
import io.github.mangi.eta.ui.components.EtaIconButton
import io.github.mangi.eta.ui.model.TaskVisualStatus
import io.github.mangi.eta.ui.model.toTaskVisualStatus
import io.github.mangi.eta.ui.theme.EtaTheme
import io.github.mangi.eta.ui.theme.darkEtaColors
import io.github.mangi.eta.ui.theme.toMiuixColors
import top.yukonga.miuix.kmp.theme.darkColorScheme

import io.github.mangi.eta.ui.components.EtaAnimatedContent as AnimatedContent
import io.github.mangi.eta.ui.components.EtaAnimatedVisibility as AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import io.github.mangi.eta.ui.theme.EtaElevation
import io.github.mangi.eta.ui.theme.etaElevation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 顶部状态胶囊：收起时只显示一句当前步骤，点击展开补充、暂停与停止。
 * 位置固定在状态栏下方居中，不随 Agent 的点击位置移动，避免和目标控件重叠。
 * 状态文字在语义变化时以上滑淡入切换，副行显示所选任务的最新流式内容。
 */
internal data class OverlayTaskItem(val id: String, val title: String)

@Composable
internal fun AgentOverlayCapsule(
    state: AgentOverlayState,
    expanded: Boolean,
    yielded: Boolean,
    onToggleExpanded: () -> Unit,
    onCollapsedSettled: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSupplementModeChange: (Boolean) -> Unit,
    onSupplement: (String) -> Unit,
    tasks: List<OverlayTaskItem> = emptyList(),
    selectedTaskId: String? = null,
    onSelectTask: (String) -> Unit = {},
    onOpenConversation: () -> Unit = {},
    onOpenPreview: () -> Unit = onOpenConversation,
) {
    val palette = remember { darkEtaColors() }
    MiuixTheme(colors = palette.toMiuixColors(darkColorScheme())) {
    EtaTheme(palette) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    var supplementMode by remember { mutableStateOf(false) }
    var supplementText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun setSupplementMode(enabled: Boolean) {
        onSupplementModeChange(enabled)
        supplementMode = enabled
        if (!enabled) supplementText = ""
    }

    // 收起键盘后再切换窗口焦点，避免输入法在窗口失焦瞬间闪退或残留。
    fun leaveSupplementMode(then: () -> Unit = {}) {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        scope.launch {
            delay(80)
            setSupplementMode(false)
            then()
        }
    }

    LaunchedEffect(expanded) {
        if (!expanded && supplementMode) leaveSupplementMode()
    }

    // 为 Agent 手势让路：离开时快速上移收小淡出，回来时用弹簧落回，只改绘制层不触发重排。
    val motion = EtaTheme.motion
    val clearance by animateFloatAsState(
        targetValue = if (yielded) 1f else 0f,
        animationSpec = if (motion.reduceMotion) tween(motion.short, easing = motion.standard) else if (yielded) {
            tween(motion.exitDuration(260), easing = motion.accelerate)
        } else {
            spring(dampingRatio = motion.dampingRatio, stiffness = motion.stiffness)
        },
        label = "capsule_clearance",
    )
    val visualStatus = state.status.toTaskVisualStatus()
    val attention = visualStatus == TaskVisualStatus.Attention
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(attention, selectedTaskId) {
        if (attention) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val shape = if (expanded) EtaTheme.shapes.radiusLg else EtaTheme.shapes.radiusFull
    val statusText = if (attention) stringResource(R.string.overlay_attention_prompt) else state.status.localizedText()
    val expandDescription = stringResource(R.string.overlay_capsule_toggle, statusText)
    val statusDescription = visualStatus.label()
    // 展开进度只驱动绘制层的透明度与位移，不参与测量；控制区用 AnimatedVisibility 只在两端各测一次。
    val expansion by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = if (motion.reduceMotion) androidx.compose.animation.core.snap() else spring(motion.dampingRatio, motion.stiffness),
        label = "capsule_expansion",
        finishedListener = { value -> if (value == 0f) onCollapsedSettled() },
    )

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(spring(EtaMotion.Default.dampingRatio, EtaMotion.Default.stiffness)) { -it } +
            fadeIn(tween(EtaMotion.Default.short, easing = EtaMotion.Default.standard)),
        exit = slideOutVertically(tween(EtaMotion.Default.exitDuration(260), easing = EtaMotion.Default.accelerate)) { -it } + fadeOut(tween(EtaMotion.Default.exitDuration(260), easing = EtaMotion.Default.accelerate)),
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        // 点击时轻微回弹，展开后保持原尺寸；只改绘制层，不触发重新布局。
                        val press = 1f - 0.03f * (1f - expansion) * expansion * 4f
                        val shrink = 1f - 0.08f * clearance
                        scaleX = if (motion.reduceMotion) 1f else press * shrink
                        scaleY = if (motion.reduceMotion) 1f else press * shrink
                        translationY = if (motion.reduceMotion) 0f else -12.dp.toPx() * clearance
                        alpha = 1f - clearance
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    .etaElevation(EtaElevation.E3, shape)
                    .clip(shape)
                    .background(if (attention) palette.attentionSoft else palette.surface.copy(alpha = 0.92f))
                    .border(1.dp, palette.outline, shape),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleExpanded,
                        )
                        .semantics { contentDescription = listOfNotNull(tasks.firstOrNull { it.id == selectedTaskId }?.title, statusDescription, expandDescription).joinToString(", ") }
                        .heightIn(min = 40.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusRing(status = visualStatus, size = 16.dp)
                    Spacer(Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = statusText,
                        transitionSpec = {
                            (slideInVertically(tween(EtaMotion.Default.short, easing = EtaMotion.Default.standard)) { it / 2 } + fadeIn(tween(EtaMotion.Default.short, easing = EtaMotion.Default.standard))) togetherWith
                                (slideOutVertically(tween(EtaMotion.Default.exitDuration(260), easing = EtaMotion.Default.accelerate)) { -it / 2 } + fadeOut(tween(EtaMotion.Default.exitDuration(260), easing = EtaMotion.Default.accelerate))) using
                                SizeTransform(clip = true) { _, _ -> snap() }
                        },
                        label = "capsule_status",
                    ) { text ->
                        Text(
                            text = if (tasks.size > 1) stringResource(R.string.overlay_task_count, tasks.size, text) else text,
                            color = MiuixTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    }
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(18.dp))
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(spring(EtaMotion.Default.dampingRatio, EtaMotion.Default.stiffness), clip = true) +
                        fadeIn(tween(EtaMotion.Default.short, delayMillis = 40, easing = EtaMotion.Default.standard)),
                    exit = shrinkVertically(spring(EtaMotion.Default.dampingRatio, EtaMotion.Default.stiffness), clip = true) +
                        fadeOut(tween(EtaMotion.Default.exitDuration(260), easing = EtaMotion.Default.accelerate)),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                            .graphicsLayer { translationY = if (motion.reduceMotion) 0f else (1f - expansion) * -6.dp.toPx() },
                    ) {
                        ThoughtLine(thought = (if (state.status == AgentOverlayStatus.GeneratingAnswer) state.detailText.takeLast(96) else state.thought)
                            .takeIf { state.phase == AgentOverlayPhase.RUNNING }.orEmpty())
                        if (supplementMode) {
                            OverlaySupplementInput(
                                value = supplementText,
                                onValueChange = { supplementText = it },
                                onCancel = { leaveSupplementMode() },
                                onSend = {
                                    val text = supplementText.trim()
                                    if (text.isNotBlank()) leaveSupplementMode { onSupplement(text) }
                                },
                            )
                        } else {
                            Column(Modifier.fillMaxWidth().heightIn(max = 112.dp).verticalScroll(rememberScrollState())) {
                                tasks.forEach { task ->
                                    Text(
                                        text = (if (task.id == selectedTaskId) "● " else "○ ") + task.title,
                                        modifier = Modifier.fillMaxWidth().clickable { onSelectTask(task.id) }.heightIn(min = 48.dp).wrapContentHeight().padding(vertical = 7.dp),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.overlay_open_conversation), modifier = Modifier.clickable(onClick = onOpenConversation)
                                    .heightIn(min = 48.dp).wrapContentHeight().padding(vertical = 10.dp), color = palette.accent, fontSize = 12.sp)
                                Text(stringResource(R.string.overlay_supplement), modifier = Modifier.clickable { setSupplementMode(true) }
                                    .heightIn(min = 48.dp).wrapContentHeight().padding(vertical = 10.dp), color = palette.accent, fontSize = 12.sp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            ) {
                                CapsuleAction(
                                    icon = Icons.Rounded.Visibility,
                                    label = stringResource(R.string.task_notice_view),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                    onClick = onOpenPreview,
                                )
                                if (state.phase == AgentOverlayPhase.RUNNING) {
                                    CapsuleAction(
                                        icon = Icons.Rounded.Pause,
                                        label = stringResource(R.string.overlay_pause),
                                        tint = MiuixTheme.colorScheme.onSurface,
                                        onClick = onPause,
                                    )
                                } else if (state.phase == AgentOverlayPhase.PAUSED) {
                                    CapsuleAction(
                                        icon = Icons.Rounded.PlayArrow,
                                        label = stringResource(R.string.overlay_resume),
                                        tint = MiuixTheme.colorScheme.primary,
                                        onClick = onResume,
                                    )
                                }
                                CapsuleAction(
                                    icon = Icons.Rounded.Stop,
                                    label = stringResource(R.string.action_stop),
                                    tint = MiuixTheme.colorScheme.error,
                                    onClick = onStop,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
    }
}

// The fixed dark palette also applies to supplemental controls.
/**
 * 思考尾句：单行、从左侧裁掉旧内容，让最新的字始终可见。
 * 流式增量只改文字，不做逐字动画；行的出现与消失用高度不变的淡入淡出，避免胶囊上下跳动。
 */
@Composable
private fun ThoughtLine(thought: String) {
    val shown = thought.isNotBlank()
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(EtaMotion.Default.short, easing = EtaMotion.Default.standard), label = "thought_alpha")
    var lastThought by remember { mutableStateOf("") }
    if (shown) lastThought = thought
    Text(
        text = lastThought,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        maxLines = 1,
        // 起始端省略：保留尾部最新的推理，像滚动字幕一样向前推进。
        overflow = TextOverflow.StartEllipsis,
        modifier = Modifier
            .padding(top = 1.dp)
            .graphicsLayer { this.alpha = alpha },
    )
}

@Composable
private fun CapsuleAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        EtaIconButton(onClick = onClick) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(20.dp), tint = tint)
        }
        Text(label, color = tint, style = EtaTheme.type.caption)
    }
}
