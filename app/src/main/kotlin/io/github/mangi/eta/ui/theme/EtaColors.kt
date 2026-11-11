package io.github.mangi.eta.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import io.github.mangi.eta.data.model.AppearanceAccentColor

/** Source of truth: etax/design/01-design-language.md §2. */
@Immutable
data class EtaColors(
    val isDark: Boolean,
    val canvas: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val outline: Color,
    val outlineStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val scrim: Color,
    val pressed: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
    val attention: Color,
    val attentionSoft: Color,
    val success: Color,
    val successSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
) {
    val attentionText: Color get() = if (isDark) attention else Color(0xFF5A35C8)
    val successText: Color get() = if (isDark) success else Color(0xFF157A40)
    val dangerText: Color get() = if (isDark) danger else Color(0xFFC22E2E)
    val statusGlyph: Color get() = if (isDark) Color(0xFF15161A) else Color.White
    val running: Color get() = accent
    val runningSoft: Color get() = accentSoft
    val paused: Color get() = textSecondary
    val pausedSoft: Color get() = surfaceSunken
    val onStatus: Color get() = Color.White
    val toastBackground: Color get() = if (isDark) Color(0xFFF3EDE4) else Color(0xFF2A241D)
    val toastForeground: Color get() = if (isDark) Color(0xFF201A12) else Color(0xFFF3EDE4)
    val toastAction: Color get() = if (isDark) Color(0xFF8A4507) else Color(0xFFF8C894)
    val shadow: Color get() = Color(0xFF101220)
    val tileBlue: Color get() = Color(0xFF3B82F6)
    val tileCaramel: Color get() = Color(0xFFD27A2C)
    val tileViolet: Color get() = Color(0xFF8B5CF6)
    val tilePink: Color get() = Color(0xFFE0569B)
    val tileOrange: Color get() = Color(0xFFF08C00)
    val tileGreen: Color get() = Color(0xFF16A34A)
    val tileTeal: Color get() = Color(0xFF0EA5A4)
    val tileSlate: Color get() = Color(0xFF64748B)
    val aurora: EtaAurora get() = EtaAurora

    /**
     * 图标底块统一为中性底（阶段 10）：不再按分类铺彩色底，页面整体更安静。
     */
    fun tileBackground(@Suppress("UNUSED_PARAMETER") color: Color): Color = surfaceSunken

    /**
     * 图标只保留三种颜色：焦糖（默认）、绿色（与「正常/完成」相关）、石板灰（系统/次要）。
     * 其余分类色一律收拢到焦糖。深色模式提亮一些。
     */
    fun tileForeground(color: Color): Color {
        val hue = when (color) {
            tileGreen, tileTeal -> tileGreen
            tileSlate -> tileSlate
            else -> tileCaramel
        }
        return if (isDark) lerp(hue, Color.White, 0.25f) else hue
    }

    fun withAccent(primary: Color, soft: Color, onSoft: Color, onPrimary: Color = onAccent): EtaColors =
        copy(accent = primary, accentSoft = soft, onAccentSoft = onSoft, onAccent = onPrimary)
}

/** 胖小胖 warm aurora: honey → peach → rose → lavender. Only for "AI is working". */
object EtaAurora {
    val honey = Color(0xFFFFC861)
    val peach = Color(0xFFFF9F7A)
    val rose = Color(0xFFF27BA8)
    val lavender = Color(0xFFB9A3FF)
    val stops get() = arrayOf(0f to honey, 0.35f to peach, 0.70f to rose, 1f to lavender)
    const val angleDegrees = 100f
}

fun lightEtaColors() = EtaColors(
    isDark = false,
    canvas = Color(0xFFFAF8F3), surface = Color.White,
    surfaceRaised = Color.White, surfaceSunken = Color(0xFFF2EDE4),
    outline = Color(0xFFE8E1D6), outlineStrong = Color(0xFFD6CBBB),
    textPrimary = Color(0xFF201A12), textSecondary = Color(0xFF5E5347), textTertiary = Color(0xFF6E6254),
    scrim = Color(0xFF1A120A).copy(alpha = 0.40f), pressed = Color(0xFF2A1A08).copy(alpha = 0.08f),
    accent = Color(0xFFB25A0E), onAccent = Color.White,
    accentSoft = Color(0xFFFCEAD5), onAccentSoft = Color(0xFF8A4507),
    // "Needs you" is violet so it never reads as the caramel accent.
    attention = Color(0xFF6D46DB), attentionSoft = Color(0xFFEFEAFF),
    success = Color(0xFF1E8E4E), successSoft = Color(0xFFE3F5EA),
    danger = Color(0xFFD33636), dangerSoft = Color(0xFFFDE8E8),
)

fun darkEtaColors() = EtaColors(
    isDark = true,
    // 深色用暖炭灰而不是近黑：长时间阅读更柔和。
    canvas = Color(0xFF1E1C19), surface = Color(0xFF262320),
    surfaceRaised = Color(0xFF2E2A26), surfaceSunken = Color(0xFF332F2A),
    outline = Color(0xFF3D3832), outlineStrong = Color(0xFF524A41),
    textPrimary = Color(0xFFF3EDE4), textSecondary = Color(0xFFC4BAAD), textTertiary = Color(0xFFADA193),
    scrim = Color.Black.copy(alpha = 0.56f), pressed = Color.White.copy(alpha = 0.08f),
    accent = Color(0xFFF0A65A), onAccent = Color(0xFF2A1600),
    accentSoft = Color(0xFF3D2B17), onAccentSoft = Color(0xFFF8C894),
    attention = Color(0xFFB8A2FF), attentionSoft = Color(0xFF2A2342),
    success = Color(0xFF5CD08A), successSoft = Color(0xFF13301F),
    danger = Color(0xFFFF7A7A), dangerSoft = Color(0xFF3A1717),
)

/** Keep the existing user-selectable seed palette; the default brand is defined above. */
internal fun AppearanceAccentColor.seedColor(): Color = when (this) {
    AppearanceAccentColor.SYSTEM, AppearanceAccentColor.BLUE -> Color(0xFF3482FF)
    AppearanceAccentColor.PURPLE -> Color(0xFF6750A4)
    AppearanceAccentColor.PINK -> Color(0xFFB0006D)
    AppearanceAccentColor.RED -> Color(0xFFBA1A1A)
    AppearanceAccentColor.ORANGE -> Color(0xFFB65D00)
    AppearanceAccentColor.YELLOW -> Color(0xFF7D5700)
    AppearanceAccentColor.GREEN -> Color(0xFF006D3B)
    AppearanceAccentColor.TEAL -> Color(0xFF006A6A)
}
