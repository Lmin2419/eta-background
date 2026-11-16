package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import io.github.mangi.eta.ui.theme.EtaElevation
import com.itos.coui.menu.couiMenuAnchor
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import io.github.mangi.eta.agent.overlay.localizedText
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import io.github.mangi.eta.ui.model.TaskVisualStatus
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.TtsProvider
import io.github.mangi.eta.data.repository.SpeechSettingsRepository
import io.github.mangi.eta.ui.model.*
import io.github.mangi.eta.ui.theme.EtaTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

/*
 * 首页与电脑会话页共用的后台任务部件（etax/design/09 第零期 0.3 从 HomeConversationTopBar.kt 移出）：
 * 任务胶囊（正在跑的本机任务与电脑会话）和「已完成 · 查看」提示条。
 */

/** 首页任务胶囊里的一项：本机任务或电脑会话。 */
internal sealed interface HomeBackgroundItem {
    val title: String
    data class Local(val task: io.github.mangi.eta.agent.runtime.TaskNotificationState) : HomeBackgroundItem {
        override val title: String get() = task.title
    }
    data class Desktop(val sessionId: String, override val title: String) : HomeBackgroundItem
}

/**
 * 点胶囊菜单里的本机任务：后台屏上的 App 任务打开画面预览；普通对话任务直接打开它的对话
 * （预览窗只给后台屏任务用，对话任务没有画面，打开会立刻关掉，看起来就是「点了没反应」）。
 */
internal fun io.github.mangi.eta.agent.runtime.TaskNotificationState.opensPreview(): Boolean = !targetPackage.isNullOrBlank()

/** 正在电脑上跑的会话（不含子代理、已归档）；只在连着电脑、列表是最新的时候算，断线时不显示过时的「运行中」。 */
internal fun desktopRunningSessions(state: io.github.mangi.eta.agent.remote.DesktopSessionsState, connected: Boolean):
    List<io.github.mangi.eta.agent.remote.DesktopSessionRow> =
    if (!connected || !state.loaded || state.cached) emptyList()
    else state.rows.filter { it.running && !it.subagent && it.parentId == null && !it.archived }

/**
 * 胶囊里列哪些：别的对话里没跑完的本机任务，加上正在电脑上跑的会话。正在看的对话（[currentConversationId]）
 * 和正在看的电脑会话（[currentDesktopSessionId]）不列——它们的进度就在眼前。
 */
internal fun backgroundItems(
    snapshots: List<io.github.mangi.eta.agent.runtime.TaskNotificationState>,
    currentConversationId: String?,
    desktop: io.github.mangi.eta.agent.remote.DesktopSessionsState,
    connected: Boolean,
    currentDesktopSessionId: String?,
): List<HomeBackgroundItem> =
    snapshots.filter { !it.completed && (currentConversationId == null || it.conversationId != currentConversationId) }
        .map { HomeBackgroundItem.Local(it) } +
        desktopRunningSessions(desktop, connected)
            .filter { it.id != currentDesktopSessionId }
            .map { HomeBackgroundItem.Desktop(it.id, it.title?.takeIf(String::isNotBlank) ?: "") }

/**
 * 后台任务胶囊：与顶部控件同一套液态玻璃（高 32dp），运行圈 + 任务标题；
 * 列出别的对话里的本机任务和正在电脑上跑的会话，点一下展开菜单，点某一项直接打开它。
 */
