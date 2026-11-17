package io.github.mangi.eta.ui.screens.desktop

import io.github.mangi.eta.agent.remote.DesktopLinkPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026111620：电脑任务的流光是单独的开关，切换通知预设不改它。 */
class DesktopNoticePresetTest {
    @Test
    fun presetsLeaveGlowAlone() {
        val glowOn = DesktopLinkPrefs(glow = true)
        DesktopNoticePreset.entries.forEach { preset -> assertTrue(glowOn.withNoticePreset(preset).glow) }
        val glowOff = DesktopLinkPrefs(glow = false)
        DesktopNoticePreset.entries.forEach { preset -> assertFalse(glowOff.withNoticePreset(preset).glow) }
    }

    @Test
    fun glowAloneDoesNotMakeThePresetProminent() {
        assertEquals(DesktopNoticePreset.Quiet, DesktopLinkPrefs(ongoingLowPriority = true, fluidCloud = false, glow = true).noticePreset)
        assertEquals(DesktopNoticePreset.Prominent, DesktopLinkPrefs(fluidCloud = true).noticePreset)
    }
}
