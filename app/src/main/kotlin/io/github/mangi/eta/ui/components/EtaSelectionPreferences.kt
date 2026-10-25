package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.preference.CheckboxLocation

@Composable
internal fun EtaSwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    startAction: (@Composable () -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    EtaPreferenceRow(
        title = title, summary = summary, modifier = modifier,
        startAction = startAction, bottomAction = bottomAction, enabled = enabled,
        interaction = Modifier.toggleable(
            value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange,
        ),
    ) {
        EtaSwitch(
            checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
internal fun EtaRadioButtonPreference(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    startAction: (@Composable () -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
) {
    EtaPreferenceRow(
        title = title, summary = summary, modifier = modifier,
        startAction = startAction, bottomAction = bottomAction, enabled = enabled,
        interaction = Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled, modifier = Modifier.clearAndSetSemantics {})
    }
}

@Composable
internal fun EtaCheckboxPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    checkboxLocation: CheckboxLocation = CheckboxLocation.End,
    startAction: (@Composable () -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
) {
    val checkbox: @Composable () -> Unit = {
        Checkbox(
            state = if (checked) ToggleableState.On else ToggleableState.Off,
            onClick = null, enabled = enabled, modifier = Modifier.clearAndSetSemantics {},
        )
    }
    val leading: (@Composable () -> Unit)? = if (checkboxLocation == CheckboxLocation.Start) {
        { Row { checkbox(); startAction?.let { Spacer(Modifier.width(8.dp)); it() } } }
    } else startAction
    val trailing: (@Composable RowScope.() -> Unit)? = if (checkboxLocation == CheckboxLocation.End) {
        { checkbox() }
    } else null
    EtaPreferenceRow(
        title = title, summary = summary, modifier = modifier,
        startAction = leading, endActions = trailing, bottomAction = bottomAction, enabled = enabled,
        interaction = if (onCheckedChange != null) Modifier.toggleable(
            value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange,
        ) else Modifier,
    )
}
