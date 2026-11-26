package io.github.mangi.eta.assistant

/** The only settings this feature owns. Short press, SOS and power-menu chords are untouched. */
internal enum class AssistantSetting(val namespace: String, val key: String) {
    ASSISTANT("secure", "assistant"),
    VOICE("secure", "voice_interaction_service"),
    RECOGNIZER("secure", "voice_recognition_service"),
    LONG_PRESS("global", "power_button_long_press"),
}

internal data class AssistantSnapshot(
    val userId: Int,
    val holders: List<String>,
    val settings: Map<AssistantSetting, String?>,
)

internal data class AssistantTick(val boot: Int, val elapsed: Long) {
    fun follows(other: AssistantTick): Boolean = boot >= 0 && boot == other.boot && elapsed > other.elapsed
}

/**
 * 各机型的目标值。AOSP：`power_button_long_press = 5`（长按呼出助手），识别服务保持原样。
 * HyperOS 自己处理电源键长按（由 [HyperPowerRoute] 改小米的长按动作），AOSP 这一项要设成 0，
 * 免得两边都响应；识别服务固定为小米自带引擎，否则开机时系统会把语音助手重置回小爱。
 */
internal data class AssistantProfile(val name: String, val longPress: String, val recognizer: String? = null) {
    companion object {
        val STANDARD = AssistantProfile("STANDARD", "5")
        fun hyper(xiaomiRecognizer: String?) = AssistantProfile("HYPEROS", "0", xiaomiRecognizer)
    }
}

internal data class AssistantBackup(
    val original: AssistantSnapshot,
    val roleAttempted: Boolean = false,
    val attempted: Set<AssistantSetting> = emptySet(),
    val appliedAt: AssistantTick? = null,
    val confirmedAt: AssistantTick? = null,
    /** 配置时用的目标值；旧备份没有这一项，按 AOSP 处理。 */
    val profile: AssistantProfile = AssistantProfile.STANDARD,
    /** 在哪台手机上配置的（厂商|型号|设备名）；换机带过来的备份据此作废。旧备份为空。 */
    val device: String? = null,
)

internal class AssistantSetupFailure(val code: String) : Exception(code)

internal interface AssistantSetupDevice {
    fun read(): AssistantSnapshot
    fun changeRole(expected: List<String>, target: List<String>)
    fun changeSetting(setting: AssistantSetting, expected: String?, target: String?)
}

internal interface AssistantBackupStore {
    fun load(): AssistantBackup?
    /** Must be durable before any device mutation. */
    fun save(backup: AssistantBackup)
    fun clear()
}

