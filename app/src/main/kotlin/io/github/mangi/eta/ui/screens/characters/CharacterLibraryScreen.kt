package io.github.mangi.eta.ui.screens.characters

import io.github.mangi.eta.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.roleplay.CharacterProfile
import io.github.mangi.eta.ui.app.CharacterLibraryStore
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaPreferenceColors
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.EtaPreferenceIcon
import io.github.mangi.eta.ui.components.EtaButton
import io.github.mangi.eta.ui.components.ListEmptyState
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.navigation.AppRoute
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import io.github.mangi.eta.ui.components.EtaIconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import io.github.mangi.eta.ui.components.EtaSearchField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val CharacterCardPadding = 16.dp
private val CharacterGridSpacing = 12.dp

@Composable
internal fun CharacterLibraryScreen(
    store: CharacterLibraryStore,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) store.importCard(uri) { onNavigate(AppRoute.CharacterDetail(it)) }
    }
    val importCard = {
        importer.launch(arrayOf("image/png", "application/json", "text/plain", "application/octet-stream"))
    }
    val createCharacter = {
        store.discardEditor()
        onNavigate(AppRoute.CharacterEditor())
    }
    MiuixScaffoldPage(
        title = stringResource(R.string.character_library_title),
        onBack = onBack,
        actions = {
            EtaIconButton(onClick = importCard, enabled = !store.busy) {
                Icon(Icons.Outlined.FileUpload, contentDescription = stringResource(R.string.character_action_import_card))
            }
            EtaIconButton(onClick = createCharacter, enabled = !store.busy) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.character_action_create))
            }
        },
    ) {
        item(key = "search") {
            EtaSearchField(
                value = store.query,
                onValueChange = { store.query = it },
                onSearch = { store.query = it },
                placeholder = stringResource(R.string.character_library_search_hint),
                modifier = Modifier.fillMaxWidth().padding(horizontal = CharacterCardPadding)
                    .padding(top = 4.dp, bottom = 12.dp),
            )
        }
        when {
            store.busy && store.characters.isEmpty() -> {
                item(key = "loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        InfiniteProgressIndicator(size = 28.dp)
                    }
                }
            }
            store.characters.isEmpty() -> {
                item(key = "empty-library") {
                    ListEmptyState(
                        title = stringResource(R.string.character_library_empty_title),
                        summary = stringResource(R.string.character_library_empty_summary),
                        icon = Icons.Outlined.TheaterComedy,
                        action = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                EtaButton(
                                    text = stringResource(R.string.character_action_restore_default),
                                    onClick = { store.restoreDefaultCharacter() },
                                    enabled = !store.busy,
                                )
                                EtaButton(
                                    text = stringResource(R.string.character_action_create),
                                    onClick = createCharacter,
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                )
                            }
                        },
                    )
                }
            }
            store.filteredCharacters.isEmpty() -> {
                item(key = "empty-search") {
                    ListEmptyState(
                        title = stringResource(R.string.character_library_no_results_title),
                        summary = stringResource(R.string.character_library_no_results_summary),
                        icon = Icons.Outlined.SearchOff,
                    )
                }
            }
        }
        // 两列竖版卡片：每行一个 Lazy 项，长列表仍按行组合。未搜索时末尾追加一张新建卡，
        // 既补齐单数时的空位，也让创建与导入在网格中就近可达。
        val showAddTile = store.query.isBlank() && store.characters.isNotEmpty()
        val tiles: List<CharacterProfile?> = store.filteredCharacters + if (showAddTile) listOf(null) else emptyList()
        val rows = tiles.chunked(2)
        items(rows, key = { row -> row.joinToString("|") { it?.id ?: "add" } }) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CharacterCardPadding)
                    .padding(bottom = CharacterGridSpacing),
                horizontalArrangement = Arrangement.spacedBy(CharacterGridSpacing),
            ) {
                row.forEach { profile ->
                    if (profile == null) {
                        CharacterAddTile(
                            enabled = !store.busy,
                            onCreate = createCharacter,
                            onImport = importCard,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        CharacterPortraitCard(
                            profile = profile,
                            onClick = { if (!store.busy) onNavigate(AppRoute.CharacterDetail(profile.id)) },
                            onToggleFavorite = { store.toggleFavorite(profile.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item(key = "persona-title") { EtaPreferenceGroupTitle(stringResource(R.string.character_library_section_mine)) }
        item(key = "persona") {
            EtaPreferenceGroup {
                EtaArrowPreference(
                    title = stringResource(R.string.character_persona_title),
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Face, tint = EtaPreferenceColors.Pink) },
                    summary = stringResource(R.string.character_persona_entry_summary),
                    onClick = { onNavigate(AppRoute.CharacterPersona) },
                )
            }
        }
    }
}

@Composable
internal fun CharacterPageMessage(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}
