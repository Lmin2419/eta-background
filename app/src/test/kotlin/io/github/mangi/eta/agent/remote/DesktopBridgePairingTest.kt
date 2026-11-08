package io.github.mangi.eta.agent.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class DesktopBridgePairingTest {
    private val pin = "N2TkHHzy6chvJjOrXeAGUIAip6Qexz5K5IKr1knR1_w"

    /** 测试专用自签证书；[pin] 由部署脚本同一条 openssl 管道算出，用来核对 App 与脚本算法一致。 */
    private val fixture = """
        -----BEGIN CERTIFICATE-----
        MIIBnjCCAUSgAwIBAgIUP4M/7fyj9u1HMUU3AH6emUUaF2EwCgYIKoZIzj0EAwIw
        GzEZMBcGA1UEAwwQcHhwLXRlc3QtZml4dHVyZTAgFw0yNjExMDgxMzMzNTBaGA8y
        MTI2MTAxNTEzMzM1MFowGzEZMBcGA1UEAwwQcHhwLXRlc3QtZml4dHVyZTBZMBMG
        ByqGSM49AgEGCCqGSM49AwEHA0IABBHtWMmkRtE3aB6NoUk8fSnM4qfT/6PSCXHN
        XqwDvhRnI6e2BW4Rq7bgXGA9T0VYp/rYSLzIMoSG4gU+EbVDAFijZDBiMB0GA1Ud
        DgQWBBQGnZ9Lfe6ee4dG+vYH1UyRjlu/YDAfBgNVHSMEGDAWgBQGnZ9Lfe6ee4dG
        +vYH1UyRjlu/YDAPBgNVHRMBAf8EBTADAQH/MA8GA1UdEQQIMAaHBMsAcQcwCgYI
        KoZIzj0EAwIDSAAwRQIhAJ4aLKnt8qcORyP2tt9TWt8BpC/7A+zcY5z/ulX4rE0D
        AiBUIDmIWFXbd6FwxjTZP6TGYIZbpwxwNlCxJ5IV0PCl9g==
        -----END CERTIFICATE-----
    """.trimIndent()

    private fun certificate(): X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(fixture.byteInputStream()) as X509Certificate

    @Test
    fun pairingCodeFillsAddressPinAndToken() {
        val config = DesktopBridgeConfig.fromPairingCode("  pxp://8.163.249.159:8443?pin=$pin&token=abc-DEF_123  ")
        assertNotNull(config)
        assertEquals("https://8.163.249.159:8443", config!!.address)
        assertEquals("wss://8.163.249.159:8443/v1/ws", config.webSocketUrl)
        assertEquals(pin, config.pin)
        assertEquals("abc-DEF_123", config.token)
        assertTrue(config.isComplete)
        assertFalse("token stays out of logs", config.toString().contains("abc-DEF_123"))
    }

    @Test
    fun invalidPairingCodesAreRejected() {
        assertNull(DesktopBridgeConfig.fromPairingCode("https://8.163.249.159:8443"))
        assertNull(DesktopBridgeConfig.fromPairingCode("pxp://8.163.249.159:8443?token=abc"))
        assertNull(DesktopBridgeConfig.fromPairingCode("pxp://8.163.249.159:8443?pin=short&token=abc"))
        assertNull(DesktopBridgeConfig.fromPairingCode("pxp://8.163.249.159:8443?pin=$pin"))
        assertNull(DesktopBridgeConfig.fromPairingCode("pxp://?pin=$pin&token=abc"))
    }

    @Test
    fun builtInServerIsTheDeployedRelay() {
        assertEquals("wss://8.163.249.159:8443/v1/ws", DesktopBridgeConfig.normalizeWebSocketUrl(DesktopBridgeDefaults.SERVER_ADDRESS))
    }

    @Test
    fun appFingerprintMatchesTheServerScript() {
        assertEquals(pin, DesktopBridgePinnedTrustManager.spkiSha256(certificate()))
    }

    @Test
    fun pinnedTrustManagerAcceptsOnlyThePinnedKey() {
        DesktopBridgePinnedTrustManager(pin).checkServerTrusted(arrayOf(certificate()), "ECDHE_ECDSA")
        val wrong = "A".repeat(43)
        try {
            DesktopBridgePinnedTrustManager(wrong).checkServerTrusted(arrayOf(certificate()), "ECDHE_ECDSA")
            fail("mismatched pin must be rejected")
        } catch (error: CertificateException) {
            val mismatch = error.findPinMismatch()
            assertNotNull(mismatch)
            assertEquals(pin, mismatch!!.actual)
        }
        try {
            DesktopBridgePinnedTrustManager(pin).checkServerTrusted(emptyArray(), "ECDHE_ECDSA")
            fail("empty chain must be rejected")
        } catch (_: CertificateException) {
        }
    }

    @Test
    fun pinMismatchIsFoundThroughWrappedExceptions() {
        val wrapped = javax.net.ssl.SSLHandshakeException("handshake failed").apply {
            initCause(RuntimeException(DesktopBridgePinMismatch(pin, "x")))
        }
        assertNotNull(wrapped.findPinMismatch())
        assertNull(RuntimeException("other").findPinMismatch())
    }
}
