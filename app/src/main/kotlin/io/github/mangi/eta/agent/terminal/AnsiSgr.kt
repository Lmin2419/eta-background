package io.github.mangi.eta.agent.terminal

/**
 * 终端单元格样式的中性表示：颜色为 0xAARRGGBB 的 Long，不依赖 Compose。
 * 块式输出渲染（ui 层）与控制台屏幕缓冲区共用同一份 SGR 解释，避免两套调色板漂移。
 */
internal data class SgrStyle(
    val fg: Long? = null,
    val bg: Long? = null,
    val bold: Boolean = false,
    val dim: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    /** 反显：前景与背景互换，TUI 用它标记列表选中项与光标所在条目。 */
    val inverse: Boolean = false,
    val strikethrough: Boolean = false,
) {
    val isPlain: Boolean
        get() = fg == null && bg == null && !bold && !dim && !italic && !underline && !inverse && !strikethrough

    companion object {
        val PLAIN = SgrStyle()
    }
}

/** SGR 参数序列解释器；[params] 为 CSI 与结尾 m 之间的原文（空串等价于 0）。 */
internal object AnsiSgr {

    /**
     * 参数以 ; 分组、组内以 : 分隔子参数（ITU T.416），两种扩展颜色写法都要识别：
     * `38;5;n` / `38;2;r;g;b` 跨组读取，`38:5:n` / `38:2::r:g:b` 只在组内读取。
     * 子参数不能当作独立参数解释，否则 `4:3`（波浪下划线）会被误读成下划线加斜体。
     */
    fun apply(params: String, style: SgrStyle): SgrStyle {
        val groups = if (params.isEmpty()) {
            listOf(listOf(0))
        } else {
            params.split(';').map { group -> group.split(':').map { it.toIntOrNull() ?: 0 } }
        }
        var current = style
        var k = 0
        while (k < groups.size) {
            val group = groups[k]
            val code = group[0]
            when (code) {
                0 -> current = SgrStyle.PLAIN
                1 -> current = current.copy(bold = true)
                2 -> current = current.copy(dim = true)
                3 -> current = current.copy(italic = true)
                4 -> current = current.copy(underline = group.getOrNull(1) != 0)
                7 -> current = current.copy(inverse = true)
                9 -> current = current.copy(strikethrough = true)
                21 -> current = current.copy(underline = true)
                22 -> current = current.copy(bold = false, dim = false)
                23 -> current = current.copy(italic = false)
                24 -> current = current.copy(underline = false)
                27 -> current = current.copy(inverse = false)
                29 -> current = current.copy(strikethrough = false)
                in 30..37 -> current = current.copy(fg = STANDARD[code - 30])
                39 -> current = current.copy(fg = null)
                in 40..47 -> current = current.copy(bg = STANDARD[code - 40])
                49 -> current = current.copy(bg = null)
                in 90..97 -> current = current.copy(fg = BRIGHT[code - 90])
                in 100..107 -> current = current.copy(bg = BRIGHT[code - 100])
                // 58 为下划线颜色：不渲染，但必须按同样规则吞掉其颜色参数。
                38, 48, 58 -> {
                    val (color, consumed) = if (group.size > 1) {
                        readSubparameterColor(group) to 0
                    } else {
                        readLegacyColor(groups, k)
                    }
                    when (code) {
                        38 -> color?.let { current = current.copy(fg = it) }
                        48 -> color?.let { current = current.copy(bg = it) }
                    }
                    k += consumed
                }
            }
            k++
        }
        return current
    }

    /** `38:5:n`、`38:2:r:g:b` 与带色彩空间 ID 的 `38:2::r:g:b`。 */
    private fun readSubparameterColor(group: List<Int>): Long? = when (group.getOrNull(1)) {
        5 -> group.getOrNull(2)?.let(::color256)
        2 -> {
            val rgb = if (group.size >= 6) group.subList(3, 6) else group.drop(2).take(3)
            if (rgb.size == 3) rgb(rgb[0], rgb[1], rgb[2]) else null
        }
        else -> null
    }

    /** `38;5;n` 与 `38;2;r;g;b`：返回颜色与额外消费的组数；参数不足时不消费。 */
    private fun readLegacyColor(groups: List<List<Int>>, index: Int): Pair<Long?, Int> {
        fun at(offset: Int): Int? = groups.getOrNull(index + offset)?.getOrNull(0)
        return when (at(1)) {
            5 -> at(2)?.let { color256(it) to 2 } ?: (null to 1)
            2 -> {
                val r = at(2)
                val g = at(3)
                val b = at(4)
                if (r != null && g != null && b != null) rgb(r, g, b) to 4 else null to 1
            }
            else -> null to 0
        }
    }

    private fun rgb(r: Int, g: Int, b: Int): Long =
        0xFF000000L or (r.coerceIn(0, 255).toLong() shl 16) or
            (g.coerceIn(0, 255).toLong() shl 8) or b.coerceIn(0, 255).toLong()

    fun color256(n: Int): Long? = when (n) {
        in 0..7 -> STANDARD[n]
        in 8..15 -> BRIGHT[n - 8]
        in 16..231 -> {
            val v = n - 16
            0xFF000000L or
                (CUBE_LEVELS[v / 36].toLong() shl 16) or
                (CUBE_LEVELS[(v / 6) % 6].toLong() shl 8) or
                CUBE_LEVELS[v % 6].toLong()
        }
        in 232..255 -> {
            val level = (8 + 10 * (n - 232)).toLong()
            0xFF000000L or (level shl 16) or (level shl 8) or level
        }
        else -> null
    }

    private val CUBE_LEVELS = intArrayOf(0, 95, 135, 175, 215, 255)

    val STANDARD: List<Long> = listOf(
        0xFF000000, 0xFFCD0000, 0xFF00CD00, 0xFFCDCD00,
        0xFF0000EE, 0xFFCD00CD, 0xFF00CDCD, 0xFFE5E5E5,
    )

    val BRIGHT: List<Long> = listOf(
        0xFF7F7F7F, 0xFFFF0000, 0xFF00FF00, 0xFFFFFF00,
        0xFF5C5CFF, 0xFFFF00FF, 0xFF00FFFF, 0xFFFFFFFF,
    )
}
