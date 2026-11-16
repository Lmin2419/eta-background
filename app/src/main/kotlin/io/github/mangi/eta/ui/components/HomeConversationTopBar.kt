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

internal val LocalChatToast = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
internal fun HomeConversationTopBar(
    models: AgentModelPickerUiState, isStreaming: Boolean, isCompacting: Boolean,
    @Suppress("UNUSED_PARAMETER") usage: AgentContextUsageUi,
    @Suppress("UNUSED_PARAMETER") canCompact: Boolean,
    canExport: Boolean,
    character: RoleplayChatUi?,
    onHistory: () -> Unit, onNew: () -> Unit, onTasks: () -> Unit,
    onCharacter: () -> Unit, onModel: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onCompact: () -> Unit,
    onExport: () -> Unit,
    onManageModels: () -> Unit,
    conversationTitle: String? = null,
    currentConversationId: String? = null,
    onOpenConversation: ((String) -> Unit)? = null,
    onOpenDesktopSession: ((String) -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    // 09 第三期 3.1：模型预设。onApplyPreset 为 null 时菜单里不出现预设一段。
    presets: List<io.github.mangi.eta.data.model.ModelPreset> = emptyList(),
    reasoningEffort: io.github.mangi.eta.data.model.ReasoningEffort? = null,
    onApplyPreset: ((io.github.mangi.eta.data.model.ModelPreset) -> Unit)? = null,
    onSavePreset: ((String) -> Unit)? = null,
    // 09 第四期 4.1：新对话页自己列出「正在进行」，顶部不再重复任务胶囊；完成提示条照常。
    showBackgroundCapsule: Boolean = true,
) {
    var settings by remember { mutableStateOf(false) }
    var savingPreset by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
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
    // 顶部控件的字最多放大 1.3 倍（CappedFontScale）：200% 字体时三个控件不挤、模型名不被截断。
    CappedFontScale {
    Column(Modifier.fillMaxWidth()
        .windowInsetsPadding(WindowInsets.statusBars)
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.etaChromeScale()) {
                CircleIconButton(Icons.AutoMirrored.Outlined.ShortText, stringResource(R.string.action_conversation_history), onHistory)
                // 别的对话 / 电脑会话有跑完没看的、等你处理的：右上角一个圆点（打开那个对话才清掉）。
                val localUnseen by io.github.mangi.eta.agent.runtime.FinishedUnseen.state.collectAsState()
                val desktopUnread by io.github.mangi.eta.agent.remote.DesktopBridge.unread.collectAsState()
                val approvals by io.github.mangi.eta.agent.remote.DesktopBridge.approvals.collectAsState()
                val questions by io.github.mangi.eta.agent.remote.DesktopBridge.questions.collectAsState()
                val level = remember(localUnseen, desktopUnread, approvals, questions, currentConversationId) {
                    unseenSummary(localUnseen, desktopUnread, approvals.mapTo(HashSet()) { it.sessionId } + questions.map { it.sessionId },
                        currentConversationId)
                }
                if (level != null) UnseenDot(level, Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp))
            }
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
        if (onOpenConversation != null) HomeDoneNotice(currentConversationId, onOpenConversation, onOpenDesktopSession ?: {})
        if (showBackgroundCapsule) HomeTaskCapsule(currentConversationId, onTasks, onOpenConversation ?: {}, onOpenDesktopSession ?: {})
    }
    }
    // 09 第二期 2.2：标题胶囊是「这个对话」的总入口——有标题时菜单里多「重命名」「导出」。
    val titled = character == null && !conversationTitle.isNullOrBlank()
    val conversationActions = ConversationMenuActions(
        onRename = onRename?.takeIf { titled }?.let { rename -> { settings = false; rename() } },
        onExport = if (titled && canExport) ({ settings = false; onExport() }) else null,
    )
    val presetState = onApplyPreset?.let { apply ->
        PresetMenuState(presets, reasoningEffort,
            onApply = { preset -> settings = false; apply(preset) },
            onSaveRequest = onSavePreset?.let { { settings = false; presetName = ""; savingPreset = true } })
    }
    if (onSavePreset != null) io.github.mangi.eta.ui.components.coui.EtaCouiInputDialog(
        visible = savingPreset,
        title = stringResource(R.string.model_presets_save_title),
        value = presetName,
        onValueChange = { presetName = it },
        message = models.selectedModel?.displayName?.let { name ->
            reasoningEffort?.let { "$name · ${reasoningEffortLevelText(it)}" } ?: name
        },
        placeholder = stringResource(R.string.model_presets_name_hint),
        confirmText = stringResource(R.string.action_save),
        confirmEnabled = presetName.isNotBlank(),
        onConfirm = { savingPreset = false; onSavePreset(presetName.trim()) },
        onDismissRequest = { savingPreset = false },
    )
    ModelPopover(settings, pillAnchor, models, isCompacting, conversationActions, presetState,
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
                // 回复中选了别的模型：这一行改成「→ 新模型 · 下一轮」，直到这一轮结束换上为止。
                val queued = state.queuedModel
                Text(if (queued != null) "→ ${queued.pillName} · ${stringResource(R.string.eta_model_next_turn)}" else model?.pillName ?: current,
                    style = EtaTheme.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal, lineHeight = 14.sp),
                    color = when { queued != null -> EtaTheme.colors.accent; locked -> EtaTheme.colors.textQuiet; else -> EtaTheme.colors.textTertiary },
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
    val queued = state.queuedModel
    val shown = if (queued != null) "${queued.pillName} · ${stringResource(R.string.eta_model_next_turn)}" else model?.pillName ?: current
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
                color = when { queued != null -> EtaTheme.colors.accent; locked -> EtaTheme.colors.textTertiary; else -> EtaTheme.colors.textPrimary },
                maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = EtaTheme.colors.textQuiet)
        }
    }
}

