package io.github.mangi.eta.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSafetyTest {

    @Test
    fun safeStackTracePreservesLocationsWithoutMessagesOrSuppressedPayloads() {
        val cause = IllegalArgumentException("private-conversation-content")
        val failure = IllegalStateException("private-api-key", cause).apply {
            stackTrace = arrayOf(StackTraceElement("io.github.mangi.eta.Store", "save", "Store.kt", 42))
            addSuppressed(IllegalStateException("private-path"))
        }
        val rendered = failure.safeStackTrace()
        assertTrue(rendered.contains("Store.save(Store.kt:42)"))
        assertTrue(rendered.contains("IllegalArgumentException"))
        assertFalse(rendered.contains("private-"))
    }

    @Test
    fun safeStackTraceBoundsDeepStacksAndStopsCauseCycles() {
        val first = IllegalStateException("secret")
        val second = IllegalArgumentException("secret")
        first.initCause(second)
        second.initCause(first)
        first.stackTrace = Array(1000) { StackTraceElement("Class".repeat(100), "method", "File.kt", it) }
        val rendered = first.safeStackTrace()
        assertTrue(rendered.length < 20_000)
        assertTrue(rendered.contains("IllegalStateException"))
        assertFalse(rendered.contains("secret"))
    }

    @Test
    fun safeLogType_returnsTypeWithoutExceptionMessage() {
        val secret = "sensitive-runtime-message"

        val rendered = IllegalStateException(secret).safeLogType()

        assertEquals("IllegalStateException", rendered)
        assertFalse(rendered.contains(secret))
    }

    @Test
    fun toSafeLogToken_acceptsStableAsciiTokens() {
        assertEquals("tool.read_file-1", "tool.read_file-1".toSafeLogToken())
        assertEquals("RESULT_OK", "RESULT_OK".toSafeLogToken())
    }

    @Test
    fun toSafeLogToken_rejectsUntrustedOrHighCardinalityValues() {
        assertEquals("unknown", null.toSafeLogToken())
        assertEquals("unknown", "".toSafeLogToken())
        assertEquals("unknown", "tool\nforged-entry".toSafeLogToken())
        assertEquals("unknown", "包含用户内容".toSafeLogToken())
        assertEquals("unknown", "a".repeat(65).toSafeLogToken())
    }
}
