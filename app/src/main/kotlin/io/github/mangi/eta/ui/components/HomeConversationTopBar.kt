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

internal val LocalChatToast = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
internal fun HomeConversationTopBar(
    models: AgentModelPickerUiState, isStreaming: Boolean, isCompacting: Boolean,
    @Suppress("UNUSED_PARAMETER") usage: AgentContextUsageUi,
    @Suppress("UNUSED_PARAMETER") canCompact: Boolean,
    @Suppress("UNUSED_PARAMETER") canExport: Boolean,
    character: RoleplayChatUi?, runningTaskCount: Int,
    onHistory: () -> Unit, onNew: () -> Unit, onTasks: () -> Unit,
    onCharacter: () -> Unit, onModel: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onCompact: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onExport: () -> Unit,
    onManageModels: () -> Unit,
    conversationTitle: String? = null,
    currentConversationId: String? = null,
    onOpenConversation: ((String) -> Unit)? = null,
) {
    var settings by remember { mutableStateOf(false) }
    val pillAnchor = com.itos.coui.menu.rememberCouiMenuAnchor()
    var pendingModel by remember { mutableStateOf<String?>(null) }
    val toast = LocalChatToast.current
    val lockedText = stringResource(R.string.eta_model_locked)
    val changedText = stringResource(R.string.eta_model_changed, models.selectedModel?.displayName.orEmpty())
    LaunchedEffect(models.selectedModel?.id) {
        if (pendingModel != null && models.selectedModel?.id == pendingModel) { toast(changedText); pendingModel = null }
    }
    // 没有整条顶栏：左边历史、中间模型胶囊、右边新建——三枚等高 44dp 的玻璃控件浮在消息上面。
    // 后台有任务在跑时，控件下方居中出现一枚深色玻璃胶囊。
    Column(Modifier.fillMaxWidth()
        .windowInsetsPadding(WindowInsets.statusBars)
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.etaChromeScale()) { CircleIconButton(Icons.AutoMirrored.Outlined.ShortText, stringResource(R.string.action_conversation_history), onHistory) }
            if (character != null) {
                Row(Modifier.weight(1f).clip(EtaTheme.shapes.radiusMd).clickable(onClick = onCharacter).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CharacterAvatar(character.characterName, character.avatarPath, size = 24.dp, shape = EtaTheme.shapes.radiusFull)
                    Text(character.characterName, style = EtaTheme.type.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box((if (character == null) Modifier.weight(1f) else Modifier).etaChromeScale(0.88f)
                .couiMenuAnchor(pillAnchor), contentAlignment = Alignment.Center) {
                // 回复进行中也能选模型：这一轮回复完后才换（AgentAppState.selectModel）；压缩和切换中仍锁住。
                val locked = isCompacting || models.isChanging
                val openModels = { if (locked) toast(lockedText) else pillAnchor.open { settings = true } }
                // 与上游一致：对话有了标题时，中间上行标题、下行模型；新对话只显示模型胶囊。
                if (character == null && !conversationTitle.isNullOrBlank()) ChatTitleCapsule(conversationTitle, models, locked, openModels)
                else ModelPill(models, locked, character != null, openModels)
            }
            Box(Modifier.etaChromeScale()) { CircleIconButton(Icons.Outlined.Add, stringResource(R.string.action_new_conversation), onNew) }
        }
        if (onOpenConversation != null) HomeDoneNotice(currentConversationId, onOpenConversation)
        HomeTaskCapsule(runningTaskCount, currentConversationId, onTasks)
    }
    ModelPopover(settings, pillAnchor, models, isCompacting,
        onDismiss = { settings = false },
        onModel = { id ->
            settings = false
            if (id != models.selectedModel?.id) { pendingModel = id; onModel(id) }
            else if (models.queuedModel != null) { pendingModel = null; onModel(id) } // 回复中改回当前模型：取消排着的切换
        },
        onManageModels = { settings = false; onManageModels() })
}

/**
 * 对话标题胶囊（上游 AgentTopBarChatHeader 的「标题＋模型」）：上行会话标题，下行模型名和下箭头，
 * 整块是 44dp 高的液态玻璃，点一下切换模型。
 */
@Composable
private fun ChatTitleCapsule(title: String, state: AgentModelPickerUiState, locked: Boolean, onClick: () -> Unit) {
    val model = state.selectedModel
    val current = model?.displayName ?: stringResource(R.string.model_not_selected)
    val description = stringResource(R.string.model_switch_current, current)
    // 高度随字号变：默认 44dp；字体放大时两行字装不下，胶囊跟着变高，不裁掉模型那一行。
    io.github.mangi.eta.ui.components.coui.EtaFloatingSurface(
        modifier = Modifier.heightIn(min = 44.dp).width(IntrinsicSize.Max).widthIn(max = 240.dp),
        shape = EtaTheme.shapes.radiusFull,
        capsule = true,
        contentDescription = description,
        onClick = onClick,
        wrapContent = true,
    ) {
        Column(Modifier.heightIn(min = 44.dp).padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(title, style = EtaTheme.type.subhead.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, lineHeight = 18.sp),
                color = EtaTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(model?.pillName ?: current, style = EtaTheme.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal, lineHeight = 14.sp),
                    color = if (locked) EtaTheme.colors.textQuiet else EtaTheme.colors.textTertiary,
                    maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(12.dp), tint = EtaTheme.colors.textQuiet)
            }
        }
    }
}

