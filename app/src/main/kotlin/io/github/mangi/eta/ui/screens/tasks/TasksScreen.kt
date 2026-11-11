package io.github.mangi.eta.ui.screens.tasks

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.eta.shadow.ShadowTools
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.localizedText
import io.github.mangi.eta.agent.runtime.*
import io.github.mangi.eta.data.repository.CommonTask
import io.github.mangi.eta.ui.app.CommonTasksStore
import io.github.mangi.eta.ui.components.*
import io.github.mangi.eta.ui.model.*
import io.github.mangi.eta.ui.theme.EtaTheme
import kotlinx.coroutines.*
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun TasksScreen(store: CommonTasksStore, onBack: () -> Unit, onRun: (CommonTask) -> Unit,
    onConversation: (String) -> Unit, initialTab: Int = -1, embedded: Boolean = false, visible: Boolean = true,
    onArchivedConversation: (String, String) -> Unit = { _, _ -> }) {
    val snapshots by AgentExecutionService.taskSnapshots.collectAsState()
    val active = remember(snapshots) { orderedActiveTasks(snapshots) }
    var tab by rememberSaveable { mutableIntStateOf(initialTab.takeIf { it in 0..2 } ?: if (active.isNotEmpty()) 0 else 2) }
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var archive by remember { mutableStateOf(emptyList<CompletedTaskUi>()) }
    val completedIds = remember(snapshots) { snapshots.filter { it.completed }.map { it.runId } }
    LaunchedEffect(tab, visible, lifecycle, completedIds) {
        if (tab == 1 && visible && lifecycle.isAtLeast(Lifecycle.State.STARTED)) {
            archive = withContext(Dispatchers.IO) { try { loadCompletedTasks(context) }
                catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { emptyList() } }
        }
    }
    LaunchedEffect(Unit) { store.refresh() }
    val completed = remember(snapshots, archive) { completedTaskRows(snapshots, archive) }
    Column(Modifier.fillMaxSize().background(EtaTheme.colors.canvas)
        .statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            MiuixBackButton(onClick = onBack)
            Text(stringResource(R.string.eta_tasks), style = if (embedded) EtaTheme.type.title2 else EtaTheme.type.title1)
        }
        EtaSegmented(listOf(stringResource(R.string.eta_tasks_running), stringResource(R.string.eta_tasks_completed), stringResource(R.string.eta_tasks_saved)),
            tab, { tab = it }, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (active.isEmpty()) item { ListEmptyState(stringResource(R.string.eta_tasks_empty), icon = Icons.Outlined.TaskAlt,
                        action = { EtaButton(stringResource(R.string.eta_tasks_start_saved), { tab = 2 }) }) }
                    items(active, key = { it.runId }) { task -> ActiveTaskCard(task, visible, onConversation) }
                }
                1 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (completed.isEmpty()) item { ListEmptyState(stringResource(R.string.eta_tasks_no_completed), icon = Icons.Outlined.TaskAlt) }
                    items(completed, key = { it.runId }) { task -> CompletedTaskRow(task, onConversation, onArchivedConversation) }
                }
                else -> CommonTasksScreen(store, onBack, onRun, embedded = true)
            }
        }
    }
}

