package io.github.mangi.eta.agent.overlay

import io.github.mangi.eta.ui.theme.EtaMotion

import io.github.mangi.eta.ui.components.EtaAnimatedVisibility as AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.Path
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.basic.IconButton
import androidx.compose.material.icons.outlined.ContentCopy
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import io.github.mangi.eta.ui.theme.EtaElevation
import io.github.mangi.eta.ui.theme.etaElevation
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.ui.markdown.MarkdownTone
import io.github.mangi.eta.ui.markdown.StaticMarkdown
import io.github.mangi.eta.R
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun overlayPhaseAccent(phase: AgentOverlayPhase): Color = when (phase) {
    AgentOverlayPhase.RUNNING -> io.github.mangi.eta.ui.theme.EtaTheme.colors.running
    AgentOverlayPhase.PAUSED -> io.github.mangi.eta.ui.theme.EtaTheme.colors.paused
    AgentOverlayPhase.FINISHED -> io.github.mangi.eta.ui.theme.EtaTheme.colors.success
    AgentOverlayPhase.FAILED -> io.github.mangi.eta.ui.theme.EtaTheme.colors.danger
}

// 与助手和状态环共用品牌流光；末尾回到蜂蜜色使环形渐变无接缝。
internal val EdgeLightColors = listOf(
    io.github.mangi.eta.ui.theme.EtaAurora.honey,
    io.github.mangi.eta.ui.theme.EtaAurora.peach,
    io.github.mangi.eta.ui.theme.EtaAurora.rose,
    io.github.mangi.eta.ui.theme.EtaAurora.lavender,
    io.github.mangi.eta.ui.theme.EtaAurora.honey,
)

/**
 * 屏幕边缘光窗口：全屏触摸穿透（FLAG_NOT_TOUCHABLE），不压暗页面，不挡操作。
 * 窗口类型 TYPE_ACCESSIBILITY_OVERLAY，截图时被 takeScreenshotOfWindow 过滤，对 Agent 透明。
 * - RUNNING：沿屏幕圆角边缘流动的细光带，外侧一层柔光；告诉用户手机正被接管。
 * - PAUSED：静止的淡色描边，提示任务仍在、控制权暂回用户。
 * - FINISHED / FAILED：不绘制。
 */
/**
 * 屏幕圆角半径（像素）。Display 只按圆弧上报一个半径，实际面板是连续曲率的圆角；
 * 光带用同一半径画连续曲线，转角处比圆弧更饱满，贴住屏幕边缘而不是在角上内缩一截。
 */
@Immutable
internal data class ScreenCornerRadii(val topLeft: Float, val topRight: Float, val bottomRight: Float, val bottomLeft: Float) {
    /** 四角在主流机型上一致；取最大值作为连续曲线的转角尺寸，不让任何一角露出直边。 */
    val radius: Float get() = maxOf(topLeft, topRight, bottomRight, bottomLeft)
}