/** One explicit, reversible setup transaction. No receiver, injection, polling or auto repair. */
internal class NativeAssistantSetup(
    private val packageName: String,
    private val userId: Int,
    private val device: AssistantSetupDevice,
    private val store: AssistantBackupStore,
    private val selectedProfile: AssistantProfile = AssistantProfile.STANDARD,
    private val deviceId: String? = null,
    private val now: () -> AssistantTick,
) {
    /** 已经配置过就按当时的目标值核对和恢复；还没配置时用现在选的机型。 */
    private fun profile(backup: AssistantBackup? = store.load()): AssistantProfile = backup?.profile ?: selectedProfile

    private val voice = "$packageName/io.github.mangi.eta.agent.voice.EtaVoiceInteractionService"
    private val recognizer = "$packageName/io.github.mangi.eta.agent.voice.EtaRecognitionService"

    fun configured(snapshot: AssistantSnapshot): Boolean {
        val profile = profile()
        return snapshot.userId == userId &&
            snapshot.holders == listOf(packageName) &&
            snapshot.settings[AssistantSetting.ASSISTANT] == voice &&
            snapshot.settings[AssistantSetting.VOICE] == voice &&
            snapshot.settings[AssistantSetting.LONG_PRESS] == profile.longPress &&
            (profile.recognizer == null || snapshot.settings[AssistantSetting.RECOGNIZER] == profile.recognizer)
    }

    fun enable() {
        // A pending/previous backup must never be overwritten by a second enable attempt.
        if (store.load() != null) throw AssistantSetupFailure("RESTORE_FIRST")
        val original = readCurrentUser()
        if (original.holders.size > 1) throw AssistantSetupFailure("UNSUPPORTED_ROLE_STATE")
        val profile = selectedProfile
        var backup = AssistantBackup(original, profile = profile, device = deviceId)
        store.save(backup)
        try {
            if (original.holders != listOf(packageName)) {
                backup = backup.copy(roleAttempted = true)
                store.save(backup)
                device.changeRole(original.holders, listOf(packageName))
            }
            var live = readCurrentUser()
            checkOwned(live, backup)
            if (live.holders != listOf(packageName)) throw AssistantSetupFailure("ROLE_NOT_APPLIED")
            val targets = linkedMapOf(
                AssistantSetting.ASSISTANT to voice,
                AssistantSetting.VOICE to voice,
                // RoleController can change this as a side effect. Keep the previous recognizer
                // unless the vendor profile pins one (HyperOS resets the assistant without it).
                AssistantSetting.RECOGNIZER to (profile.recognizer ?: original.settings[AssistantSetting.RECOGNIZER]),
                AssistantSetting.LONG_PRESS to profile.longPress,
            )
            for ((setting, target) in targets) {
                if (live.settings[setting] == target) continue
                backup = backup.copy(attempted = backup.attempted + setting)
                store.save(backup)
                device.changeSetting(setting, live.settings[setting], target)
                live = readCurrentUser()
                checkOwned(live, backup)
            }
            if (!configured(live)) throw AssistantSetupFailure("CONFIGURATION_NOT_APPLIED")
            store.save(backup.copy(appliedAt = now()))
        } catch (failure: Exception) {
            // A timeout may still be executing in RoleController. Do not replay or race an undo.
            if (failure !is AssistantSetupFailure || failure.code !in uncertainCodes) {
                runCatching { restore() }
            }
            throw failure
        }
    }

    fun restore() {
        val backup = store.load() ?: return
        if (backup.original.userId != userId) throw AssistantSetupFailure("USER_CHANGED")
        var live = readCurrentUser()
        checkOwned(live, backup)
        if (live == backup.original) {
            store.clear()
            return
        }
        if (live.holders != backup.original.holders) {
            device.changeRole(live.holders, backup.original.holders)
            live = readCurrentUser()
            // Restoring the previous role may legitimately restore its voice/recognizer values.
            checkOwned(live, backup)
        }
        for (setting in AssistantSetting.entries) {
            val target = backup.original.settings[setting]
            if (live.settings[setting] == target) continue
            device.changeSetting(setting, live.settings[setting], target)
            live = readCurrentUser()
            checkOwned(live, backup)
        }
        if (live != backup.original) throw AssistantSetupFailure("RESTORE_INCOMPLETE")
        store.clear()
    }

    /** Explicit user confirmation plus a new visible Eta overlay; never infer a physical key event. */
    fun confirmWake(visibleOverlay: AssistantTick?) {
        val backup = store.load() ?: throw AssistantSetupFailure("NOT_CONFIGURED")
        val applied = backup.appliedAt ?: throw AssistantSetupFailure("RESTORE_FIRST")
        val current = now()
        if (!configured(readCurrentUser())) throw AssistantSetupFailure("CONFIGURATION_CHANGED")
        val newOverlay = visibleOverlay != null && applied.boot >= 0 &&
            (visibleOverlay.follows(applied) || visibleOverlay.boot > applied.boot)
        if (!newOverlay ||
            visibleOverlay.boot != current.boot || current.elapsed < visibleOverlay.elapsed ||
            current.elapsed - visibleOverlay.elapsed > 5 * 60_000L
        ) throw AssistantSetupFailure("WAKE_NOT_SEEN")
        store.save(backup.copy(confirmedAt = current))
    }

    private fun readCurrentUser(): AssistantSnapshot = device.read().also {
        if (it.userId != userId) throw AssistantSetupFailure("USER_CHANGED")
    }

    private fun checkOwned(live: AssistantSnapshot, backup: AssistantBackup) {
        val original = backup.original
        val allowedRoles = if (backup.roleAttempted) setOf(original.holders, listOf(packageName))
            else setOf(original.holders)
        if (live.holders !in allowedRoles) throw AssistantSetupFailure("CONFIGURATION_CHANGED")
        for (setting in AssistantSetting.entries) {
            val allowed = mutableSetOf(original.settings[setting])
            if (setting in backup.attempted || (backup.roleAttempted && setting != AssistantSetting.LONG_PRESS)) {
                when (setting) {
                    AssistantSetting.ASSISTANT, AssistantSetting.VOICE -> allowed.add(voice)
                    AssistantSetting.RECOGNIZER -> {
                        allowed.add(recognizer)
                        backup.profile.recognizer?.let(allowed::add)
                    }
                    AssistantSetting.LONG_PRESS -> allowed.add(backup.profile.longPress)
                }
                // RoleController can clear the assistant values while changing holders.
                if (backup.roleAttempted && setting != AssistantSetting.LONG_PRESS) {
                    allowed.add(null)
                    allowed.add("")
                }
            }
            if (live.settings[setting] !in allowed) throw AssistantSetupFailure("CONFIGURATION_CHANGED")
        }
    }

    private companion object {
        val uncertainCodes = setOf("COMMAND_TIMEOUT", "COMMAND_RESULT_UNKNOWN", "ROOT_REQUIRED", "ROOT_UNAVAILABLE")
    }
}
