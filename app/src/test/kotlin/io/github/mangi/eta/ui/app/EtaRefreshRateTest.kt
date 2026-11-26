package io.github.mangi.eta.ui.app

import io.github.mangi.eta.data.model.AppearanceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EtaRefreshRateTest {
    private val m = EtaRefreshRate::Mode

    @Test fun picksTheFastestModeAtTheCurrentResolution() {
        val modes = listOf(m(1, 1440, 3200, 60f), m(2, 1440, 3200, 120f), m(3, 1080, 2400, 144f), m(4, 1440, 3200, 90f))
        assertEquals(2, EtaRefreshRate.preferredModeId(m(1, 1440, 3200, 60f), modes))
        // 已经在最高档也照样指定它，免得系统再降下来。
        assertEquals(2, EtaRefreshRate.preferredModeId(m(2, 1440, 3200, 120f), modes))
    }

    @Test fun aSingleRefreshRateNeedsNoRequest() {
        assertNull(EtaRefreshRate.preferredModeId(m(1, 1080, 2400, 60f), listOf(m(1, 1080, 2400, 60f), m(5, 720, 1600, 90f))))
    }

    @Test fun explicitChoiceWins() {
        assertTrue(EtaRefreshRate.enabled(AppearanceSettings(highRefreshRate = true)))
        assertFalse(EtaRefreshRate.enabled(AppearanceSettings(highRefreshRate = false)))
    }
}
