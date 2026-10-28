package io.github.mangi.eta.agent.runtime

import com.eta.shadow.ShadowTools
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal enum class BackgroundScreenPhase { WaitingForApp, ReleasingScreen, OpeningApp, RestoringDisplay }

internal interface ScreenToolConnection {
    fun execute(name: String, arguments: String): String?
    fun release(keepFinished: Boolean): Boolean
    fun keepAlive()
}

private class ShadowScreenConnection(id: String) : ScreenToolConnection {
    private val tools = ShadowTools(id)
    override fun execute(name: String, arguments: String) = tools.intercept(name, arguments)
    override fun release(keepFinished: Boolean) = tools.closeSession(keepFinished)
    override fun keepAlive() = tools.keepAlive()
}

/** Capacity is reserved before launch; a cancelled owner keeps its slot until engine cleanup ACK. */
internal class ScreenLeaseRegistry(private val capacity: Int = 2) {
    private data class Lease(val owner: String, val app: String)
    private val monitor = Object()
    private val leases = linkedMapOf<String, Lease>()

    fun acquire(owner: String, app: String, checkCancelled: () -> Unit,
        onWaiting: (BackgroundScreenPhase) -> Unit = {}) {
        val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10)
        while (true) {
            checkCancelled()
            var waiting = BackgroundScreenPhase.WaitingForApp
            synchronized(monitor) {
                if (leases[owner]?.app == app) return
                // Two tasks using one Android app must never race its existing Activity task.
                if (leases.values.none { it.app == app }) {
                    waiting = BackgroundScreenPhase.ReleasingScreen
                    if (leases.size < capacity) {
                        leases[owner] = Lease(owner, app)
                        return
                    }
                }
                monitor.wait(250)
            }
            onWaiting(waiting)
            check(System.nanoTime() < deadline) { "等待后台应用或屏幕释放超时，请检查正在运行的任务" }
        }
    }

    fun release(owner: String) = synchronized(monitor) {
        leases.remove(owner)
        monitor.notifyAll()
    }

    internal fun owners(): List<String> = synchronized(monitor) { leases.keys.toList() }
}

