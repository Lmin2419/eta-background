package io.github.mangi.eta.agent.web

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebResponseDecoderTest {
    @Test fun bomOverridesHttpCharsetAndHtmlMeta() {
        val text = "<meta charset='GBK'><p>中文😀</p>"
        val bytes = byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + text.toByteArray(Charsets.UTF_8)
        val decoded = decode(bytes, "text/html; charset=GBK")
        assertEquals(text, decoded.text)
        assertEquals("UTF-8", decoded.charset)
        assertFalse(decoded.lossy)
        assertEquals(text, decode(bytes, "text/html; charset=not-a-charset").text)
    }

    @Test fun httpCharsetOverridesConflictingHtmlMetaForChineseContent() {
        val text = "<meta charset='UTF-8'><article>中文说明</article>"
        val decoded = decode(text.toByteArray(Charset.forName("GBK")), "text/html; Charset=\"GBK\"")
        assertEquals(text, decoded.text)
        assertEquals("GBK", decoded.charset)
        assertFalse(decoded.lossy)
    }

    @Test fun htmlMetaSupportsCharsetAndHttpEquivDeclarations() {
        val documents = listOf(
            "<html><head><meta charset='GBK'></head><body>中文正文</body></html>",
            "<html><head><meta HTTP-EQUIV='Content-Type' content='text/html; charset=GBK'></head><body>中文正文</body></html>",
        )
        for (text in documents) {
            val decoded = decode(text.toByteArray(Charset.forName("GBK")), "text/html")
            assertEquals(text, decoded.text)
            assertEquals("GBK", decoded.charset)
            assertFalse(decoded.lossy)
        }
    }

    @Test fun statefulHtmlEncodingIsNotRejectedBecauseItUsesEscapeBytes() {
        val text = "<html><head><meta charset='ISO-2022-JP'></head><body>日本語</body></html>"
        val decoded = decode(text.toByteArray(Charset.forName("ISO-2022-JP")), null)
        assertEquals("text/html", decoded.contentType)
        assertEquals(text, decoded.text)
        assertFalse(decoded.lossy)
    }

    @Test fun missingContentTypeRecognizesHtmlAfterUtf8OrUtf16Bom() {
        val text = "<!doctype html><html><body><main>中文网页😀</main></body></html>"
        val encodings = listOf(
            "UTF-8" to byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()),
            "UTF-16LE" to byteArrayOf(0xff.toByte(), 0xfe.toByte()),
            "UTF-16BE" to byteArrayOf(0xfe.toByte(), 0xff.toByte()),
        )
        for ((encoding, bom) in encodings) {
            val decoded = decode(bom + text.toByteArray(Charset.forName(encoding)), null)
            assertEquals("text/html", decoded.contentType)
            assertEquals(text, decoded.text)
            val page = WebPageContent.extract(decoded.text, decoded.contentType, "https://example.com/page")
            assertEquals("中文网页😀", page.content)
        }
    }

    @Test fun absentMimeDistinguishesHtmlFragmentsFromPlainTextAndExplicitMimeWins() {
        val html = "  <main>正文</main>"
        assertEquals("text/html", decode(html.toByteArray(), null).contentType)
        assertEquals("text/plain", decode("普通文本\n第二行".toByteArray(), null).contentType)
        assertEquals("text/plain", decode("<binary>文字范例".toByteArray(), null).contentType)
        val plain = decode(html.toByteArray(), "text/plain; charset=utf-8")
        assertEquals("text/plain", plain.contentType)
        assertEquals(html, plain.text)
    }

    @Test fun utf8DownloadCutDropsOnlyIncompleteTrailingCharacterWithoutLossyFlag() {
        val prefix = "已读中文"
        val character = "😀".toByteArray(Charsets.UTF_8)
        for (count in 1..3) {
            val decoded = decode(prefix.toByteArray(Charsets.UTF_8) + character.take(count), "text/plain", truncated = true)
            assertEquals(prefix, decoded.text)
            assertFalse(decoded.lossy)
        }
    }

    @Test fun utf16DownloadCutNeverLeaksHalfCodeUnitOrSurrogate() {
        for (encoding in listOf("UTF-16LE", "UTF-16BE")) {
            val charset = Charset.forName(encoding)
            val prefix = "已读"
            val character = "😀".toByteArray(charset)
            for (count in 1..3) {
                val decoded = decode(prefix.toByteArray(charset) + character.take(count), "text/plain; charset=$encoding", truncated = true)
                assertEquals(prefix, decoded.text)
                assertFalse(decoded.lossy)
            }
        }
    }

    @Test fun gbkCutAndMalformedCompleteResponseHaveDifferentLossyStatus() {
        val charset = Charset.forName("GBK")
        val incomplete = "A中".toByteArray(charset).dropLast(1).toByteArray()
        val cut = decode(incomplete, "text/plain; charset=GBK", truncated = true)
        assertEquals("A", cut.text)
        assertFalse(cut.lossy)
        val malformed = decode(incomplete, "text/plain; charset=GBK", truncated = false)
        assertEquals("A\ufffd", malformed.text)
        assertTrue(malformed.lossy)
    }

    @Test fun genuineMalformedBytesAreMarkedLossyEvenWhenDownloadIsAlsoTruncated() {
        val bytes = byteArrayOf(0x41, 0xc3.toByte(), 0x28, 0x42, 0xe2.toByte(), 0x82.toByte())
        val decoded = decode(bytes, "text/plain; charset=utf-8", truncated = true)
        assertEquals("A\ufffd(B", decoded.text)
        assertTrue(decoded.lossy)
        val completeMalformed = decode("A".toByteArray() + "😀".toByteArray().take(3), "text/plain", truncated = false)
        assertTrue(completeMalformed.lossy)
        assertEquals("A\ufffd", completeMalformed.text)
    }

    @Test fun binarySignaturesWithoutMimeDoNotBecomePlainText() {
        val binaries = listOf(
            "%PDF-1.7\n1 0 obj".toByteArray(),
            "GIF89a".toByteArray(),
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
            byteArrayOf(0x50, 0x4b, 0x03, 0x04),
            byteArrayOf(0x41, 0x00, 0x42),
        )
        for (bytes in binaries) assertFailure("BINARY_CONTENT") { decode(bytes, null) }
        assertFailure("BINARY_CONTENT") { decode(byteArrayOf(0x41, 0x00, 0x42), "text/plain") }
    }

    @Test fun unsupportedMimeOrCharsetHasAnExplicitFailure() {
        for (type in listOf("application/pdf", "image/png", "application/octet-stream", "application/zip")) {
            assertFailure("UNSUPPORTED_CONTENT_TYPE") { decode("sample".toByteArray(), type) }
        }
        assertFailure("UNSUPPORTED_CHARSET") { decode("text".toByteArray(), "text/plain; charset=eta-nonexistent") }
    }

    @Test fun jsonBomIsRemovedButOriginalSpacingIsPreserved() {
        val text = " {\n  \"message\": \"中文\"\n}\n"
        val decoded = decode(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + text.toByteArray(), "application/problem+json")
        assertEquals(text, decoded.text)
        assertEquals("application/problem+json", decoded.contentType)
        assertFalse(decoded.lossy)
    }

    @Test fun emptyBodyAndDecodeBudgetAreHandledWithoutUnboundedAllocation() {
        assertEquals("", decode(byteArrayOf(), null).text)
        assertFailure("WEB_DECODE_LIMIT") { decode(ByteArray(WebHttpTransport.MAX_RESPONSE_BYTES + 1), "text/plain") }
    }

    private fun decode(bytes: ByteArray, contentType: String?, truncated: Boolean = false): DecodedWebResponse =
        WebResponseDecoder.decode(WebHttpResponse("https://example.com", "https://example.com", 200, contentType, bytes, truncated))

    private fun assertFailure(code: String, block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected $code")
        } catch (error: WebRequestException) { assertEquals(code, error.code) }
    }
}
