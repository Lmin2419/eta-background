package io.github.mangi.eta.ui.screens.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.github.mangi.eta.agent.terminal.SgrStyle
import io.github.mangi.eta.agent.terminal.TerminalScreenBuffer
import io.github.mangi.eta.agent.terminal.terminalCharWidth

/** 网格渲染所需的主题色；反显、光标与组字预览需要显式的默认前景和背景。 */
@Immutable
internal data class ConsolePalette(
    val foreground: Color,
    val background: Color,
    val composing: Color,
)

/** 单元格尺寸与基线，全部以像素计；行高与列宽由同一份度量决定，保证网格与 PTY 尺寸一致。 */
@Immutable
internal data class ConsoleCellMetrics(
    val width: Float,
    val height: Float,
    val baseline: Float,
)

/**
 * 字形排版缓存：同一字符与字重/字形组合只测量一次，颜色在绘制时覆盖。
 * 行内容每帧都可能变化，但出现的字符集合很小，缓存命中后绘制只剩矩形与位图合成。
 */
internal class ConsoleGlyphCache(
    private val measurer: TextMeasurer,
    private val baseStyle: TextStyle,
) {
    private data class Key(val text: String, val bold: Boolean, val italic: Boolean)

    private val layouts = HashMap<Key, TextLayoutResult>()

    /**
     * 列宽取数字 0 的宽度：真等宽字体下所有字形相同；部分系统把等宽字体替换为比例字体时，
     * 数字仍为等宽字形，宽于它的字母在绘制时横向压缩进单元格，不会挤乱网格。
     */
    fun metrics(): ConsoleCellMetrics {
        val layout = measurer.measure("0", baseStyle, softWrap = false, maxLines = 1)
        return ConsoleCellMetrics(
            width = layout.size.width.toFloat().coerceAtLeast(1f),
            height = layout.size.height.toFloat().coerceAtLeast(1f),
            baseline = layout.firstBaseline,
        )
    }

    fun layout(text: String, bold: Boolean, italic: Boolean): TextLayoutResult {
        val key = Key(text, bold, italic)
        layouts[key]?.let { return it }
        if (layouts.size >= MAX_CACHED_GLYPHS) layouts.clear()
        val style = baseStyle.copy(
            fontWeight = if (bold) FontWeight.Bold else baseStyle.fontWeight,
            fontStyle = if (italic) FontStyle.Italic else baseStyle.fontStyle,
        )
        return measurer.measure(text, style, softWrap = false, maxLines = 1).also { layouts[key] = it }
    }

    private companion object {
        const val MAX_CACHED_GLYPHS = 4096
    }
}

/**
 * 按单元格绘制一行：每个字形固定落在 `列号 × 列宽` 处，不依赖字体本身的字宽，
 * 因此 CJK 回退字体与非等宽系统字体都不会让 TUI 的边框、对齐和光标错位。
 */
@Composable
internal fun ConsoleGridLine(
    line: TerminalScreenBuffer.RenderLine,
    cursorCol: Int?,
    composing: String,
    composingCol: Int,
    palette: ConsolePalette,
    metrics: ConsoleCellMetrics,
    glyphs: ConsoleGlyphCache,
) {
    val cells = line.cells
    val heightDp = with(LocalDensity.current) { metrics.height.toDp() }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp),
    ) {
        drawCells(cells, cursorCol, palette, metrics, glyphs)
        if (composing.isNotEmpty()) drawComposing(composing, composingCol, palette, metrics, glyphs)
    }
}

private fun DrawScope.drawCells(
    cells: List<TerminalScreenBuffer.Cell>,
    cursorCol: Int?,
    palette: ConsolePalette,
    metrics: ConsoleCellMetrics,
    glyphs: ConsoleGlyphCache,
) {
    var col = 0
    while (col < cells.size) {
        val cell = cells[col]
        if (cell.continuation) {
            col++
            continue
        }
        val span = if (col + 1 < cells.size && cells[col + 1].continuation) 2 else 1
        val x = col * metrics.width
        val cellWidth = span * metrics.width
        var (foreground, background) = resolveColors(cell.style, palette)
        if (col == cursorCol) {
            foreground = palette.background
            background = palette.foreground
        }
        if (background != Color.Unspecified) {
            drawRect(background, topLeft = Offset(x, 0f), size = Size(cellWidth, metrics.height))
        }
        if (cell.text.isNotBlank()) {
            drawGlyph(cell.text, cell.style, foreground, x, cellWidth, metrics, glyphs)
        }
        col += span
    }
}

private fun DrawScope.drawGlyph(
    text: String,
    style: SgrStyle,
    color: Color,
    x: Float,
    cellWidth: Float,
    metrics: ConsoleCellMetrics,
    glyphs: ConsoleGlyphCache,
) {
    val layout = glyphs.layout(text, style.bold, style.italic)
    val glyphWidth = layout.size.width.toFloat()
    val top = metrics.baseline - layout.firstBaseline
    val decoration = when {
        style.underline && style.strikethrough -> TextDecoration.combine(
            listOf(TextDecoration.Underline, TextDecoration.LineThrough),
        )
        style.underline -> TextDecoration.Underline
        style.strikethrough -> TextDecoration.LineThrough
        else -> null
    }
    if (glyphWidth > cellWidth * GLYPH_OVERFLOW_TOLERANCE) {
        scale(scaleX = cellWidth / glyphWidth, scaleY = 1f, pivot = Offset(x, 0f)) {
            drawText(layout, color = color, topLeft = Offset(x, top), textDecoration = decoration)
        }
    } else {
        val inset = ((cellWidth - glyphWidth) / 2f).coerceAtLeast(0f)
        drawText(layout, color = color, topLeft = Offset(x + inset, top), textDecoration = decoration)
    }
}

/** 组字预览覆盖在光标处，宽度按字符占格计算，未上屏前不进入网格。 */
private fun DrawScope.drawComposing(
    composing: String,
    col: Int,
    palette: ConsolePalette,
    metrics: ConsoleCellMetrics,
    glyphs: ConsoleGlyphCache,
) {
    var x = col * metrics.width
    var index = 0
    while (index < composing.length) {
        val codePoint = composing.codePointAt(index)
        val value = String(Character.toChars(codePoint))
        val span = terminalCharWidth(codePoint).coerceAtLeast(1)
        val cellWidth = span * metrics.width
        drawRect(palette.background, topLeft = Offset(x, 0f), size = Size(cellWidth, metrics.height))
        drawGlyph(value, COMPOSING_STYLE, palette.composing, x, cellWidth, metrics, glyphs)
        x += cellWidth
        index += Character.charCount(codePoint)
    }
}

/** 显式颜色优先，未指定时落到主题默认色；反显互换前景与背景，dim 以透明度衰减。 */
private fun resolveColors(style: SgrStyle, palette: ConsolePalette): Pair<Color, Color> {
    val foreground = style.fg?.let { Color(it) } ?: palette.foreground
    val background = style.bg?.let { Color(it) }
    val (text, fill) = if (style.inverse) {
        (background ?: palette.background) to foreground
    } else {
        foreground to (background ?: Color.Unspecified)
    }
    return (if (style.dim) text.copy(alpha = text.alpha * DIM_ALPHA) else text) to fill
}

/** 比例字体的字形略宽于列宽时保持原样，明显超出才压缩，避免常规字母被无谓变形。 */
private const val GLYPH_OVERFLOW_TOLERANCE = 1.08f
private const val DIM_ALPHA = 0.6f
private val COMPOSING_STYLE = SgrStyle(underline = true)
