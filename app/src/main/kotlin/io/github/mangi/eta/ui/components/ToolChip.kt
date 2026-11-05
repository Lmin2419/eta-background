package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.model.TaskVisualStatus
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun ToolChip(text: String, modifier: Modifier = Modifier, status: TaskVisualStatus? = null) {
    Row(modifier.heightIn(min = 24.dp).background(status?.backgroundColor() ?: EtaTheme.colors.surfaceSunken, EtaTheme.shapes.radiusFull)
        .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (status != null) StatusRing(status, size = 12.dp)
        Text(text, style = EtaTheme.type.caption, color = status?.color() ?: EtaTheme.colors.textSecondary)
    }
}
