package io.github.mangi.eta.ui.screens.settings

import io.github.mangi.eta.ui.components.coui.rememberEtaMenuAnchor
import io.github.mangi.eta.ui.components.coui.etaMenuAnchor
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.ModelPreset
import io.github.mangi.eta.data.model.enabledModel
import io.github.mangi.eta.data.repository.ModelPresetRepository
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaContextMenu
import io.github.mangi.eta.ui.components.EtaListGroup
import io.github.mangi.eta.ui.components.EtaMenuItem
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.coui.EtaCouiInputDialog
import io.github.mangi.eta.ui.components.reasoningEffortLevelText
import io.github.mangi.eta.ui.theme.EtaTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text

/**
 * 设置 › 模型与对话 › 模型预设（09 第三期 3.1）：列出存好的预设，点一行可以改名或删除。
 * 新预设在标题胶囊菜单里用「存为预设…」把当前模型和思考强度存下来。
 */
@Composable
internal fun ModelPresetsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val presets by remember { ModelPresetRepository.presetsFlow() }.collectAsState(initial = emptyList())
    val providers by remember { ProviderRepository.providersFlow() }.collectAsState(initial = emptyList())
    var renaming by remember { mutableStateOf<ModelPreset?>(null) }
    var name by remember { mutableStateOf("") }
    MiuixScaffoldPage(
        title = stringResource(R.string.model_presets_title),
        onBack = onBack,
        overlay = {
            EtaCouiInputDialog(
                visible = renaming != null,
                title = stringResource(R.string.model_presets_rename),
                value = name,
                onValueChange = { name = it },
                confirmText = stringResource(R.string.action_save),
                confirmEnabled = name.isNotBlank(),
                onConfirm = {
                    renaming?.let { preset -> scope.launch { ModelPresetRepository.rename(preset.id, name) } }
                    renaming = null
                },
                onDismissRequest = { renaming = null },
            )
        },
    ) {
        if (presets.isEmpty()) {
            item(key = "empty") {
                Text(stringResource(R.string.model_presets_empty), style = EtaTheme.type.subhead, color = EtaTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp))
            }
        } else {
            item(key = "presets") {
                EtaListGroup {
                    presets.forEachIndexed { index, preset ->
                        if (index > 0) EtaPreferenceDivider(hasLeading = false)
                        val model = remember(providers, preset.modelId) { providers.enabledModel(preset.modelId) }
                        PresetRow(
                            preset = preset,
                            summary = (model?.model?.displayName ?: stringResource(R.string.model_presets_unavailable)) +
                                " · " + reasoningEffortLevelText(preset.effort),
                            onRename = { name = preset.name; renaming = preset },
                            onDelete = { scope.launch { ModelPresetRepository.delete(preset.id) } },
                        )
                    }
                }
            }
        }
        item(key = "hint") {
            Text(stringResource(R.string.model_presets_hint), style = EtaTheme.type.footnote, color = EtaTheme.colors.textTertiary,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun PresetRow(preset: ModelPreset, summary: String, onRename: () -> Unit, onDelete: () -> Unit) {
    val anchor = rememberEtaMenuAnchor()
    var menu by remember { mutableStateOf(false) }
    EtaArrowPreference(
        title = preset.name,
        summary = summary,
        modifier = Modifier.etaMenuAnchor(anchor),
        onClick = { anchor.open { menu = true } },
    )
    EtaContextMenu(menu, onDismiss = { menu = false }, couiAnchor = anchor) {
        EtaMenuItem(stringResource(R.string.action_rename), onClick = { menu = false; onRename() })
        EtaMenuItem(stringResource(R.string.ui_delete_3755f5), destructive = true, onClick = { menu = false; onDelete() })
    }
}
