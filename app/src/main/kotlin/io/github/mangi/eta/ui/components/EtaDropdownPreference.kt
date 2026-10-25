package io.github.mangi.eta.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.popup.WindowDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun EtaDropdownPreference(
    title: String,
    items: List<DropdownItem>,
    selectedIndex: Int,
    startAction: (@Composable () -> Unit)? = null,
    summary: String? = null,
    modifier: Modifier = Modifier,
    bottomAction: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    useWindow: Boolean = true,
    onSelectedIndexChange: (Int) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    val available = enabled && items.isNotEmpty()
    LaunchedEffect(available) {
        if (!available) { expanded = false; holding = false }
    }
    val haptics = LocalHapticFeedback.current
    val color = if (available) MiuixTheme.colorScheme.onSurfaceVariantActions
        else MiuixTheme.colorScheme.disabledOnSurface
    val entry = DropdownEntry(items.mapIndexed { index, item ->
        item.copy(selected = index == selectedIndex, onClick = {
            onSelectedIndexChange(index)
            item.onClick?.invoke()
        })
    })
    EtaPreferenceRow(
        title = title,
        summary = summary,
        modifier = modifier,
        bottomAction = bottomAction,
        startAction = startAction,
        enabled = available,
        holdDownState = holding && available,
        interaction = Modifier.clickable(enabled = available, role = Role.DropdownList) {
            expanded = !expanded
            if (expanded) {
                holding = true
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            }
        },
    ) {
        items.getOrNull(selectedIndex)?.text?.let { value ->
            Text(
                text = value,
                modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                style = MiuixTheme.textStyles.body2,
                color = color,
                textAlign = TextAlign.End,
            )
        }
        Row {
            DropdownArrowEndAction(actionColor = color)
            if (useWindow) {
                WindowDropdownPopup(
                    entry = entry,
                    show = expanded && available,
                    onDismiss = { expanded = false },
                    onDismissFinished = { holding = false },
                    maxHeight = null,
                    dropdownColors = DropdownDefaults.dropdownColors(),
                )
            } else {
                OverlayDropdownPopup(
                    entry = entry,
                    show = expanded && available,
                    onDismiss = { expanded = false },
                    onDismissFinished = { holding = false },
                    maxHeight = null,
                    dropdownColors = DropdownDefaults.dropdownColors(),
                    renderInRootScaffold = true,
                )
            }
        }
    }
}

@Composable
internal fun EtaWindowSpinnerPreference(
    title: String,
    items: List<DropdownItem>,
    selectedIndex: Int,
    summary: String? = null,
    startAction: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onSelectedIndexChange: (Int) -> Unit,
) = EtaDropdownPreference(
    title = title, items = items, selectedIndex = selectedIndex, summary = summary,
    startAction = startAction, enabled = enabled, useWindow = true,
    onSelectedIndexChange = onSelectedIndexChange,
)

@Composable
internal fun EtaOverlayDropdownPreference(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    summary: String? = null,
    startAction: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onSelectedIndexChange: (Int) -> Unit,
) = EtaDropdownPreference(
    title = title, items = items.map { DropdownItem(text = it) }, selectedIndex = selectedIndex,
    summary = summary, startAction = startAction, enabled = enabled, useWindow = false,
    onSelectedIndexChange = onSelectedIndexChange,
)