/**
 * 模型胶囊：只留模型名和下箭头（44dp 高的玻璃）。名字放不下时保留两端（系列名与版本号），中间省略。
 * [iconOnly]（角色对话、电脑会话）时只显示品牌标记。
 */
@Composable
internal fun ModelPill(state: AgentModelPickerUiState, locked: Boolean, iconOnly: Boolean = false, onClick: () -> Unit) {
    val model = state.selectedModel
    val current = model?.displayName ?: stringResource(R.string.model_not_selected)
    val shown = model?.pillName ?: current
    // 模型胶囊：上游 COUI 的流体液态玻璃胶囊；名字放不下时保留两端（系列名与版本号），中间省略。
    io.github.mangi.eta.ui.components.coui.EtaFloatingSurface(
        modifier = Modifier.height(44.dp).width(IntrinsicSize.Max).widthIn(max = 220.dp),
        shape = EtaTheme.shapes.radiusFull,
        capsule = true,
        contentDescription = current,
        onClick = onClick,
    ) {
        Row(Modifier.padding(start = if (iconOnly) 12.dp else 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (iconOnly) ModelBrandMark(model?.modelId, model?.providerSourceType, 16.dp)
            else Text(shown,
                modifier = Modifier.weight(1f, fill = false), style = EtaTheme.type.rowTitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = if (locked) EtaTheme.colors.textTertiary else EtaTheme.colors.textPrimary,
                maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = EtaTheme.colors.textQuiet)
        }
    }
}

/**
 * 点模型胶囊展开的小窗：按服务商分组的模型列表（多于 6 个时带搜索），末行「管理模型…」；
 * 配了语音朗读时多一项「自动朗读」。原底部面板里的「压缩」挪到上下文圆环，「导出」挪到侧栏长按。
 */
@Composable
private fun ModelPopover(
    show: Boolean, anchor: com.itos.coui.menu.CouiMenuAnchor, models: AgentModelPickerUiState, busy: Boolean,
    onDismiss: () -> Unit, onModel: (String) -> Unit, onManageModels: () -> Unit,
) {
    val speech by remember { SpeechSettingsRepository.settingsFlow() }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val autoRead = speech?.tts != null && speech?.tts != TtsProvider.NONE
    val multipleGroups = models.providerGroups.size > 1
    val nextTurn = stringResource(R.string.eta_model_next_turn)
    if (models.modelCount <= ModelMenuLimit) {
        // 模型不多：与上游一致的 COUI 菜单，从模型胶囊处展开。
        EtaContextMenu(show, onDismiss = onDismiss, couiAnchor = anchor) {
            models.providerGroups.forEachIndexed { groupIndex, group ->
                group.models.forEachIndexed { index, model ->
                    if (multipleGroups && groupIndex > 0 && index == 0) EtaMenuDivider()
                    val name = if (multipleGroups) "${group.providerName} · ${model.displayName}" else model.displayName
                    EtaMenuItem(if (model.id == models.queuedModel?.id) "$name · $nextTurn" else name,
                        onClick = { onModel(model.id) }, enabled = !busy && !models.isChanging,
                        selected = model.id == models.selectedModel?.id)
                }
            }
            EtaMenuDivider()
            if (autoRead) EtaMenuItem(stringResource(R.string.eta_auto_read), selected = speech?.chatAutoSpeak == true, onClick = {
                onDismiss()
                speech?.let { value -> scope.launch { SpeechSettingsRepository.save(value.copy(chatAutoSpeak = !value.chatAutoSpeak)) } }
            })
            EtaMenuItem(stringResource(R.string.eta_manage_models_ellipsis), onClick = onManageModels)
        }
    } else {
        // 模型很多：半窗（COUI 底部对话框）里带搜索的完整列表。
        var query by remember(show) { mutableStateOf("") }
        val groups = remember(models.providerGroups, query) { models.filterGroups(query) }
        EtaSheet(show, title = stringResource(R.string.model_picker_title), onDismissRequest = onDismiss) {
            EtaSearchField(query, { query = it }, stringResource(R.string.ui_search_model_df5586), Modifier.padding(bottom = 8.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                groups.forEach { group ->
                    if (groups.size > 1) Text(group.providerName, style = EtaTheme.type.caption, color = EtaTheme.colors.textTertiary,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    group.models.forEach { model ->
                        EtaMenuItem(if (model.id == models.queuedModel?.id) "${model.displayName} · $nextTurn" else model.displayName,
                            onClick = { onModel(model.id) },
                            enabled = !busy && !models.isChanging, selected = model.id == models.selectedModel?.id)
                    }
                }
                if (autoRead) EtaMenuItem(stringResource(R.string.eta_auto_read), selected = speech?.chatAutoSpeak == true, onClick = {
                    speech?.let { value -> scope.launch { SpeechSettingsRepository.save(value.copy(chatAutoSpeak = !value.chatAutoSpeak)) } }
                })
                EtaMenuItem(stringResource(R.string.eta_manage_models_ellipsis), onClick = onManageModels)
            }
        }
    }
}

/** 与上游一致：模型不超过 8 个用菜单，更多时用带搜索的半窗。 */
private const val ModelMenuLimit = 8

/**
 * 后台任务胶囊：深色玻璃（#161616 82%，高 32dp），运行圈 + 当前任务标题与步骤；
 * 点一下从这里展开任务卡片（任务列表、停止、查看全部）。
 */
@Composable
private fun HomeTaskCapsule(runningTaskCount: Int, currentConversationId: String?, onTasks: () -> Unit) {
    val snapshots by io.github.mangi.eta.agent.runtime.AgentExecutionService.taskSnapshots.collectAsState()
    // 当前对话自己的任务不进胶囊：顶部标题和对话里的「思考中」已经说明了。胶囊只提示别的对话还在后台跑。
    val running = remember(snapshots, currentConversationId) {
        snapshots.filter { !it.completed && (currentConversationId == null || it.conversationId != currentConversationId) }
    }
    var open by remember { mutableStateOf(false) }
    val anchor = com.itos.coui.menu.rememberCouiMenuAnchor()
    LaunchedEffect(running.isEmpty()) { if (running.isEmpty()) open = false }
    val context = androidx.compose.ui.platform.LocalContext.current
    EtaAnimatedVisibility(
        visible = runningTaskCount > 0 && running.isNotEmpty(),
        enter = androidx.compose.animation.expandVertically(tween(260, easing = EtaTheme.motion.emphasizedDecelerate)) +
            fadeIn(tween(200, delayMillis = 60)) + androidx.compose.animation.scaleIn(initialScale = 0.9f),
        exit = fadeOut(tween(140)) + androidx.compose.animation.shrinkVertically(tween(200, easing = EtaTheme.motion.accelerate)),
        label = "taskCapsule",
    ) {
        val first = running.firstOrNull() ?: return@EtaAnimatedVisibility
        // 一个任务显示它的标题（一行），多个显示「N 个后台任务」。
        val label = if (running.size == 1) first.title.taskLabel(24)
            else stringResource(R.string.eta_background_tasks_count, running.size)
        // 与顶部三个控件同一套液态玻璃（不再是深色胶囊），高 32dp。
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
                        Text(label, style = EtaTheme.type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                            color = EtaTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                    }
                }
            }
        }
    }
    // 点胶囊：COUI 菜单列出后台任务（一行标题，点开预览），末项「查看全部」。
    EtaContextMenu(open, onDismiss = { open = false }, couiAnchor = anchor, header = stringResource(R.string.eta_tasks)) {
        running.take(6).forEach { task ->
            EtaMenuItem(task.title.taskLabel(16), onClick = { open = false; io.github.mangi.eta.ui.screens.tasks.openTaskPreview(context, task) })
        }
        EtaMenuDivider()
        EtaMenuItem(stringResource(R.string.eta_tasks_view_all), onClick = { open = false; onTasks() })
    }
}