@Composable
internal fun AgentOverlayGlow(
    state: AgentOverlayState,
    corners: ScreenCornerRadii,
    intensity: io.github.mangi.eta.data.model.AppearanceGlowIntensity = io.github.mangi.eta.data.model.AppearanceGlowIntensity.STANDARD,
    /** 智能流光：EDGE = 屏幕四周细光（AI 在操作屏幕）；后台干活时 CAMERA = 摄像头外圈小光圈，TOP_BAR = 顶部流动光条。 */
    presence: GlowPresence = GlowPresence.EDGE,
    /** 摄像头开孔在屏幕上的位置；null 时小光圈退到顶边正中。 */
    cameraCutout: android.graphics.RectF? = null,
    /** 窗口出现时先跑一遍「开始」流光。 */
    startSweep: Boolean = false,
    /** 用户在「摄像头光圈位置」里调的偏移和大小。 */
    ringAdjust: CameraRingAdjust = CameraRingAdjust.AUTO,
) {
    val phase = state.phase
    if (phase != AgentOverlayPhase.RUNNING && phase != AgentOverlayPhase.PAUSED) return
    val motion = io.github.mangi.eta.ui.theme.EtaTheme.motion
    val running = phase == AgentOverlayPhase.RUNNING
    val pausedAccent = overlayPhaseAccent(AgentOverlayPhase.PAUSED)
    var shown by remember { mutableStateOf(false) }
    val sweep = remember { androidx.compose.animation.core.Animatable(if (startSweep && !motion.reduceMotion) 0f else 1f) }
    LaunchedEffect(Unit) {
        shown = true
        if (sweep.value < 1f) sweep.animateTo(1f, tween(AmbientGlowPolicy.START_SWEEP_MS, easing = LinearEasing))
    }
    val reveal by animateFloatAsState(if (shown) 1f else 0f, tween(motion.long, easing = motion.emphasizedDecelerate), label = "edge_reveal")
    // 边框与小光圈之间淡入淡出地切换（AI 开始 / 停止操作屏幕时）。
    val edgeWeight by animateFloatAsState(if (presence == GlowPresence.EDGE) 1f else 0f, tween(motion.medium, easing = motion.standard), label = "edge_weight")
    val ringWeight by animateFloatAsState(if (presence == GlowPresence.CAMERA) 1f else 0f, tween(motion.medium, easing = motion.standard), label = "ring_weight")
    val barWeight by animateFloatAsState(if (presence == GlowPresence.TOP_BAR) 1f else 0f, tween(motion.medium, easing = motion.standard), label = "bar_weight")

    val rotation = if (running && !motion.reduceMotion) {
        val transition = rememberInfiniteTransition(label = "edge_light")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(motion.auroraMillis, easing = LinearEasing), RepeatMode.Restart),
            label = "edge_rotation",
        )
    } else null
    // 顶部光条的亮光从正中流向两侧，一次 2.4 秒；只在用到光条时才跑这个动画。
    val flow = if (running && !motion.reduceMotion && presence == GlowPresence.TOP_BAR) {
        val transition = rememberInfiniteTransition(label = "top_bar")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(TopBarGeometry.FLOW_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "top_bar_flow",
        )
    } else null
    val colors = remember { EdgeLightColors.map { it.toArgb() }.toIntArray() }

    Box(
        modifier = Modifier.fillMaxSize().drawBehind {
            val density = this.density
            val edge = screenEdgePath(size.width, size.height, corners, density)
            val cornerRadius = corners.radius
            val angle = rotation?.value ?: 0f
            // 开始流光跑的时候，常驻光效跟着它慢慢亮起来，不抢它的风头。
            val settle = reveal * (0.25f + 0.75f * sweep.value)
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.drawStartSweep(edge, size.width, sweep.value, colors, intensity, density)
                val ring = AmbientGlowPolicy.cameraRing(cameraCutout, size.width, density, ringAdjust)
                if (!running) {
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = intensity.lineWidthDp * density
                        color = pausedAccent.copy(alpha = 0.58f * intensity.lineAlpha * reveal * edgeWeight).toArgb()
                    }
                    if (edgeWeight > 0f) native.drawPath(edge, paint)
                    native.drawCameraRing(ring, colors, 0f, intensity, density, reveal * ringWeight,
                        pausedColor = pausedAccent.toArgb())
                    native.drawTopBar(edge, size.width, cornerRadius, colors, 0f, intensity, density, reveal * barWeight,
                        pausedColor = pausedAccent.toArgb())
                    return@drawIntoCanvas
                }
                if (edgeWeight > 0f) {
                    val (halo, line) = strokePaints(edgeShader(colors, size.width, size.height, angle), 0, intensity, density, settle * edgeWeight)
                    native.drawPath(edge, halo)
                    native.drawPath(edge, line)
                }
                if (ringWeight > 0f) native.drawCameraRing(ring, colors, angle * 2f, intensity, density, settle * ringWeight)
                // 减弱动效时光条不流动，停在两团亮光刚分开的位置。
                if (barWeight > 0f) native.drawTopBar(edge, size.width, cornerRadius, colors, flow?.value ?: 0.35f,
                    intensity, density, settle * barWeight)
            }
        }
    )
}

/**
 * 结束提示：任务都结束后，屏幕四周细光亮起再暗下去一次，然后调用 [onFinished] 收起窗口。
 */
@Composable
internal fun AgentOverlayEndPulse(
    corners: ScreenCornerRadii,
    intensity: io.github.mangi.eta.data.model.AppearanceGlowIntensity,
    onFinished: () -> Unit,
) {
    val motion = io.github.mangi.eta.ui.theme.EtaTheme.motion
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    val colors = remember { EdgeLightColors.map { it.toArgb() }.toIntArray() }
    LaunchedEffect(Unit) {
        if (!motion.reduceMotion) progress.animateTo(1f, tween(AmbientGlowPolicy.END_PULSE_MS, easing = LinearEasing))
        onFinished()
    }
    Box(
        modifier = Modifier.fillMaxSize().drawBehind {
            val edge = screenEdgePath(size.width, size.height, corners, density)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawEndPulse(edge, size.width, size.height, progress.value, colors, intensity, density)
            }
        }
    )
}

/** 光带沿屏幕轮廓的连续曲线走：路径向内收一点，转角同步减小，保证与屏幕边缘等距。 */
private fun screenEdgePath(width: Float, height: Float, corners: ScreenCornerRadii, density: Float): android.graphics.Path {
    val inset = 1.5f * density
    return Path().apply {
        addSquircleRect(
            width = width - inset * 2,
            height = height - inset * 2,
            cornerRadius = (corners.radius - inset).coerceAtLeast(0f),
        )
        translate(Offset(inset, inset))
    }.asAndroidPath()
}

