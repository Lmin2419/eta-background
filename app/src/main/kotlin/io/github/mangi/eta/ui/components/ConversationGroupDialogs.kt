package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.ConversationPaneUiState
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField

internal class ConversationGroupDialogState {
    var moveIds by mutableStateOf<Set<String>?>(null)
    var menuId by mutableStateOf<String?>(null)
    var edit by mutableStateOf<ConversationGroupEdit?>(null)
    var deleteId by mutableStateOf<String?>(null)
    fun dismiss() { moveIds = null; menuId = null; edit = null; deleteId = null }
}
internal data class ConversationGroupEdit(val id: String?, val name: String, val moveIds: Set<String> = emptySet())

@Composable
internal fun ConversationGroupDialogs(state: ConversationPaneUiState, actions: PaneManagementActions,
    dialogs: ConversationGroupDialogState, onMoved: () -> Unit) {
    val groups = state.groups.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    val moving = dialogs.moveIds
    EtaSheet(moving != null, title = stringResource(R.string.eta_move_to_group), onDismissRequest = { dialogs.moveIds = null }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            groups.forEach { group ->
                EtaArrowPreference(stringResource(R.string.eta_group_count, group.name, group.conversationCount),
                    onClick = { moving?.let { actions.moveToGroup(it, group.id) }; dialogs.moveIds = null; onMoved() })
            }
            EtaArrowPreference(stringResource(R.string.eta_new_group), onClick = {
                dialogs.edit = ConversationGroupEdit(null, "", moving.orEmpty()); dialogs.moveIds = null
            })
            if (state.conversations.any { it.id in moving.orEmpty() && it.groupId != null }) {
                EtaArrowPreference(stringResource(R.string.eta_remove_from_group), onClick = {
                    moving?.let { actions.moveToGroup(it, null) }; dialogs.moveIds = null; onMoved()
                })
            }
        }
    }
    val group = groups.firstOrNull { it.id == dialogs.menuId }
    EtaSheet(group != null, title = group?.name, onDismissRequest = { dialogs.menuId = null }) {
        if (group != null) Column {
            val index = groups.indexOf(group)
            EtaArrowPreference(stringResource(R.string.eta_rename_group), onClick = {
                dialogs.edit = ConversationGroupEdit(group.id, group.name); dialogs.menuId = null
            })
            EtaArrowPreference(stringResource(R.string.eta_move_group_up), enabled = index > 0,
                onClick = { actions.reorderGroup(group.id, -1); dialogs.menuId = null })
            EtaArrowPreference(stringResource(R.string.eta_move_group_down), enabled = index < groups.lastIndex,
                onClick = { actions.reorderGroup(group.id, 1); dialogs.menuId = null })
            EtaArrowPreference(stringResource(R.string.eta_delete_group), onClick = { dialogs.deleteId = group.id; dialogs.menuId = null })
        }
    }
    val editing = dialogs.edit
    if (editing != null) key(editing) {
        var value by remember { mutableStateOf(editing.name) }
        var error by remember { mutableStateOf<String?>(null) }
        EtaWindowDialog(true, title = stringResource(if (editing.id == null) R.string.eta_new_group else R.string.eta_rename_group),
            onDismissRequest = { dialogs.edit = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(value, onValueChange = { value = it; error = null }, label = stringResource(R.string.eta_group_name),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, style = EtaTheme.type.footnote, color = EtaTheme.colors.danger) }
                MiuixDialogActions(confirmText = stringResource(R.string.action_save), onCancel = { dialogs.edit = null }, onConfirm = {
                    error = if (editing.id == null) actions.createGroup(value, editing.moveIds) else actions.renameGroup(editing.id, value)
                    if (error == null) { dialogs.edit = null; if (editing.id == null) onMoved() }
                })
            }
        }
    }
    val deleting = groups.firstOrNull { it.id == dialogs.deleteId }
    if (deleting != null) EtaWindowDialog(true, title = stringResource(R.string.eta_delete_group),
        summary = stringResource(R.string.eta_delete_group_body, deleting.conversationCount), onDismissRequest = { dialogs.deleteId = null }) {
        MiuixDialogActions(confirmText = stringResource(R.string.action_delete), destructive = true,
            onCancel = { dialogs.deleteId = null }, onConfirm = { actions.deleteGroup(deleting.id); dialogs.deleteId = null })
    }
}
