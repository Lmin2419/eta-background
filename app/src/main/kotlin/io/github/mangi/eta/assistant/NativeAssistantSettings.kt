package io.github.mangi.eta.assistant

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.AtomicFile
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.agent.device.BoundedRootCommandExecutor
import io.github.mangi.eta.agent.device.DeviceToolContract
import io.github.mangi.eta.agent.device.RootAccess
import io.github.mangi.eta.core.AndroidAgentLogger
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal class NativeAssistantBackupFile(context: Context) : AssistantBackupStore {
    private val file = AtomicFile(File(context.filesDir, "native_assistant_backup.json"))

    override fun load(): AssistantBackup? = try {
        val json = JSONObject(readAssistantAtomic(file).toString(Charsets.UTF_8))
        require(json.getInt("version") == 1)
        val attempted = json.getJSONArray("attempted")
        AssistantBackup(assistantSnapshotFromJson(json.getJSONObject("original")),
            json.getBoolean("roleAttempted"),
            (0 until attempted.length()).map { AssistantSetting.valueOf(attempted.getString(it)) }.toSet(),
            json.tick("appliedAt"), json.tick("confirmedAt"))
    } catch (_: FileNotFoundException) {
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) {
            throw AssistantSetupFailure("BACKUP_UNAVAILABLE")
        }
        null
    } catch (_: Exception) {
        throw AssistantSetupFailure("BACKUP_UNAVAILABLE")
    }

    override fun save(backup: AssistantBackup) {
        val json = JSONObject().put("version", 1).put("original", backup.original.toJson())
            .put("roleAttempted", backup.roleAttempted)
            .put("attempted", JSONArray(backup.attempted.map { it.name }))
            .put("appliedAt", backup.appliedAt?.toJson() ?: JSONObject.NULL)
            .put("confirmedAt", backup.confirmedAt?.toJson() ?: JSONObject.NULL)
        writeAtomic(file, json)
    }

    override fun clear() {
        file.delete()
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) {
            throw AssistantSetupFailure("BACKUP_UNAVAILABLE")
        }
    }
}

/** Visible-window evidence only; it does not claim to identify a physical power-key press. */
internal object NativeAssistantWakeEvidence {
    fun recordVisible(context: Context) {
        if (!File(context.filesDir, "native_assistant_backup.json").exists()) return
        runCatching { writeAtomic(file(context), tick(context).toJson()) }
    }

    fun read(context: Context): AssistantTick? = runCatching {
        val json = JSONObject(readAssistantAtomic(file(context)).toString(Charsets.UTF_8))
        AssistantTick(json.getInt("boot"), json.getLong("elapsed"))
    }.getOrNull()

    private fun file(context: Context) = AtomicFile(File(context.filesDir, "native_assistant_wake.json"))

    fun tick(context: Context): AssistantTick = AssistantTick(
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1),
        SystemClock.elapsedRealtime(),
    )
}

internal fun AssistantTick.toJson() = JSONObject().put("boot", boot).put("elapsed", elapsed)
internal fun JSONObject.tick(key: String): AssistantTick? = if (isNull(key)) null else getJSONObject(key).let {
    AssistantTick(it.getInt("boot"), it.getLong("elapsed"))
}

/** Readers in :voice must not let AtomicFile.openRead delete another process's in-flight .new. */
internal fun readAssistantAtomic(file: AtomicFile): ByteArray {
    val legacy = File(file.baseFile.path + ".bak")
    return (if (legacy.exists()) legacy else file.baseFile).readBytes()
}

internal fun writeAtomic(file: AtomicFile, json: JSONObject) {
    val bytes = json.toString().toByteArray(Charsets.UTF_8)
    val stream = file.startWrite()
    try {
        stream.write(bytes)
        file.finishWrite(stream)
        // AtomicFile logs rename failures instead of throwing. Never mutate system settings after
        // merely assuming the original-value journal was committed successfully.
        if (!file.baseFile.readBytes().contentEquals(bytes)) throw AssistantSetupFailure("BACKUP_UNAVAILABLE")
    } catch (failure: Exception) {
        file.failWrite(stream)
        throw AssistantSetupFailure("BACKUP_UNAVAILABLE")
    }
}

internal class NativeAssistantRootDevice(private val context: Context) : AssistantSetupDevice {
    private val userId = DeviceToolContract.appUserId(context)

