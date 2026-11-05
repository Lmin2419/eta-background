package io.github.mangi.eta.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.ui.components.EtaMonospace

/** Typography tokens are opt-in; stage 1 must not change existing page measurements. */
object EtaTypography {
    private fun style(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = FontFamily.Default, fontSize = size.sp, lineHeight = height.sp, fontWeight = weight)

    val display = style(30, 38, FontWeight.SemiBold)
    val title1 = style(26, 34, FontWeight.SemiBold)
    val title2 = style(20, 28, FontWeight.SemiBold)
    val topBarTitle = style(18, 24, FontWeight.SemiBold)
    val headline = style(17, 24, FontWeight.SemiBold)
    val body = style(16, 26)
    val rowTitle = style(16, 22, FontWeight.Medium)
    val subhead = style(14, 20)
    val footnote = style(13, 18)
    val caption = style(12, 16, FontWeight.Medium).copy(letterSpacing = 0.2.sp)
    val mono = style(13, 20).copy(fontFamily = EtaMonospace)
    val inlineCode = mono.copy(fontSize = 14.sp)
    val markdownH1 = style(22, 30, FontWeight.SemiBold)
    val markdownH2 = style(19, 28, FontWeight.SemiBold)
    val markdownH3 = style(17, 26, FontWeight.SemiBold)
    fun tabular(style: TextStyle) = style.copy(fontFeatureSettings = "tnum")
}
