package io.github.mangi.eta.ui.components

import io.github.mangi.eta.ui.components.coui.EtaMenuAnchor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.filterGroups
import io.github.mangi.eta.ui.model.modelCount
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.Text

/**
 * 换模型重答（etax/design/09 第三期 3.2）：回答的长按菜单里「换模型重答」用到的模型列表和回调。
 * 由手机对话页提供；电脑会话不支持重新生成，不提供，菜单项也就不出现。
 */
internal class RegenerateWithModelSupport(
    val models: AgentModelPickerUiState,
    val onRegenerate: (messageId: String, modelId: String) -> Unit,
)

internal val LocalRegenerateWithModel = staticCompositionLocalOf<RegenerateWithModelSupport?> { null }

/**
 * 选一个模型重答这一条：模型不超过 8 个时在长按菜单原来的位置弹第二个菜单（同一个锚点），更多时用带搜索的半窗，
 * 写法照标题胶囊的模型小窗。当前对话正在用的模型打勾（选它等于普通的重新生成）。
 */
@Composable
internal fun RegenerateModelChooser(
    show: Boolean,
    anchor: EtaMenuAnchor?,
    models: AgentModelPickerUiState,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val multipleGroups = models.providerGroups.size > 1
    val title = stringResource(R.string.regenerate_with_model)
    if (models.modelCount <= RegenerateMenuLimit) {
        EtaContextMenu(show, onDismiss = onDismiss, couiAnchor = anchor, header = title) {
            models.providerGroups.forEachIndexed { groupIndex, group ->
                group.models.forEachIndexed { index, model ->
                    if (multipleGroups && groupIndex > 0 && index == 0) EtaMenuDivider()
                    EtaMenuItem(if (multipleGroups) "${group.providerName} · ${model.displayName}" else model.displayName,
                        onClick = { onPick(model.id) }, selected = model.id == models.selectedModel?.id)
                }
            }
        }
    } else {
        var query by remember(show) { mutableStateOf("") }
        val groups = remember(models.providerGroups, query) { models.filterGroups(query) }
        EtaSheet(show, title = title, onDismissRequest = onDismiss) {
            EtaSearchField(query, { query = it }, stringResource(R.string.ui_search_model_df5586), Modifier.padding(bottom = 8.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                groups.forEach { group ->
                    if (groups.size > 1) Text(group.providerName, style = EtaTheme.type.caption, color = EtaTheme.colors.textTertiary,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    group.models.forEach { model ->
                        EtaMenuItem(model.displayName, onClick = { onPick(model.id) }, selected = model.id == models.selectedModel?.id)
                    }
                }
            }
        }
    }
}

private const val RegenerateMenuLimit = 8
