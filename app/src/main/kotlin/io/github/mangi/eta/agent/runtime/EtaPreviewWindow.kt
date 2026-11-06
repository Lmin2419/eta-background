package io.github.mangi.eta.agent.runtime

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import io.github.mangi.eta.ui.theme.EtaElevation
import io.github.mangi.eta.ui.theme.etaElevation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.components.*
import io.github.mangi.eta.ui.model.TaskVisualStatus
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

/** Damped spring curve over the design's long duration; only the resize interpolator changes. */
internal val PreviewResizeInterpolator = android.animation.TimeInterpolator { fraction ->
    val motion = io.github.mangi.eta.ui.theme.EtaMotion()
    val damping = motion.dampingRatio.toDouble()
    val omega = kotlin.math.sqrt(motion.stiffness.toDouble())
    val remainder = kotlin.math.sqrt(1.0 - damping * damping)
    fun response(t: Double): Double = 1.0 - kotlin.math.exp(-damping * omega * t) *
        (kotlin.math.cos(omega * remainder * t) + damping / remainder * kotlin.math.sin(omega * remainder * t))
    val duration = motion.long / 1000.0
    (response(fraction * duration) / response(duration)).toFloat()
}

internal class PreviewHint { var text by mutableStateOf("正在连接画面…") }

internal class PreviewPresentation {
    val hint = PreviewHint()
    var controls by mutableStateOf(PreviewWindowMapper.map(PreviewSnapshot(
        TaskVisualStatus.Running, false, false, false, false, false, false, false, false)))
    var appName by mutableStateOf("")
    var appIcon by mutableStateOf<Drawable?>(null)
    var expanded by mutableStateOf(false)
    var manual by mutableStateOf(false)
    var inputFailed by mutableStateOf(false)
    var handoffPending by mutableStateOf(false)
    var frameUnavailable by mutableStateOf(false)
    var frameReady by mutableStateOf(false)
    var canOperateCompleted by mutableStateOf(false)
    var canOpenCompletedWithoutFrame by mutableStateOf(false)
    var canResumeWithoutFrame by mutableStateOf(false)
    var step by mutableStateOf("")
    var result by mutableStateOf("")
    var failureReason by mutableStateOf("")
}

