package io.github.mangi.eta.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.*
import io.github.mangi.eta.ui.theme.EtaTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

internal data class PaneWorkspaceActions(
    val onNew: () -> Unit = {}, val onTasks: () -> Unit = {}, val onTerminal: () -> Unit = {},
    val onBrowser: () -> Unit = {}, val onKimi: (() -> Unit)? = null,
    val kimiLabel: String = "Kimi Code", val canStopKimi: Boolean = false,
    val onStopKimi: () -> Unit = {}, val onRefreshKimi: () -> Unit = {},
    val runningCount: Int = 0, val permissionsPending: Int = 0,
)
internal val LocalPaneWorkspace = staticCompositionLocalOf { PaneWorkspaceActions() }

@Composable
internal fun conversationPaneContainerColor(): Color = EtaTheme.colors.surface

@Composable
internal fun ConversationPanePanel(
    state: ConversationPaneUiState, width: Dp, listState: LazyListState,
    onSearchChange: (String) -> Unit, onConversationSelected: (String) -> Unit,
    onConversationRename: (ConversationSummaryUi) -> Unit, onConversationExport: (ConversationSummaryUi) -> Unit,
    onConversationDelete: (ConversationSummaryUi) -> Unit, onOpenSettings: () -> Unit,
    onOpenModelProviders: () -> Unit, onOpenTools: () -> Unit, onOpenSkills: () -> Unit,
    onOpenCharacters: () -> Unit, onOpenPermissions: () -> Unit, modifier: Modifier = Modifier,
) {
    val workspace = LocalPaneWorkspace.current
    val today = LocalDate.now()
    val groups = remember(state.conversations, today) { state.conversations.groupBy { conversationSection(it, today) }.toSortedMap() }
    Column(modifier.width(width).fillMaxHeight().background(EtaTheme.colors.surface)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            EtaSearchField(state.searchQuery, onSearchChange, stringResource(R.string.conversation_search_hint), Modifier.weight(1f))
            EtaIconButton(workspace.onNew) { Icon(Icons.Rounded.AddComment, stringResource(R.string.action_new_conversation), Modifier.size(24.dp)) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            val entryWidth = (width - 32.dp) / 4
            WorkspaceEntry(Icons.Rounded.TaskAlt, stringResource(R.string.eta_tasks), EtaTheme.colors.tileIndigo, entryWidth, workspace.onTasks, count = workspace.runningCount)
            WorkspaceEntry(Icons.Rounded.PeopleAlt, stringResource(R.string.eta_characters), EtaTheme.colors.tilePink, entryWidth, onOpenCharacters)
            WorkspaceEntry(Icons.Rounded.Terminal, stringResource(R.string.route_terminal), EtaTheme.colors.tileSlate, entryWidth, workspace.onTerminal)
            WorkspaceEntry(Icons.Rounded.Language, stringResource(R.string.eta_browser), EtaTheme.colors.tileTeal, entryWidth, workspace.onBrowser)
            workspace.onKimi?.let { launch -> WorkspaceEntry(Icons.Rounded.Code, workspace.kimiLabel, EtaTheme.colors.tileSlate, entryWidth, launch,
                onLongClick = if (workspace.canStopKimi) workspace.onStopKimi else null) }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.conversations.isEmpty()) item {
                ListEmptyState(stringResource(if (state.searchQuery.isBlank()) R.string.conversation_empty else R.string.conversation_no_results), icon = Icons.Rounded.Search)
            }
            groups.forEach { (section, conversations) ->
                item(key = "section:$section") {
                    Text(stringResource(when (section) { 0 -> R.string.conversation_section_pinned; 1 -> R.string.conversation_section_today;
                        2 -> R.string.eta_yesterday; 3 -> R.string.eta_last_week; else -> R.string.eta_earlier }),
                        Modifier.padding(top = 12.dp, bottom = 6.dp), style = EtaTheme.type.caption, color = EtaTheme.colors.textTertiary)
                }
                items(conversations, key = { it.id }) { conversation ->
                    ConversationTextRow(conversation, state.selectedConversationId == conversation.id, state.searchQuery,
                        { onConversationSelected(conversation.id) }, { onConversationRename(conversation) },
                        { onConversationExport(conversation) }, { onConversationDelete(conversation) })
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(EtaTheme.colors.outline))
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(EtaTheme.shapes.radiusMd).clickable(onClick = onOpenSettings).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Icon(Icons.Rounded.Settings, null, Modifier.size(20.dp), tint = EtaTheme.colors.textSecondary)
                    if (workspace.permissionsPending > 0) Box(Modifier.align(Alignment.TopEnd).size(6.dp).background(EtaTheme.colors.attention, EtaTheme.shapes.radiusFull))
                }
                Text(stringResource(R.string.route_settings), Modifier.padding(start = 8.dp), style = EtaTheme.type.subhead)
            }
            Row(Modifier.weight(1f).clip(EtaTheme.shapes.radiusMd).clickable(onClick = onOpenPermissions).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VerifiedUser, stringResource(R.string.route_permissions), Modifier.size(20.dp), tint = EtaTheme.colors.textSecondary)
                Text(if (workspace.permissionsPending > 0) stringResource(R.string.eta_permissions_pending, workspace.permissionsPending) else stringResource(R.string.eta_permissions_ready),
                    Modifier.padding(start = 8.dp), style = EtaTheme.type.subhead, color = EtaTheme.colors.textSecondary)
            }
        }
    }
}

