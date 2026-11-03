package io.github.mangi.eta.assistant

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.AtomicFile
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OplusPowerBackupFileTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val file get() = File(context.filesDir, OplusPowerBackupFile.FILE_NAME)
    private val original = OplusPowerSnapshot(0, "", null, true, "tested-firmware")

    @Before fun clean() {
        listOf("", ".bak", ".new").forEach { File(file.path + it).delete() }
    }

    @Test fun processRecreationPreservesOriginalValuesAndIncompleteJournal() {
        val backup = OplusPowerBackup(original, broadcastAttempted = true, gateAttempted = true)
        OplusPowerBackupFile(context).save(backup)
        assertEquals(backup, OplusPowerBackupFile(context).load())
        assertNull(OplusPowerBackupFile(context).load()!!.appliedAt)
    }

    @Test fun originalAbsentGateAndEmptyGateAreNotInterchangeable() {
        val store = OplusPowerBackupFile(context)
        store.save(OplusPowerBackup(original))
        assertNull(store.load()!!.original.gate)
        store.save(OplusPowerBackup(original.copy(gate = "")))
        assertEquals("", store.load()!!.original.gate)
        store.clear()
        assertNull(store.load())
    }

    @Test fun missingOriginalGateIsCorruptionRatherThanPermissionToRemoveIt() {
        val store = OplusPowerBackupFile(context)
        store.save(OplusPowerBackup(original))
        val json = JSONObject(file.readText())
        json.getJSONObject("original").remove("gate")
        file.writeText(json.toString())
        try { store.load(); fail("Missing original gate must stop restore") }
        catch (failure: AssistantSetupFailure) { assertEquals("BACKUP_UNAVAILABLE", failure.code) }
        assertTrue(file.exists())
    }

    @Test fun broadcastReceiverRequiresAPrivilegedSenderAndUsesTheVoiceProcess() {
        val component = ComponentName(context, OplusPowerWakeReceiver::class.java)
        val info = context.packageManager.getReceiverInfo(component, 0)
        assertEquals("android.permission.DEVICE_POWER", info.permission)
        assertTrue(info.exported)
        assertTrue(info.processName.endsWith(":voice"))
    }

    @Test fun readerLeavesAnInFlightJournalWriteIntact() {
        val store = OplusPowerBackupFile(context)
        val previous = OplusPowerBackup(original)
        store.save(previous)
        val changed = JSONObject(file.readText()).put("broadcastAttempted", true)
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        stream.write(changed.toString().toByteArray(Charsets.UTF_8))
        assertEquals(previous, store.load())
        assertTrue(File(file.path + ".new").exists())
        atomic.finishWrite(stream)
        assertEquals(previous.copy(broadcastAttempted = true), store.load())
    }

    @Test fun earlyOemBroadcastCannotBypassSetupRestrictionOrUnderwaterMode() {
        val resolver = context.contentResolver
        Settings.Secure.putInt(resolver, "user_setup_complete", 0)
        assertFalse(OplusPowerWake.environmentAllowsWake(context))
        Settings.Secure.putInt(resolver, "user_setup_complete", 1)
        assertTrue(OplusPowerWake.environmentAllowsWake(context))
        Settings.Global.putInt(resolver, "oppo_show_device_locked", 1)
        assertFalse(OplusPowerWake.environmentAllowsWake(context))
        Settings.Global.putInt(resolver, "oppo_show_device_locked", 0)
        Settings.System.putInt(resolver, "under_water_camera_status", 1)
        assertFalse(OplusPowerWake.environmentAllowsWake(context))
        Settings.System.putInt(resolver, "under_water_camera_status", 0)
        assertTrue(OplusPowerWake.environmentAllowsWake(context))
    }
}