/**
 * 点模型胶囊展开的小窗：按服务商分组的模型列表（多于 6 个时带搜索），末行「管理模型…」；
 * 配了语音朗读时多一项「自动朗读」。对话有标题时多「重命名」「导出」（09 第二期 2.2，侧栏长按里也有）；
 * 原底部面板里的「压缩」挪到上下文圆环。
 */
@Composable
private fun ModelPopover(
    show: Boolean, anchor: com.itos.coui.menu.CouiMenuAnchor, models: AgentModelPickerUiState, busy: Boolean,
    conversation: ConversationMenuActions,
    presets: PresetMenuState?,
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
            if (presets != null) {
                PresetMenuItems(presets, models, busy)
                EtaMenuDivider()
            }
            models.providerGroups.forEachIndexed { groupIndex, group ->
                group.models.forEachIndexed { index, model ->
                    if (multipleGroups && groupIndex > 0 && index == 0) EtaMenuDivider()
                    val name = if (multipleGroups) "${group.providerName} · ${model.displayName}" else model.displayName
                    EtaMenuItem(if (model.id == models.queuedModel?.id) "$name · $nextTurn" else name,
                        onClick = { onModel(model.id) }, enabled = !busy && !models.isChanging,
                        selected = model.id == models.selectedModel?.id)
                }
            }
            if (conversation.any) {
                EtaMenuDivider()
                ConversationMenuItems(conversation)
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
                if (presets != null && query.isBlank()) {
                    PresetMenuItems(presets, models, busy)
                    EtaMenuDivider()
                }
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
                if (conversation.any) {
                    EtaMenuDivider()
                    ConversationMenuItems(conversation)
                }
            }
        }
    }
}

/** 标题胶囊菜单最上面的模型预设一段（09 第三期 3.1）。[onSaveRequest] 为 null 时不显示「存为预设…」。 */
internal class PresetMenuState(
    val presets: List<io.github.mangi.eta.data.model.ModelPreset>,
    val effort: io.github.mangi.eta.data.model.ReasoningEffort?,
    val onApply: (io.github.mangi.eta.data.model.ModelPreset) -> Unit,
    val onSaveRequest: (() -> Unit)?,
)

/**
 * 每个预设一项：标题是名字，说明是「模型名 · 思考强度」，模型和强度都对得上时打勾；
 * 模型已停用时置灰并注明「模型不可用」。COUI 菜单没有说明行，名字和说明并成一行。
 */
@Composable
private fun PresetMenuItems(state: PresetMenuState, models: AgentModelPickerUiState, busy: Boolean) {
    val unavailable = stringResource(R.string.model_presets_unavailable)
    val oneLine = LocalEtaMenuCollector.current != null
    state.presets.forEach { preset ->
        val model = models.providerGroups.firstNotNullOfOrNull { group -> group.models.firstOrNull { it.id == preset.modelId } }
        val summary = (model?.displayName ?: unavailable) + " · " + reasoningEffortLevelText(preset.effort)
        EtaMenuItem(if (oneLine) "${preset.name} · $summary" else preset.name,
            onClick = { state.onApply(preset) },
            enabled = model != null && !busy && !models.isChanging,
            selected = io.github.mangi.eta.data.model.presetMatches(preset, models.selectedModel?.id, state.effort),
            summary = if (oneLine) null else summary)
    }
    state.onSaveRequest?.let { save ->
        EtaMenuItem(stringResource(R.string.model_presets_save), onClick = save, enabled = models.selectedModel != null && !busy)
    }
}

/** 标题胶囊菜单里「这个对话」的操作；为 null 的不显示（新对话没有标题时两项都不显示）。 */
internal class ConversationMenuActions(val onRename: (() -> Unit)?, val onExport: (() -> Unit)?) {
    val any: Boolean get() = onRename != null || onExport != null
}

@Composable
private fun ConversationMenuItems(actions: ConversationMenuActions) {
    actions.onRename?.let { EtaMenuItem(stringResource(R.string.action_rename), onClick = it) }
    actions.onExport?.let { EtaMenuItem(stringResource(R.string.action_export), onClick = it) }
}

/** 与上游一致：模型不超过 8 个用菜单，更多时用带搜索的半窗。 */
private const val ModelMenuLimit = 8

