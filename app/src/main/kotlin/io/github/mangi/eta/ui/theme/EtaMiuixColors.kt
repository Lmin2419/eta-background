package io.github.mangi.eta.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import io.github.mangi.eta.data.model.AppearanceAccentColor
import io.github.mangi.eta.data.model.AppearanceSettings
import top.yukonga.miuix.kmp.theme.Colors

internal val AppearanceSettings.usesGeneratedAccent: Boolean
    get() = monetEnabled || accentColor != AppearanceAccentColor.SYSTEM

internal fun resolveEtaColors(appearance: AppearanceSettings, isDark: Boolean, generated: Colors): EtaColors {
    var colors = if (isDark) darkEtaColors() else lightEtaColors()
    if (appearance.usesGeneratedAccent) {
        colors = colors.withAccent(generated.primary, generated.primaryContainer,
            generated.onPrimaryContainer, generated.onPrimary)
    }
    // Preserve the existing optional OLED mode and its Monet-only availability.
    if (isDark && appearance.monetEnabled && appearance.pureBlackEnabled) {
        colors = colors.copy(canvas = Color.Black, surface = Color.Black)
    }
    return colors
}

/** Bridge legacy components onto the neutral Eta surfaces without changing their layout. */
internal fun EtaColors.toMiuixColors(base: Colors): Colors = base.copy(
    primary = accent, onPrimary = onAccent,
    primaryVariant = accent, onPrimaryVariant = onAccent,
    primaryContainer = accentSoft, onPrimaryContainer = onAccentSoft,
    disabledPrimary = accent.copy(alpha = 0.38f).compositeOver(surface),
    disabledOnPrimary = onAccent.copy(alpha = 0.38f),
    disabledPrimaryButton = accent.copy(alpha = 0.38f).compositeOver(surface),
    disabledOnPrimaryButton = onAccent.copy(alpha = 0.38f),
    disabledPrimarySlider = accent.copy(alpha = 0.38f).compositeOver(surface),
    secondary = outlineStrong, onSecondary = onStatus,
    secondaryVariant = surfaceSunken, onSecondaryVariant = textPrimary,
    disabledSecondary = surfaceSunken, disabledOnSecondary = textTertiary.copy(alpha = 0.38f),
    disabledSecondaryVariant = surfaceSunken, disabledOnSecondaryVariant = textPrimary.copy(alpha = 0.38f),
    secondaryContainer = surfaceSunken, onSecondaryContainer = textSecondary,
    secondaryContainerVariant = surfaceSunken, onSecondaryContainerVariant = textTertiary,
    tertiaryContainer = accentSoft, onTertiaryContainer = onAccentSoft,
    tertiaryContainerVariant = accentSoft,
    background = canvas, onBackground = textPrimary, onBackgroundVariant = textSecondary,
    surface = surface, onSurface = textPrimary, surfaceVariant = surfaceRaised,
    onSurfaceSecondary = textSecondary, onSurfaceVariantSummary = textSecondary,
    onSurfaceVariantActions = textTertiary, disabledOnSurface = textPrimary.copy(alpha = 0.38f),
    surfaceContainer = surface, onSurfaceContainer = textPrimary, onSurfaceContainerVariant = textTertiary,
    surfaceContainerHigh = surfaceRaised, onSurfaceContainerHigh = textSecondary,
    surfaceContainerHighest = surfaceSunken, onSurfaceContainerHighest = textPrimary,
    outline = outlineStrong, dividerLine = outline, windowDimming = scrim,
    error = danger, onError = statusGlyph, errorContainer = dangerSoft, onErrorContainer = dangerText,
    sliderKeyPoint = outlineStrong, sliderKeyPointForeground = accent, sliderBackground = surfaceSunken,
)
