package io.github.mangi.eta.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class EtaContrastTest {
    private fun contrast(a: Color, b: Color): Float {
        val high = maxOf(a.luminance(), b.luminance())
        val low = minOf(a.luminance(), b.luminance())
        return (high + .05f) / (low + .05f)
    }

    @Test fun allNeutralTextAndStatusTextRemainReadableOnTheirSurfaces() {
        for (p in listOf(lightEtaColors(), darkEtaColors())) {
            for (bg in listOf(p.canvas, p.surface, p.surfaceRaised, p.surfaceSunken)) {
                for (fg in listOf(p.textPrimary, p.textSecondary, p.textTertiary)) {
                    assertTrue("Text contrast dark=${p.isDark}: ${contrast(fg, bg)}", contrast(fg, bg) >= 4.5f)
                }
            }
            for ((fg, bg) in listOf(p.attentionText to p.attentionSoft, p.successText to p.successSoft,
                p.dangerText to p.dangerSoft, p.onAccent to p.accent, p.onAccentSoft to p.accentSoft,
                p.toastForeground to p.toastBackground, p.toastAction to p.toastBackground)) {
                assertTrue("Semantic text contrast: ${contrast(fg, bg)}", contrast(fg, bg) >= 4.5f)
            }
        }
    }

    @Test fun statusSymbolsDoNotDisappearOnBrightDarkThemeFills() {
        for (p in listOf(lightEtaColors(), darkEtaColors())) {
            for (fill in listOf(p.attention, p.success, p.danger)) {
                assertTrue(contrast(p.statusGlyph, fill) >= 3f)
            }
        }
    }
}