    override fun read(): AssistantSnapshot = assistantSnapshotFromJson(call(JSONObject().put("operation", "read")))

    override fun changeRole(expected: List<String>, target: List<String>) {
        call(JSONObject().put("operation", "role").put("expected", JSONArray(expected)).put("target", JSONArray(target)))
    }

    override fun changeSetting(setting: AssistantSetting, expected: String?, target: String?) {
        call(JSONObject().put("operation", "setting").put("setting", setting.name)
            .put("expected", expected ?: JSONObject.NULL).put("target", target ?: JSONObject.NULL))
    }

    internal fun call(request: JSONObject, asSystem: Boolean = false): JSONObject {
        request.put("version", 1).put("user", userId)
        val launcher = "CLASSPATH=${DeviceToolContract.quote(context.applicationInfo.sourceDir)} " +
            "app_process /system/bin io.github.mangi.eta.assistant.NativeAssistantRootMain"
        // Binder records the helper's identity when the process starts. Dropping UID after the
        // first transaction cannot satisfy this firmware's vendor-policy permission check.
        val command = if (asSystem) "su 1000 -c ${DeviceToolContract.quote(launcher)}" else launcher
        val result = BoundedRootCommandExecutor(AndroidAgentLogger).use {
            it.execute(command, timeoutMillis = 30_000, maxOutputBytes = 16 * 1024,
                input = request.toString().toByteArray(Charsets.UTF_8))
        }
        if (!result.ok || result.truncated) throw AssistantSetupFailure(when {
            result.timedOut -> "COMMAND_TIMEOUT"
            result.errorCode in setOf("ROOT_REQUIRED", "ROOT_UNAVAILABLE") -> result.errorCode
            else -> "COMMAND_RESULT_UNKNOWN"
        })
        val reply = result.stdout.lineSequence().lastOrNull { it.startsWith(NativeAssistantRootMain.MARKER) }
            ?.removePrefix(NativeAssistantRootMain.MARKER)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: throw AssistantSetupFailure("COMMAND_RESULT_UNKNOWN")
        if (!reply.optBoolean("ok")) throw AssistantSetupFailure(reply.optString("code", "NATIVE_ROUTE_UNAVAILABLE"))
        return reply
    }
}

internal data class NativeAssistantUiState(
    val busy: Boolean = false,
    val status: String = "OFF",
    val backupExists: Boolean = false,
    val configured: Boolean = false,
    val canEnable: Boolean = true,
    val error: String? = null,
)

/** App-owned work survives closing the settings dialog; at most one transaction runs. */
internal object NativeAssistantSettings {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val working = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(NativeAssistantUiState())
    val state = mutableState.asStateFlow()

    fun refresh(context: Context) = run(context, "refresh")
    fun enable(context: Context) = run(context, "enable")
    fun restore(context: Context) = run(context, "restore")
    fun confirmWake(context: Context) = run(context, "confirm")

