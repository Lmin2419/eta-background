package io.github.mangi.eta.assistant

import android.content.Context
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 换机带过来的电源键备份：属于旧手机，作废；本机的保留。备份记下当时的目标值和手机。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class ForeignPowerBackupTest {
    private lateinit var app: Context
    private val snapshot = AssistantSnapshot(0, listOf("com.miui.voiceassist"), mapOf(
        AssistantSetting.ASSISTANT to "com.miui.voiceassist/.Voice",
        AssistantSetting.VOICE to "com.miui.voiceassist/.Voice",
        AssistantSetting.RECOGNIZER to null,
        AssistantSetting.LONG_PRESS to "0",
    ))

    @Before fun setUp() {
        app = RuntimeEnvironment.getApplication()
        app.filesDir.listFiles()?.forEach { it.delete() }
    }

    private fun exists(name: String) = File(app.filesDir, name).exists()

    @Test fun backupKeepsProfileAndDevice() {
        val profile = AssistantProfile.hyper(HyperPowerContract.XIAOMI_RECOGNIZER)
        NativeAssistantBackupFile(app).save(AssistantBackup(snapshot, profile = profile, device = "Xiaomi|17 Max|x"))
        val loaded = NativeAssistantBackupFile(app).load()!!
        assertEquals(profile, loaded.profile)
        assertEquals("Xiaomi|17 Max|x", loaded.device)
        HyperPowerBackupFile(app).save(HyperPowerBackup(HyperPowerSnapshot(0, null), attempted = true, device = "Xiaomi|17 Max|x"))
        assertEquals(HyperPowerBackup(HyperPowerSnapshot(0, null), attempted = true, device = "Xiaomi|17 Max|x"),
            HyperPowerBackupFile(app).load())
    }

    @Test fun backupsFromThisPhoneStay() {
        NativeAssistantBackupFile(app).save(AssistantBackup(snapshot, device = "me"))
        HyperPowerBackupFile(app).save(HyperPowerBackup(HyperPowerSnapshot(0, "launch_voice_assistant"), device = "me"))
        assertFalse(NativeAssistantSettings.discardForeignBackups(app, "me"))
        assertNotNull(NativeAssistantBackupFile(app).load())
        assertNotNull(HyperPowerBackupFile(app).load())
    }

    @Test fun backupsFromAnotherPhoneAreDropped() {
        NativeAssistantBackupFile(app).save(AssistantBackup(snapshot, device = "old"))
        HyperPowerBackupFile(app).save(HyperPowerBackup(HyperPowerSnapshot(0, "launch_voice_assistant"), device = "old"))
        assertTrue(NativeAssistantSettings.discardForeignBackups(app, "new"))
        assertNull(NativeAssistantBackupFile(app).load())
        assertNull(HyperPowerBackupFile(app).load())
    }

    @Test fun legacyColorOsPairFromAnotherPhoneIsDropped() {
        // 老版本的备份不记手机；ColorOS 那份的构建指纹是一加的，这台不是。
        NativeAssistantBackupFile(app).save(AssistantBackup(snapshot))
        OplusPowerBackupFile(app).save(OplusPowerBackup(OplusPowerSnapshot(0, "1", "1", true,
            "OnePlus/PMA110/OP5D0DL1:17/AP3A/1:user/release-keys")))
        assertTrue(NativeAssistantSettings.discardForeignBackups(app, "new"))
        assertFalse(exists(OplusPowerBackupFile.FILE_NAME))
        assertFalse(exists("native_assistant_backup.json"))
    }
}