@Composable
internal fun HomeTaskCapsule(currentConversationId: String?, onTasks: () -> Unit,
    onOpenConversation: (String) -> Unit, onOpenDesktopSession: (String) -> Unit, currentDesktopSessionId: String? = null) {
    val snapshots by io.github.mangi.eta.agent.runtime.AgentExecutionService.taskSnapshots.collectAsState()
    val desktop by io.github.mangi.eta.agent.remote.DesktopBridge.sessions.collectAsState()
    val link by io.github.mangi.eta.agent.remote.DesktopBridge.client.state.collectAsState()
    // 当前对话自己的任务不进胶囊：顶部标题和对话里的「思考中」已经说明了。胶囊只提示别的对话、电脑上还在跑的。
    val running: List<HomeBackgroundItem> = remember(snapshots, currentConversationId, desktop, link, currentDesktopSessionId) {
        backgroundItems(snapshots, currentConversationId, desktop,
            link is io.github.mangi.eta.agent.remote.DesktopBridgeClient.State.Connected, currentDesktopSessionId)
    }
    var open by remember { mutableStateOf(false) }
    val anchor = com.itos.coui.menu.rememberCouiMenuAnchor()
    LaunchedEffect(running.isEmpty()) { if (running.isEmpty()) open = false }
    val context = androidx.compose.ui.platform.LocalContext.current
    val untitled = stringResource(R.string.desktop_untitled)
    EtaAnimatedVisibility(
        visible = running.isNotEmpty(),
        enter = androidx.compose.animation.expandVertically(tween(260, easing = EtaTheme.motion.emphasizedDecelerate)) +
            fadeIn(tween(200, delayMillis = 60)) + androidx.compose.animation.scaleIn(initialScale = 0.9f),
        exit = fadeOut(tween(140)) + androidx.compose.animation.shrinkVertically(tween(200, easing = EtaTheme.motion.accelerate)),
        label = "taskCapsule",
    ) {
        val first = running.firstOrNull() ?: return@EtaAnimatedVisibility
        // 一个任务显示它的标题（一行），多个显示「N 个后台任务」。
        val label = if (running.size == 1) first.title.ifBlank { untitled }.taskLabel(24)
            else stringResource(R.string.eta_background_tasks_count, running.size)
        Box(Modifier.fillMaxWidth().padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.couiMenuAnchor(anchor)) {
                io.github.mangi.eta.ui.components.coui.EtaFloatingSurface(
                    modifier = Modifier.height(32.dp).width(IntrinsicSize.Max).widthIn(max = 280.dp),
                    shape = EtaTheme.shapes.radiusFull,
                    capsule = true,
                    contentDescription = label,
                    onClick = { anchor.open { open = true } },
                ) {
                    Row(Modifier.padding(start = 10.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusRing(TaskVisualStatus.Running, size = 12.dp, contentDescription = null)
                        if (running.size == 1 && first is HomeBackgroundItem.Desktop)
                            Icon(Icons.Outlined.Computer, null, Modifier.size(14.dp), tint = EtaTheme.colors.textSecondary)
                        Text(label, style = EtaTheme.type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                            color = EtaTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                    }
                }
            }
        }
    }
    // 点胶囊：COUI 菜单列出后台任务（一行标题，电脑会话带电脑图标），点某一项直接打开它；末项「查看全部」。
    EtaContextMenu(open, onDismiss = { open = false }, couiAnchor = anchor, header = stringResource(R.string.eta_tasks)) {
        running.take(6).forEach { item ->
            when (item) {
                is HomeBackgroundItem.Local -> EtaMenuItem(item.task.title.taskLabel(16), onClick = {
                    open = false
                    if (item.task.opensPreview()) io.github.mangi.eta.ui.screens.tasks.openTaskPreview(context, item.task)
                    else item.task.conversationId?.let(onOpenConversation)
                })
                is HomeBackgroundItem.Desktop -> EtaMenuItem(item.title.ifBlank { untitled }.taskLabel(16), icon = Icons.Outlined.Computer,
                    onClick = { open = false; onOpenDesktopSession(item.sessionId) })
            }
        }
        EtaMenuDivider()
        EtaMenuItem(stringResource(R.string.eta_tasks_view_all), onClick = { open = false; onTasks() })
    }
}

/** 首页顶部「已完成 · 查看」提示条的一项。 */
internal data class DoneNoticeItem(val key: String, val title: String, val failed: Boolean, val desktop: Boolean, val target: String)

internal fun io.github.mangi.eta.agent.runtime.TaskNotificationState.toDoneNotice(): DoneNoticeItem? {
    val conversation = conversationId ?: return null
    val failed = replyFailed || status == io.github.mangi.eta.agent.overlay.AgentOverlayStatus.RunFailed ||
        status == io.github.mangi.eta.agent.overlay.AgentOverlayStatus.ContinuationUnavailable
    return DoneNoticeItem("local:$runId", title, failed, desktop = false, target = conversation)
}

/**
 * 电脑会话一轮结束 → 提示条。用户自己停掉的（aborted / cancelled）、子代理的不提示；
 * 其余非 completed 的结束原因都算出错。
 */
internal fun desktopDoneNotice(turn: io.github.mangi.eta.agent.remote.DesktopTurnEvent,
    rows: List<io.github.mangi.eta.agent.remote.DesktopSessionRow>): DoneNoticeItem? {
    if (turn.reason == "aborted" || turn.reason == "cancelled") return null
    val row = rows.firstOrNull { it.id == turn.sessionId }
    if (row != null && (row.subagent || row.parentId != null)) return null
    return DoneNoticeItem("desktop:${turn.sessionId}:${turn.turn}", row?.title?.takeIf(String::isNotBlank) ?: "",
        failed = turn.reason != "completed", desktop = true, target = turn.sessionId)
}

/**
 * 别的对话里的任务、电脑上的会话刚结束：任务胶囊的位置先出现一枚「「标题」已完成 · 查看」，6 秒后收起，
 * 点一下打开那个对话或电脑会话。停在首页时用它代替系统的完成横幅与电脑完成通知。
 */
@Composable
internal fun HomeDoneNotice(currentConversationId: String?, onOpenConversation: (String) -> Unit, onOpenDesktopSession: (String) -> Unit,
    currentDesktopSessionId: String? = null) {
    val snapshots by io.github.mangi.eta.agent.runtime.AgentExecutionService.taskSnapshots.collectAsState()
    val tracker = remember { DoneNoticeTracker() }
    var notice by remember { mutableStateOf<DoneNoticeItem?>(null) }
    val latestConversation by rememberUpdatedState(currentConversationId)
    LaunchedEffect(snapshots) { tracker.newlyFinished(snapshots, latestConversation)?.toDoneNotice()?.let { notice = it } }
    LaunchedEffect(Unit) {
        io.github.mangi.eta.agent.remote.DesktopBridge.turns.collect { turn ->
            // 正在看的这个电脑会话自己结束时不提示（回复就在眼前）。
            if (turn.sessionId != currentDesktopSessionId)
                desktopDoneNotice(turn, io.github.mangi.eta.agent.remote.DesktopBridge.sessions.value.rows)?.let { notice = it }
        }
    }
    LaunchedEffect(notice) { if (notice != null) { kotlinx.coroutines.delay(6_000); notice = null } }
    LaunchedEffect(currentConversationId) { if (notice?.let { !it.desktop && it.target == currentConversationId } == true) notice = null }
    DoneNoticeCapsule(notice) { item ->
        notice = null
        if (item.desktop) onOpenDesktopSession(item.target) else onOpenConversation(item.target)
    }
}

/** 提示条本身（单独拆出来便于测试点击）。 */
@Composable
internal fun DoneNoticeCapsule(notice: DoneNoticeItem?, onOpen: (DoneNoticeItem) -> Unit) {
    EtaAnimatedVisibility(
        visible = notice != null,
        enter = androidx.compose.animation.expandVertically(tween(260, easing = EtaTheme.motion.emphasizedDecelerate)) +
            fadeIn(tween(200, delayMillis = 60)) + androidx.compose.animation.scaleIn(initialScale = 0.9f),
        exit = fadeOut(tween(140)) + androidx.compose.animation.shrinkVertically(tween(200, easing = EtaTheme.motion.accelerate)),
        label = "doneNotice",
    ) {
        var shown by remember { mutableStateOf(notice) }
        if (notice != null) shown = notice
        val item = shown ?: return@EtaAnimatedVisibility
        val name = item.title.ifBlank { stringResource(R.string.desktop_untitled) }.taskLabel(14)
        val label = stringResource(if (item.failed) R.string.eta_task_failed_notice else R.string.eta_task_done_notice, name)
        Box(Modifier.fillMaxWidth().padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
            io.github.mangi.eta.ui.components.coui.EtaFloatingSurface(
                modifier = Modifier.height(32.dp).width(IntrinsicSize.Max).widthIn(max = 300.dp),
                shape = EtaTheme.shapes.radiusFull,
                capsule = true,
                contentDescription = label,
                onClick = { onOpen(item) },
            ) {
                Row(Modifier.padding(start = 10.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusRing(if (item.failed) TaskVisualStatus.Failed else TaskVisualStatus.Success, size = 12.dp, contentDescription = null)
                    if (item.desktop) Icon(Icons.Outlined.Computer, null, Modifier.size(14.dp), tint = EtaTheme.colors.textSecondary)
                    Text(label, style = EtaTheme.type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                        color = EtaTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    Text(stringResource(R.string.eta_task_notice_open), style = EtaTheme.type.footnote,
                        color = EtaTheme.colors.accent, maxLines = 1)
                }
            }
        }
    }
}

/**
 * 找出「刚结束、属于别的对话」的任务：第一次看到的已完成卡片只记下不提示（打开应用时不翻旧账），
 * 当前对话自己的、没有对话的、用户自己停掉的都不提示。
 */
internal class DoneNoticeTracker {
    private var seen: Set<String>? = null

    fun newlyFinished(snapshots: List<io.github.mangi.eta.agent.runtime.TaskNotificationState>, currentConversationId: String?):
        io.github.mangi.eta.agent.runtime.TaskNotificationState? {
        val completed = snapshots.filter { it.completed }
        val previous = seen
        seen = completed.mapTo(HashSet()) { it.runId }
        if (previous == null) return null
        return completed.lastOrNull {
            it.runId !in previous && it.conversationId != null && it.conversationId != currentConversationId &&
                it.status != io.github.mangi.eta.agent.overlay.AgentOverlayStatus.Stopped
        }
    }
}

/** 任务标题只取第一行，超过 [max] 个字截断加省略号：胶囊和菜单里都只占一行。 */
internal fun String.taskLabel(max: Int): String {
    val line = lineSequence().map(String::trim).firstOrNull { it.isNotEmpty() }.orEmpty()
    return if (line.length > max) line.take(max).trimEnd() + "…" else line
}
