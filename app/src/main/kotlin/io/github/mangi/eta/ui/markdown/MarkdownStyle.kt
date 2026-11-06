package io.github.mangi.eta.ui.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.ui.theme.EtaTheme

/** 正文与思考共用同一套渲染器，只在字号、颜色与留白上区分层级。 */
internal enum class MarkdownTone { Answer, Thinking }

internal val LocalMarkdownBodyStyle = androidx.compose.runtime.staticCompositionLocalOf<TextStyle?> { null }

/**
 * 一次渲染所需的全部样式。按主题与语气整体记忆，块级组件只读取不再推导，
 * 避免每个段落各自拼一遍 TextStyle。
 */
@Immutable
internal class MarkdownStyle(
    val tone: MarkdownTone,
    val body: TextStyle,
    private val headings: List<TextStyle>,
    val code: TextStyle,
    val table: TextStyle,
    val textColor: Color,
    val secondaryColor: Color,
    val markerColor: Color,
    val dividerColor: Color,
    val codeBackground: Color,
    val tableHeaderBackground: Color,
    val inline: MarkdownInlineStyle,
) {
    fun heading(level: Int): TextStyle = headings[(level - 1).coerceIn(0, headings.lastIndex)]

    /** 引用块内的弱化样式：文字退到次要色，结构与字号不变。 */
    fun muted(): MarkdownStyle = MarkdownStyle(
        tone = tone,
        body = body.copy(color = secondaryColor),
        headings = headings.map { it.copy(color = secondaryColor) },
        code = code,
        table = table.copy(color = secondaryColor),
        textColor = secondaryColor,
        secondaryColor = secondaryColor,
        markerColor = markerColor,
        dividerColor = dividerColor,
        codeBackground = codeBackground,
        tableHeaderBackground = tableHeaderBackground,
        inline = inline,
    )
}

@Composable
internal fun rememberMarkdownStyle(tone: MarkdownTone): MarkdownStyle {
    val colors = EtaTheme.colors
    val type = EtaTheme.type
    val bodyOverride = LocalMarkdownBodyStyle.current
    return remember(tone, colors, type, bodyOverride) {
        val answer = tone == MarkdownTone.Answer
        val textColor = if (answer) colors.textPrimary else colors.textSecondary
        val body = (if (answer) bodyOverride ?: type.body else type.subhead.copy(lineHeight = THINKING_LINE_HEIGHT_SP.sp)).copy(color = textColor)
        MarkdownStyle(
            tone = tone, body = body,
            headings = if (answer) listOf(type.markdownH1, type.markdownH2, type.markdownH3, body.copy(fontWeight = FontWeight.SemiBold))
                .map { it.copy(color = textColor) } else listOf(body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)),
            code = type.mono.copy(color = textColor, fontSize = if (answer) 13.sp else 12.sp, lineHeight = if (answer) 20.sp else 18.sp),
            table = type.footnote.copy(color = textColor), textColor = textColor,
            secondaryColor = colors.textSecondary, markerColor = colors.textTertiary,
            dividerColor = colors.outline, codeBackground = colors.surfaceSunken,
            tableHeaderBackground = colors.surfaceSunken,
            inline = MarkdownInlineStyle(linkColor = colors.accent, codeBackground = colors.surfaceSunken),
        )
    }
}

/**
 * 相邻块之间的留白。源码中的空行只用于切分块，不直接占据高度；可见间距按语义分配：
 * 段落之间约半行，标题前更宽以开启新段落，标题后收紧使其贴近所属内容。
 */
internal fun markdownBlockGap(previous: MarkdownBlock?, current: MarkdownBlock, tone: MarkdownTone): TextUnit {
    if (previous == null) return 0.sp
    val gap = when {
        current is MarkdownHeading && previous is MarkdownHeading -> 8f
        current is MarkdownHeading -> if (current.level <= 2) 22f else 18f
        previous is MarkdownHeading -> 8f
        previous is MarkdownParagraph && current is MarkdownParagraph -> 12f
        else -> 14f
    }
    val scale = if (tone == MarkdownTone.Answer) 1f else THINKING_GAP_SCALE
    return (gap * scale).sp
}

/** 紧凑列表项内部（例如段落后紧跟子列表）只保留很小的间距。 */
internal fun markdownCompactGap(previous: MarkdownBlock?, tone: MarkdownTone): TextUnit =
    if (previous == null) 0.sp else if (tone == MarkdownTone.Answer) 4.sp else 3.sp

internal const val ANSWER_LINE_HEIGHT_SP = 26
internal const val THINKING_LINE_HEIGHT_SP = 22
private const val THINKING_GAP_SCALE = 0.75f
