package io.github.mangi.eta.agent.remote

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 提醒哪些会话：只提醒在手机上发过消息（或手动打开提醒）的会话，或者全部会话。 */
internal enum class DesktopReminderScope { Watched, All }

/**
 * 电脑同步在手机上的偏好，和胖小胖本机任务的通知、流光设置完全分开。
 *
 * 常驻通知默认静默、低优先级，不显示流光和流体云：电脑上的任务可能一直在跑，
 * 不应该像本机任务那样持续占据屏幕。每一项都可以在「设置 → 电脑」里打开。
 */
internal data class DesktopLinkPrefs(
    /** 手机端总开关：关闭时不连接电脑，也不保留常驻通知。 */
    val enabled: Boolean = true,
    /** 常驻通知低优先级（折叠显示，不弹出）。 */
    val ongoingLowPriority: Boolean = true,
    /** 电脑有任务运行时显示胖小胖的屏幕流光。 */
    val glow: Boolean = false,
    /** 电脑有任务运行时把常驻通知提升为流体云 / 实时活动。 */
    val fluidCloud: Boolean = false,
    /** 提醒通知响铃和振动；关闭时静默提醒。 */
    val reminderSound: Boolean = false,
    val remindCompleted: Boolean = true,
    val remindApproval: Boolean = true,
    val remindQuestion: Boolean = true,
    val remindError: Boolean = true,
    val reminderScope: DesktopReminderScope = DesktopReminderScope.Watched,
    /** 在手机上发过消息或手动打开提醒的会话。 */
    val watched: Set<String> = emptySet(),
    /** 手动关闭提醒的会话（优先于 [reminderScope]）。 */
    val muted: Set<String> = emptySet(),
) {
    fun reminds(sessionId: String): Boolean = when {
        sessionId in muted -> false
        reminderScope == DesktopReminderScope.All -> true
        else -> sessionId in watched
    }
}

internal object DesktopLinkSettings {
    private const val FILE = "desktop_link"
    private const val MAX_WATCHED = 200

    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(DesktopLinkPrefs())
    val state: StateFlow<DesktopLinkPrefs> = _state.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = store
        _state.value = read(store)
    }

    fun update(transform: (DesktopLinkPrefs) -> DesktopLinkPrefs) {
        _state.update(transform)
        prefs?.let { write(it, _state.value) }
    }

    /** 手机给这个会话发过消息：之后它的完成、审批、提问都会提醒。 */
    fun markWatched(sessionId: String) {
        val current = _state.value
        if (sessionId in current.watched && sessionId !in current.muted) return
        update { it.copy(watched = (it.watched - sessionId + sessionId).toList().takeLast(MAX_WATCHED).toSet(), muted = it.muted - sessionId) }
    }

    fun setSessionReminders(sessionId: String, on: Boolean) = update {
        if (on) it.copy(watched = it.watched + sessionId, muted = it.muted - sessionId)
        else it.copy(watched = it.watched - sessionId, muted = (it.muted + sessionId).toList().takeLast(MAX_WATCHED).toSet())
    }

    private fun read(store: SharedPreferences): DesktopLinkPrefs {
        val defaults = DesktopLinkPrefs()
        return DesktopLinkPrefs(
            enabled = store.getBoolean("enabled", defaults.enabled),
            ongoingLowPriority = store.getBoolean("ongoing_low", defaults.ongoingLowPriority),
            glow = store.getBoolean("glow", defaults.glow),
            fluidCloud = store.getBoolean("fluid_cloud", defaults.fluidCloud),
            reminderSound = store.getBoolean("reminder_sound", defaults.reminderSound),
            remindCompleted = store.getBoolean("remind_completed", defaults.remindCompleted),
            remindApproval = store.getBoolean("remind_approval", defaults.remindApproval),
            remindQuestion = store.getBoolean("remind_question", defaults.remindQuestion),
            remindError = store.getBoolean("remind_error", defaults.remindError),
            reminderScope = if (store.getString("scope", null) == "all") DesktopReminderScope.All else DesktopReminderScope.Watched,
            watched = store.getStringSet("watched", null)?.toSet().orEmpty(),
            muted = store.getStringSet("muted", null)?.toSet().orEmpty(),
        )
    }

    private fun write(store: SharedPreferences, value: DesktopLinkPrefs) {
        store.edit()
            .putBoolean("enabled", value.enabled)
            .putBoolean("ongoing_low", value.ongoingLowPriority)
            .putBoolean("glow", value.glow)
            .putBoolean("fluid_cloud", value.fluidCloud)
            .putBoolean("reminder_sound", value.reminderSound)
            .putBoolean("remind_completed", value.remindCompleted)
            .putBoolean("remind_approval", value.remindApproval)
            .putBoolean("remind_question", value.remindQuestion)
            .putBoolean("remind_error", value.remindError)
            .putString("scope", if (value.reminderScope == DesktopReminderScope.All) "all" else "watched")
            .putStringSet("watched", HashSet(value.watched))
            .putStringSet("muted", HashSet(value.muted))
            .apply()
    }
}