@Composable
internal fun EtaPreviewWindow(ui: PreviewPresentation, picture: TaskPreviewImageView,
    onAction: (PreviewAction) -> Unit, onZoom: () -> Unit, onClose: () -> Unit) {
    val colors = EtaTheme.colors
    val controls = ui.controls
    val status = when (controls.mode) {
        PreviewMode.Running, PreviewMode.Handoff -> TaskVisualStatus.Running
        PreviewMode.Manual, PreviewMode.Paused -> TaskVisualStatus.Paused
        PreviewMode.NeedsAttention -> TaskVisualStatus.Attention
        PreviewMode.Completed -> TaskVisualStatus.Success
        PreviewMode.Incomplete -> TaskVisualStatus.Failed
    }
    Column(Modifier.fillMaxSize().etaElevation(EtaElevation.E3, EtaTheme.shapes.radiusXl)
        .clip(EtaTheme.shapes.radiusXl).background(colors.surface)
        .border(1.dp, colors.outline, EtaTheme.shapes.radiusXl).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            AndroidView(factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                update = { it.setImageDrawable(ui.appIcon) }, modifier = Modifier.size(24.dp))
            val statusLabel = if (controls.mode == PreviewMode.Manual) stringResource(R.string.preview_manual_status) else status.label()
            // Measure the single-line status first: app name yields space before the status does.
            Layout(modifier = Modifier.weight(1f).padding(start = 6.dp)
                .clearAndSetSemantics { contentDescription = listOf(ui.appName, statusLabel, ui.step).filter { it.isNotBlank() }.joinToString(", ") }, content = {
                Text(ui.appName, color = colors.textPrimary, style = EtaTheme.type.footnote,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                Row(Modifier.background(status.backgroundColor(), CircleShape).padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusRing(status, size = 12.dp, contentDescription = null)
                    Text(statusLabel, Modifier.weight(1f, fill = false).testTag("preview_status"),
                        color = status.textColor(), style = EtaTheme.type.caption,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }) { children, constraints ->
                val chip = children[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
                val nameWidth = (constraints.maxWidth - chip.width - 4.dp.roundToPx()).coerceAtLeast(0)
                val name = children[0].measure(constraints.copy(minWidth = 0, maxWidth = nameWidth, minHeight = 0))
                val height = maxOf(name.height, chip.height)
                layout(constraints.maxWidth, height) {
                    name.placeRelative(0, (height - name.height) / 2)
                    chip.placeRelative(constraints.maxWidth - chip.width, (height - chip.height) / 2)
                }
            }
            EtaIconButton(onClick = onZoom, enabled = controls.zoomEnabled, modifier = Modifier.testTag("preview_zoom")) {
                Icon(if (ui.expanded) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull,
                    stringResource(if (ui.expanded) R.string.preview_collapse else R.string.preview_expand),
                    tint = colors.textPrimary, modifier = Modifier.size(20.dp))
            }
            EtaIconButton(onClick = onClose, modifier = Modifier.testTag("preview_close")) {
                Icon(Icons.Rounded.Close, stringResource(R.string.action_close), tint = colors.textPrimary, modifier = Modifier.size(20.dp))
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp))
            .border(if (ui.manual) 2.dp else 1.dp, if (ui.manual) colors.accent else colors.outline, RoundedCornerShape(16.dp))) {
            // This is the very same view, listener and FIT_CENTER geometry as before Compose.
            AndroidView(factory = { picture }, modifier = Modifier.fillMaxSize().testTag("preview_frame"))
        }
        val hint = when {
            ui.inputFailed -> ui.hint.text
            controls.mode == PreviewMode.Manual && ui.frameReady -> stringResource(R.string.preview_manual_hint)
            controls.mode == PreviewMode.Handoff -> stringResource(R.string.preview_wait)
            controls.mode == PreviewMode.Paused -> stringResource(R.string.preview_paused_hint)
            controls.mode == PreviewMode.Completed -> ui.result.ifBlank { ui.hint.text }
            controls.mode == PreviewMode.NeedsAttention -> ui.result.ifBlank { ui.step.ifBlank { ui.hint.text } }
            controls.mode == PreviewMode.Incomplete -> ui.failureReason.ifBlank { ui.hint.text }
            controls.mode == PreviewMode.Running && !ui.frameUnavailable -> ui.step.ifBlank { ui.hint.text }
            else -> ui.hint.text
        }
        Text(hint, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).testTag("preview_hint"),
            color = if (ui.inputFailed || controls.mode == PreviewMode.Incomplete) colors.danger else colors.textSecondary,
            style = EtaTheme.type.footnote, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            EtaButton(previewActionLabel(controls.primary, controls.mode), { onAction(controls.primary) },
                enabled = controls.primaryEnabled, kind = EtaButtonKind.Primary, size = EtaButtonSize.Regular,
                loading = controls.mode == PreviewMode.Handoff, minHeight = 48.dp, minWidth = 0.dp,
                modifier = Modifier.weight(1f).testTag("preview_primary"))
            controls.secondary.forEach { action ->
                PreviewSecondaryAction(action, controls.mode, controls.secondaryEnabled) { onAction(action) }
            }
        }
        // Legacy recovery/operate actions remain reachable without stealing the main action's width.
        if (ui.canOperateCompleted || ui.canOpenCompletedWithoutFrame || ui.canResumeWithoutFrame) {
            FlowRow(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ui.canOperateCompleted) PreviewSecondaryAction(PreviewAction.TakeOver, controls.mode, true,
                    label = stringResource(R.string.preview_operate_page)) { onAction(PreviewAction.TakeOver) }
                if (ui.canOpenCompletedWithoutFrame) PreviewSecondaryAction(PreviewAction.OpenApp, controls.mode, true) { onAction(PreviewAction.OpenApp) }
                if (ui.canResumeWithoutFrame) PreviewSecondaryAction(PreviewAction.Resume, controls.mode, true) { onAction(PreviewAction.Resume) }
            }
        }
    }
}

@Composable
internal fun previewActionLabel(action: PreviewAction, mode: PreviewMode): String = stringResource(when (action) {
    PreviewAction.TakeOver -> if (mode == PreviewMode.NeedsAttention) R.string.preview_handle else R.string.preview_takeover
    PreviewAction.Pause -> R.string.overlay_pause
    PreviewAction.Resume -> R.string.overlay_resume
    PreviewAction.ReturnToAi -> R.string.preview_return_ai
    PreviewAction.Back -> R.string.preview_back
    PreviewAction.OpenApp -> R.string.background_open_app
    PreviewAction.Close -> R.string.action_close
    PreviewAction.OpenConversation -> R.string.overlay_open_conversation
    PreviewAction.Wait -> R.string.preview_wait
})

@Composable
private fun PreviewSecondaryAction(action: PreviewAction, mode: PreviewMode, enabled: Boolean,
    label: String = previewActionLabel(action, mode), onClick: () -> Unit) {
    val icon = when (action) {
        PreviewAction.TakeOver -> Icons.Rounded.TouchApp
        PreviewAction.Pause -> Icons.Rounded.Pause
        PreviewAction.Resume -> Icons.Rounded.PlayArrow
        PreviewAction.ReturnToAi -> Icons.Rounded.SmartToy
        PreviewAction.Back -> Icons.Rounded.ArrowBack
        PreviewAction.OpenApp -> Icons.Rounded.OpenInNew
        PreviewAction.Close -> Icons.Rounded.Close
        PreviewAction.OpenConversation -> Icons.Rounded.ChatBubbleOutline
        PreviewAction.Wait -> Icons.Rounded.HourglassEmpty
    }
    EtaIconButton(onClick, enabled = enabled,
        modifier = Modifier.size(48.dp).border(1.dp, EtaTheme.colors.outlineStrong, CircleShape).testTag("preview_secondary")) {
        Icon(icon, label, tint = EtaTheme.colors.textPrimary, modifier = Modifier.size(24.dp))
    }
}
