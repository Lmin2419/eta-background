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
    /** 只用于占位文字、统计小字、时间、右箭头；不参与 4.5:1。 */
    val textQuiet: Color = textTertiary,
    /** 图形填充（发送键、运行圈、上下文圆环、爪印）；上面不放文字。 */
    val brand: Color = accent,
    val onBrand: Color = onAccent,
) {
    val attentionText: Color get() = if (isDark) attention else Color(0xFF5A35C8)
    val successText: Color get() = if (isDark) success else Color(0xFF157A40)
    val dangerText: Color get() = if (isDark) danger else Color(0xFFC22E2E)
    val statusGlyph: Color get() = if (isDark) Color(0xFF15161A) else Color.White
    val running: Color get() = brand
    val runningSoft: Color get() = accentSoft
    val paused: Color get() = textSecondary
    val pausedSoft: Color get() = surfaceSunken
    val onStatus: Color get() = Color.White
    val toastBackground: Color get() = if (isDark) Color(0xFFF2F1EF) else Color(0xFF1F1F1F)
    val toastForeground: Color get() = if (isDark) Color(0xFF1A1A1A) else Color(0xFFF5F5F3)
    val toastAction: Color get() = if (isDark) Color(0xFF9A4426) else Color(0xFFF2B8A2)
    val shadow: Color get() = Color(0xFF1A1612)
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
    fun tileForeground(@Suppress("UNUSED_PARAMETER") color: Color): Color = textPrimary

    fun withAccent(primary: Color, soft: Color, onSoft: Color, onPrimary: Color = onAccent): EtaColors =
        copy(accent = primary, accentSoft = soft, onAccentSoft = onSoft, onAccent = onPrimary,
            brand = primary, onBrand = onPrimary)
}

/** 胖小胖 warm aurora: honey → peach → rose → lavender. Only for "AI is working". */
object EtaAurora {
    // 阶段 12：流光只用同一种珊瑚色的深浅（与 Claude 的橘色一致），不再是蜂蜜→玫瑰→薰衣草彩虹。
    val honey = Color(0xFFF2B8A2)
    val peach = Color(0xFFE38D6F)
    val rose = Color(0xFFD97757)
    val lavender = Color(0xFFB85A3A)
    val stops get() = arrayOf(0f to honey, 0.35f to peach, 0.70f to rose, 1f to lavender)
    const val angleDegrees = 100f
}

fun lightEtaColors() = EtaColors(
    isDark = false,
    // 阶段 13：页面底是极浅的纸白，白色卡片与玻璃才有层次；暖色只留在 brand（图形）和 accent（文字）。
    canvas = Color(0xFFFBFAF8), surface = Color.White,
    surfaceRaised = Color.White, surfaceSunken = Color(0xFFF1F0ED),
    outline = Color(0xFFE9E7E3), outlineStrong = Color(0xFFD8D5D0),
    textPrimary = Color(0xFF1A1A1A), textSecondary = Color(0xFF55534F), textTertiary = Color(0xFF6B6966),
    scrim = Color.Black.copy(alpha = 0.40f), pressed = Color.Black.copy(alpha = 0.06f),
    // 主色是 Claude 式珊瑚色（文字可读的深一档）；「柔和主色」改为中性灰，选中行、Tonal 按钮不再泛橘。
    accent = Color(0xFFB4532F), onAccent = Color.White,
    accentSoft = Color(0xFFECEAE6), onAccentSoft = Color(0xFF1A1A1A),
    // "Needs you" is violet so it never reads as the caramel accent.
    attention = Color(0xFF6D46DB), attentionSoft = Color(0xFFEFEAFF),
    success = Color(0xFF1E8E4E), successSoft = Color(0xFFE3F5EA),
    danger = Color(0xFFD33636), dangerSoft = Color(0xFFFDE8E8),
    textQuiet = Color(0xFF9E9B96), brand = Color(0xFFD97757), onBrand = Color.White,
)

fun darkEtaColors() = EtaColors(
    isDark = true,
    // 深色用暖炭灰而不是近黑：长时间阅读更柔和。
    canvas = Color(0xFF161616), surface = Color(0xFF1F1F1F),
    surfaceRaised = Color(0xFF262626), surfaceSunken = Color(0xFF2A2A2A),
    outline = Color(0xFF323232), outlineStrong = Color(0xFF444444),
    textPrimary = Color(0xFFF0EFED), textSecondary = Color(0xFFBDBBB7), textTertiary = Color(0xFFA09E9A),
    scrim = Color.Black.copy(alpha = 0.56f), pressed = Color.White.copy(alpha = 0.08f),
    accent = Color(0xFFE38D6F), onAccent = Color(0xFF2A1208),
    accentSoft = Color(0xFF303030), onAccentSoft = Color(0xFFF0EFED),
    attention = Color(0xFFB8A2FF), attentionSoft = Color(0xFF2A2342),
    success = Color(0xFF5CD08A), successSoft = Color(0xFF13301F),
    danger = Color(0xFFFF7A7A), dangerSoft = Color(0xFF3A1717),
    textQuiet = Color(0xFF6F6D6A), brand = Color(0xFFE38D6F), onBrand = Color(0xFF2A1208),
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
