package io.github.mangi.eta.agent.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 电脑端 dsh-bridge 的连接参数。 */
internal data class DesktopBridgeConfig(
    /** 用户填写的地址，例如 `https://desk.example.com`。 */
    val address: String,
    val token: String,
) {
    val webSocketUrl: String? get() = normalizeWebSocketUrl(address)
    val isComplete: Boolean get() = webSocketUrl != null && token.isNotBlank()

    companion object {
        /**
         * 接受 `desk.example.com`、`https://…`、`wss://…`、`http://ip:port` 等写法，统一为 `/v1/ws` 地址。
         * 没写协议时按 https 处理：经公网中转的连接必须走 TLS。
         */
        fun normalizeWebSocketUrl(address: String): String? {
            var value = address.trim().trimEnd('/')
            if (value.isEmpty()) return null
            if (!value.contains("://")) value = "https://$value"
            val scheme = value.substringBefore("://").lowercase()
            val rest = value.substringAfter("://")
            val wsScheme = when (scheme) {
                "https", "wss" -> "wss"
                "http", "ws" -> "ws"
                else -> return null
            }
            val hostAndPath = rest.removeSuffix("/v1/ws").trimEnd('/')
            if (hostAndPath.isEmpty() || hostAndPath.any { it.isWhitespace() }) return null
            return "$wsScheme://$hostAndPath/v1/ws"
        }
    }
}

/** 地址明文保存在私有偏好中；token 只以 Android Keystore 密文保存。 */
internal class DesktopBridgeConfigStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): DesktopBridgeConfig? {
        val address = preferences.getString(KEY_ADDRESS, null)?.takeIf { it.isNotBlank() } ?: return null
        return DesktopBridgeConfig(address, decryptToken().orEmpty())
    }

    @Synchronized
    fun save(config: DesktopBridgeConfig) {
        val editor = preferences.edit().putString(KEY_ADDRESS, config.address.trim())
        val token = config.token.trim()
        if (token.isEmpty()) {
            editor.remove(KEY_TOKEN)
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val payload = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            editor.putString(KEY_TOKEN, Base64.encodeToString(payload, Base64.NO_WRAP))
        }
        check(editor.commit()) { "电脑连接配置保存失败" }
    }

    @Synchronized
    fun clear() {
        check(preferences.edit().clear().commit()) { "电脑连接配置删除失败" }
    }

    private fun decryptToken(): String? {
        val encoded = preferences.getString(KEY_TOKEN, null) ?: return null
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > GCM_IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, payload, 0, GCM_IV_BYTES))
            cipher.doFinal(payload, GCM_IV_BYTES, payload.size - GCM_IV_BYTES).toString(Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "eta_desktop_bridge"
        const val KEY_ADDRESS = "address"
        const val KEY_TOKEN = "token"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "eta_desktop_bridge_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
