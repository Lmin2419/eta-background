package io.github.mangi.eta.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object EtaSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 40.dp
    val jumbo = 56.dp
    val expandedBreakpoint = 600.dp
    fun pageMargin(width: Dp) = if (width >= expandedBreakpoint) xxl else lg
    val conversationMaxWidth = 720.dp
    val rowMinHeight = 56.dp
    val rowWithSummaryMinHeight = 64.dp
    val rowWithTwoLineSummaryMinHeight = 72.dp
    val iconTextGap = md
    val groupGap = xxl
    val groupTitleGap = sm
    val minTouchTarget = 48.dp
    val iconButtonVisualSize = huge
    val markdownParagraphGap = md
    val markdownListGap = 6.dp
    val inlineCodePadding = xs
}