@Composable
internal fun OverlaySupplementInput(
    value: String,
    onValueChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val textColor = MiuixTheme.colorScheme.onSurface
    val fieldBg = MiuixTheme.colorScheme.surfaceContainer

    LaunchedEffect(Unit) {
        delay(180)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp, max = 112.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(fieldBg)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            if (value.isBlank()) {
                Text(
                    text = stringResource(R.string.overlay_supplement_hint),
                    color = textColor.copy(alpha = 0.45f),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    color = textColor,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                cursorBrush = SolidColor(io.github.mangi.eta.ui.theme.EtaTheme.colors.accent),
                maxLines = 4,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                text = stringResource(R.string.action_cancel),
                onClick = onCancel,
                minWidth = 44.dp,
                minHeight = 32.dp,
                insideMargin = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                text = stringResource(R.string.overlay_send),
                onClick = onSend,
                enabled = value.isNotBlank(),
                minWidth = 44.dp,
                minHeight = 32.dp,
                insideMargin = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

/**
 * 任务结束后的结果卡片：底部浮起的圆角面板，顶部抓手提示可下滑关闭。
 * 关闭途径：无交互自动收起、下滑、系统返回（由 Service 注册）、底部"完成"按钮；
 * 次要操作"复制"放在同一行，正文可滚动，过长时底部渐隐提示还有内容。
 */
@Composable
internal fun AgentResultCard(
    state: AgentOverlayState,
    onClose: () -> Unit,
    onOpenEta: () -> Unit,
) {
    val motion = io.github.mangi.eta.ui.theme.EtaTheme.motion
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scope = rememberCoroutineScope()
    fun close() {
        if (!visible) return
        visible = false
        // 等退场动画结束再移除窗口，避免卡片被瞬间抽走。
        scope.launch {
            delay(motion.exitDuration(380).toLong())
            onClose()
        }
    }

    val isFailed = state.phase == AgentOverlayPhase.FAILED
    val accent = overlayPhaseAccent(state.phase)
    val statusLabel = stringResource(
        if (isFailed) R.string.overlay_substatus_failed else R.string.overlay_substatus_finished,
    )
    val content = state.detailText.ifBlank { state.status.localizedText() }
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var touching by remember { mutableStateOf(false) }
    var interactionTick by remember { mutableIntStateOf(0) }
    val interacting = touching || scroll.isScrollInProgress || dragOffset > 0f
    LaunchedEffect(visible, interacting, interactionTick) {
        if (!visible || interacting) return@LaunchedEffect
        delay(ResultCardAutoDismissMs)
        close()
    }
    val settledOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = if (motion.reduceMotion) androidx.compose.animation.core.snap() else spring(motion.dampingRatio, motion.stiffness),
        label = "result_drag",
    )
    val dismissThreshold = with(density) { 96.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(spring(EtaMotion.Default.dampingRatio, EtaMotion.Default.stiffness)) { it } +
                fadeIn(tween(EtaMotion.Default.short, easing = EtaMotion.Default.standard)),
            exit = slideOutVertically(tween(motion.exitDuration(380), easing = motion.accelerate)) { it } + fadeOut(tween(motion.exitDuration(380), easing = motion.accelerate)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .graphicsLayer { translationY = settledOffset }
                    .etaElevation(EtaElevation.E3, RoundedCornerShape(ResultCardCorner))
                    .clip(RoundedCornerShape(ResultCardCorner))
                    .background(MiuixTheme.colorScheme.surface)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitPointerEvent(PointerEventPass.Initial)
                            touching = true
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                            } while (event.changes.any { it.pressed })
                            touching = false
                            interactionTick++
                        }
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { if (dragOffset > dismissThreshold) close() else dragOffset = 0f },
                            onDragCancel = { dragOffset = 0f },
                        ) { change, amount ->
                            change.consume()
                            // 只允许向下拖；向上带阻尼，避免卡片被拖离底边。
                            dragOffset = (dragOffset + amount).coerceAtLeast(0f)
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f)),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isFailed) Icons.Outlined.Close else Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = accent,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = statusLabel,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Box(modifier = Modifier.weight(1f, fill = false)) {
                    StaticMarkdown(
                        content = content,
                        tone = MarkdownTone.Answer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scroll)
                            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp),
                    )
                    if (scroll.canScrollForward) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(28.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, MiuixTheme.colorScheme.surface),
                                    ),
                                ),
                        )
                    }
                }

                ResultCardActions(content = content, onOpenEta = onOpenEta, onDone = ::close)
            }
        }
    }
}

@Composable
private fun ResultCardActions(content: String, onOpenEta: () -> Unit, onDone: () -> Unit) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_400)
            copied = false
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 复制是图标按钮，主次两个文字按钮分别是"完成"（留在当前应用）与"回到 Eta"（查看完整过程）。
        IconButton(
            onClick = {
                clipboard.setText(AnnotatedString(content))
                copied = true
            },
            backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
            minWidth = 44.dp,
            minHeight = 44.dp,
            cornerRadius = 22.dp,
        ) {
            Icon(
                imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                contentDescription = stringResource(if (copied) R.string.copy_copied else R.string.ui_copy_4edd1d),
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
        TextButton(
            text = stringResource(R.string.overlay_result_done),
            onClick = onDone,
            modifier = Modifier.weight(1f),
            minHeight = 44.dp,
        )
        TextButton(
            text = stringResource(R.string.overlay_result_open_eta),
            onClick = onOpenEta,
            modifier = Modifier.weight(1f),
            minHeight = 44.dp,
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}

private val ResultCardCorner = 28.dp
private const val ResultCardAutoDismissMs = 8_000L