@Composable
internal fun ActiveTaskCard(task: TaskNotificationState, visible: Boolean, onConversation: (String) -> Unit) {
    val context = LocalContext.current
    val status = task.toTaskVisualStatus()
    val paused = task.paused || status == TaskVisualStatus.Paused
    val accessibleSummary = taskAccessibilitySummary(task)
    var confirmStop by remember(task.runId) { mutableStateOf(false) }
    val background = TaskPreviewRegistry.isBackground(task.runId)
    EtaCard(Modifier.fillMaxWidth().then(if (status == TaskVisualStatus.Attention)
        Modifier.border(1.5.dp, EtaTheme.colors.attention, EtaTheme.shapes.radiusMd) else Modifier), insideMargin = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (LocalDensity.current.fontScale < 1.5f) Row(Modifier.clearAndSetSemantics { contentDescription = accessibleSummary }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskApplicationIcon(task.targetPackage, Modifier.size(24.dp))
                Text(task.title, Modifier.weight(1f), style = EtaTheme.type.rowTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                TaskStatusLabel(status)
            } else Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = accessibleSummary }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskApplicationIcon(task.targetPackage, Modifier.size(24.dp))
                    Text(task.title, Modifier.weight(1f), style = EtaTheme.type.rowTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.padding(start = 32.dp)) { TaskStatusLabel(status) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (background) TaskThumbnail(task, visible, Modifier.size(72.dp, 156.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(task.status.localizedText(), style = EtaTheme.type.subhead, color = EtaTheme.colors.textSecondary)
                    TaskElapsedText(task, visible)
                    if (task.streamPreview.isNotBlank()) Text(task.streamPreview, style = EtaTheme.type.footnote,
                        color = EtaTheme.colors.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        EtaButton(stringResource(R.string.eta_task_view), {
                            if (background) openTaskPreview(context, task) else task.conversationId?.let(onConversation)
                        }, size = EtaButtonSize.Small, enabled = background || task.conversationId != null)
                        EtaButton(stringResource(if (paused) R.string.overlay_resume else R.string.overlay_pause), {
                            taskAction(task, if (paused) AgentExecutionService.ACTION_RESUME_RUN else AgentExecutionService.ACTION_PAUSE_RUN)
                        }, kind = EtaButtonKind.Secondary, size = EtaButtonSize.Small)
                        EtaButton(stringResource(R.string.overlay_stop_task), { confirmStop = true }, kind = EtaButtonKind.Destructive, size = EtaButtonSize.Small)
                    }
                }
            }
        }
    }
    EtaWindowDialog(confirmStop, title = stringResource(R.string.overlay_stop_task), summary = stringResource(R.string.eta_task_stop_confirm), onDismissRequest = { confirmStop = false }) {
        MiuixDialogActions(stringResource(R.string.overlay_stop_task), onCancel = { confirmStop = false }, destructive = true,
            onConfirm = { confirmStop = false; taskAction(task, AgentExecutionService.ACTION_STOP_RUN) })
    }
}

@Composable
internal fun TaskStatusLabel(status: TaskVisualStatus) {
    Row(Modifier.background(status.backgroundColor(), EtaTheme.shapes.radiusFull).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        StatusRing(status, size = 12.dp)
        Text(status.label(), style = EtaTheme.type.caption, color = status.textColor())
    }
}

