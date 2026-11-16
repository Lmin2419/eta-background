package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun EtaSearchField(
    value: String, onValueChange: (String) -> Unit, placeholder: String,
    modifier: Modifier = Modifier, enabled: Boolean = true, onSearch: (String) -> Unit = {},
    /** 放在液态玻璃胶囊里时不画自己的底色和描边。 */
    bare: Boolean = false,
) {
    val colors = EtaTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = EtaTheme.shapes.radiusFull
    val focusBorder by animateColorAsState(if (focused) colors.accent else androidx.compose.ui.graphics.Color.Transparent,
        tween(EtaTheme.motion.short), label = "searchFocus")
    BasicTextField(value = value, onValueChange = onValueChange, enabled = enabled,
        singleLine = true, interactionSource = source,
        textStyle = EtaTheme.type.subhead.copy(color = colors.textPrimary), cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(value) }),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = placeholder }
            .then(if (bare) Modifier else Modifier.background(colors.surfaceSunken, shape).border(1.5.dp, focusBorder, shape)),
        decorationBox = { inner ->
            Row(Modifier.heightIn(min = 44.dp).padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Search, null, Modifier.size(20.dp), tint = colors.textTertiary)
                Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    if (value.isEmpty()) Text(placeholder, style = EtaTheme.type.subhead, color = colors.textTertiary)
                    inner()
                }
                if (value.isNotEmpty()) EtaIconButton(onClick = { onValueChange("") }, modifier = Modifier.height(40.dp), enabled = enabled) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.eta_clear_search), Modifier.size(20.dp), tint = colors.textTertiary)
                }
            }
        },
    )
}
