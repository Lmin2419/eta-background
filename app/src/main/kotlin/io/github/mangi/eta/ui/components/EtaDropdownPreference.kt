package io.github.mangi.eta.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UnfoldMore
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.Icon
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.itos.coui.menu.couiMenuAnchor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text
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
    val color = if (available) EtaTheme.colors.textSecondary
        else MiuixTheme.colorScheme.disabledOnSurface
    // 与上游设置页一致：选项用 COUI 菜单从这一行展开，当前项打勾。
    val anchor = com.itos.coui.menu.rememberCouiMenuAnchor()
    var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    @Suppress("UNUSED_VARIABLE") val unusedWindowChoice = useWindow
    EtaPreferenceRow(
        title = title,
        summary = summary,
        modifier = modifier
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .couiMenuAnchor(anchor),
        bottomAction = bottomAction,
        startAction = startAction,
        enabled = available,
        holdDownState = holding && available,
        interaction = Modifier.clickable(enabled = available, role = Role.DropdownList) {
            anchor.open {
                expanded = !expanded
                if (expanded) {
                    holding = true
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
            }
        },
    ) {
        items.getOrNull(selectedIndex)?.text?.let { value ->
            Text(
                text = value,
                modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                style = EtaTheme.type.subhead,
                color = color,
                textAlign = TextAlign.End,
            )
        }
        Row {
            Icon(Icons.Outlined.UnfoldMore, null, Modifier.size(20.dp), tint = color)
        }
    }
    EtaContextMenu(
        show = expanded && available,
        anchor = bounds,
        onDismiss = { expanded = false; holding = false },
        alignEnd = true,
        couiAnchor = anchor,
    ) {
        items.forEachIndexed { index, item ->
            EtaMenuItem(
                text = item.text,
                selected = index == selectedIndex,
                summary = item.summary,
                onClick = {
                    expanded = false
                    holding = false
                    onSelectedIndexChange(index)
                    item.onClick?.invoke()
                },
            )
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
