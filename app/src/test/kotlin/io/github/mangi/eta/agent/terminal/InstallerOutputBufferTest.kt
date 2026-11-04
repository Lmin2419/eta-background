package io.github.mangi.eta.agent.terminal

import org.junit.Assert.*
import org.junit.Test

class InstallerOutputBufferTest {
    @Test fun defaultPreservesExistingPrefixBehaviour() {
        val buffer = InstallerOutputBuffer(8)
        buffer.append("abcdef".toByteArray())
        buffer.append("ghijklmnop".toByteArray())
        assertEquals("abcdefgh", buffer.text())
        assertTrue(buffer.truncated())
    }

    @Test fun tailIsCorrectAcrossMultipleWrapsAndLargeChunks() {
        val buffer = InstallerOutputBuffer(8, captureTail = true)
        var all = ""
        for (chunk in listOf("abc", "de", "fgh", "ij", "klmnopqrstuvwxyz", "01", "234567", "8")) {
            all += chunk
            buffer.append(chunk.toByteArray())
            assertEquals(all.takeLast(8), buffer.text())
        }
        assertTrue(buffer.truncated())
    }

    @Test fun exactCapacityDoesNotClaimTruncationAndReadLengthIsRespected() {
        val buffer = InstallerOutputBuffer(8, captureTail = true)
        buffer.append("abcdefghignored".toByteArray(), 8)
        assertEquals("abcdefgh", buffer.text())
        assertFalse(buffer.truncated())
        buffer.append("xunused".toByteArray(), 1)
        assertEquals("bcdefghx", buffer.text())
        assertTrue(buffer.truncated())
    }
}
