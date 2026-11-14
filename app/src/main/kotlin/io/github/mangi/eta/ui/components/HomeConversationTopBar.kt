package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.outlined.AddComment
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
    usage: AgentContextUsageUi, canCompact: Boolean, canExport: Boolean,
    character: RoleplayChatUi?, runningTaskCount: Int,
    onHistory: () -> Unit, onNew: () -> Unit, onTasks: () -> Unit,
    onCharacter: () -> Unit, onModel: (String) -> Unit, onCompact: () -> Unit,
    onExport: () -> Unit, onManageModels: () -> Unit,
) {
    var settings by remember { mutableStateOf(false) }
    var pendingModel by remember { mutableStateOf<String?>(null) }
    val toast = LocalChatToast.current
    val lockedText = stringResource(R.string.eta_model_locked)
    val changedText = stringResource(R.string.eta_model_changed, models.selectedModel?.displayName.orEmpty())
    LaunchedEffect(models.selectedModel?.id) {
        if (pendingModel != null && models.selectedModel?.id == pendingModel) { toast(changedText); pendingModel = null }
    }
    val colors = EtaTheme.colors
    // 顶栏本身透明，铺在玻璃顶栏材质上；左边历史、中间模型、右边一枚玻璃胶囊（任务状态 + 新建会话）。
    Row(Modifier.fillMaxWidth()
        .windowInsetsPadding(WindowInsets.statusBars)
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        .heightIn(min = 60.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircleIconButton(Icons.AutoMirrored.Outlined.ShortText, stringResource(R.string.action_conversation_history), onHistory)
        if (character != null) {
            Row(Modifier.weight(1f).clip(EtaTheme.shapes.radiusMd).clickable(onClick = onCharacter).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CharacterAvatar(character.characterName, character.avatarPath, size = 24.dp, shape = EtaTheme.shapes.radiusFull)
                Text(character.characterName, style = EtaTheme.type.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(if (character == null) Modifier.weight(1f) else Modifier, contentAlignment = Alignment.Center) {
            ModelPill(models, isStreaming || isCompacting || models.isChanging, character != null) {
                if (isStreaming || isCompacting || models.isChanging) toast(lockedText) else settings = true
            }
        }
        // 右侧按钮收进同一枚玻璃胶囊：有任务运行时，任务状态从左侧展开进来，两个按钮间距固定，不会挤在一起。
        Row(Modifier.height(44.dp).etaGlass(EtaTheme.shapes.radiusFull, elevation = null, useLocalBackdrop = false)
            .padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            EtaAnimatedVisibility(
                visible = runningTaskCount > 0,
                enter = expandHorizontally(tween(320, easing = EtaTheme.motion.emphasizedDecelerate), expandFrom = Alignment.End) +
                    fadeIn(tween(240, delayMillis = 80)),
                exit = fadeOut(tween(140)) + shrinkHorizontally(tween(220, easing = EtaTheme.motion.accelerate), shrinkTowards = Alignment.End),
                label = "runningTasks",
            ) {
                Box(Modifier.size(40.dp).etaTouchScale(0.88f).clip(EtaTheme.shapes.radiusFull).clickable(onClick = onTasks), contentAlignment = Alignment.Center) {
                    StatusRing(TaskVisualStatus.Running, size = 20.dp, contentDescription = stringResource(R.string.eta_tasks))
                    // 数量角标留在按钮范围内，不再压到旁边的按钮。
                    Text(runningTaskCount.toString(), style = EtaTheme.type.caption.copy(fontSize = 10.sp),
                        color = colors.onAccent, modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp)
                            .background(colors.accent, EtaTheme.shapes.radiusFull).padding(horizontal = 4.dp))
                }
            }
            Box(Modifier.size(40.dp).etaTouchScale(0.88f).clip(EtaTheme.shapes.radiusFull).clickable(onClickLabel = stringResource(R.string.action_new_conversation), onClick = onNew),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AddComment, stringResource(R.string.action_new_conversation), Modifier.size(22.dp), tint = colors.textPrimary)
            }
        }
    }
    ConversationSettingsSheet(settings, models, isStreaming || isCompacting, usage, canCompact, canExport,
        onDismiss = { settings = false },
        onModel = { id -> settings = false; if (id != models.selectedModel?.id) { pendingModel = id; onModel(id) } },
        onCompact = { settings = false; onCompact() }, onExport = { settings = false; onExport() },
        onManageModels = { settings = false; onManageModels() })
}

@Composable
internal fun ModelPill(state: AgentModelPickerUiState, locked: Boolean, iconOnly: Boolean = false, onClick: () -> Unit) {
    val model = state.selectedModel
    val current = model?.displayName ?: stringResource(R.string.model_not_selected)
    val shown = model?.pillName ?: current
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(Modifier.heightIn(min = 40.dp).etaPressScale(press, pressedScale = 0.95f)
        .etaGlass(EtaTheme.shapes.radiusFull, elevation = null, useLocalBackdrop = false, interactionSource = press)
        .clip(EtaTheme.shapes.radiusFull).clickable(interactionSource = press, indication = null, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = current }
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ModelBrandMark(model?.modelId, model?.providerSourceType, 16.dp)
        // Use the whole space between the top-bar buttons; when it still does not fit, keep both
        // the model family and its version/size, which sit at the two ends of the name.
        if (!iconOnly) Text(shown,
            modifier = Modifier.weight(1f, fill = false), style = EtaTheme.type.rowTitle.copy(fontSize = 15.sp),
            color = if (locked) EtaTheme.colors.textTertiary else EtaTheme.colors.textPrimary,
            maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = EtaTheme.colors.textTertiary)
    }
}

@Composable
internal fun ConversationSettingsSheet(
    show: Boolean, models: AgentModelPickerUiState, busy: Boolean, usage: AgentContextUsageUi,
    canCompact: Boolean, canExport: Boolean, onDismiss: () -> Unit, onModel: (String) -> Unit,
    onCompact: () -> Unit, onExport: () -> Unit, onManageModels: () -> Unit,
) {
    var query by remember(show) { mutableStateOf("") }
    val groups = remember(models.providerGroups, query) { models.filterGroups(query) }
    val speech by remember { SpeechSettingsRepository.settingsFlow() }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.78f }
    val usageText = formatContextUsage(usage, stringResource(R.string.context_no_previous_usage),
        stringResource(R.string.context_no_model_limit), LocalConfiguration.current.locales[0])
    EtaSheet(show, title = stringResource(R.string.eta_chat_settings), onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxHeight).imePadding(),
            contentPadding = PaddingValues(bottom = 16.dp)) {
            if (models.modelCount > 8) item(key = "search") {
                EtaSearchField(query, { query = it }, stringResource(R.string.ui_search_model_df5586), Modifier.padding(bottom = 12.dp))
            }
            groups.forEach { group ->
                item(key = "provider:${group.providerId}") { EtaPreferenceGroupTitle(group.providerName) }
                items(group.models, key = { "model:${it.providerId}:${it.id}" }) { model ->
                    ModelPickerRow(model, model.id == models.selectedModel?.id, { onModel(model.id) }, enabled = !busy && !models.isChanging)
                }
            }
            item(key = "manage") { EtaArrowPreference(stringResource(R.string.eta_manage_models), onClick = onManageModels) }
            item(key = "conversation") {
                EtaPreferenceGroupTitle(stringResource(R.string.eta_this_conversation), Modifier.padding(top = 16.dp))
                EtaListGroup(modifier = Modifier.fillMaxWidth()) {
                    if (speech?.tts != null && speech?.tts != TtsProvider.NONE) {
                        EtaSwitchPreference(stringResource(R.string.eta_auto_read), checked = speech?.chatAutoSpeak == true,
                            onCheckedChange = { enabled -> speech?.let { value -> scope.launch { SpeechSettingsRepository.save(value.copy(chatAutoSpeak = enabled)) } } })
                        EtaPreferenceDivider(false)
                    }
                    EtaArrowPreference(stringResource(R.string.context_compact_action),
                        summary = usageText + if (canCompact && !busy) "" else "\n" + stringResource(R.string.eta_compact_unavailable),
                        enabled = canCompact && !busy, onClick = onCompact)
                    EtaPreferenceDivider(false)
                    EtaArrowPreference(stringResource(R.string.action_export), enabled = canExport, onClick = onExport)
                }
            }
        }
    }
}
