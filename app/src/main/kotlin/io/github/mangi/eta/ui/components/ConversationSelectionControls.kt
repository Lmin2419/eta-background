package io.github.mangi.eta.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun ConversationSelectionHeader(
    count: Int,
    allSelected: Boolean,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
) {
    val colors = EtaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(colors.surface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EtaIconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.action_cancel),
                tint = colors.textPrimary,
            )
        }
        Text(
            text = stringResource(R.string.eta_selection_count, count),
            style = EtaTheme.type.rowTitle,
            color = colors.textPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        EtaButton(
            text = stringResource(
                if (allSelected) R.string.eta_deselect_all else R.string.eta_select_all,
            ),
            onClick = onSelectAll,
            modifier = Modifier.weight(0.9f),
            kind = EtaButtonKind.Ghost,
            size = EtaButtonSize.Small,
        )
    }
}

@Composable
internal fun ConversationSelectionBar(
    archived: Boolean,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onMore: () -> Unit,
) {
    val colors = EtaTheme.colors
    val outlineColor = colors.outline
    val dividerStroke = with(androidx.compose.ui.platform.LocalDensity.current) { 1.dp.toPx() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(colors.surface)
            .drawBehind {
                drawLine(
                    color = outlineColor,
                    start = Offset(0f, dividerStroke / 2f),
                    end = Offset(size.width, dividerStroke / 2f),
                    strokeWidth = dividerStroke,
                )
            }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EtaButton(
            text = stringResource(
                if (archived) R.string.eta_unarchive else R.string.eta_archive,
            ),
            onClick = onArchive,
            modifier = Modifier.weight(1f),
            kind = EtaButtonKind.Tonal,
            size = EtaButtonSize.Small, minHeight = 48.dp,
            insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 10.dp),
        )
        EtaButton(
            text = stringResource(R.string.action_delete),
            onClick = onDelete,
            modifier = Modifier.weight(1f),
            kind = EtaButtonKind.Destructive,
            size = EtaButtonSize.Small, minHeight = 48.dp,
            insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 10.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .clip(EtaTheme.shapes.radiusMd)
                .background(colors.accentSoft)
                .clickable(onClick = onMore),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreHoriz,
                contentDescription = stringResource(R.string.eta_more),
                tint = colors.accent,
            )
        }
    }
}

@Composable
internal fun ConversationSelectionMarker(selected: Boolean) {
    val colors = EtaTheme.colors
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.9f,
        animationSpec = if (EtaTheme.motion.reduceMotion) androidx.compose.animation.core.snap() else tween(durationMillis = EtaTheme.motion.short, easing = EtaTheme.motion.standard),
        label = "conversationSelectionMarkerScale",
    )
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) colors.accent else Color.Transparent)
            .then(
                if (selected) Modifier
                else Modifier.border(1.5.dp, colors.outlineStrong, CircleShape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(14.dp).graphicsLayer { scaleX = scale; scaleY = scale },
            )
        }
    }
}
