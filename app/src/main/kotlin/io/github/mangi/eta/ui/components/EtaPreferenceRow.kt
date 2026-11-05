package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun EtaArrowPreference(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    startAction: (@Composable () -> Unit)? = null,
    endActions: @Composable RowScope.() -> Unit = {},
    bottomAction: (@Composable () -> Unit)? = null,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    enabled: Boolean = true,
    holdDownState: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    EtaPreference(
        title = title, summary = summary, modifier = modifier,
        startAction = startAction, bottomAction = bottomAction,
        titleColor = titleColor, enabled = enabled,
        holdDownState = holdDownState, onClick = onClick,
        endActions = {
            Row(Modifier.weight(1f, fill = false), content = endActions)
            Spacer(Modifier.width(8.dp))
            val direction = LocalLayoutDirection.current
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp).graphicsLayer {
                    scaleX = if (direction == LayoutDirection.Rtl) -1f else 1f
                },
                tint = if (enabled) EtaTheme.colors.textTertiary
                    else MiuixTheme.colorScheme.disabledOnSurface,
            )
        },
    )
}

@Composable
internal fun EtaPreference(
    title: String? = null,
    modifier: Modifier = Modifier,
    summary: String? = null,
    startAction: (@Composable () -> Unit)? = null,
    endActions: (@Composable RowScope.() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    enabled: Boolean = true,
    holdDownState: Boolean = false,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    role: Role? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    EtaPreferenceRow(
        title = title, summary = summary, modifier = modifier,
        startAction = startAction, endActions = endActions, bottomAction = bottomAction,
        titleColor = titleColor, summaryColor = summaryColor, titleContent = content,
        enabled = enabled, holdDownState = holdDownState,
        interaction = if (onClick != null) {
            Modifier.clickable(enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)
        } else Modifier,
    )
}

@Composable
internal fun EtaPreferenceRow(
    title: String?,
    modifier: Modifier = Modifier,
    interaction: Modifier = Modifier,
    startAction: (@Composable () -> Unit)? = null,
    summary: String? = null,
    enabled: Boolean = true,
    holdDownState: Boolean = false,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    bottomAction: (@Composable () -> Unit)? = null,
    titleContent: (@Composable ColumnScope.() -> Unit)? = null,
    endActions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth()
            .background(if (holdDownState) EtaTheme.colors.pressed else Color.Transparent)
            .then(interaction),
    ) {
        Layout(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = if (summary != null) 64.dp else EtaPreferenceDefaults.RowMinHeight)
                .padding(horizontal = EtaPreferenceDefaults.SidePadding, vertical = 12.dp),
            content = {
                if (startAction != null) Box(contentAlignment = Alignment.Center) { startAction() }
                Column {
                    if (titleContent != null) {
                        titleContent()
                    } else {
                        if (title != null) Text(
                            text = title,
                            style = EtaTheme.type.rowTitle,
                            fontWeight = FontWeight.Medium,
                            color = if (enabled) titleColor.color else titleColor.disabledColor,
                        )
                        if (summary != null) Text(
                            text = summary,
                            style = EtaTheme.type.subhead,
                            color = if (enabled) summaryColor.color else summaryColor.disabledColor,
                        )
                    }
                }
                if (endActions != null) Row(verticalAlignment = Alignment.CenterVertically, content = endActions)
            },
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            var index = 0
            val leading = if (startAction != null) measurables[index++].measure(loose) else null
            val text = measurables[index++]
            val leadingCell = if (leading != null) maxOf(leading.width, EtaPreferenceDefaults.IconSize.roundToPx()) else 0
            val leadingWidth = leadingCell + if (leading != null) EtaPreferenceDefaults.IconTextGap.roundToPx() else 0
            val available = (constraints.maxWidth - leadingWidth).coerceAtLeast(0)
            val actionGap = if (endActions != null) 12.dp.roundToPx().coerceAtMost(available) else 0
            val actionWidth = ((available - actionGap) * 0.45f).toInt()
            val actions = if (endActions != null) measurables[index].measure(loose.copy(maxWidth = actionWidth)) else null
            val titleBlock = text.measure(loose.copy(maxWidth = (available - actionGap - (actions?.width ?: 0)).coerceAtLeast(0)))
            val height = maxOf(leading?.height ?: 0, titleBlock.height, actions?.height ?: 0)
                .coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(constraints.maxWidth, height) {
                leading?.placeRelative((leadingCell - leading.width) / 2, (height - leading.height) / 2)
                titleBlock.placeRelative(leadingWidth, (height - titleBlock.height) / 2)
                actions?.placeRelative(constraints.maxWidth - actions.width, (height - actions.height) / 2)
            }
        }
        bottomAction?.let { action ->
            Column(Modifier.fillMaxWidth().padding(horizontal = EtaPreferenceDefaults.SidePadding).padding(bottom = 12.dp)) {
                action()
            }
        }
    }
}
