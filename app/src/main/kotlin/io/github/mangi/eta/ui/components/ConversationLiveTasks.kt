package io.github.mangi.eta.ui.components

import io.github.mangi.eta.ui.components.EtaAnimatedContent as AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import io.github.mangi.eta.agent.overlay.localizedText
import io.github.mangi.eta.agent.runtime.AgentExecutionService
import io.github.mangi.eta.agent.runtime.TaskPreviewRegistry
import io.github.mangi.eta.ui.model.TaskVisualStatus
import io.github.mangi.eta.ui.model.toTaskVisualStatus
import io.github.mangi.eta.ui.screens.tasks.openTaskPreview
import io.github.mangi.eta.ui.screens.tasks.taskAction
import io.github.mangi.eta.ui.screens.tasks.TaskApplicationIcon
import io.github.mangi.eta.ui.screens.tasks.TaskElapsedText
import io.github.mangi.eta.ui.theme.EtaTheme
import io.github.mangi.eta.ui.theme.EtaElevation
import io.github.mangi.eta.ui.theme.etaElevation
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun ConversationLiveTasks(conversationId: String?, onOpenTasks: () -> Unit, modifier: Modifier = Modifier) {
    if (conversationId == null) return
    val snapshots by AgentExecutionService.taskSnapshots.collectAsState()
    var dismissed by rememberSaveable(conversationId) { mutableStateOf(emptyList<String>()) }
    var expanded by rememberSaveable(conversationId) { mutableStateOf(false) }
    val tasks = snapshots.filter { it.conversationId == conversationId && it.runId !in dismissed &&
        (it.targetPackage != null || TaskPreviewRegistry.isBackground(it.runId)) }
    tasks.forEach { task -> key(task.runId) {
        LaunchedEffect(task.completed, task.toTaskVisualStatus()) {
            if (task.completed && task.toTaskVisualStatus() == TaskVisualStatus.Success) {
                delay(3000); dismissed = (dismissed + task.runId).takeLast(64)
            }
        }
    } }
    if (tasks.isEmpty()) return
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tasks.size > 1) Row(Modifier.fillMaxWidth().clip(EtaTheme.shapes.radiusMd).clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width((32 + 16 * (minOf(tasks.size, 3) - 1)).dp).height(32.dp)) {
                tasks.take(3).forEachIndexed { index, task ->
                    TaskApplicationIcon(task.targetPackage, Modifier.offset(x = (index * 16).dp).size(32.dp)
                        .background(EtaTheme.colors.surfaceRaised, EtaTheme.shapes.radiusFull).padding(2.dp))
                }
            }
            Text(stringResource(R.string.eta_background_tasks_count, tasks.size), Modifier.weight(1f).padding(start = 8.dp), style = EtaTheme.type.footnote)
            Icon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp))
        }
        if (tasks.size == 1 || expanded) tasks.forEach { task -> key(task.runId) {
            val status = task.toTaskVisualStatus()
            val stripColor = if (status == TaskVisualStatus.Attention) EtaTheme.colors.attentionSoft else EtaTheme.colors.surfaceRaised
            val paused = task.paused || status == TaskVisualStatus.Paused
            val accessibleSummary = io.github.mangi.eta.ui.screens.tasks.taskAccessibilitySummary(task)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).etaElevation(EtaElevation.E2, EtaTheme.shapes.radiusLg)
                .background(stripColor, EtaTheme.shapes.radiusLg)
                .clickable { openTaskPreview(context, task) }
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(40.dp).height(28.dp)) {
                    TaskApplicationIcon(task.targetPackage, Modifier.size(28.dp))
                    Box(Modifier.offset(x = 24.dp, y = 24.dp).size(16.dp).background(stripColor, CircleShape), contentAlignment = Alignment.Center) {
                        StatusRing(status, size = 12.dp)
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp).clearAndSetSemantics { contentDescription = accessibleSummary }) {
                    Text(task.title, style = EtaTheme.type.footnote, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val step = if (task.status == AgentOverlayStatus.PreviewPaused || task.status == AgentOverlayStatus.AppTakeoverPaused)
                        stringResource(R.string.eta_task_paused_user) else task.status.localizedText()
                    val duration = EtaTheme.motion.short
                    AnimatedContent(step, Modifier.weight(1f), transitionSpec = {
                        (fadeIn(tween(duration)) + slideInVertically(tween(duration)) { it / 3 }) togetherWith fadeOut(tween(duration))
                    }, label = "liveTaskStep") { text ->
                        Text(text, style = EtaTheme.type.caption, color = status.textColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TaskElapsedText(task)
                    }
                }
                if (task.completed) EtaIconButton({ dismissed = (dismissed + task.runId).takeLast(64) }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.action_close), Modifier.size(16.dp))
                } else {
                    if (!paused) EtaIconButton({ taskAction(task, AgentExecutionService.ACTION_PAUSE_RUN) }) {
                        Icon(Icons.Rounded.Pause, stringResource(R.string.overlay_pause), Modifier.size(18.dp))
                    }
                    EtaButton(stringResource(if (paused) R.string.eta_task_return else if (status == TaskVisualStatus.Attention) R.string.eta_task_handle else R.string.eta_task_view), {
                    if (paused) taskAction(task, AgentExecutionService.ACTION_RESUME_RUN) else openTaskPreview(context, task)
                    }, size = EtaButtonSize.Small)
                }
            }
        } }
    }
}
