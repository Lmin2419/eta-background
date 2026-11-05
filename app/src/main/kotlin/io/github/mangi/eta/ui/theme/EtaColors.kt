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
    val running: Color get() = accent
    val runningSoft: Color get() = accentSoft
    val paused: Color get() = textSecondary
    val pausedSoft: Color get() = surfaceSunken
    val onStatus: Color get() = Color.White
    val toastBackground: Color get() = if (isDark) Color(0xFFECEDF2) else Color(0xFF1E2027)
    val toastForeground: Color get() = if (isDark) Color(0xFF15161A) else Color(0xFFECEDF2)
    val toastAction: Color get() = if (isDark) Color(0xFF3240D9) else Color(0xFFB9C0FF)
    val shadow: Color get() = Color(0xFF101220)
    val tileBlue: Color get() = Color(0xFF3B82F6)
    val tileIndigo: Color get() = Color(0xFF4B5BFF)
    val tileViolet: Color get() = Color(0xFF8B5CF6)
    val tilePink: Color get() = Color(0xFFE0569B)
    val tileOrange: Color get() = Color(0xFFF08C00)
    val tileGreen: Color get() = Color(0xFF16A34A)
    val tileTeal: Color get() = Color(0xFF0EA5A4)
    val tileSlate: Color get() = Color(0xFF64748B)
    val aurora: EtaAurora get() = EtaAurora

    fun tileBackground(color: Color): Color =
        color.copy(alpha = if (isDark) 0.20f else 0.12f).compositeOver(surface)

    // The design asks for a lighter dark-mode glyph without prescribing exact values.
    fun tileForeground(color: Color): Color = if (isDark) lerp(color, Color.White, 0.25f) else color

    fun withAccent(primary: Color, soft: Color, onSoft: Color, onPrimary: Color = onAccent): EtaColors =
        copy(accent = primary, accentSoft = soft, onAccentSoft = onSoft, onAccent = onPrimary)
}

object EtaAurora {
    val blue = Color(0xFF71BEFF)
    val indigo = Color(0xFFADB8FF)
    val pink = Color(0xFFF27BDE)
    val peach = Color(0xFFFFC99A)
    val stops get() = arrayOf(0f to blue, 0.35f to indigo, 0.70f to pink, 1f to peach)
    const val angleDegrees = 100f
}

fun lightEtaColors() = EtaColors(
    isDark = false,
    canvas = Color(0xFFF4F5F8), surface = Color.White,
    surfaceRaised = Color.White, surfaceSunken = Color(0xFFECEEF3),
    outline = Color(0xFFE2E4EA), outlineStrong = Color(0xFFCDD0D9),
    textPrimary = Color(0xFF15161A), textSecondary = Color(0xFF575A66), textTertiary = Color(0xFF6F7280),
    scrim = Color(0xFF0B0C10).copy(alpha = 0.40f), pressed = Color(0xFF14162A).copy(alpha = 0.08f),
    accent = Color(0xFF4B5BFF), onAccent = Color.White,
    accentSoft = Color(0xFFE9EBFF), onAccentSoft = Color(0xFF3240D9),
    attention = Color(0xFFB45F00), attentionSoft = Color(0xFFFFF1DF),
    success = Color(0xFF1E8E4E), successSoft = Color(0xFFE3F5EA),
    danger = Color(0xFFD33636), dangerSoft = Color(0xFFFDE8E8),
)

fun darkEtaColors() = EtaColors(
    isDark = true,
    canvas = Color(0xFF0D0E12), surface = Color(0xFF16171C),
    surfaceRaised = Color(0xFF1E2027), surfaceSunken = Color(0xFF23252D),
    outline = Color(0xFF2A2C35), outlineStrong = Color(0xFF3A3D48),
    textPrimary = Color(0xFFECEDF2), textSecondary = Color(0xFFA6A9B6), textTertiary = Color(0xFF80838F),
    scrim = Color.Black.copy(alpha = 0.56f), pressed = Color.White.copy(alpha = 0.08f),
    accent = Color(0xFF8C97FF), onAccent = Color(0xFF0B0E2A),
    accentSoft = Color(0xFF262A52), onAccentSoft = Color(0xFFB9C0FF),
    attention = Color(0xFFFFB14A), attentionSoft = Color(0xFF3A2A12),
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
