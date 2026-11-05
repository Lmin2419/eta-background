package io.github.mangi.eta.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.theme.*
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun EtaSegmented(
    items: List<String>, selectedIndex: Int, onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
) {
    if (items.isEmpty()) return
    val motion = EtaTheme.motion
    val position = animateFloatAsState(selectedIndex.coerceIn(items.indices).toFloat(),
        if (motion.reduceMotion) tween(motion.medium) else spring(dampingRatio = motion.dampingRatio, stiffness = motion.stiffness),
        label = "segmentPosition")
    val shape = EtaTheme.shapes.radiusFull
    Layout(modifier = modifier.fillMaxWidth().heightIn(min = 36.dp).clip(shape)
        .background(EtaTheme.colors.surfaceSunken).padding(3.dp).selectableGroup(),
        content = {
            Box(Modifier.etaElevation(EtaElevation.E1, shape).background(EtaTheme.colors.surfaceRaised, shape))
            items.forEachIndexed { index, label ->
                Box(Modifier.clip(shape).selectable(selected = index == selectedIndex, enabled = enabled,
                    role = Role.Tab, onClick = { onSelectedIndexChange(index) }).padding(horizontal = 8.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center) {
                    Text(label, style = EtaTheme.type.subhead.copy(fontWeight = FontWeight.Medium),
                        color = (if (index == selectedIndex) EtaTheme.colors.textPrimary else EtaTheme.colors.textSecondary)
                            .copy(alpha = if (enabled) 1f else 0.38f))
                }
            }
        }) { measurables, constraints ->
        val cellWidth = constraints.maxWidth / items.size
        val cells = measurables.drop(1).map { it.measure(constraints.copy(minWidth = cellWidth, maxWidth = cellWidth, minHeight = constraints.minHeight)) }
        val height = cells.maxOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
        val indicator = measurables.first().measure(Constraints.fixed(cellWidth, height))
        layout(constraints.maxWidth, height) {
            indicator.placeRelative((cellWidth * position.value).toInt(), 0)
            cells.forEachIndexed { i, cell -> cell.placeRelative(i * cellWidth, (height - cell.height) / 2) }
        }
    }
}
