package io.github.mangi.eta.assistant

/** Firmware-specific semantic long-press route; never intercepts raw power-key events. */
internal object OplusPowerContract {
    const val ACTION = "oppo.intent.action.power.longpress"
    const val PROPERTY = "persist.sys.custom.enable.powerkeybroadcast"
    const val POLICY = "breeno_disable_key"
    /** 发出长按广播的厂商框架代码可能在这两个 jar 里。 */
    val FRAMEWORKS = listOf("/system/framework/oplus-services.jar", "/system/framework/services.jar")

    fun isCandidate(model: String, sdk: Int) = model == "PMA110" && sdk == 37

    /**
     * 同一机型、同一 Android 大版本（SDK）就认。以前还要求 oplus-services.jar 的 SHA-256 与核对过的那一版完全一致，
     * 系统小版本更新（框架 jar 必然变）后就判成「固件不匹配」、电源键失效（2026111608 真机反馈）。
     * 现在改查框架里还有没有这条长按通道：[frameworkHasRoute] 为 false（读得到框架代码、却找不到长按广播）才拒绝；
     * 读不到（代码被预编译剥离）时不据此拒绝，配置后仍要实际长按确认，随时可以恢复原配置。
     */
    fun supported(model: String, sdk: Int, user: Int, frameworkHasRoute: Boolean?) =
        isCandidate(model, sdk) && user == 0 && frameworkHasRoute != false
}

internal data class OplusPowerSnapshot(val user: Int, val broadcast: String, val gate: String?, val supported: Boolean,
    val fingerprint: String = "")
internal data class OplusPowerBackup(
    val original: OplusPowerSnapshot,
    val broadcastAttempted: Boolean = false,
    val gateAttempted: Boolean = false,
    val appliedAt: AssistantTick? = null,
)

internal interface OplusPowerDevice {
    fun read(): OplusPowerSnapshot
    fun changeBroadcast(expected: String, target: String)
    fun changeGate(expected: String?, target: String?)
}

internal interface OplusPowerStore {
    fun load(): OplusPowerBackup?
    fun save(backup: OplusPowerBackup)
    fun clear()
}

internal class OplusPowerRoute(
    private val device: OplusPowerDevice,
    private val store: OplusPowerStore,
    private val now: () -> AssistantTick,
) {
    // 不再比对构建指纹：小版本更新后指纹会变，但属性与厂商策略都保留，长按通道照常可用。
    // 大版本更新由 isCandidate 的 SDK 判断挡住；固件里没了这条通道时 supported 为 false。
    fun configured(snapshot: OplusPowerSnapshot): Boolean = snapshot.user == 0 && snapshot.supported &&
        snapshot.broadcast == "1" && snapshot.gate == "1" && store.load()?.appliedAt != null

    fun enable() {
        if (store.load() != null) throw AssistantSetupFailure("RESTORE_FIRST")
        val original = current()
        if (!original.supported) throw AssistantSetupFailure("COLOROS_FIRMWARE_UNSUPPORTED")
        // Do not override a pre-existing enterprise/vendor policy, including an unknown value.
        if (original.gate !in setOf(null, "0", "")) throw AssistantSetupFailure("COLOROS_POLICY_OWNED")
        var backup = OplusPowerBackup(original)
        store.save(backup)
        try {
            if (original.broadcast != "1") {
                backup = backup.copy(broadcastAttempted = true)
                store.save(backup)
                device.changeBroadcast(original.broadcast, "1")
            }
            var live = current()
            owned(live, backup)
            if (live.broadcast != "1") throw AssistantSetupFailure("CONFIGURATION_NOT_APPLIED")
            backup = backup.copy(gateAttempted = true)
            store.save(backup)
            device.changeGate(live.gate, "1")
            live = current()
            owned(live, backup)
            if (!live.supported || live.broadcast != "1" || live.gate != "1") {
                throw AssistantSetupFailure("CONFIGURATION_NOT_APPLIED")
            }
            store.save(backup.copy(appliedAt = now()))
        } catch (failure: Exception) {
            if (failure !is AssistantSetupFailure || failure.code !in uncertainCodes) runCatching { restore() }
            throw failure
        }
    }

    fun restore() {
        val backup = store.load() ?: return
        var live = current()
        owned(live, backup)
        // Persist disarming before either restore write; a partially restored route must not wake Eta.
        store.save(backup.copy(appliedAt = null))
        // Reopen the stock route first; a later property failure must not strand the key.
        if (live.gate != backup.original.gate) {
            device.changeGate(live.gate, backup.original.gate)
            live = current()
            owned(live, backup)
        }
        if (live.broadcast != backup.original.broadcast) {
            device.changeBroadcast(live.broadcast, backup.original.broadcast)
            live = current()
            owned(live, backup)
        }
        if (live.gate != backup.original.gate || live.broadcast != backup.original.broadcast) {
            throw AssistantSetupFailure("RESTORE_INCOMPLETE")
        }
        store.clear()
    }

    private fun current(): OplusPowerSnapshot = device.read().also {
        if (it.user != 0) throw AssistantSetupFailure("USER_CHANGED")
    }

    private fun owned(live: OplusPowerSnapshot, backup: OplusPowerBackup) {
        if (backup.original.user != live.user) throw AssistantSetupFailure("USER_CHANGED")
        val broadcasts = mutableSetOf(backup.original.broadcast)
        val gates = mutableSetOf(backup.original.gate)
        if (backup.broadcastAttempted) broadcasts.add("1")
        if (backup.gateAttempted) gates.add("1")
        if (live.broadcast !in broadcasts || live.gate !in gates) throw AssistantSetupFailure("CONFIGURATION_CHANGED")
    }

    private companion object {
        // A rejected precondition can mean another writer installed the same value we intended.
        // Do not assume ownership and automatically undo it just because it equals our target.
        val uncertainCodes = setOf("COMMAND_TIMEOUT", "COMMAND_RESULT_UNKNOWN", "ROOT_REQUIRED", "ROOT_UNAVAILABLE",
            "CONFIGURATION_CHANGED")
    }
}
