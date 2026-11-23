package io.github.mangi.eta.config

/**
 * 「运行期间保持屏幕常亮」三档（14 号方案 14.1）。
 *
 * 上游 95b4060a 把常亮改成「任何任务（含后台任务）执行时都常亮」；后台虚拟屏本来就能在锁屏时干活，
 * 定时任务也多半在你不看手机时跑，一律常亮只是白耗电。所以拆成三档，默认 [FOREGROUND]。
 */
internal enum class KeepScreenOnMode(val wireValue: String) {
    /** 从不因为任务而常亮。 */
    OFF("off"),

    /** 正在主屏上操作手机、普通对话、或你正看着胖小胖界面时常亮；后台虚拟屏和定时任务不常亮。 */
    FOREGROUND("foreground"),

    /** 任何任务执行时都常亮（上游语义）。 */
    ALL("all");

    companion object {
        val DEFAULT = FOREGROUND

        fun fromWireValue(raw: String?): KeepScreenOnMode? = entries.firstOrNull { it.wireValue == raw }

        /** 还没选过三档时，从旧的布尔开关推出来：关 → [OFF]，开或没设置 → [DEFAULT]。 */
        fun resolve(raw: String?, legacyEnabled: Boolean?): KeepScreenOnMode =
            fromWireValue(raw) ?: if (legacyEnabled == false) OFF else DEFAULT
    }
}

/** 常亮判定，只看事实不碰 Android，方便单测。 */
internal object KeepScreenOnPolicy {
    data class RunFacts(
        /** 没结束、没取消、没暂停。 */
        val active: Boolean,
        /** 当前持有主屏操作权（前台操作手机）。 */
        val ownsGui: Boolean,
        /** 定时任务触发的运行。 */
        val scheduled: Boolean,
        /** 正在或刚刚在后台虚拟屏里操作应用。 */
        val usesBackgroundScreen: Boolean,
    )

    fun shouldKeepOn(mode: KeepScreenOnMode, runs: List<RunFacts>, etaUiVisible: Boolean): Boolean = when (mode) {
        KeepScreenOnMode.OFF -> false
        KeepScreenOnMode.ALL -> runs.any { it.active }
        KeepScreenOnMode.FOREGROUND -> runs.any { run ->
            run.active && (run.ownsGui || (!run.scheduled && (!run.usesBackgroundScreen || etaUiVisible)))
        }
    }
}
