package io.github.mangi.eta.assistant

/** Firmware-specific semantic long-press route; never intercepts raw power-key events. */
internal object OplusPowerContract {
    const val ACTION = "oppo.intent.action.power.longpress"
    const val PROPERTY = "persist.sys.custom.enable.powerkeybroadcast"
    const val POLICY = "breeno_disable_key"
    const val FRAMEWORK = "/system/framework/oplus-services.jar"
    const val FRAMEWORK_SHA256 = "56a8ae52c2562d5da80c9737b1b04801cce4e6744d9efefc4c67b2270f9817a5"

    fun isCandidate(model: String, sdk: Int) = model == "PMA110" && sdk == 37
    fun supported(model: String, sdk: Int, user: Int, hash: String) =
        isCandidate(model, sdk) && user == 0 && hash == FRAMEWORK_SHA256
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
    fun configured(snapshot: OplusPowerSnapshot): Boolean = snapshot.user == 0 && snapshot.supported &&
        snapshot.broadcast == "1" && snapshot.gate == "1" && store.load()?.let {
            it.appliedAt != null && it.original.fingerprint == snapshot.fingerprint
        } == true

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
