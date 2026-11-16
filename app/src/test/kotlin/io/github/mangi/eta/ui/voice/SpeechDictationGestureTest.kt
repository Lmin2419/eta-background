package io.github.mangi.eta.ui.voice

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 上游按住说话的取消区：手指移出话筒四周的安全边距才算取消。 */
class SpeechDictationGestureTest {
    @Test
    fun holdCancelZoneRequiresLeavingSafetyMargin() {
        assertFalse(isInHoldCancelZone(Offset(50f, 50f), width = 100f, height = 100f, cancelMarginPx = 90f))
        assertFalse(isInHoldCancelZone(Offset(50f, -80f), width = 100f, height = 100f, cancelMarginPx = 90f))
        assertTrue(isInHoldCancelZone(Offset(50f, -95f), width = 100f, height = 100f, cancelMarginPx = 90f))
        assertTrue(isInHoldCancelZone(Offset(-95f, 50f), width = 100f, height = 100f, cancelMarginPx = 90f))
        assertTrue(isInHoldCancelZone(Offset(195f, 50f), width = 100f, height = 100f, cancelMarginPx = 90f))
        assertTrue(isInHoldCancelZone(Offset(50f, 195f), width = 100f, height = 100f, cancelMarginPx = 90f))
    }
}
