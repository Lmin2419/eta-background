package io.github.mangi.eta.agent.terminal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PtyInputFramesTest {
    @Test
    fun dataFrameCarriesTypeAndBigEndianLength() {
        val frame = PtyInputFrames.data(byteArrayOf(0x61, 0x03))

        assertArrayEquals(byteArrayOf(0, 0, 2, 0x61, 0x03), frame)
    }

    @Test
    fun largeInputIsSplitIntoBoundedFrames() {
        val payload = ByteArray(PtyInputFrames.MAX_PAYLOAD + 10) { (it % 7).toByte() }

        val frames = PtyInputFrames.data(payload)

        assertEquals(payload.size + 6, frames.size)
        assertEquals(0x10, frames[1].toInt() and 0xFF)
        assertEquals(0x00, frames[2].toInt() and 0xFF)
        val second = 3 + PtyInputFrames.MAX_PAYLOAD
        assertArrayEquals(byteArrayOf(0, 0, 10), frames.copyOfRange(second, second + 3))
        assertArrayEquals(payload.copyOfRange(PtyInputFrames.MAX_PAYLOAD, payload.size), frames.copyOfRange(second + 3, frames.size))
    }

    @Test
    fun resizeFrameEncodesRowsThenColumns() {
        assertArrayEquals(
            byteArrayOf(1, 0, 4, 0x01, 0x2C, 0x00, 0x50),
            PtyInputFrames.resize(rows = 300, cols = 80),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun resizeRejectsOutOfRangeSize() {
        PtyInputFrames.resize(rows = 0, cols = 80)
    }
}
