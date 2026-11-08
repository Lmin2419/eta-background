package io.github.mangi.eta.agent.remote

import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** 服务器出示的公钥与配对码里的指纹不一致：可能被中间人劫持，或服务器重新生成了证书。 */
internal class DesktopBridgePinMismatch(val expected: String, val actual: String) :
    CertificateException("服务器证书指纹与配对码不一致（期望 ${expected.take(8)}…，实际 ${actual.take(8)}…）")

/**
 * 只信任指纹匹配的那一把公钥。中转服务器用的是自签证书且只有 IP，
 * 公钥固定比 CA 体系更适合：配对码由用户从自己的服务器上复制过来，即信任锚。
 */
internal class DesktopBridgePinnedTrustManager(private val pin: String) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("服务器没有出示证书")
        val actual = spkiSha256(leaf)
        if (!MessageDigest.isEqual(actual.toByteArray(), pin.toByteArray())) throw DesktopBridgePinMismatch(pin, actual)
        leaf.checkValidity()
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("不接受客户端证书")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    companion object {
        /** 与部署脚本 `openssl x509 -pubkey | openssl pkey -pubin -outform der | sha256 | base64url` 一致。 */
        fun spkiSha256(certificate: X509Certificate): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }
    }
}

/** 有指纹时换成固定公钥校验；主机名校验交给指纹（IP 自签证书没有可信 CA）。 */
internal fun OkHttpClient.Builder.pinnedTo(pin: String?): OkHttpClient.Builder {
    if (pin.isNullOrBlank()) return this
    val trustManager = DesktopBridgePinnedTrustManager(pin)
    val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
    return sslSocketFactory(context.socketFactory, trustManager).hostnameVerifier { _, _ -> true }
}

/** 沿异常链找指纹不符：这类失败不该自动重试，要提示用户重新配对。 */
internal fun Throwable.findPinMismatch(): DesktopBridgePinMismatch? {
    var current: Throwable? = this
    repeat(8) {
        if (current is DesktopBridgePinMismatch) return current as DesktopBridgePinMismatch
        current = current?.cause ?: return null
    }
    return null
}