    private fun run(context: Context, action: String) {
        if (!working.compareAndSet(false, true)) return
        val app = context.applicationContext
        mutableState.value = mutableState.value.copy(busy = true, error = null)
        scope.launch {
            val store = NativeAssistantBackupFile(app)
            val device = NativeAssistantRootDevice(app)
            val oplusStore = OplusPowerBackupFile(app)
            val oplusDevice = OplusPowerRootDevice(app)
            val oplus = OplusPowerRoute(oplusDevice, oplusStore) { NativeAssistantWakeEvidence.tick(app) }
            val needsOplus = OplusPowerContract.isCandidate(Build.MODEL, Build.VERSION.SDK_INT) || OplusPowerBackupFile.exists(app)
            val setup = NativeAssistantSetup(app.packageName, DeviceToolContract.appUserId(app), device, store) {
                NativeAssistantWakeEvidence.tick(app)
            }
            var failure: String? = null
            try {
                if (action != "refresh") RootAccess.request(app).join()
                if (!RootAccess.isGranted) throw AssistantSetupFailure("ROOT_REQUIRED")
                // Connected != proven active hook, but a connected framework prevents a clean test.
                if (action in setOf("enable", "confirm") && EtaApp.serviceInstance != null) {
                    throw AssistantSetupFailure("FRAMEWORK_CONNECTED")
                }
                if (action == "enable" && !Settings.canDrawOverlays(app)) throw AssistantSetupFailure("OVERLAY_REQUIRED")
                when (action) {
                    "enable" -> {
                        if (needsOplus) {
                            val probe = oplusDevice.read()
                            if (!probe.supported) throw AssistantSetupFailure("COLOROS_FIRMWARE_UNSUPPORTED")
                            if (probe.gate !in setOf(null, "", "0")) throw AssistantSetupFailure("COLOROS_POLICY_OWNED")
                        }
                        val existing = store.load()
                        val upgrading = needsOplus && existing?.appliedAt != null && setup.configured(device.read())
                        if (!upgrading) setup.enable()
                        if (needsOplus) {
                            try {
                                oplus.enable()
                            } catch (failure: Exception) {
                                // Only undo our newly created AOSP setup after OEM rollback completed.
                                // Keep a pre-existing setup and its original backup when upgrading it.
                                if (!upgrading && oplusStore.load() == null) runCatching { setup.restore() }
                                throw failure
                            }
                        }
                    }
                    "restore" -> {
                        if (OplusPowerBackupFile.exists(app)) oplus.restore()
                        setup.restore()
                    }
                    "confirm" -> {
                        val visible = NativeAssistantWakeEvidence.read(app)
                        if (needsOplus) {
                            if (!oplus.configured(oplusDevice.read())) throw AssistantSetupFailure("CONFIGURATION_CHANGED")
                            val applied = oplusStore.load()?.appliedAt ?: throw AssistantSetupFailure("NOT_CONFIGURED")
                            if (visible == null || (!visible.follows(applied) && visible.boot <= applied.boot)) {
                                throw AssistantSetupFailure("WAKE_NOT_SEEN")
                            }
                        }
                        setup.confirmWake(visible)
                    }
                }
            } catch (error: Exception) {
                failure = (error as? AssistantSetupFailure)?.code ?: "NATIVE_ROUTE_UNAVAILABLE"
            }
            try {
                val backup = store.load()
                val snapshot = if (RootAccess.isGranted) device.read() else null
                val oplusBackup = oplusStore.load()
                val oplusSnapshot = if (needsOplus && RootAccess.isGranted) oplusDevice.read() else null
                val baseConfigured = backup?.appliedAt != null && snapshot?.let(setup::configured) == true
                val configured = baseConfigured && (!needsOplus || oplusSnapshot?.let(oplus::configured) == true)
                val confirmed = configured && backup.confirmedAt?.let {
                    val applied = oplusBackup?.appliedAt
                    it.boot >= 0 && it.boot == NativeAssistantWakeEvidence.tick(app).boot &&
                        (!needsOplus || (applied != null && (it.follows(applied) || it.boot > applied.boot)))
                } == true
                mutableState.value = NativeAssistantUiState(
                    status = when {
                        backup == null && oplusBackup == null -> "OFF"
                        snapshot == null -> "ROOT_REQUIRED"
                        backup?.appliedAt == null -> "RESTORE_FIRST"
                        needsOplus && oplusSnapshot?.supported != true -> "COLOROS_FIRMWARE_UNSUPPORTED"
                        needsOplus && baseConfigured && oplusBackup == null -> "COLOROS_ADAPTER_REQUIRED"
                        oplusBackup != null && oplusBackup.appliedAt == null -> "RESTORE_FIRST"
                        !configured -> "CONFIGURATION_CHANGED"
                        EtaApp.serviceInstance != null -> "FRAMEWORK_CONNECTED"
                        confirmed -> "CONFIRMED_BY_USER"
                        else -> "AWAITING_KEY_TEST"
                    },
                    backupExists = backup != null || oplusBackup != null, configured = configured,
                    canEnable = (backup == null && oplusBackup == null) ||
                        (baseConfigured && needsOplus && oplusBackup == null && oplusSnapshot?.supported == true),
                    error = failure ?: if (configured && needsOplus) OplusPowerWake.lastFailure(app) else null,
                )
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(busy = false, configured = false,
                    canEnable = false,
                    backupExists = File(app.filesDir, "native_assistant_backup.json").exists() || OplusPowerBackupFile.exists(app),
                    status = "CHECK_UNAVAILABLE", error = failure ?: (error as? AssistantSetupFailure)?.code ?: "NATIVE_ROUTE_UNAVAILABLE")
            } finally {
                working.set(false)
            }
        }
    }
}
