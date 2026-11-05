package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.layout.rememberIsWideScreen
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text

/** Existing nested-scroll state owns collapse and fling; scrolling only invalidates layout/drawing. */
@Composable
fun AdaptiveTopAppBar(
    title: String, modifier: Modifier = Modifier, color: Color = EtaTheme.colors.canvas,
    scrollBehavior: ScrollBehavior? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    compact: Boolean = false,
) {
    val pinned = compact || rememberIsWideScreen()
    val wasPinned = remember(scrollBehavior) { booleanArrayOf(false) }
    val state = scrollBehavior?.state
    val collapsed by remember(state, pinned) {
        derivedStateOf { pinned || (state?.collapsedFraction ?: 0f) >= 1f / 3f }
    }
    val outline = EtaTheme.colors.outline
    Layout(
        modifier = modifier.fillMaxWidth().background(color)
            .drawWithContent {
                drawContent()
                if (state == null || state.contentOffset < 0f || state.heightOffset < 0f)
                    drawLine(outline, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx())
            }
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
            .clipToBounds().pointerInput(Unit) { detectTapGestures { /* Preserve top-bar tap consumption. */ } },
        content = {
            Box(Modifier.padding(start = 8.dp), contentAlignment = Alignment.Center) { navigationIcon() }
            Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
            Box {
                if (collapsed) Text(title, style = EtaTheme.type.topBarTitle, color = EtaTheme.colors.textPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp)
                .then(if (collapsed) Modifier.clearAndSetSemantics {} else Modifier)) {
                if (!pinned) Text(title, style = EtaTheme.type.title1, color = EtaTheme.colors.textPrimary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            }
            Box(Modifier.fillMaxWidth()) { bottomContent() }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val nav = measurables[0].measure(loose)
        val actionsPlaceable = measurables[1].measure(loose.copy(maxWidth = (constraints.maxWidth * 0.45f).toInt()))
        val small = measurables[2].measure(loose.copy(maxWidth = (constraints.maxWidth - nav.width - actionsPlaceable.width - 16.dp.roundToPx()).coerceAtLeast(0)))
        val large = measurables[3].measure(loose)
        val bottom = measurables[4].measure(loose)
        val expansion = if (pinned) 0 else large.height
        state?.let {
            val limit = -expansion.toFloat()
            if (it.heightOffsetLimit != limit) {
                it.heightOffsetLimit = limit
                if (!pinned && wasPinned[0]) it.heightOffset = if (it.contentOffset < 0f) limit else 0f
            }
            if (pinned) it.heightOffset = 0f
        }
        wasPinned[0] = pinned
        val offset = if (pinned) 0 else (state?.heightOffset ?: 0f).toInt().coerceIn(-expansion, 0)
        val toolbarHeight = maxOf(56.dp.roundToPx(), nav.height, actionsPlaceable.height, small.height)
        val titleHeight = expansion + offset
        layout(constraints.maxWidth, toolbarHeight + titleHeight + bottom.height) {
            nav.placeRelative(0, (toolbarHeight - nav.height) / 2)
            actionsPlaceable.placeRelative(constraints.maxWidth - actionsPlaceable.width, (toolbarHeight - actionsPlaceable.height) / 2)
            val titleX = ((constraints.maxWidth - small.width) / 2).coerceIn(nav.width, (constraints.maxWidth - actionsPlaceable.width - small.width).coerceAtLeast(nav.width))
            small.placeRelative(titleX, (toolbarHeight - small.height) / 2)
            if (!pinned) large.placeRelativeWithLayer(0, toolbarHeight + offset) {
                alpha = 1f - ((state?.collapsedFraction ?: 0f) * 3f).coerceIn(0f, 1f)
            }
            bottom.placeRelative(0, toolbarHeight + titleHeight)
        }
    }
}
