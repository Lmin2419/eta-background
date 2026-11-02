package io.github.mangi.eta.ui.screens.tasks

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.repository.CommonTask
import io.github.mangi.eta.data.repository.CommonTaskRepository
import io.github.mangi.eta.ui.CommonTaskShortcuts
import io.github.mangi.eta.ui.app.CommonTasksStore
import io.github.mangi.eta.ui.components.EtaCard
import io.github.mangi.eta.ui.components.EtaTextButton
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.ListEmptyState
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun CommonTasksScreen(store: CommonTasksStore, onBack: () -> Unit, onRun: (CommonTask) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var instruction by rememberSaveable { mutableStateOf("") }
    var menuId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val editable = !store.busy && !store.loadFailed

    fun edit(task: CommonTask?, copy: Boolean = false) {
        editId = if (copy) null else task?.id
        name = if (copy && task != null) resources.getString(R.string.common_tasks_copy_name, task.name)
            .take(CommonTaskRepository.MAX_NAME_LENGTH) else task?.name.orEmpty()
        instruction = task?.instruction.orEmpty()
        editorOpen = true
        menuId = null
    }

    LaunchedEffect(store.error) {
        if (store.error) {
            Toast.makeText(context, R.string.common_tasks_save_failed, Toast.LENGTH_LONG).show()
            store.dismissError()
        }
    }

    MiuixScaffoldPage(title = stringResource(R.string.common_tasks_title), onBack = onBack) {
        item(key = "description") {
            Text(stringResource(R.string.common_tasks_description),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        item(key = "create") {
            EtaTextButton(stringResource(R.string.common_tasks_create), onClick = { edit(null) },
                enabled = editable && store.tasks.size < CommonTaskRepository.MAX_TASKS,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        }
        if (store.loadFailed) {
            item(key = "error") {
                ListEmptyState(title = stringResource(R.string.common_tasks_load_failed),
                    summary = stringResource(R.string.common_tasks_load_preserved), icon = Icons.Rounded.BookmarkBorder,
                    action = { EtaTextButton(stringResource(R.string.common_tasks_retry), store::refresh, enabled = !store.busy) })
            }
        } else if (store.tasks.isEmpty()) {
            item(key = "empty") {
                ListEmptyState(title = stringResource(if (store.busy) R.string.common_tasks_loading else R.string.common_tasks_empty),
                    summary = stringResource(R.string.common_tasks_empty_hint), icon = Icons.Rounded.BookmarkBorder)
            }
        }
        items(store.tasks, key = { it.id }) { task ->
            EtaCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(task.name, style = MiuixTheme.textStyles.headline1)
                    Text(task.instruction, style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EtaTextButton(stringResource(R.string.common_tasks_run), { onRun(task) },
                            modifier = Modifier.weight(1f), enabled = editable,
                            colors = ButtonDefaults.textButtonColorsPrimary())
                        EtaTextButton(stringResource(R.string.common_tasks_manage), { menuId = task.id },
                            modifier = Modifier.weight(1f), enabled = editable)
                    }
                }
            }
        }
        if (store.tasks.size >= CommonTaskRepository.MAX_TASKS) {
            item(key = "limit") {
                Text(stringResource(R.string.common_tasks_limit, CommonTaskRepository.MAX_TASKS),
                    modifier = Modifier.padding(20.dp), style = MiuixTheme.textStyles.footnote1)
            }
        }
    }

    if (editorOpen) {
        EtaWindowDialog(show = true, title = stringResource(if (editId == null) R.string.common_tasks_create else R.string.common_tasks_edit),
            onDismissRequest = { if (!store.busy) editorOpen = false }) {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(value = name, onValueChange = { if (it.length <= CommonTaskRepository.MAX_NAME_LENGTH) name = it },
                    label = stringResource(R.string.common_tasks_name), singleLine = true, enabled = !store.busy,
                    modifier = Modifier.fillMaxWidth())
                TextField(value = instruction,
                    onValueChange = { if (it.length <= CommonTaskRepository.MAX_INSTRUCTION_LENGTH) instruction = it },
                    label = stringResource(R.string.common_tasks_instruction), minLines = 4, maxLines = 10,
                    enabled = !store.busy, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.common_tasks_input_limit, CommonTaskRepository.MAX_NAME_LENGTH,
                    CommonTaskRepository.MAX_INSTRUCTION_LENGTH), style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                MiuixDialogActions(confirmText = stringResource(R.string.common_tasks_save),
                    onCancel = { editorOpen = false }, cancelEnabled = !store.busy,
                    confirmEnabled = editable && name.isNotBlank() && instruction.isNotBlank(),
                    onConfirm = {
                        store.save(editId, name, instruction) { saved ->
                            editorOpen = false
                            // Shortcut metadata failures do not undo the saved task.
                            runCatching { CommonTaskShortcuts.update(context, saved) }
                        }
                    })
            }
        }
    }

    store.tasks.firstOrNull { it.id == menuId }?.let { task ->
        EtaWindowDialog(show = true, title = task.name, onDismissRequest = { menuId = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EtaTextButton(stringResource(R.string.common_tasks_edit), { edit(task) }, Modifier.fillMaxWidth())
                EtaTextButton(stringResource(R.string.common_tasks_copy), { edit(task, copy = true) }, Modifier.fillMaxWidth(),
                    enabled = store.tasks.size < CommonTaskRepository.MAX_TASKS)
                EtaTextButton(stringResource(R.string.common_tasks_pin), {
                    menuId = null
                    val accepted = runCatching { CommonTaskShortcuts.requestPin(context, task) }.getOrDefault(false)
                    Toast.makeText(context, if (accepted) R.string.common_tasks_pin_requested else R.string.common_tasks_pin_unavailable,
                        Toast.LENGTH_LONG).show()
                }, Modifier.fillMaxWidth())
                EtaTextButton(stringResource(R.string.common_tasks_delete), { menuId = null; deleteId = task.id }, Modifier.fillMaxWidth())
            }
        }
    }
    store.tasks.firstOrNull { it.id == deleteId }?.let { task ->
        EtaWindowDialog(show = true, title = stringResource(R.string.common_tasks_delete),
            summary = stringResource(R.string.common_tasks_delete_confirm, task.name),
            onDismissRequest = { if (!store.busy) deleteId = null }) {
            MiuixDialogActions(confirmText = stringResource(R.string.common_tasks_delete), destructive = true,
                onCancel = { deleteId = null }, cancelEnabled = !store.busy, confirmEnabled = editable,
                onConfirm = { store.delete(task.id) { deleteId = null; runCatching { CommonTaskShortcuts.disable(context, task.id) } } })
        }
    }
}