@Composable
internal fun CompletedTaskRow(task: CompletedTaskUi, onConversation: (String) -> Unit, onArchivedConversation: (String, String) -> Unit) {
    val context = LocalContext.current
    val intent = remember(task.targetPackage) { task.targetPackage?.let { context.packageManager.getLaunchIntentForPackage(it) } }
    val applicationName by produceState<String?>(null, task.targetPackage) {
        value = withContext(Dispatchers.IO) {
            task.targetPackage?.let { pkg -> runCatching {
                context.packageManager.getApplicationInfo(pkg, 0).loadLabel(context.packageManager).toString()
            }.getOrNull() }
        }
    }
    Row(Modifier.fillMaxWidth().clip(EtaTheme.shapes.radiusMd).clickable(enabled = task.conversationId != null || task.archiveConversationKey != null) {
        if (task.conversationId != null) onConversation(task.conversationId)
        else if (task.archiveSource != null && task.archiveConversationKey != null) onArchivedConversation(task.archiveSource, task.archiveConversationKey)
    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusRing(task.status)
        Column(Modifier.weight(1f)) {
            Text(task.title, style = EtaTheme.type.rowTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val duration = task.durationMs?.let {
                stringResource(R.string.eta_task_elapsed, "${it / 60000}:${(it / 1000 % 60).toString().padStart(2, '0')}")
            }
            val detail = listOfNotNull(applicationName, task.completedAt?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }, duration).joinToString(" · ")
            Text(detail.ifBlank { task.status.label() }, style = EtaTheme.type.footnote, color = EtaTheme.colors.textTertiary)
        }
        if (intent != null) EtaButton(stringResource(R.string.eta_open_app), { context.startActivity(intent) }, kind = EtaButtonKind.Secondary, size = EtaButtonSize.Small)
    }
}

/** A leaf timer, based on first published card time, stopped with the visible UI. */
@Composable
internal fun TaskElapsedText(task: TaskNotificationState, visible: Boolean = true) {
    val started = task.firstPublishedAtElapsedMs ?: return
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val elapsed by produceState((task.completedAtElapsedMs ?: android.os.SystemClock.elapsedRealtime()) - started,
        task.runId, task.completedAtElapsedMs, visible, lifecycle) {
        if (task.completedAtElapsedMs != null) value = task.completedAtElapsedMs - started
        else if (visible && lifecycle.isAtLeast(Lifecycle.State.STARTED)) while (isActive) {
            value = android.os.SystemClock.elapsedRealtime() - started; delay(1000)
        }
    }
    val seconds = (elapsed.coerceAtLeast(0) / 1000)
    Text(stringResource(R.string.eta_task_elapsed, "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"),
        style = EtaTheme.type.caption.copy(fontFeatureSettings = "tnum"), color = EtaTheme.colors.textTertiary)
}

/** Read-only frame polling. No preview_acquire, input, task pause, or ownership transfer here. */
@Composable
internal fun TaskThumbnail(task: TaskNotificationState, visible: Boolean, modifier: Modifier = Modifier,
    background: Boolean = TaskPreviewRegistry.isBackground(task.runId), frameSource: TaskFrameSource = RootTaskFrameSource) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val view = LocalView.current
    var inViewport by remember { mutableStateOf(false) }
    var frame by remember(task.runId) { mutableStateOf<ImageBitmap?>(null) }
    val owner = remember(task.runId) { UUID.randomUUID().toString() }
    val allowed = taskPreviewPollingAllowed(visible, lifecycle.isAtLeast(Lifecycle.State.STARTED), inViewport,
        background, task.completed)
    LaunchedEffect(task.runId, task.targetPackage, allowed, frameSource) {
        if (!allowed) { frame = null; return@LaunchedEffect }
        var previous = ""
        while (isActive) {
            val result = frameSource.read(task.runId, task.targetPackage.orEmpty(), owner, previous)
            ensureActive()
            if (result?.optBoolean("ok") == true) {
                if (!result.optBoolean("unchanged")) {
                    frame = withContext(Dispatchers.Default) {
                        val encoded = result.optString("image")
                        if (encoded.length > 550000) null else runCatching {
                            val bytes = Base64.decode(encoded, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        }.getOrNull()
                    }
                    previous = result.optString("previewKey")
                }
            } else { frame = null; previous = "" }
            delay(2000)
        }
    }
    Box(modifier.onGloballyPositioned {
        val bounds = it.boundsInWindow()
        inViewport = bounds.width > 0 && bounds.height > 0 && bounds.right > 0 && bounds.bottom > 0 && bounds.left < view.width && bounds.top < view.height
    }.clip(EtaTheme.shapes.radiusSm).background(EtaTheme.colors.surfaceSunken).border(1.dp, EtaTheme.colors.outline, EtaTheme.shapes.radiusSm), contentAlignment = Alignment.Center) {
        val bitmap = frame
        if (bitmap == null) TaskApplicationIcon(task.targetPackage, Modifier.size(28.dp))
        else Image(bitmap, stringResource(R.string.eta_task_view), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

@Composable
internal fun TaskApplicationIcon(pkg: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            pkg?.let { runCatching { context.packageManager.getApplicationIcon(it).toBitmap(64, 64).asImageBitmap() }.getOrNull() }
        }
    }
    val icon = bitmap
    if (icon == null) Icon(Icons.Outlined.PhoneAndroid, null, modifier, tint = EtaTheme.colors.textTertiary)
    else Image(icon, null, modifier, contentScale = ContentScale.Fit)
}

internal fun interface TaskFrameSource {
    suspend fun read(runId: String, pkg: String, owner: String, since: String): JSONObject?
}

private object RootTaskFrameSource : TaskFrameSource {
    override suspend fun read(runId: String, pkg: String, owner: String, since: String): JSONObject? = withContext(Dispatchers.IO) {
        // Existing RPC has a 3s socket timeout. Cancellation prevents decode/publication and any next request;
        // it cannot retract an already sent read-only request from the root process.
        runCatching { JSONObject(ShadowTools.preview(runId, pkg, owner, "preview_frame",
            JSONObject().put("edge", 320).put("since", since).toString())) }.getOrNull()
    }
}
