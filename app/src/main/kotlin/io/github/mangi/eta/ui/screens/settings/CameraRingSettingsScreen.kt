package io.github.mangi.eta.ui.screens.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.AmbientGlowPolicy
import io.github.mangi.eta.agent.overlay.CameraCutoutLocator
import io.github.mangi.eta.agent.overlay.CameraRingAdjust
import io.github.mangi.eta.agent.overlay.EdgeLightColors
import io.github.mangi.eta.agent.overlay.cameraRingAdjust
import io.github.mangi.eta.agent.overlay.drawCameraRing
import io.github.mangi.eta.data.repository.AppearanceSettingsRepository
import io.github.mangi.eta.ui.app.LocalAppearanceSettings
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.theme.EtaTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 摄像头小光圈的位置和大小（2026111619）。各家机型报给应用的开孔范围不一定准，
 * 这里让用户对着自己手机的摄像头调：页面顶部摄像头那里实时画出光圈，拖滑块马上看到变化。
 */
@Composable
internal fun CameraRingSettingsScreen(onBack: () -> Unit) {
    val appearance = LocalAppearanceSettings.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val cutout = remember { CameraCutoutLocator.bounds(context) }
    val screenWidth = remember {
        context.getSystemService(android.view.WindowManager::class.java)?.maximumWindowMetrics?.bounds?.width()?.toFloat()
            ?: context.resources.displayMetrics.widthPixels.toFloat()
    }
    val saved = appearance.cameraRingAdjust()
    // 拖动时只改草稿（预览跟着动），松手才存；存下的值同步给后台的光效窗口。
    var draft by remember(saved) { mutableStateOf(saved) }
    val auto = remember(cutout, screenWidth, density) { AmbientGlowPolicy.cameraRing(cutout, screenWidth, density) }
    val autoWidthDp = auto.width() / density
    val autoHeightDp = auto.height() / density

    fun save(value: CameraRingAdjust) {
        draft = value
        scope.launch {
            AppearanceSettingsRepository.update { current ->
                current.copy(
                    cameraRingOffsetXDp = value.offsetXDp,
                    cameraRingOffsetYDp = value.offsetYDp,
                    cameraRingWidthDp = value.widthDp,
                    cameraRingHeightDp = value.heightDp,
                )
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        MiuixScaffoldPage(title = stringResource(R.string.camera_ring_title), onBack = onBack) {
            item(key = "hint") {
                Text(
                    stringResource(if (cutout != null) R.string.camera_ring_hint else R.string.camera_ring_hint_no_cutout),
                    style = EtaTheme.type.subhead,
                    color = EtaTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
                )
            }
            item(key = "sliders") {
                EtaPreferenceGroup {
                    RingSlider(stringResource(R.string.camera_ring_horizontal), draft.offsetXDp, -CameraRingAdjust.OFFSET_X_RANGE,
                        CameraRingAdjust.OFFSET_X_RANGE, signed = true, onChange = { draft = draft.copy(offsetXDp = it) },
                        onDone = { save(draft) })
                    EtaPreferenceDivider(hasLeading = false)
                    RingSlider(stringResource(R.string.camera_ring_vertical), draft.offsetYDp, -CameraRingAdjust.OFFSET_Y_RANGE,
                        CameraRingAdjust.OFFSET_Y_RANGE, signed = true, onChange = { draft = draft.copy(offsetYDp = it) },
                        onDone = { save(draft) })
                    EtaPreferenceDivider(hasLeading = false)
                    RingSlider(stringResource(R.string.camera_ring_width), draft.widthDp.takeIf { it > 0f } ?: autoWidthDp,
                        CameraRingAdjust.MIN_SIZE, CameraRingAdjust.MAX_WIDTH, auto = draft.widthDp <= 0f,
                        onChange = { draft = draft.copy(widthDp = it) }, onDone = { save(draft) })
                    EtaPreferenceDivider(hasLeading = false)
                    RingSlider(stringResource(R.string.camera_ring_height), draft.heightDp.takeIf { it > 0f } ?: autoHeightDp,
                        CameraRingAdjust.MIN_SIZE, CameraRingAdjust.MAX_HEIGHT, auto = draft.heightDp <= 0f,
                        onChange = { draft = draft.copy(heightDp = it) }, onDone = { save(draft) })
                }
            }
            item(key = "reset") {
                EtaPreferenceGroup {
                    EtaArrowPreference(
                        title = stringResource(R.string.camera_ring_reset),
                        summary = stringResource(R.string.camera_ring_reset_summary),
                        onClick = { save(CameraRingAdjust.AUTO) },
                    )
                }
            }
        }
        // 预览画在整页最上层，坐标换算成屏幕坐标，和后台光效窗口画的位置一致。
        CameraRingPreview(cutout, screenWidth, draft, appearance.runtimeGlowIntensity, Modifier.fillMaxSize())
    }
}

@Composable
private fun RingSlider(
    title: String,
    value: Float,
    min: Float,
    max: Float,
    signed: Boolean = false,
    auto: Boolean = false,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    val autoText = stringResource(R.string.camera_ring_auto)
    io.github.mangi.eta.ui.components.EtaPreference(
        title = title,
        endActions = {
            val rounded = (value * 2f).roundToInt() / 2f
            val number = if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
            Text(
                text = when {
                    auto -> "$autoText · $number"
                    signed && rounded > 0f -> "+$number"
                    else -> number
                },
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                maxLines = 1,
            )
        },
        bottomAction = {
            Slider(
                value = value.coerceIn(min, max),
                onValueChange = { onChange((it * 2f).roundToInt() / 2f) },
                onValueChangeFinished = onDone,
                valueRange = min..max,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun CameraRingPreview(
    cutout: android.graphics.RectF?,
    screenWidth: Float,
    adjust: CameraRingAdjust,
    intensity: io.github.mangi.eta.data.model.AppearanceGlowIntensity,
    modifier: Modifier,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val colors = remember { EdgeLightColors.map { it.toArgb() }.toIntArray() }
    val rotation by rememberInfiniteTransition(label = "camera_ring_preview").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(EtaTheme.motion.auroraMillis / 2, easing = LinearEasing), RepeatMode.Restart),
        label = "camera_ring_rotation",
    )
    Box(
        modifier
            .onGloballyPositioned { origin = it.positionOnScreen() }
            .drawBehind {
                val ring = AmbientGlowPolicy.cameraRing(cutout, screenWidth, density, adjust)
                ring.offset(-origin.x, -origin.y)
                drawIntoCanvas { it.nativeCanvas.drawCameraRing(ring, colors, rotation, intensity, density, 1f) }
            },
    )
}