internal class BackgroundScreenTools(
    private val runId: String,
    private val controller: AgentRunController,
    private val onPhase: (BackgroundScreenPhase?) -> Unit = {},
    private val onAppOpened: (String) -> Unit = {},
    private val registry: ScreenLeaseRegistry = sharedLeases,
    private val connectionFactory: (String) -> ScreenToolConnection = ::ShadowScreenConnection,
    private val retryDelayMs: Long = 150,
) : AutoCloseable {
    private class Binding(val id: String, val app: String, val tools: ScreenToolConnection) {
        val closing = java.util.concurrent.atomic.AtomicBoolean()
        val released = CountDownLatch(1)
        var heartbeat: java.util.concurrent.ScheduledFuture<*>? = null
    }
    private val lock = Any()
    private var binding: Binding? = null
    @Volatile private var closed = false
    @Volatile private var phase: BackgroundScreenPhase? = null
    private var observedGeneration = controller.resumeGeneration

    private fun phase(next: BackgroundScreenPhase?) {
        if (phase == next) return
        phase = next
        RuntimeDiagnostics.record("screen_phase", next?.name ?: "ready")
        onPhase(next)
    }

    @Synchronized fun execute(name: String, arguments: String): String? {
        return try { executeInternal(name, arguments) } finally { phase(null) }
    }

    private fun executeInternal(name: String, arguments: String): String? {
        if (!name.startsWith("screen_") && name != "terminal") return null
        controller.throwIfCancelled()
        if (binding != null && controller.resumeGeneration != observedGeneration && name in MUTATING_TOOLS) {
            return """{"ok":false,"code":"OBSERVE_AFTER_RESUME","error":"暂停后页面可能已经变化，请先调用 screen_dump、screen_list 或 screen_shot 重新观察，再决定下一步。此操作尚未执行。"}"""
        }
        if (name == "screen_open") {
            val args = JSONObject(arguments)
            val app = sequenceOf("component", "package", "app_name")
                .map { args.optString(it).trim() }.firstOrNull { it.isNotEmpty() }.orEmpty().substringBefore('/')
            if (!app.contains('.')) return "需要有效的应用包名"
            val previous = synchronized(lock) { binding }
            if (previous != null && (previous.app != app || previous.closing.get())) {
                phase(BackgroundScreenPhase.ReleasingScreen)
                // Switching Apps in one task uses the same park/reuse path as screen_finish.
                // Root still counts parked screens against the two-display limit.
                cleanup(previous, keepFinished = true)
                val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(2)
                while (!previous.released.await(100, TimeUnit.MILLISECONDS)) {
                    controller.throwIfCancelled()
                    check(System.nanoTime() < deadline) { "屏幕仍在回收，后台会继续恢复；可稍后重试或停止任务" }
                }
            }
            var next = synchronized(lock) { binding?.takeIf { it.app == app && !it.closing.get() } }
            if (next == null) {
                val id = "$runId:${UUID.randomUUID()}"
                next = Binding(id, app, connectionFactory(id))
                val candidate = next
                registry.acquire(candidate.id, app, controller::throwIfCancelled,
                    onWaiting = ::phase)
                synchronized(lock) {
                    if (closed || controller.isCancelled) {
                        registry.release(candidate.id)
                        controller.throwIfCancelled()
                        error("任务已结束")
                    }
                    binding = candidate
                    candidate.heartbeat = maintenance.scheduleWithFixedDelay(candidate.tools::keepAlive, 30, 30, TimeUnit.SECONDS)
                }
            }
            phase(BackgroundScreenPhase.OpeningApp)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            controller.throwIfCancelled()
            var result = next.tools.execute(name, arguments)
            // A parked screen may need asynchronous engine cleanup after the app lease
            // has been returned. Retry only the explicit pre-launch rejection, never a timeout.
            while (runCatching { JSONObject(result.orEmpty()).optString("code") == "CLEANUP_PENDING" }.getOrDefault(false)
                && System.nanoTime() < deadline) {
                phase(BackgroundScreenPhase.ReleasingScreen)
                Thread.sleep(250)
                controller.throwIfCancelled()
                result = next.tools.execute(name, arguments)
            }
            if (runCatching { JSONObject(result.orEmpty()).optBoolean("ok") }.getOrDefault(false)) {
                onAppOpened(app)
            }
            return result
        }
        val current = synchronized(lock) { binding }
        if (current == null) {
            // A terminal guard and health query need no GUI session, but node reads never target display 0 implicitly.
            return if (name == "terminal" || name == "screen_state") ShadowTools(runId).intercept(name, arguments)
            else """{"ok":false,"code":"NO_SESSION","reason":"NO_ACTIVE_BINDING","error":"当前任务没有可操作的屏幕；已完成页面可能仍被保留，请调用 screen_open 重新取得所有权。"}"""
        }
        if (current.closing.get()) return "屏幕正在回收，请先调用 screen_open 打开下一应用"
        if (name == "screen_finish") phase(BackgroundScreenPhase.RestoringDisplay)
        val observationGeneration = controller.resumeGeneration
        return current.tools.execute(name, arguments).also { result ->
            val response = runCatching { JSONObject(result.orEmpty()) }.getOrNull()
            if (response?.optString("code") in LOST_SESSION_CODES ||
                (name == "screen_state" && response?.has("sessionValid") == true && !response.optBoolean("sessionValid"))) {
                // Release this binding only, and never replay an input whose outcome is unknown.
                cleanup(current, keepFinished = false)
            }
            if (name in OBSERVATION_TOOLS && runCatching { JSONObject(result.orEmpty()).optBoolean("ok") }.getOrDefault(false)) {
                observedGeneration = observationGeneration
            }
            if (name == "screen_finish" && runCatching { JSONObject(result.orEmpty()).optBoolean("ok") }.getOrDefault(false)) {
                // Release ownership at the tool's confirmed finish, not only when the model
                // eventually finishes replying. The same run can then open the next app.
                cleanup(current, keepFinished = true)
            }
        }
    }

    private fun cleanup(value: Binding, keepFinished: Boolean) {
        if (!value.closing.compareAndSet(false, true)) return
        value.heartbeat?.cancel(false)
        var retries = 0
        fun attempt() {
            val acknowledged = runCatching { value.tools.release(keepFinished && !controller.isCancelled) }.getOrDefault(false)
            if (acknowledged) {
                registry.release(value.id)
                synchronized(lock) { if (binding === value) binding = null }
                value.released.countDown()
                RuntimeDiagnostics.record("screen_cleanup", "acknowledged")
            } else {
                RuntimeDiagnostics.record("screen_cleanup", "retrying")
                // Keep ownership until an engine ACK; a later engine restart also ACKs missing sessions.
                val delay = (retryDelayMs * (1L shl retries.coerceAtMost(6))).coerceAtMost(15_000)
                retries++
                maintenance.schedule(::attempt, delay, TimeUnit.MILLISECONDS)
            }
        }
        maintenance.execute(::attempt)
    }

    override fun close() {
        closed = true
        synchronized(lock) { binding }?.let { cleanup(it, keepFinished = true) }
    }

    companion object {
        private val LOST_SESSION_CODES = setOf("NO_SESSION", "SESSION_CLOSED", "STALE_SCREEN")
        private val MUTATING_TOOLS = setOf("screen_tap", "screen_click", "screen_press", "screen_swipe", "screen_drag", "screen_scroll", "screen_text")
        private val OBSERVATION_TOOLS = setOf("screen_dump", "screen_list", "screen_shot")
        private val sharedLeases = ScreenLeaseRegistry()
        private val maintenance = java.util.concurrent.Executors.newScheduledThreadPool(3) { runnable ->
            Thread(runnable, "eta-screen-maintenance").apply { isDaemon = true }
        }
    }
}