internal fun conversationSection(conversation: ConversationSummaryUi, today: LocalDate): Int {
    if (conversation.isPinned) return 0
    if (conversation.updatedAtMillis <= 0) return 1
    val date = Instant.ofEpochMilli(conversation.updatedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return when { !date.isBefore(today) -> 1; date == today.minusDays(1) -> 2; !date.isBefore(today.minusDays(7)) -> 3; else -> 4 }
}

@Composable
private fun WorkspaceEntry(icon: ImageVector, label: String, tint: Color, width: Dp, onClick: () -> Unit,
    count: Int = 0, onLongClick: (() -> Unit)? = null) {
    val haptic = LocalHapticFeedback.current
    var showStop by remember { mutableStateOf(false) }
    Column(Modifier.width(width).clip(EtaTheme.shapes.radiusMd).combinedClickable(onClick = onClick,
        onLongClick = onLongClick?.let { { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showStop = true } }).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            EtaIconTile(icon, tint = tint, size = 40.dp)
            if (count > 0) Text(count.toString(), Modifier.align(Alignment.TopEnd).background(EtaTheme.colors.accent, EtaTheme.shapes.radiusFull).padding(horizontal = 4.dp),
                style = EtaTheme.type.caption, color = EtaTheme.colors.onAccent)
        }
        Text(label, style = EtaTheme.type.caption, color = EtaTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    EtaSheet(showStop, title = label, onDismissRequest = { showStop = false }) {
        EtaButton(stringResource(R.string.action_stop), { showStop = false; onLongClick?.invoke() }, kind = EtaButtonKind.Destructive, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ConversationTextRow(conversation: ConversationSummaryUi, selected: Boolean, query: String,
    onClick: () -> Unit, onRename: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    var menu by remember(conversation.id) { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val colors = EtaTheme.colors
    val title = conversation.title.ifBlank { conversation.preview }
    val markedTitle = remember(title, query, colors.accent) { buildAnnotatedString {
        append(title)
        if (query.isNotBlank()) {
            var index = title.indexOf(query, ignoreCase = true)
            while (index >= 0) { addStyle(SpanStyle(color = colors.accent), index, index + query.length); index = title.indexOf(query, index + query.length, ignoreCase = true) }
        }
    } }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(EtaTheme.shapes.radiusMd)
        .background(if (selected) colors.accentSoft else Color.Transparent)
        .combinedClickable(onClick = onClick, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true })
        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (conversation.isActiveRun) StatusRing(TaskVisualStatus.Running, size = 12.dp)
        Text(markedTitle, Modifier.weight(1f), style = EtaTheme.type.rowTitle.copy(fontSize = 15.sp), color = if (selected) colors.onAccentSoft else colors.textPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(conversation.timeLabel, style = EtaTheme.type.caption, color = colors.textTertiary, maxLines = 1)
    }
    EtaSheet(menu, title = title, onDismissRequest = { menu = false }) {
        Column {
            EtaArrowPreference(stringResource(R.string.action_rename), onClick = { menu = false; onRename() })
            EtaArrowPreference(stringResource(R.string.action_export), onClick = { menu = false; onExport() })
            EtaArrowPreference(stringResource(R.string.action_delete), onClick = { menu = false; onDelete() })
        }
    }
}
