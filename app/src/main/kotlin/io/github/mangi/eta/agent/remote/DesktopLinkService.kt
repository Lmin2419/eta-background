package io.github.mangi.eta.agent.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.accessibility.AgentAccessibilityService
import io.github.mangi.eta.agent.overlay.AgentOverlayGlow
import io.github.mangi.eta.agent.overlay.AgentOverlayState
import io.github.mangi.eta.agent.overlay.ScreenCornerRadii
import io.github.mangi.eta.ui.MainActivity
import io.github.mangi.eta.ui.theme.EtaTheme
import io.github.mangi.eta.ui.theme.darkEtaColors
import io.github.mangi.eta.ui.theme.lightEtaColors
import io.github.mangi.eta.ui.theme.toMiuixColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 电脑同步的常驻连接：开关打开且已配对时运行，进程在后台也保持与电脑的连接，
 * 让提醒、审批和「打开即见」不用等重连。
 *
 * 和胖小胖本机任务完全分开：独立的通知类别，默认静默、低优先级，不显示流光和流体云
 * （电脑上的任务可能一直在跑），这些都在「设置 → 电脑」里单独打开。
 */
internal class DesktopLinkService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var glowView: ComposeView? = null
    private var reminders: DesktopReminders? = null
    private var started = false

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        DesktopLinkSettings.init(this)
        ensureChannels(this)
        startInForeground(buildOngoing(LinkSummary()))
        DesktopBridge.acquire(this)
        DesktopBridge.setBackground(!MainActivity.isInForeground)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        reminders = DesktopReminders(this, scope).also { it.start() }
        scope.launch {
            combine(DesktopBridge.client.state, DesktopBridge.sessions, DesktopLinkSettings.state) { state, sessions, prefs ->
                summarize(state, sessions, prefs)
            }.distinctUntilChanged().collect { summary ->
                // 常驻通知本身从不提升为流体云；流体云是单独一条只在运行时存在的通知，
                // 运行结束直接取消它。同一条通知从「提升」改回普通时，ColorOS 不一定收起胶囊，
                // 会出现任务完成提醒弹出、旧流体云还挂着的情况。
                notificationManager().notify(ONGOING_ID, buildOngoing(summary.copy(fluidCloud = false)))
                if (summary.fluidCloud) notificationManager().notify(LIVE_ID, buildLive(summary))
                else notificationManager().cancel(LIVE_ID)
                updateGlow(summary.glow)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISABLE) {
            DesktopLinkSettings.update { it.copy(enabled = false) }
            DesktopLinkController.sync(this)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        reminders?.stop()
        updateGlow(false)
        notificationManager().cancel(LIVE_ID)
        scope.cancel()
        if (started) DesktopBridge.release()
        started = false
        super.onDestroy()
    }

    private fun startInForeground(notification: Notification) {
        startForeground(ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        started = true
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    // ---- 常驻通知 ----------------------------------------------------------

    /** 常驻通知需要的全部信息；只有它变化时才重发通知。 */
    private data class LinkSummary(
        val phase: Phase = Phase.Connecting,
        val running: List<String> = emptyList(),
        val lowPriority: Boolean = true,
        val fluidCloud: Boolean = false,
        val glow: Boolean = false,
        val detail: String? = null,
    )

    private enum class Phase { Connecting, Connected, Offline }

    private fun summarize(state: DesktopBridgeClient.State, sessions: DesktopSessionsState, prefs: DesktopLinkPrefs): LinkSummary {
        val running = sessions.rows
            .filter { it.running && !it.subagent && it.parentId == null && !it.archived }
            .map { it.title?.takeIf(String::isNotBlank) ?: getString(R.string.desktop_untitled) }
        val phase = when (state) {
            is DesktopBridgeClient.State.Connected -> Phase.Connected
            is DesktopBridgeClient.State.Unauthorized -> Phase.Offline
            else -> Phase.Connecting
        }
        val detail = when (state) {
            is DesktopBridgeClient.State.Unauthorized -> state.message
            is DesktopBridgeClient.State.Connecting -> state.lastError
            else -> null
        }
        val active = phase == Phase.Connected && running.isNotEmpty()
        return LinkSummary(
            phase = phase,
            running = if (phase == Phase.Connected) running else emptyList(),
            lowPriority = prefs.ongoingLowPriority,
            fluidCloud = prefs.fluidCloud && active,
            glow = prefs.glow && active,
            detail = detail,
        )
    }

    /** 电脑上有任务在跑时的流体云 / 实时活动通知（Android 16 起系统提升为胶囊）。 */
    private fun buildLive(summary: LinkSummary): Notification {
        val builder = Notification.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.desktop_link_running, summary.running.size))
            .setContentText(summary.running.take(3).joinToString(" · "))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(DesktopLinkNav.openIntent(this, null, REQUEST_OPEN_LIST))
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setStyle(Notification.ProgressStyle().setProgressIndeterminate(true))
                .setShortCriticalText(getString(R.string.desktop_link_short, summary.running.size))
            builder.extras.putBoolean("android.requestPromotedOngoing", true)
        }
        return builder.build()
    }

    private fun buildOngoing(summary: LinkSummary): Notification {
        val title = when (summary.phase) {
            Phase.Connected -> if (summary.running.isEmpty()) getString(R.string.desktop_link_connected)
            else getString(R.string.desktop_link_running, summary.running.size)
            Phase.Connecting -> getString(R.string.desktop_link_connecting)
            Phase.Offline -> getString(R.string.desktop_link_offline)
        }
        val text = when {
            summary.running.isNotEmpty() -> summary.running.take(3).joinToString(" · ")
            summary.detail != null -> summary.detail
            else -> getString(R.string.desktop_link_idle_text)
        }
        val channel = if (summary.lowPriority && !summary.fluidCloud) CHANNEL_ONGOING_LOW else CHANNEL_ONGOING
        val builder = Notification.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(DesktopLinkNav.openIntent(this, null, REQUEST_OPEN_LIST))
            .addAction(Notification.Action.Builder(null, getString(R.string.desktop_link_disable),
                PendingIntent.getService(this, REQUEST_DISABLE, Intent(this, DesktopLinkService::class.java).setAction(ACTION_DISABLE),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build())
        if (summary.fluidCloud && Build.VERSION.SDK_INT >= 36) {
            // 流体云 / 实时活动：系统把带进度样式、请求提升的常驻通知显示成胶囊。
            builder.setStyle(Notification.ProgressStyle().setProgressIndeterminate(true))
                .setShortCriticalText(getString(R.string.desktop_link_short, summary.running.size))
            builder.extras.putBoolean("android.requestPromotedOngoing", true)
        }
        return builder.build()
    }

    // ---- 流光 --------------------------------------------------------------

    private fun updateGlow(show: Boolean) {
        val context = AgentAccessibilityService.current() ?: this
        val wm = context.getSystemService(WindowManager::class.java) ?: return
        if (!show) {
            glowView?.let { view -> runCatching { wm.removeView(view) } }
            glowView = null
            return
        }
        if (glowView != null) return
        val accessibility = AgentAccessibilityService.current() != null
        if (!accessibility && !Settings.canDrawOverlays(this)) return
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@DesktopLinkService)
            setViewTreeSavedStateRegistryOwner(this@DesktopLinkService)
            setContent {
                val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                val palette = if (dark) darkEtaColors() else lightEtaColors()
                MiuixTheme(colors = palette.toMiuixColors(if (dark) darkColorScheme() else lightColorScheme())) {
                    EtaTheme(palette) {
                        CompositionLocalProvider(LocalSquircleEnabled provides false) {
                            AgentOverlayGlow(state = AgentOverlayState.Initial, corners = cornerRadii(context))
                        }
                    }
                }
            }
        }
        @Suppress("DEPRECATION")
        val realHeight = runCatching {
            val point = android.graphics.Point()
            wm.defaultDisplay.getRealSize(point)
            point.y
        }.getOrDefault(WindowManager.LayoutParams.MATCH_PARENT)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            realHeight,
            if (accessibility) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            windowAnimations = 0
        }
        runCatching { wm.addView(view, params) }.onSuccess { glowView = view }
    }

    private fun cornerRadii(context: Context): ScreenCornerRadii {
        val display = context.getSystemService(android.hardware.display.DisplayManager::class.java)
            ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val fallback = 32f * context.resources.displayMetrics.density
        fun radius(position: Int): Float = display?.getRoundedCorner(position)?.radius?.takeIf { it > 0 }?.toFloat() ?: fallback
        return ScreenCornerRadii(
            topLeft = radius(android.view.RoundedCorner.POSITION_TOP_LEFT),
            topRight = radius(android.view.RoundedCorner.POSITION_TOP_RIGHT),
            bottomRight = radius(android.view.RoundedCorner.POSITION_BOTTOM_RIGHT),
            bottomLeft = radius(android.view.RoundedCorner.POSITION_BOTTOM_LEFT),
        )
    }

    companion object {
        const val CHANNEL_ONGOING_LOW = "desktop_link_low"
        const val CHANNEL_ONGOING = "desktop_link"
        const val CHANNEL_REMINDERS = "desktop_reminders"
        const val CHANNEL_REMINDERS_SILENT = "desktop_reminders_silent"
        private const val ONGOING_ID = 0x0D5B0001
        private const val LIVE_ID = 0x0D5B0002
        private const val ACTION_DISABLE = "io.github.mangi.eta.desktop.DISABLE"
        private const val REQUEST_OPEN_LIST = 7100
        private const val REQUEST_DISABLE = 7101

        /** 电脑同步的通知类别，与胖小胖本机任务的类别分开，可在系统设置里单独管理。 */
        fun ensureChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val group = context.getString(R.string.desktop_link_channel_group)
            manager.createNotificationChannelGroup(android.app.NotificationChannelGroup(CHANNEL_GROUP, group))
            fun silent(channel: NotificationChannel) = channel.apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                setGroup(CHANNEL_GROUP)
            }
            manager.createNotificationChannel(silent(NotificationChannel(CHANNEL_ONGOING_LOW,
                context.getString(R.string.desktop_link_channel_ongoing_low), NotificationManager.IMPORTANCE_LOW)))
            manager.createNotificationChannel(silent(NotificationChannel(CHANNEL_ONGOING,
                context.getString(R.string.desktop_link_channel_ongoing), NotificationManager.IMPORTANCE_DEFAULT)))
            manager.createNotificationChannel(NotificationChannel(CHANNEL_REMINDERS,
                context.getString(R.string.desktop_link_channel_reminders), NotificationManager.IMPORTANCE_HIGH).apply { setGroup(CHANNEL_GROUP) })
            manager.createNotificationChannel(silent(NotificationChannel(CHANNEL_REMINDERS_SILENT,
                context.getString(R.string.desktop_link_channel_reminders_silent), NotificationManager.IMPORTANCE_DEFAULT)).apply { setShowBadge(true) })
        }

        private const val CHANNEL_GROUP = "desktop_link_group"
    }
}

