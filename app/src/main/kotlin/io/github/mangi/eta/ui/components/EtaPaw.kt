package io.github.mangi.eta.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.theme.EtaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/*
 * 胖小胖的爪印，与桌面图标同一套几何（etax/design/brand/pangxiaopang-icon.svg）：
 * 掌心肉垫 + 四颗脚趾，设计坐标 320×320（原点在 96,96）。这里用 Canvas 画，
 * 让每颗脚趾能单独动——「思考中」依次按下、首页依次冒出、下拉刷新随手指逐颗出现。
 * 动画值只在绘制阶段读取，不触发重组。
 */

private const val PawBox = 320f
private const val PawOrigin = 96f
private val PadPath: Path = PathParser().parsePathString(
    "M256,268 C306,268 350,302 367,344 C381,380 364,414 334,414 C316,414 306,406 290,406 " +
        "C276,406 268,414 256,414 C244,414 236,406 222,406 C206,406 196,414 178,414 " +
        "C148,414 131,380 145,344 C162,302 206,268 256,268 Z",
).toPath()
private val Toes = listOf(Triple(158f, 230f, -20f), Triple(218f, 172f, -7f), Triple(294f, 172f, 7f), Triple(354f, 230f, 20f))
private val PawLight = Color(0xFFEB9A7B)
private val PawDeep = Color(0xFFC95F3D)
private val Gloss = Color.White

/** 每颗脚趾（0..3）与掌心的状态：[appear] 0→1 冒出来，[press] 0→1 按下去。 */
internal class PawPose(
    val appear: (Int) -> Float = { 1f },
    val press: (Int) -> Float = { 0f },
    val padAppear: () -> Float = { 1f },
)

private fun DrawScope.drawPaw(pose: PawPose) {
    val s = size.minDimension / PawBox
    // 爪印的视觉中心比几何中心略低，往上提一点让它在圆框里居中。
    translate(left = (size.width - PawBox * s) / 2f, top = (size.height - PawBox * s) / 2f - 14f * s) {
        scale(s, s, pivot = Offset.Zero) {
            translate(-PawOrigin, -PawOrigin) {
                val brush = Brush.radialGradient(listOf(PawLight, PawDeep), center = Offset(226f, 214f), radius = 250f)
                val pad = pose.padAppear().coerceIn(0f, 1.2f)
                if (pad > 0.01f) {
                    val maxPress = (0..3).maxOf { pose.press(it) }
                    val squash = 1f - 0.04f * maxPress
                    scale(pad * (2f - squash), pad * squash, pivot = Offset(256f, 414f)) {
                        drawPath(PadPath, brush)
                        rotate(-20f, pivot = Offset(222f, 306f)) {
                            drawOval(Gloss.copy(alpha = 0.35f), topLeft = Offset(222f - 34f, 306f - 14f), size = Size(68f, 28f))
                        }
                    }
                }
                Toes.forEachIndexed { i, (cx, cy, deg) ->
                    val a = pose.appear(i).coerceIn(0f, 1.25f)
                    if (a <= 0.01f) return@forEachIndexed
                    val p = pose.press(i).coerceIn(0f, 1f)
                    translate(top = 12f * p) {
                        scale(a * (1f - 0.1f * p), pivot = Offset(cx, cy)) {
                            rotate(deg, pivot = Offset(cx, cy)) {
                                drawOval(brush, topLeft = Offset(cx - 37f, cy - 45f), size = Size(74f, 90f))
                            }
                            rotate(deg - 32f, pivot = Offset(cx - 9f, cy - 17f)) {
                                drawOval(Gloss.copy(alpha = 0.43f), topLeft = Offset(cx - 20f, cy - 23.5f), size = Size(22f, 13f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EtaPaw(pose: PawPose, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawPaw(pose) }
}

/**
 * 「正在思考」：四颗脚趾从左到右依次轻轻按下，像猫在踩键盘，约 1.4 秒一轮；掌心随之微微一沉。
 * 减少动画时静止显示。
 */
@Composable
internal fun EtaThinkingMark(modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val motion = EtaTheme.motion
    val phase: State<Float>? = if (motion.reduceMotion) null else rememberInfiniteTransition(label = "pawThinking")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val pose = remember(phase) {
        PawPose(press = { i ->
            val t = (phase?.value ?: 0f) - i * 0.14f
            if (t in 0f..0.28f) sin(PI * t / 0.28f).toFloat() else 0f
        })
    }
    EtaPaw(pose, modifier.size(size))
}

/** 首页爪印：掌心先落下，四颗脚趾依次带一点回弹地冒出来。减少动画时直接显示。 */
@Composable
internal fun EtaPawArrival(modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val motion = EtaTheme.motion
    val start = if (motion.reduceMotion) 1f else 0f
    val pad = remember { Animatable(start) }
    val toes = remember { List(4) { Animatable(start) } }
    LaunchedEffect(Unit) {
        if (motion.reduceMotion) return@LaunchedEffect
        launch { pad.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 420f)) }
        delay(120)
        toes.forEachIndexed { i, toe ->
            launch { delay(i * 70L); toe.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 520f)) }
        }
    }
    val pose = remember { PawPose(appear = { toes[it].value }, padAppear = { pad.value }) }
    val name = androidx.compose.ui.res.stringResource(io.github.mangi.eta.R.string.app_name)
    EtaPaw(pose, modifier.size(size).semantics { contentDescription = name })
}
