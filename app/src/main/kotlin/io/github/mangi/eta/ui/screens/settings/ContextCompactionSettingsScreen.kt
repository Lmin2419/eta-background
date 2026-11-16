package io.github.mangi.eta.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.R
import io.github.mangi.eta.config.Prefs
import io.github.mangi.eta.data.model.ContextCompactionSettings
import io.github.mangi.eta.data.model.ReasoningEffort
import io.github.mangi.eta.data.model.enabledModel
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import io.github.mangi.eta.ui.SwitchPref
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaOverlayDropdownPreference
import io.github.mangi.eta.ui.components.EtaPreferenceColors
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.EtaSheet
import io.github.mangi.eta.ui.components.EtaSwitchPreference
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.model.AgentModelPickerProjector
import io.github.mangi.eta.ui.theme.EtaTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text

/**
 * 上下文压缩（etax/design/05 §6.3）：自动压缩、后台压缩、压缩模型与思考等级、开始比例、保留轮数。
 * 照「对话标题」设置页的写法；压缩模型复用子代理的模型选择面板。
 */
@Composable
internal fun ContextCompactionSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val scope = rememberCoroutineScope()
    val agentPrefs = remember { Prefs.localAgentPreferences() }
    val providers by ProviderRepository.providersFlow().collectAsState(initial = emptyList())
    val primaryProviderId by RuntimeConfigRepository.selectedProviderIdFlow().collectAsState(initial = null)
    val primaryModelId by RuntimeConfigRepository.selectedModelIdFlow().collectAsState(initial = null)
    val settings by RuntimeConfigRepository.contextCompactionSettingsFlow()
        .collectAsState(initial = ContextCompactionSettings())
    LaunchedEffect(Unit) { RuntimeConfigRepository.ensureDefaults(EtaApp.serviceInstance) }

    val pickerState = remember(providers, primaryProviderId, primaryModelId) {
        AgentModelPickerProjector.project(providers, primaryProviderId, primaryModelId)
    }
    val resolvedCustom = remember(providers, settings.modelId) {
        settings.modelId?.takeIf { it.isNotBlank() }?.let { providers.enabledModel(it) }
    }
    val followTitle = stringResource(R.string.compaction_settings_follow_conversation)
    val modelSummary = resolvedCustom?.let { "${it.provider.name} / ${it.model.displayName}" } ?: followTitle
    val capabilities = remember(providers, resolvedCustom, primaryModelId) {
        (resolvedCustom ?: providers.enabledModel(primaryModelId))?.let { (provider, model) ->
            RuntimeConfigRepository.reasoningCapabilities(provider, model)
        }
    }
    val effortOptions = remember(capabilities) { listOf<ReasoningEffort?>(null) + capabilities?.selectableEfforts.orEmpty() }
    val autoMinimal = stringResource(R.string.conversation_title_settings_reasoning_auto_minimal)
    val providerDefault = stringResource(R.string.conversation_title_settings_reasoning_provider_default)
    val effortLabels = effortOptions.map { effort ->
        when (effort) {
            null -> autoMinimal
            ReasoningEffort.DEFAULT -> providerDefault
            else -> effort.displayName
        }
    }
    val effortIndex = effortOptions.indexOf(settings.resolvedReasoningEffort).takeIf { it >= 0 } ?: 0
    val startOptions = listOf(50, 60, 70)
    val keepOptions = listOf(2, 4, 6)
    var showModelSheet by remember { mutableStateOf(false) }
    fun save(next: ContextCompactionSettings) { scope.launch { RuntimeConfigRepository.setContextCompactionSettings(next) } }

    val modelTitle = stringResource(R.string.compaction_settings_model)
    MiuixScaffoldPage(
        title = stringResource(R.string.compaction_settings_title),
        onBack = onBack,
        overlay = {
            EtaSheet(show = showModelSheet, title = modelTitle, onDismissRequest = { showModelSheet = false },
                insideMargin = DpSize(0.dp, 0.dp)) {
                io.github.mangi.eta.ui.screens.subagent.SubagentModelPickerContent(
                    pickerState = pickerState,
                    selectedSecondaryModelId = resolvedCustom?.model?.id,
                    primaryModelName = pickerState.selectedModel?.displayName,
                    inheritLabel = followTitle,
                    onSelectModel = { id ->
                        showModelSheet = false
                        save(settings.copy(modelId = id, reasoningEffort = null))
                    },
                )
            }
        },
    ) {
        item(key = "compaction_switches") {
            EtaPreferenceGroupTitle(stringResource(R.string.compaction_settings_group))
            EtaPreferenceGroup {
                SwitchPref(
                    context = context,
                    prefs = agentPrefs,
                    title = stringResource(R.string.settings_auto_compaction),
                    key = Prefs.Keys.AGENT_AUTO_COMPACTION_ENABLED,
                    icon = Icons.Outlined.Layers,
                    iconTint = EtaPreferenceColors.Blue,
                )
                EtaPreferenceDivider()
                EtaSwitchPreference(
                    title = stringResource(R.string.compaction_settings_background),
                    summary = stringResource(R.string.compaction_settings_background_summary),
                    checked = settings.background,
                    onCheckedChange = { save(settings.copy(background = it)) },
                )
            }
        }
        item(key = "compaction_model") {
            EtaPreferenceGroupTitle(stringResource(R.string.compaction_settings_model_group))
            EtaPreferenceGroup {
                EtaArrowPreference(title = modelTitle, summary = modelSummary, enabled = settings.background,
                    onClick = { showModelSheet = true })
                EtaPreferenceDivider(hasLeading = false)
                EtaOverlayDropdownPreference(
                    title = stringResource(R.string.conversation_title_settings_reasoning_effort),
                    items = effortLabels,
                    selectedIndex = effortIndex,
                    enabled = settings.background && resolvedCustom != null && effortOptions.size > 1,
                    onSelectedIndexChange = { index -> save(settings.copy(reasoningEffort = effortOptions.getOrNull(index)?.wireValue)) },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaOverlayDropdownPreference(
                    title = stringResource(R.string.compaction_settings_start),
                    items = startOptions.map { "$it%" },
                    selectedIndex = startOptions.indexOf(settings.startPercent).takeIf { it >= 0 } ?: 1,
                    enabled = settings.background,
                    onSelectedIndexChange = { index -> save(settings.copy(startPercent = startOptions[index])) },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaOverlayDropdownPreference(
                    title = stringResource(R.string.compaction_settings_keep),
                    items = keepOptions.map { resources.getString(R.string.compaction_settings_keep_turns, it) },
                    selectedIndex = keepOptions.indexOf(settings.keepRecentTurns).takeIf { it >= 0 } ?: 1,
                    enabled = settings.background,
                    onSelectedIndexChange = { index -> save(settings.copy(keepRecentTurns = keepOptions[index])) },
                )
            }
            Text(
                text = stringResource(R.string.compaction_settings_note),
                style = EtaTheme.type.caption,
                color = EtaTheme.colors.textTertiary,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            )
        }
    }
}