/**
 * 别的对话里的任务刚结束：任务胶囊的位置先出现一枚「「标题」已完成 · 查看」，6 秒后收起，点一下打开那个对话。
 * 停在首页时用它代替系统的完成横幅（AgentExecutionService.scheduleCompletionAlert）。
 */
@Composable
private fun HomeDoneNotice(currentConversationId: String?, onOpenConversation: (String) -> Unit) {
    val snapshots by io.github.mangi.eta.agent.runtime.AgentExecutionService.taskSnapshots.collectAsState()
    val tracker = remember { DoneNoticeTracker() }
    var notice by remember { mutableStateOf<io.github.mangi.eta.agent.runtime.TaskNotificationState?>(null) }
    val latestConversation by rememberUpdatedState(currentConversationId)
    LaunchedEffect(snapshots) { tracker.newlyFinished(snapshots, latestConversation)?.let { notice = it } }
    LaunchedEffect(notice) { if (notice != null) { kotlinx.coroutines.delay(6_000); notice = null } }
    LaunchedEffect(currentConversationId) { if (notice?.conversationId == currentConversationId) notice = null }
    EtaAnimatedVisibility(
        visible = notice != null,
        enter = androidx.compose.animation.expandVertically(tween(260, easing = EtaTheme.motion.emphasizedDecelerate)) +
            fadeIn(tween(200, delayMillis = 60)) + androidx.compose.animation.scaleIn(initialScale = 0.9f),
        exit = fadeOut(tween(140)) + androidx.compose.animation.shrinkVertically(tween(200, easing = EtaTheme.motion.accelerate)),
        label = "doneNotice",
    ) {
        var shown by remember { mutableStateOf(notice) }
        if (notice != null) shown = notice
        val task = shown ?: return@EtaAnimatedVisibility
        val failed = task.isFailure()
        val label = stringResource(if (failed) R.string.eta_task_failed_notice else R.string.eta_task_done_notice, task.title.taskLabel(14))
        Box(Modifier.fillMaxWidth().padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
            io.github.mangi.eta.ui.components.coui.EtaFloatingSurface(
                modifier = Modifier.height(32.dp).width(IntrinsicSize.Max).widthIn(max = 300.dp),
                shape = EtaTheme.shapes.radiusFull,
                capsule = true,
                contentDescription = label,
                onClick = { notice = null; task.conversationId?.let(onOpenConversation) },
            ) {
                Row(Modifier.padding(start = 10.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusRing(if (failed) TaskVisualStatus.Failed else TaskVisualStatus.Success, size = 12.dp, contentDescription = null)
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

private fun io.github.mangi.eta.agent.runtime.TaskNotificationState.isFailure(): Boolean = replyFailed ||
    status == io.github.mangi.eta.agent.overlay.AgentOverlayStatus.RunFailed ||
    status == io.github.mangi.eta.agent.overlay.AgentOverlayStatus.ContinuationUnavailable

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
