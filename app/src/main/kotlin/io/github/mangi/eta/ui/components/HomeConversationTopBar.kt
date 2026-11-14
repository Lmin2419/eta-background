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
) {
    var settings by remember { mutableStateOf(false) }
    var pillAnchor by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
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
                .onGloballyPositioned { pillAnchor = it.boundsInWindow() }, contentAlignment = Alignment.Center) {
                ModelPill(models, isStreaming || isCompacting || models.isChanging, character != null) {
                    if (isStreaming || isCompacting || models.isChanging) toast(lockedText) else settings = true
                }
            }
            Box(Modifier.etaChromeScale()) { CircleIconButton(Icons.Outlined.Add, stringResource(R.string.action_new_conversation), onNew) }
        }
        HomeTaskCapsule(runningTaskCount, onTasks)
    }
    ModelPopover(settings, pillAnchor, models, isStreaming || isCompacting,
        onDismiss = { settings = false },
        onModel = { id -> settings = false; if (id != models.selectedModel?.id) { pendingModel = id; onModel(id) } },
        onManageModels = { settings = false; onManageModels() })
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
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(Modifier.height(44.dp).etaPressScale(press, pressedScale = 0.95f)
        .etaGlass(EtaTheme.shapes.radiusFull, elevation = EtaElevation.E1, interactionSource = press)
        .clip(EtaTheme.shapes.radiusFull).clickable(interactionSource = press, indication = null, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = current }
        .padding(start = if (iconOnly) 12.dp else 16.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (iconOnly) ModelBrandMark(model?.modelId, model?.providerSourceType, 16.dp)
        else Text(shown,
            modifier = Modifier.weight(1f, fill = false), style = EtaTheme.type.rowTitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            color = if (locked) EtaTheme.colors.textTertiary else EtaTheme.colors.textPrimary,
            maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = EtaTheme.colors.textQuiet)
    }
}

/**
 * 点模型胶囊展开的小窗：按服务商分组的模型列表（多于 6 个时带搜索），末行「管理模型…」；
 * 配了语音朗读时多一项「自动朗读」。原底部面板里的「压缩」挪到上下文圆环，「导出」挪到侧栏长按。
 */
@Composable
private fun ModelPopover(
    show: Boolean, anchor: androidx.compose.ui.geometry.Rect, models: AgentModelPickerUiState, busy: Boolean,
    onDismiss: () -> Unit, onModel: (String) -> Unit, onManageModels: () -> Unit,
) {
    var query by remember(show) { mutableStateOf("") }
    val groups = remember(models.providerGroups, query) { models.filterGroups(query) }
    val speech by remember { SpeechSettingsRepository.settingsFlow() }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    EtaPopover(show, anchor, onDismiss, align = EtaPopoverAlign.Center, title = stringResource(R.string.eta_chat_settings),
        minWidth = 260.dp, maxWidth = 300.dp) {
        if (models.modelCount > 6) {
            EtaSearchField(query, { query = it }, stringResource(R.string.ui_search_model_df5586), Modifier.padding(bottom = 6.dp))
        }
        groups.forEach { group ->
            if (groups.size > 1) Text(group.providerName, style = EtaTheme.type.caption, color = EtaTheme.colors.textTertiary,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            group.models.forEach { model ->
                EtaMenuItem(model.displayName, onClick = { onModel(model.id) },
                    enabled = !busy && !models.isChanging, selected = model.id == models.selectedModel?.id)
            }
        }
        EtaMenuDivider()
        if (speech?.tts != null && speech?.tts != TtsProvider.NONE) {
            EtaMenuItem(stringResource(R.string.eta_auto_read), selected = speech?.chatAutoSpeak == true, onClick = {
                speech?.let { value -> scope.launch { SpeechSettingsRepository.save(value.copy(chatAutoSpeak = !value.chatAutoSpeak)) } }
            })
        }
        EtaMenuItem(stringResource(R.string.eta_manage_models_ellipsis), onClick = onManageModels)
    }
}

/**
 * 后台任务胶囊：深色玻璃（#161616 82%，高 32dp），运行圈 + 当前任务标题与步骤；
 * 点一下从这里展开任务卡片（任务列表、停止、查看全部）。
 */
@Composable
private fun HomeTaskCapsule(runningTaskCount: Int, onTasks: () -> Unit) {
    val snapshots by io.github.mangi.eta.agent.runtime.AgentExecutionService.taskSnapshots.collectAsState()
    val running = remember(snapshots) { snapshots.filter { !it.completed } }
    var open by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
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
        val step = first.status.localizedText()
        val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val capsule = androidx.compose.ui.graphics.Color(0xFF161616)
        Box(Modifier.fillMaxWidth().padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
            Row(Modifier.height(32.dp).widthIn(max = 300.dp)
                .onGloballyPositioned { anchor = it.boundsInWindow() }
                .etaPressScale(press, pressedScale = 0.96f)
                .etaGlass(EtaTheme.shapes.radiusFull, tint = capsule, elevation = EtaElevation.E2, blurAlpha = 0.82f, solidAlpha = 0.9f)
                .clip(EtaTheme.shapes.radiusFull)
                .clickable(interactionSource = press, indication = null, role = Role.Button) { open = true }
                .padding(start = 10.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusRing(TaskVisualStatus.Running, size = 14.dp, contentDescription = null)
                Text(first.title, style = EtaTheme.type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                    color = androidx.compose.ui.graphics.Color(0xFFF5F5F3), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                val tail = if (running.size > 1) "+${running.size - 1}" else step
                if (!tail.isNullOrBlank()) Text(tail, style = EtaTheme.type.footnote, color = androidx.compose.ui.graphics.Color(0xFFA09E9A),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    EtaPopover(open, anchor, { open = false }, align = EtaPopoverAlign.Center, title = stringResource(R.string.eta_tasks),
        minWidth = 260.dp, maxWidth = 320.dp) {
        running.take(5).forEach { task ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .clickable { open = false; io.github.mangi.eta.ui.screens.tasks.openTaskPreview(context, task) }
                .padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusRing(TaskVisualStatus.Running, size = 16.dp, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = EtaTheme.type.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    task.status.localizedText().takeIf { it.isNotBlank() }?.let {
                        Text(it, style = EtaTheme.type.caption, color = EtaTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                EtaIconButton({ io.github.mangi.eta.ui.screens.tasks.taskAction(task, io.github.mangi.eta.agent.runtime.AgentExecutionService.ACTION_STOP_RUN) }) {
                    Icon(Icons.Outlined.StopCircle, stringResource(R.string.overlay_stop_task), Modifier.size(20.dp), tint = EtaTheme.colors.textSecondary)
                }
            }
        }
        EtaMenuDivider()
        EtaMenuItem(stringResource(R.string.eta_tasks_view_all), onClick = { open = false; onTasks() })
    }
}
