package io.github.mangi.eta.ui.screens.characters

import io.github.mangi.eta.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.app.CharacterLibraryStore
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaCard
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.EtaButton
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import io.github.mangi.eta.ui.components.EtaIconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun CharacterEditorScreen(
    id: String?,
    store: CharacterLibraryStore,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    var worldbookExpanded by rememberSaveable { mutableStateOf(false) }
    var worldbookEntry by rememberSaveable { mutableStateOf<Int?>(null) }
    val card = store.draft
    MiuixScaffoldPage(
        title = stringResource(if (id == null) R.string.character_action_create else R.string.character_action_edit),
        onBack = onBack,
        modifier = Modifier.imePadding(),
        actions = {
            EtaIconButton(
                onClick = { store.saveEditor(onSaved) },
                enabled = !store.busy && store.draftName.isNotBlank(),
            ) {
                Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.character_action_save))
            }
        },
    ) {
        if (card == null) {
            item { CharacterPageMessage(stringResource(if (store.busy) R.string.character_editor_loading else R.string.character_editor_load_failed)) }
            return@MiuixScaffoldPage
        }
        item(key = "name") { CharacterTextField(stringResource(R.string.character_field_name), store.draftName, store::updateName, !store.busy, singleLine = true) }
        item(key = "description-title") { EtaPreferenceGroupTitle(stringResource(R.string.character_section_description)) }
        item(key = "description") { CharacterTextField(stringResource(R.string.character_field_description_label), card.description, { value -> store.updateDraft { it.withEdits(description = value) } }, !store.busy, minLines = 5) }
        item(key = "greeting-title") { EtaPreferenceGroupTitle(stringResource(R.string.character_section_greetings)) }
        item(key = "greeting") { CharacterTextField(stringResource(R.string.character_greeting_default), card.firstMessage, { value -> store.updateDraft { it.withEdits(firstMessage = value) } }, !store.busy) }
        itemsIndexed(card.alternateGreetings, key = { index, _ -> "alternate-$index" }) { index, value ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = value,
                    onValueChange = { updated ->
                        store.updateDraft { it.withEdits(alternateGreetings = it.alternateGreetings.toMutableList().apply { this[index] = updated }) }
                    },
                    label = stringResource(R.string.character_greeting_numbered, index + 2),
                    enabled = !store.busy,
                    minLines = 2,
                    maxLines = 14,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
                EtaIconButton(
                    onClick = {
                        store.updateDraft { it.withEdits(alternateGreetings = it.alternateGreetings.filterIndexed { i, _ -> i != index }) }
                    },
                    enabled = !store.busy,
                    minWidth = 36.dp,
                    minHeight = 36.dp,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.character_greeting_remove),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        item(key = "add-greeting") {
            EtaButton(
                stringResource(R.string.character_greeting_add_alternate),
                onClick = { store.updateDraft { it.withEdits(alternateGreetings = it.alternateGreetings + "") } },
                enabled = !store.busy,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            )
        }
        item(key = "advanced") {
            EtaPreferenceGroup(modifier = Modifier.padding(horizontal = CharacterCardPadding, vertical = 8.dp)) {
                EtaArrowPreference(
                    title = stringResource(R.string.character_editor_advanced_title),
                    summary = stringResource(R.string.character_editor_advanced_summary),
                    onClick = { advanced = !advanced },
                )
            }
        }
        if (advanced) {
            item(key = "personality") { CharacterTextField(stringResource(R.string.character_field_personality), card.personality, { value -> store.updateDraft { it.withEdits(personality = value) } }, !store.busy) }
            item(key = "scenario") { CharacterTextField(stringResource(R.string.character_field_scenario), card.scenario, { value -> store.updateDraft { it.withEdits(scenario = value) } }, !store.busy) }
            item(key = "examples") { CharacterTextField(stringResource(R.string.character_field_example_messages), card.exampleMessages, { value -> store.updateDraft { it.withEdits(exampleMessages = value) } }, !store.busy, minLines = 4) }
            item(key = "prompts-title") { EtaPreferenceGroupTitle(stringResource(R.string.character_section_prompts)) }
            item(key = "system") { CharacterTextField(stringResource(R.string.character_field_system_prompt), card.systemPrompt, { value -> store.updateDraft { it.withEdits(systemPrompt = value) } }, !store.busy) }
            item(key = "post-history") { CharacterTextField(stringResource(R.string.character_field_post_history_instructions), card.postHistoryInstructions, { value -> store.updateDraft { it.withEdits(postHistoryInstructions = value) } }, !store.busy) }
            item(key = "credits-title") { EtaPreferenceGroupTitle(stringResource(R.string.character_section_credits)) }
            item(key = "creator-notes") { CharacterTextField(stringResource(R.string.character_field_creator_notes), card.creatorNotes, { value -> store.updateDraft { it.withEdits(creatorNotes = value) } }, !store.busy) }
            item(key = "tags") { CharacterTextField(stringResource(R.string.character_field_tags), card.tags.joinToString("\n"), { value -> store.updateDraft { it.withEdits(tags = value.lines()) } }, !store.busy) }
            item(key = "creator") { CharacterTextField(stringResource(R.string.character_field_creator), card.creator, { value -> store.updateDraft { it.withEdits(creator = value) } }, !store.busy, singleLine = true) }
            item(key = "version") { CharacterTextField(stringResource(R.string.character_field_version), card.version, { value -> store.updateDraft { it.withEdits(version = value) } }, !store.busy, singleLine = true) }
            characterWorldbookEditor(
                store = store, expanded = worldbookExpanded, expandedEntry = worldbookEntry,
                onToggleExpanded = { worldbookExpanded = !worldbookExpanded },
                onExpandEntry = { worldbookEntry = it },
            )
            item(key = "compatibility") { CharacterPageMessage(stringResource(R.string.character_editor_compatibility_note)) }
        }
        item(key = "save") {
            EtaButton(
                text = stringResource(if (store.busy) R.string.character_editor_processing else R.string.character_action_save),
                onClick = { store.saveEditor(onSaved) },
                enabled = !store.busy && store.draftName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CharacterCardPadding, vertical = 12.dp),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
internal fun CharacterTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    singleLine: Boolean = false,
    minLines: Int = 2,
) {
    TextField(
        value = value, onValueChange = onChange, label = label, enabled = enabled,
        singleLine = singleLine, minLines = if (singleLine) 1 else minLines,
        maxLines = if (singleLine) 1 else 14,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
internal fun CharacterFieldGroupLabel(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}
