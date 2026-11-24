package io.github.mangi.eta.config

/**
 * 息屏时后台屏醒不过来怎么办（e52，2026-11-24 息屏排查）。
 *
 * 后台虚拟屏有自己的电源状态，手机息屏时可能跟着睡下。引擎先只针对后台屏试几种唤醒（不碰主屏）；
 * 都不行时，按这里的档位决定能不能点亮主屏（点亮后任务期间保持亮着，结束后按系统设置熄屏）。
 */
internal enum class BgScreenMainWake(val wireValue: String) {
    /** 不点亮：后台屏醒不过来就让任务报告失败，等你亮屏后再试。 */
    NEVER("never"),

    /** 只有定时任务可以点亮（默认）：手动发起的任务通常你正拿着手机，屏幕本来就亮着。 */
    SCHEDULED("scheduled"),

    /** 所有任务都可以点亮。 */
    ALWAYS("always");

    /** 这次运行能不能为后台屏点亮主屏。 */
    fun allows(scheduledRun: Boolean): Boolean = when (this) {
        NEVER -> false
        SCHEDULED -> scheduledRun
        ALWAYS -> true
    }

    companion object {
        val DEFAULT = SCHEDULED

        fun fromWireValue(raw: String?): BgScreenMainWake = entries.firstOrNull { it.wireValue == raw } ?: DEFAULT

        /** ShadowTools 的会话编号是「运行编号:随机串」，取回运行编号。 */
        fun runIdOf(sessionId: String): String = sessionId.substringBeforeLast(':', sessionId)
    }
}
