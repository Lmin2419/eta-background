package io.github.mangi.eta.agent.terminal

/**
 * 终端字符占格：CJK 全角/宽字符与 emoji 占两格；组合附加符与零宽字符占 0 格，
 * 由缓冲区并入前一个单元格，避免把 é、👍🏻 之类的组合序列拆成多格错位。
 */
internal fun terminalCharWidth(codePoint: Int): Int = when {
    codePoint < 0x20 || codePoint in 0x7F..0x9F -> 0
    isZeroWidth(codePoint) -> 0
    codePoint in 0x1100..0x115F || // Hangul Jamo
        codePoint in 0x2E80..0x303E || // CJK Radicals .. CJK Symbols
        codePoint in 0x3041..0x33FF || // Hiragana .. CJK Compatibility
        codePoint in 0x3400..0x4DBF || // CJK Ext A
        codePoint in 0x4E00..0x9FFF || // CJK Unified
        codePoint in 0xA000..0xA4CF ||
        codePoint in 0xAC00..0xD7A3 || // Hangul Syllables
        codePoint in 0xF900..0xFAFF || // CJK Compatibility Ideographs
        codePoint in 0xFE30..0xFE4F ||
        codePoint in 0xFF00..0xFF60 || // Fullwidth Forms
        codePoint in 0xFFE0..0xFFE6 ||
        codePoint in 0x1F300..0x1F64F || // Emoji
        codePoint in 0x1F680..0x1F6FF || // Transport & Map
        codePoint in 0x1F900..0x1F9FF ||
        codePoint in 0x1FA70..0x1FAFF ||
        codePoint in 0x20000..0x2FFFD ||
        codePoint in 0x30000..0x3FFFD -> 2
    else -> 1
}

private fun isZeroWidth(codePoint: Int): Boolean =
    codePoint in 0x0300..0x036F || // Combining Diacritical Marks
        codePoint in 0x1AB0..0x1AFF ||
        codePoint in 0x1DC0..0x1DFF ||
        codePoint in 0x200B..0x200F || // ZWSP、ZWJ 与方向标记
        codePoint in 0x20D0..0x20FF ||
        codePoint in 0xFE00..0xFE0F || // Variation Selectors
        codePoint in 0x1F3FB..0x1F3FF || // Emoji 肤色修饰符
        codePoint in 0xE0100..0xE01EF

/**
 * DEC Special Graphics：`ESC ( 0` 选入后 0x5F..0x7E 映射为线框与符号。
 * ncurses 类程序按 terminfo 的 acsc 用它画边框，不映射会显示成成排的 q、x。
 */
internal fun decSpecialGraphic(char: Char): Char? =
    if (char in '_'..'~') DEC_SPECIAL_GRAPHICS[char - '_'] else null

private const val DEC_SPECIAL_GRAPHICS =
    " ◆▒␉␌␍␊°±␤␋┘┐┌└┼⎺⎻─⎼⎽├┤┴┬│≤≥π≠£·"
