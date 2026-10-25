package io.github.mangi.eta.ui.components

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.mangi.eta.R

/**
 * 随包的等宽字体。部分系统会把 [FontFamily.Monospace] 替换成比例字体，终端列宽与对齐随之失效；
 * 终端界面统一使用这份字体，CJK 等缺失字形由系统字体回退，网格对齐由按单元格绘制保证。
 * 采用无连字版本，避免 `->`、`!=` 在命令与代码里被合成单个字形。
 */
internal val EtaMonospace = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)
