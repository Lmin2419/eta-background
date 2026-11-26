package io.github.mangi.eta.assistant

/**
 * HyperOS（小米、红米、POCO）的长按电源键：小米不看 AOSP 的 `power_button_long_press`，而是看系统设置
 * `long_press_power_key`（「快捷手势 › 长按电源键」）。默认是 `launch_voice_assistant`（唤醒小爱），
 * 改成 `launch_google_search` 时系统呼出**当前的默认数字助理**——胖小胖由 [NativeAssistantSetup] 设为默认助理。
 * 只改这一项，改前备份原值，恢复时原样写回。
 */
internal object HyperPowerContract {
    const val NAMESPACE = "system"
    const val KEY = "long_press_power_key"
    const val ASSISTANT_ACTION = "launch_google_search"
    /** 小米自带的语音识别引擎。识别服务不是它时，HyperOS 开机会把语音助手重置回小爱。 */
    const val XIAOMI_RECOGNIZER = "com.xiaomi.mibrain.speech/com.xiaomi.mibrain.speech.asr.AsrService"
    private val VALUE = Regex("[A-Za-z0-9_]{1,48}")

    fun validValue(value: String?) = value == null || value == "" || VALUE.matches(value)
}

internal data class HyperPowerSnapshot(val user: Int, val action: String?)

internal data class HyperPowerBackup(
    val original: HyperPowerSnapshot,
    val attempted: Boolean = false,
    val appliedAt: AssistantTick? = null,
    val device: String? = null,
)

internal interface HyperPowerDevice {
    fun read(): HyperPowerSnapshot
    fun changeAction(expected: String?, target: String?)
}

internal interface HyperPowerStore {
    fun load(): HyperPowerBackup?
    fun save(backup: HyperPowerBackup)
    fun clear()
}

internal class HyperPowerRoute(
    private val device: HyperPowerDevice,
    private val store: HyperPowerStore,
    private val deviceId: String? = null,
    private val now: () -> AssistantTick,
) {
    fun configured(snapshot: HyperPowerSnapshot): Boolean =
        snapshot.action == HyperPowerContract.ASSISTANT_ACTION && store.load()?.appliedAt != null

    /** 小米把长按动作改回去了（常见于重启后重置语音助手）：配置过、但现在不是我们设的值。 */
    fun reverted(snapshot: HyperPowerSnapshot): Boolean =
        store.load()?.appliedAt != null && snapshot.action != HyperPowerContract.ASSISTANT_ACTION

    fun enable() {
        if (store.load() != null) throw AssistantSetupFailure("RESTORE_FIRST")
        val original = device.read()
        if (!HyperPowerContract.validValue(original.action)) throw AssistantSetupFailure("UNSUPPORTED_SETTING_VALUE")
        var backup = HyperPowerBackup(original, device = deviceId)
        store.save(backup)
        try {
            if (original.action != HyperPowerContract.ASSISTANT_ACTION) {
                backup = backup.copy(attempted = true)
                store.save(backup)
                device.changeAction(original.action, HyperPowerContract.ASSISTANT_ACTION)
            }
            val live = device.read()
            owned(live, backup)
            if (live.action != HyperPowerContract.ASSISTANT_ACTION) throw AssistantSetupFailure("CONFIGURATION_NOT_APPLIED")
            store.save(backup.copy(appliedAt = now()))
        } catch (failure: Exception) {
            if (failure !is AssistantSetupFailure || failure.code !in uncertainCodes) runCatching { restore() }
            throw failure
        }
    }

    fun restore() {
        val backup = store.load() ?: return
        val live = device.read()
        if (live.user != backup.original.user) throw AssistantSetupFailure("USER_CHANGED")
        // 先标记为未生效，恢复到一半也不会被当成「已配置」。
        store.save(backup.copy(appliedAt = null))
        // 小米自己改回去的值（比如重启后回到唤醒小爱）不是我们写的，但恢复的目标就是原值，照常写回。
        if (live.action != backup.original.action) {
            device.changeAction(live.action, backup.original.action)
            if (device.read().action != backup.original.action) throw AssistantSetupFailure("RESTORE_INCOMPLETE")
        }
        store.clear()
    }

    private fun owned(live: HyperPowerSnapshot, backup: HyperPowerBackup) {
        if (live.user != backup.original.user) throw AssistantSetupFailure("USER_CHANGED")
        val allowed = mutableSetOf(backup.original.action)
        if (backup.attempted) allowed.add(HyperPowerContract.ASSISTANT_ACTION)
        if (live.action !in allowed) throw AssistantSetupFailure("CONFIGURATION_CHANGED")
    }

    private companion object {
        val uncertainCodes = setOf("COMMAND_TIMEOUT", "COMMAND_RESULT_UNKNOWN", "ROOT_REQUIRED", "ROOT_UNAVAILABLE",
            "CONFIGURATION_CHANGED")
    }
}

/** 电源键适配方式：自动按机型判断，或用户手动指定。 */
internal enum class PowerKeyAdapter { AUTO, COLOROS, HYPEROS, GENERIC }

internal object PowerKeyVendor {
    private val xiaomiBrands = setOf("xiaomi", "redmi", "poco", "blackshark")
    private val oplusBrands = setOf("oppo", "oneplus", "realme")

    /**
     * 按厂商、品牌和系统属性判断。HyperOS 有 `ro.mi.os.version.name`（MIUI 时代是 `ro.miui.ui.version.name`），
     * ColorOS 有 `ro.build.version.oplusrom`。都认不出时走通用（AOSP 默认助手）。
     */
    fun detect(manufacturer: String, brand: String, property: (String) -> String): PowerKeyAdapter {
        val names = setOf(manufacturer.lowercase(), brand.lowercase())
        return when {
            property("ro.mi.os.version.name").isNotBlank() || property("ro.miui.ui.version.name").isNotBlank() ||
                names.any { it in xiaomiBrands } -> PowerKeyAdapter.HYPEROS
            property("ro.build.version.oplusrom").isNotBlank() || names.any { it in oplusBrands } -> PowerKeyAdapter.COLOROS
            else -> PowerKeyAdapter.GENERIC
        }
    }

    fun resolve(choice: PowerKeyAdapter, detected: PowerKeyAdapter): PowerKeyAdapter =
        if (choice == PowerKeyAdapter.AUTO) detected else choice

    /** 备份里记的手机标识：同一台手机系统更新后不变，换了手机就不同。 */
    fun deviceId(manufacturer: String, model: String, device: String) = "$manufacturer|$model|$device"
}
