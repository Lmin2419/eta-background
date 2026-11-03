package io.github.mangi.eta.assistant

import android.content.Context
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NativeAssistantBackupFileTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val file get() = File(context.filesDir, "native_assistant_backup.json")
    private val backup = AssistantBackup(AssistantSnapshot(0, emptyList(), mapOf(
        AssistantSetting.ASSISTANT to null,
        AssistantSetting.VOICE to "",
        AssistantSetting.RECOGNIZER to "com.example/.Recognizer",
        AssistantSetting.LONG_PRESS to "1",
    )), roleAttempted = true, attempted = setOf(AssistantSetting.LONG_PRESS),
        appliedAt = AssistantTick(2, 700), confirmedAt = null)

    @Before fun cleanFiles() {
        listOf("", ".bak", ".new").forEach { File(file.path + it).delete() }
        File(context.filesDir, "native_assistant_wake.json").delete()
    }

    @Test fun roundTripPreservesNullEmptyAndPendingConfirmation() {
        NativeAssistantBackupFile(context).save(backup)
        assertEquals(backup, NativeAssistantBackupFile(context).load())
        NativeAssistantBackupFile(context).clear()
        assertNull(NativeAssistantBackupFile(context).load())
    }

    @Test fun malformedBackupIsNotTreatedAsNoBackup() {
        file.writeText("{incomplete")
        try {
            NativeAssistantBackupFile(context).load()
            fail("Corrupt backup must block enable")
        } catch (e: AssistantSetupFailure) { assertEquals("BACKUP_UNAVAILABLE", e.code) }
        assertEquals("{incomplete", file.readText())
    }

    @Test fun missingSettingDoesNotTurnIntoADeleteDuringRestoration() {
        NativeAssistantBackupFile(context).save(backup)
        val json = org.json.JSONObject(file.readText())
        json.getJSONObject("original").getJSONObject("settings").remove("ASSISTANT")
        file.writeText(json.toString())
        try {
            NativeAssistantBackupFile(context).load()
            fail("Missing original field must not be restored as null")
        } catch (e: AssistantSetupFailure) { assertEquals("BACKUP_UNAVAILABLE", e.code) }
    }

    @Test fun normalAssistantUseDoesNotCreateWakeEvidenceWhenFeatureIsOff() {
        NativeAssistantWakeEvidence.recordVisible(context)
        assertFalse(File(context.filesDir, "native_assistant_wake.json").exists())
    }
}
