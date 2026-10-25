package io.github.mangi.eta.data.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.model.SpeechCredentials
import io.github.mangi.eta.data.model.SpeechSettings
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

internal object SpeechSettingsRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun settingsFlow(): Flow<SpeechSettings> = SettingsDataStore.speechSettingsFlow()
    suspend fun settings(): SpeechSettings = SettingsDataStore.speechSettings()
    suspend fun save(settings: SpeechSettings) = SettingsDataStore.setSpeechSettings(settings)

    suspend fun credentials(context: Context): SpeechCredentials = withContext(Dispatchers.IO) {
        val encoded = context.getSharedPreferences("eta_speech_secrets", Context.MODE_PRIVATE)
            .getString("credentials", null) ?: return@withContext SpeechCredentials()
        try {
            val bytes = Base64.getDecoder().decode(encoded)
            require(bytes.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, 12))
            json.decodeFromString<SpeechCredentials>(
                cipher.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8),
            )
        } catch (_: Exception) {
            throw SpeechCredentialsUnavailable()
        }
    }

    suspend fun saveCredentials(context: Context, credentials: SpeechCredentials) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(json.encodeToString(credentials).toByteArray(Charsets.UTF_8))
        check(context.getSharedPreferences("eta_speech_secrets", Context.MODE_PRIVATE).edit()
            .putString("credentials", Base64.getEncoder().encodeToString(encrypted)).commit()) {
            "语音凭据保存失败"
        }
    }

    @Synchronized
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("eta_speech_credentials_v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("eta_speech_credentials_v1",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
}

internal class SpeechCredentialsUnavailable : Exception("语音凭据无法解密，请重新填写并保存")
