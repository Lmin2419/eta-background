package io.github.mangi.eta.assistant

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import org.json.JSONObject

internal class HyperPowerBackupFile(context: Context) : HyperPowerStore {
    private val file = AtomicFile(File(context.filesDir, FILE_NAME))

    override fun load(): HyperPowerBackup? = try {
        val json = JSONObject(readAssistantAtomic(file).toString(Charsets.UTF_8))
        require(json.getInt("version") == 1)
        val original = json.getJSONObject("original")
        HyperPowerBackup(HyperPowerSnapshot(original.getInt("user"), original.nullableString("action")),
            json.getBoolean("attempted"), json.tick("appliedAt"),
            if (json.isNull("device")) null else json.getString("device"))
    } catch (_: FileNotFoundException) {
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw AssistantSetupFailure("BACKUP_UNAVAILABLE")
        null
    } catch (_: Exception) {
        throw AssistantSetupFailure("BACKUP_UNAVAILABLE")
    }

    override fun save(backup: HyperPowerBackup) = writeAtomic(file, JSONObject().put("version", 1)
        .put("original", JSONObject().put("user", backup.original.user).put("action", backup.original.action ?: JSONObject.NULL))
        .put("attempted", backup.attempted)
        .put("appliedAt", backup.appliedAt?.toJson() ?: JSONObject.NULL)
        .put("device", backup.device ?: JSONObject.NULL))

    override fun clear() {
        file.delete()
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw AssistantSetupFailure("BACKUP_UNAVAILABLE")
    }

    companion object {
        const val FILE_NAME = "native_hyper_power_backup.json"
        fun exists(context: Context): Boolean = File(context.filesDir, FILE_NAME).exists() ||
            File(context.filesDir, "$FILE_NAME.bak").exists()
    }
}

internal class HyperPowerRootDevice(context: Context) : HyperPowerDevice {
    private val root = NativeAssistantRootDevice(context)
    override fun read(): HyperPowerSnapshot {
        val json = root.call(JSONObject().put("operation", "hyper_read"))
        return HyperPowerSnapshot(json.getInt("user"), json.nullableString("action"))
    }
    override fun changeAction(expected: String?, target: String?) {
        root.call(JSONObject().put("operation", "hyper_action").put("expected", expected ?: JSONObject.NULL)
            .put("target", target ?: JSONObject.NULL))
    }
}

/** 适配方式的选择（自动 / 指定），以及这台手机的识别结果。 */
internal object PowerKeyAdapterSettings {
    private const val PREFS = "native_assistant"
    private const val KEY = "power_key_adapter"

    fun choice(context: Context): PowerKeyAdapter = runCatching {
        PowerKeyAdapter.valueOf(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: "AUTO")
    }.getOrDefault(PowerKeyAdapter.AUTO)

    fun setChoice(context: Context, choice: PowerKeyAdapter) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, choice.name).apply()
    }

    fun detected(): PowerKeyAdapter = PowerKeyVendor.detect(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty(), ::property)

    fun resolved(context: Context): PowerKeyAdapter = PowerKeyVendor.resolve(choice(context), detected())

    fun deviceId(): String = PowerKeyVendor.deviceId(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty(), Build.DEVICE.orEmpty())

    /** 小米自带语音识别引擎在不在；不在就保持原来的识别服务。 */
    fun xiaomiRecognizer(context: Context): String? = HyperPowerContract.XIAOMI_RECOGNIZER.takeIf {
        runCatching {
            context.packageManager.getServiceInfo(ComponentName.unflattenFromString(it)!!, 0)
        }.isSuccess
    }

    private fun property(name: String): String = runCatching {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java, String::class.java)
            .invoke(null, name, "") as String
    }.getOrDefault("")
}
