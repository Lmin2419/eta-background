package io.github.mangi.eta.agent.web

import java.io.ByteArrayInputStream
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ScraplingProtocolTest {
    @Test fun rejectsAnotherRequestsResultInsteadOfReplayingOrReusingIt() {
        val reply = JSONObject().put("protocol", 1).put("request_id", "old").put("ok", true)
        assertEquals("SCRAPLING_PROTOCOL_ERROR", assertThrows(WebRequestException::class.java) {
            ScraplingProtocol.decode(reply.toString(), "current")
        }.code)
    }

    @Test fun malformedIncompleteAndOversizedOutputCannotBecomePageContent() {
        assertThrows(WebRequestException::class.java) { ScraplingProtocol.decode("not json", "id") }
        assertThrows(WebRequestException::class.java) {
            ScraplingProtocol.readLine(ByteArrayInputStream("truncated json".toByteArray()))
        }
        assertThrows(WebRequestException::class.java) {
            ScraplingProtocol.readLine(ByteArrayInputStream(ByteArray(2 * 1024 * 1024 + 1) { 65 }))
        }
    }

    @Test fun unicodeBodyAndChallengeMetadataRemainSeparate() {
        val reply = JSONObject().put("protocol", 1).put("request_id", "id").put("ok", true)
            .put("status", 200).put("content", "测试正文").put("challenge_detected", false)
        val line = ScraplingProtocol.readLine(ByteArrayInputStream((reply.toString() + "\n").toByteArray()))!!
        val result = ScraplingProtocol.decode(line, "id")
        assertEquals("测试正文", result.getString("content"))
        assertFalse(result.getBoolean("challenge_detected"))
    }

    @Test fun unsafeErrorTextIsNotSurfacedAsAnErrorCode() {
        assertEquals("SCRAPLING_PROTOCOL_ERROR", ScraplingProtocol.safeErrorCode("token=private"))
        assertEquals("WEB_TIMEOUT", ScraplingProtocol.safeErrorCode("WEB_TIMEOUT"))
    }
}
