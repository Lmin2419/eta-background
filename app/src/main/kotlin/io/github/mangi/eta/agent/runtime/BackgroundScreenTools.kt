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
    fun setTakeoverListener(listener: () -> Unit) = Unit
}

/** One run's existing background-screen executor; never a process-wide current screen. */
internal interface ScreenToolBackend {
    fun execute(name: String, arguments: String): String?

    /** Read-only calls that may run at the same time; results keep the order of [calls]. */
    fun executeConcurrently(calls: List<Pair<String, String>>): List<String?> =
        calls.map { (name, arguments) -> execute(name, arguments) }
}

private class ShadowScreenConnection(id: String) : ScreenToolConnection {
    private val tools = ShadowTools(id)
    override fun execute(name: String, arguments: String) = tools.intercept(name, arguments)
    override fun release(keepFinished: Boolean) = tools.closeSession(keepFinished)
    override fun keepAlive() = tools.keepAlive()
    override fun setTakeoverListener(listener: () -> Unit) = tools.setTakeoverListener(listener)
}

/** Capacity is reserved before launch; a cancelled owner keeps its slot until engine cleanup ACK. */
internal class ScreenLeaseRegistry(private val capacity: Int = com.eta.shadow.ShadowSchema.MAX_CONCURRENT_APPS) {
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
    /** 等引擎收掉旧屏最多多久（测试里调小）。 */
    private val cleanupWaitMs: Long = CLEANUP_WAIT_MS,
) : AutoCloseable, ScreenToolBackend {
    private class Binding(val id: String, val app: String, val tools: ScreenToolConnection) {
        val closing = java.util.concurrent.atomic.AtomicBoolean()
        val takenOver = java.util.concurrent.atomic.AtomicBoolean()
        val released = CountDownLatch(1)
        var heartbeat: java.util.concurrent.ScheduledFuture<*>? = null
        var awaitingInput = false
    }
    private val lock = Any()
    private var binding: Binding? = null
    @Volatile private var closed = false
    @Volatile private var phase: BackgroundScreenPhase? = null
    private var observedGeneration = controller.resumeGeneration
    private val takeoverPending = java.util.concurrent.atomic.AtomicBoolean()
    /** 应用被切到主屏、任务因此暂停时的恢复代数；用户点「继续」后代数变了，旧的「已被接管」就不再拦。 */
    @Volatile private var takeoverGeneration = -1L

    private fun pauseForTakeover(value: Binding) {
        synchronized(lock) {
            if (binding !== value || closed || value.closing.get() || controller.isCancelled ||
                !value.takenOver.compareAndSet(false, true)) return
            takeoverPending.set(true)
            takeoverGeneration = controller.resumeGeneration
            controller.pause(forAppTakeover = true)
        }
        // Publish the existing paused status even when no opening/closing phase was active.
        runCatching { onPhase(phase) }
        cleanup(value, keepFinished = true)
        RuntimeDiagnostics.record("screen_handoff", "paused_for_foreground")
    }

    private fun phase(next: BackgroundScreenPhase?) {
        if (phase == next) return
        phase = next
        RuntimeDiagnostics.record("screen_phase", next?.name ?: "ready")
        onPhase(next)
    }

    @Synchronized override fun execute(name: String, arguments: String): String? {
        return try { BackgroundRecoveryPolicy.decorate(name, executeInternal(name, arguments)) } finally { phase(null) }
    }

    /**
     * observe_screen's node read and capture: one admission, the same post-processing as
     * [execute], but the two calls run together. Root admits a node read while only a capture
     * is executing; any edge case (no binding, cleanup, takeover) takes the sequential path.
     */
    @Synchronized override fun executeConcurrently(calls: List<Pair<String, String>>): List<String?> {
        val current = synchronized(lock) { binding }
        if (calls.size != 2 || calls.any { it.first !in CONCURRENT_OBSERVATION_TOOLS } ||
            current == null || current.closing.get() || takeoverPending.get()) {
            return calls.map { (name, arguments) -> execute(name, arguments) }
        }
        return try {
            controller.throwIfCancelled()
            val observationGeneration = controller.resumeGeneration
            val results = controller.withScreenOperation {
                val second = observationPool.submit(java.util.concurrent.Callable {
                    current.tools.execute(calls[1].first, calls[1].second)
                })
                val first = current.tools.execute(calls[0].first, calls[0].second)
                val other = try { second.get() } catch (failure: java.util.concurrent.ExecutionException) {
                    throw failure.cause ?: failure
                }
                listOf(first, other)
            }
            calls.indices.map { index ->
                val name = calls[index].first
                afterCall(name, current, observationGeneration, results[index])
                BackgroundRecoveryPolicy.decorate(name, results[index])
            }
        } finally { phase(null) }
    }

    private fun executeInternal(name: String, arguments: String): String? {
        if (!name.startsWith("screen_") && name != "terminal") return null
        controller.throwIfCancelled()
        // 2026111625：用户点了「继续」之后的 launch_app 就是要把应用接回后台屏。以前这里还会先回一次 APP_TAKEN_OVER
        // （附带「不要拉回」），接回动作根本没发出去。其他工具仍照旧补报一次，让模型知道应用被切走过。
        val reclaiming = name == "screen_open" && controller.resumeGeneration != takeoverGeneration
        if (takeoverPending.getAndSet(false) && !reclaiming) return """{"ok":false,"code":"APP_TAKEN_OVER","error":"应用已离开后台屏，原后台操作已暂停。用户恢复后请重新观察并取得后台屏，不能重放旧输入或自动切换到前台操作。"}"""
        if (binding != null && controller.resumeGeneration != observedGeneration && name in MUTATING_TOOLS) {
            return """{"ok":false,"code":"OBSERVE_AFTER_RESUME","error":"暂停后页面可能已经变化，请先调用 observe_screen 重新观察，再决定下一步。此操作尚未执行。"}"""
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
                    candidate.tools.setTakeoverListener { pauseForTakeover(candidate) }
                    candidate.heartbeat = maintenance.scheduleWithFixedDelay(candidate.tools::keepAlive, 30, 30, TimeUnit.SECONDS)
                }
            }
            phase(BackgroundScreenPhase.OpeningApp)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            controller.throwIfCancelled()
            var result = controller.withScreenOperation { next.tools.execute(name, arguments) }
            // A parked screen may need asynchronous engine cleanup after the app lease
            // has been returned. Retry only the explicit pre-launch rejection, never a timeout.
            // 2026111625：应用被切到主屏后，引擎要等它在主屏的窗口稳定（或最多约 4 秒）才收掉旧屏，
            // 以前只等 3×0.25 秒就把 CLEANUP_PENDING 交给模型，看起来就是「拉不回去」。现在最多等 [CLEANUP_WAIT_MS]。
            val cleanupDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(cleanupWaitMs)
            while (runCatching { JSONObject(result.orEmpty()).optString("code") == "CLEANUP_PENDING" }.getOrDefault(false)
                && System.nanoTime() < minOf(deadline, cleanupDeadline)) {
                phase(BackgroundScreenPhase.ReleasingScreen)
                controller.awaitRetryDelay(minOf(CLEANUP_RETRY_MS, cleanupWaitMs))
                result = controller.withScreenOperation { next.tools.execute(name, arguments) }
            }
            if (runCatching { JSONObject(result.orEmpty()).optBoolean("ok") }.getOrDefault(false)) {
                val opened = JSONObject(result.orEmpty())
                next.awaitingInput = opened.has("inputReady") && !opened.optBoolean("inputReady")
                onAppOpened(app)
            }
            if (runCatching { JSONObject(result.orEmpty()).optString("code") == "APP_TAKEN_OVER" }.getOrDefault(false)) {
                pauseForTakeover(next)
            }
            if (runCatching { JSONObject(result.orEmpty()).optString("code") in LOST_SESSION_CODES }.getOrDefault(false)) {
                cleanup(next, keepFinished = false)
            }
            return result
        }
        val current = synchronized(lock) { binding }
        if (current == null) {
            // A terminal guard and health query need no GUI session, but node reads never target display 0 implicitly.
            return if (name == "terminal" || name == "screen_state") ShadowTools(runId).intercept(name, arguments)
            else """{"ok":false,"code":"NO_SESSION","reason":"NO_ACTIVE_BINDING","error":"当前任务会话不可用；已完成页面可能仍保留。请报告状态，由用户决定是否重新打开，不能把新会话视作原页面恢复。"}"""
        }
        if (current.closing.get()) return """{"ok":false,"code":"CLEANUP_PENDING","operationMayHaveRun":false,"error":"当前任务的屏幕正在回收，请等待释放结果；不要强停应用或反复重开。"}"""
        if (name == "screen_finish") phase(BackgroundScreenPhase.RestoringDisplay)
        val observationGeneration = controller.resumeGeneration
        return controller.withScreenOperation {
            if (name in MUTATING_TOOLS && current.awaitingInput) {
                val rawReadiness = current.tools.execute("screen_input_state", "{}")
                controller.throwIfCancelled()
                val readiness = runCatching {
                    JSONObject(rawReadiness.orEmpty())
                }.getOrNull()
                if (readiness?.optBoolean("ok") != true || !readiness.optBoolean("ready")) {
                    return@withScreenOperation JSONObject().put("ok", false)
                        .put("code", readiness?.optString("code")?.takeIf { it.isNotBlank() } ?: "INPUT_NOT_READY")
                        .put("inputReady", false).put("inputState", readiness ?: JSONObject().put("reason", "OBSERVER_UNAVAILABLE"))
                        .put("operationMayHaveRun", false)
                        .put("message", "启动请求已接受，但当前窗口尚未确认可操作；本次没有发送输入，请检查后台状态或等待观察服务恢复。")
                        .toString()
                }
                current.awaitingInput = false
            }
            // Pause may have begun after preflight, while this call was waiting for admission.
            if (name in MUTATING_TOOLS && observationGeneration != controller.resumeGeneration)
                """{"ok":false,"code":"OBSERVE_AFTER_RESUME","error":"暂停期间页面可能变化，请重新观察；本次操作未执行。","operationMayHaveRun":false}"""
            else current.tools.execute(name, arguments)
        }.also { result -> afterCall(name, current, observationGeneration, result) }
    }

    /** Shared by single and concurrent calls: never replays an input whose outcome is unknown. */
    private fun afterCall(name: String, current: Binding, observationGeneration: Long, result: String?) {
        val response = runCatching { JSONObject(result.orEmpty()) }.getOrNull()
        if (response?.optString("code") == "APP_TAKEN_OVER") pauseForTakeover(current)
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
        /** 等引擎收掉旧屏（应用被切到主屏后的交还）最多多久；引擎那边最多约 4 秒，再留出巡检间隔。 */
        internal const val CLEANUP_WAIT_MS = 10_000L
        internal const val CLEANUP_RETRY_MS = 400L
        private val LOST_SESSION_CODES = setOf("NO_SESSION", "SESSION_CLOSED", "STALE_SCREEN", "SYSTEM_RESTARTED")
        internal val MUTATING_TOOLS = setOf("screen_tap", "screen_click", "screen_press", "screen_swipe", "screen_drag", "screen_scroll", "screen_text", "screen_key")
        private val OBSERVATION_TOOLS = setOf("screen_dump", "screen_list", "screen_shot")
        private val CONCURRENT_OBSERVATION_TOOLS = setOf("screen_dump", "screen_shot")
        private val observationPool = java.util.concurrent.Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "eta-screen-observe").apply { isDaemon = true }
        }
        private val sharedLeases = ScreenLeaseRegistry()
        private val maintenance = java.util.concurrent.Executors.newScheduledThreadPool(3) { runnable ->
            Thread(runnable, "eta-screen-maintenance").apply { isDaemon = true }
        }
    }
}
