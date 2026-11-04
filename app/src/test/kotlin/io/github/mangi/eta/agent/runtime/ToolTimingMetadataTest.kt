package io.github.mangi.eta.agent.runtime

import org.junit.Assert.*
import org.junit.Test

class ToolTimingMetadataTest {
    @Test fun resultContentCannotIntroducePrivateOrUnboundedMetrics() {
        val raw = """{"content":"private page text", "timing_ms":{
            "http":123,"extraction":0,"screenshot":-1,"navigation":"456",
            "challenge":600001,"action_wait":1.5,"browser_start":true,
            "https://private.invalid/token?secret=x":999
        }}"""
        val timings = ToolTimingMetadata.fromToolResult(raw)
        assertEquals(mapOf("http" to 123L, "extraction" to 0L), timings)
        assertFalse(ToolTimingMetadata.encode(timings).contains("private"))
        assertEquals(timings, ToolTimingMetadata.decode(ToolTimingMetadata.encode(timings)))
        assertTrue(ToolTimingMetadata.fromToolResult("not JSON").isEmpty())
        assertTrue(ToolTimingMetadata.fromToolResult("{" + "x".repeat(2 * 1024 * 1024)).isEmpty())
        assertTrue(ToolTimingMetadata.decode("x".repeat(1025)).isEmpty())
    }

    @Test fun cumulativeTotalsMayExceedOneCallBudgetWithoutAcceptingUnknownKeys() {
        val values = mapOf("http" to 900_000L, "private" to 1L)
        assertTrue(ToolTimingMetadata.sanitize(values).isEmpty())
        assertEquals(mapOf("http" to 900_000L), ToolTimingMetadata.sanitize(values, aggregate = true))
    }
}
