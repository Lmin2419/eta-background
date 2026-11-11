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

    // 阶段 11：问候语、页面标题、Markdown 标题用系统衬线体（中文为系统宋体，不打包字体），
    // 正文与控件仍是无衬线；衬线体本身笔画重，字重降到 Medium。
    private fun serif(size: Int, height: Int) =
        TextStyle(fontFamily = FontFamily.Serif, fontSize = size.sp, lineHeight = height.sp, fontWeight = FontWeight.Medium)

    val display = serif(30, 38)
    val title1 = serif(26, 34)
    val title2 = serif(20, 28)
    val topBarTitle = serif(18, 24)
    val headline = style(17, 24, FontWeight.SemiBold)
    val body = style(16, 26)
    val rowTitle = style(16, 22, FontWeight.Medium)
    val subhead = style(14, 20)
    val footnote = style(13, 18)
    val caption = style(12, 16, FontWeight.Medium).copy(letterSpacing = 0.2.sp)
    val mono = style(13, 20).copy(fontFamily = EtaMonospace)
    val inlineCode = mono.copy(fontSize = 14.sp)
    val markdownH1 = serif(22, 30)
    val markdownH2 = serif(19, 28)
    val markdownH3 = style(17, 26, FontWeight.SemiBold)
    fun tabular(style: TextStyle) = style.copy(fontFeatureSettings = "tnum")
}
