package io.github.mangi.eta.assistant

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.os.UserManager
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.util.AtomicFile
import io.github.mangi.eta.agent.device.DeviceToolContract
import io.github.mangi.eta.agent.voice.EtaVoiceInteractionService
import java.io.File
import java.io.FileNotFoundException
import org.json.JSONObject

internal class OplusPowerBackupFile(context: Context) : OplusPowerStore {
    private val file = AtomicFile(File(context.filesDir, FILE_NAME))

    override fun load(): OplusPowerBackup? = try {
        val json = JSONObject(readAssistantAtomic(file).toString(Charsets.UTF_8))
        require(json.getInt("version") == 1)
        OplusPowerBackup(snapshot(json.getJSONObject("original")), json.getBoolean("broadcastAttempted"),
            json.getBoolean("gateAttempted"), json.tick("appliedAt"))
    } catch (_: FileNotFoundException) {
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw AssistantSetupFailure("BACKUP_UNAVAILABLE")
        null
    } catch (_: Exception) {
        throw AssistantSetupFailure("BACKUP_UNAVAILABLE")
    }

    override fun save(backup: OplusPowerBackup) = writeAtomic(file,
        JSONObject().put("version", 1).put("original", backup.original.toJson())
            .put("broadcastAttempted", backup.broadcastAttempted).put("gateAttempted", backup.gateAttempted)
            .put("appliedAt", backup.appliedAt?.toJson() ?: JSONObject.NULL))

    override fun clear() {
        file.delete()
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw AssistantSetupFailure("BACKUP_UNAVAILABLE")
    }

    companion object {
        const val FILE_NAME = "native_oplus_power_backup.json"
        fun exists(context: Context): Boolean = File(context.filesDir, FILE_NAME).exists() ||
            File(context.filesDir, "$FILE_NAME.bak").exists()
    }
}

internal class OplusPowerRootDevice(context: Context) : OplusPowerDevice {
    private val root = NativeAssistantRootDevice(context)
    override fun read(): OplusPowerSnapshot = snapshot(root.call(JSONObject().put("operation", "oplus_read")))
    override fun changeBroadcast(expected: String, target: String) {
        root.call(JSONObject().put("operation", "oplus_property").put("expected", expected).put("target", target))
    }
    override fun changeGate(expected: String?, target: String?) {
        root.call(JSONObject().put("operation", "oplus_policy").put("expected", expected ?: JSONObject.NULL)
            .put("target", target ?: JSONObject.NULL), asSystem = true)
    }
}

private fun snapshot(json: JSONObject) = OplusPowerSnapshot(json.getInt("user"), json.getString("broadcast"),
    json.nullableString("gate"), json.getBoolean("supported"), json.getString("fingerprint"))
private fun OplusPowerSnapshot.toJson(): JSONObject = JSONObject().put("user", user).put("broadcast", broadcast)
    .put("gate", gate ?: JSONObject.NULL).put("supported", supported).put("fingerprint", fingerprint)

internal object OplusPowerWake {
    private var lastAccepted = -1L

    fun armed(context: Context): Boolean = runCatching {
        OplusPowerContract.isCandidate(Build.MODEL, Build.VERSION.SDK_INT) &&
            DeviceToolContract.appUserId(context) == 0 &&
            context.getSystemService(UserManager::class.java)?.isUserUnlocked == true &&
            environmentAllowsWake(context) &&
            OplusPowerBackupFile(context).load()?.let { it.appliedAt != null && it.original.supported &&
                it.original.fingerprint == Build.FINGERPRINT } == true &&
            VoiceInteractionService.isActiveService(context, ComponentName(context, EtaVoiceInteractionService::class.java))
    }.getOrDefault(false)

    // The OEM emits its broadcast before checking these conditions in its own speech path.
    // Preserve those restrictions rather than opening our UI during setup or underwater capture.
    internal fun environmentAllowsWake(context: Context): Boolean = runCatching {
        val resolver = context.contentResolver
        Settings.Secure.getInt(resolver, "user_setup_complete", 0) == 1 &&
            Settings.Global.getInt(resolver, "oppo_show_device_locked", 0) == 0 &&
            (Settings.System.getInt(resolver, "under_water_camera_status", 0) and 1) == 0
    }.getOrDefault(false)

    fun accept(context: Context): Boolean {
        if (!armed(context)) return false
        val now = SystemClock.elapsedRealtime()
        if (lastAccepted >= 0 && now - lastAccepted in 0..1000) return false
        lastAccepted = now
        record(context, "POWER_EVENT_RECEIVED")
        return true
    }

    fun record(context: Context, code: String) {
        runCatching { writeAtomic(file(context), NativeAssistantWakeEvidence.tick(context).toJson().put("code", code)) }
    }

    fun lastFailure(context: Context): String? = runCatching {
        val json = JSONObject(readAssistantAtomic(file(context)).toString(Charsets.UTF_8))
        val applied = OplusPowerBackupFile(context).load()?.appliedAt ?: return@runCatching null
        val event = AssistantTick(json.getInt("boot"), json.getLong("elapsed"))
        val current = NativeAssistantWakeEvidence.tick(context)
        json.getString("code").takeIf { event.boot == current.boot &&
            (event.follows(applied) || event.boot > applied.boot) && it == "ASSISTANT_SERVICE_NOT_READY" }
    }.getOrNull()

    private fun file(context: Context) = AtomicFile(File(context.filesDir, "native_oplus_power_event.json"))
}

/** Manifest requires DEVICE_POWER from the sender; ordinary apps cannot forge these broadcasts. */
class OplusPowerWakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != OplusPowerContract.ACTION || !OplusPowerWake.accept(context)) return
        EtaVoiceInteractionService.requestPowerSession(context)
    }
}