/** 按手机端开关和配对状态启停常驻连接。 */
internal object DesktopLinkController {
    /** 手机端总开关：关闭时立即断开，不再保留常驻通知。 */
    fun setEnabled(context: Context, on: Boolean) {
        DesktopLinkSettings.init(context)
        DesktopLinkSettings.update { it.copy(enabled = on) }
        if (on) DesktopBridge.retry() else DesktopBridge.client.stop()
        sync(context)
    }

    fun sync(context: Context) {
        val app = context.applicationContext
        DesktopBridge.prepare(app)
        val wanted = DesktopLinkSettings.state.value.enabled && DesktopBridge.config.value?.isComplete == true
        val intent = Intent(app, DesktopLinkService::class.java)
        if (wanted) {
            // 从后台（如开机广播之外的场景）启动前台服务可能被系统拒绝；下次打开 App 会再试。
            runCatching { app.startForegroundService(intent) }
        } else {
            app.stopService(intent)
            if (!DesktopLinkSettings.state.value.enabled) DesktopBridge.client.stop()
        }
    }
}

/** 开机后恢复常驻连接（用户打开了电脑同步时）。 */
internal class DesktopLinkBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            DesktopLinkController.sync(context)
        }
    }
}

/** 从通知打开电脑会话：MainActivity 收到后交给界面跳转。 */
internal object DesktopLinkNav {
    const val ACTION_OPEN_DESKTOP = "io.github.mangi.eta.action.OPEN_DESKTOP"
    const val EXTRA_SESSION_ID = "io.github.mangi.eta.extra.DESKTOP_SESSION_ID"

    /** 待打开的目标：空字符串表示会话列表。 */
    private val _requests = MutableStateFlow<String?>(null)
    val requests: StateFlow<String?> = _requests.asStateFlow()

    fun accept(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_DESKTOP) return
        _requests.value = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        intent.action = null
    }

    fun consume() {
        _requests.value = null
    }

    fun openIntent(context: Context, sessionId: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_DESKTOP)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (sessionId != null) intent.putExtra(EXTRA_SESSION_ID, sessionId)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
