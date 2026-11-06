package io.github.mangi.eta.ui.screens.tasks

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.rounded.Add
import io.github.mangi.eta.ui.theme.EtaElevation
import io.github.mangi.eta.ui.theme.etaElevation
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material.icons.rounded.MoreHoriz
import io.github.mangi.eta.ui.theme.EtaTheme
import io.github.mangi.eta.ui.components.EtaSheet
import io.github.mangi.eta.ui.components.EtaIconButton
import io.github.mangi.eta.ui.components.EtaButtonKind
import io.github.mangi.eta.ui.components.EtaButtonSize
import top.yukonga.miuix.kmp.basic.Icon
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import io.github.mangi.eta.ui.components.EtaButton
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.ListEmptyState
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun CommonTasksScreen(store: CommonTasksStore, onBack: () -> Unit, onRun: (CommonTask) -> Unit, embedded: Boolean = false) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var editRevision by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var instruction by rememberSaveable { mutableStateOf("") }
    var menuId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val editable = !store.busy && !store.loadFailed
    val listState = rememberLazyListState()
    val collapsed by remember { derivedStateOf {
        listState.isScrollInProgress || listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
    } }

    fun edit(task: CommonTask?, copy: Boolean = false) {
        editId = if (copy) null else task?.id
        editRevision = if (copy) null else task?.revision
        name = if (copy && task != null) resources.getString(R.string.common_tasks_copy_name, task.name)
            .take(CommonTaskRepository.MAX_NAME_LENGTH) else task?.name.orEmpty()
        instruction = task?.instruction.orEmpty()
        editorOpen = true
        menuId = null
    }

    LaunchedEffect(store.error) {
        if (store.error) {
            Toast.makeText(context, if (store.conflict) R.string.common_tasks_conflict else R.string.common_tasks_save_failed,
                Toast.LENGTH_LONG).show()
            store.dismissError()
        }
    }

    val taskContent: LazyListScope.() -> Unit = {
        item(key = "description") {
            Text(stringResource(R.string.common_tasks_description),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = EtaTheme.type.subhead, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        if (store.loadFailed) {
            item(key = "error") {
                ListEmptyState(title = stringResource(R.string.common_tasks_load_failed),
                    summary = stringResource(R.string.common_tasks_load_preserved), icon = Icons.Rounded.BookmarkBorder,
                    action = { EtaButton(stringResource(R.string.common_tasks_retry), store::refresh, enabled = !store.busy) })
            }
        } else if (store.tasks.isEmpty()) {
            item(key = "empty") {
                ListEmptyState(title = stringResource(if (store.busy) R.string.common_tasks_loading else R.string.common_tasks_empty),
                    summary = stringResource(R.string.common_tasks_empty_hint), icon = Icons.Rounded.BookmarkBorder)
            }
        }
        items(store.tasks, key = { it.id }) { task ->
            EtaCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(task.name, style = EtaTheme.type.headline)
                    Text(task.instruction, style = EtaTheme.type.subhead,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EtaButton(stringResource(R.string.common_tasks_run), { onRun(task) },
                            enabled = editable, kind = EtaButtonKind.Tonal, size = EtaButtonSize.Small)
                        Spacer(Modifier.weight(1f))
                        EtaIconButton({ menuId = task.id }, enabled = editable) {
                            Icon(Icons.Rounded.MoreHoriz, stringResource(R.string.common_tasks_manage))
                        }
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

    val createButton: @Composable () -> Unit = {
        val createLabel = stringResource(R.string.common_tasks_create)
        Box(Modifier.fillMaxSize()) {
            val enabled = editable && store.tasks.size < CommonTaskRepository.MAX_TASKS
            Row(Modifier.align(Alignment.BottomEnd).padding(16.dp)
                .etaElevation(EtaElevation.E2, EtaTheme.shapes.radiusFull)
                .clip(EtaTheme.shapes.radiusFull)
                .background(EtaTheme.colors.accent.copy(alpha = if (enabled) 1f else .38f))
                .clickable(enabled = enabled, role = Role.Button) { edit(null) }
                .semantics { contentDescription = createLabel }
                .heightIn(min = 52.dp).widthIn(min = 52.dp)
                .padding(horizontal = if (collapsed) 14.dp else 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Add, null, Modifier.size(24.dp), tint = EtaTheme.colors.onAccent)
                if (!collapsed) Text(createLabel, style = EtaTheme.type.rowTitle, color = EtaTheme.colors.onAccent)
            }
        }
    }
    if (embedded) Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 96.dp), content = taskContent)
        createButton()
    } else MiuixScaffoldPage(title = stringResource(R.string.common_tasks_title), onBack = onBack, listState = listState,
        overlay = createButton, content = { taskContent(); item { Spacer(Modifier.height(96.dp)) } })

    if (editorOpen) {
        EtaSheet(show = true, title = stringResource(if (editId == null) R.string.common_tasks_create else R.string.common_tasks_edit),
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
                        store.save(editId, name, instruction, editRevision) { saved ->
                            editorOpen = false
                            // Shortcut metadata failures do not undo the saved task.
                            runCatching { CommonTaskShortcuts.update(context, saved) }
                        }
                    })
            }
        }
    }

    store.tasks.firstOrNull { it.id == menuId }?.let { task ->
        EtaSheet(show = true, title = task.name, onDismissRequest = { menuId = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EtaButton(stringResource(R.string.common_tasks_edit), { edit(task) }, Modifier.fillMaxWidth())
                EtaButton(stringResource(R.string.common_tasks_copy), { edit(task, copy = true) }, Modifier.fillMaxWidth(),
                    enabled = store.tasks.size < CommonTaskRepository.MAX_TASKS)
                EtaButton(stringResource(R.string.common_tasks_pin), {
                    menuId = null
                    val accepted = runCatching { CommonTaskShortcuts.requestPin(context, task) }.getOrDefault(false)
                    Toast.makeText(context, if (accepted) R.string.common_tasks_pin_requested else R.string.common_tasks_pin_unavailable,
                        Toast.LENGTH_LONG).show()
                }, Modifier.fillMaxWidth())
                EtaButton(stringResource(R.string.common_tasks_delete), { menuId = null; deleteId = task.id }, Modifier.fillMaxWidth())
            }
        }
    }
    store.tasks.firstOrNull { it.id == deleteId }?.let { task ->
        EtaWindowDialog(show = true, title = stringResource(R.string.common_tasks_delete),
            summary = stringResource(R.string.common_tasks_delete_confirm, task.name),
            onDismissRequest = { if (!store.busy) deleteId = null }) {
            MiuixDialogActions(confirmText = stringResource(R.string.common_tasks_delete), destructive = true,
                onCancel = { deleteId = null }, cancelEnabled = !store.busy, confirmEnabled = editable,
                onConfirm = { store.delete(task.id, task.revision) { deleteId = null; runCatching { CommonTaskShortcuts.disable(context, task.id) } } })
        }
    }
}
