package io.github.mangi.eta.agent.terminal

import io.github.mangi.eta.core.AgentLogger
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.json.JSONArray
import org.json.JSONObject

/** App 持有后台命令；Agent run 只提交和查询，结束回复不会释放任务。 */
internal class BackgroundTerminalJobs(
    private val supervisor: DetachedTaskSupervisor,
    private val logger: AgentLogger,
    private val acquireLease: (() -> Unit) -> Boolean = { true },
    private val releaseLease: () -> Unit = {},
    private val onCompleted: (DetachedTask) -> Unit = {},
    private val completionNotificationsEnabled: () -> Boolean = { true },
) : AutoCloseable {
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "eta-background-commands").apply { isDaemon = true }
    }
    private val waitLock = ReentrantLock()
    private val changed = waitLock.newCondition()
    private var monitor: ScheduledFuture<*>? = null
    private var leased = false
    private var closed = false

    @Synchronized
    fun start(
        launcher: DetachedTaskSupervisor,
        command: String,
        cwd: String,
        identity: String,
        environment: TerminalEnvironment,
        description: String,
        timeoutMs: Long,
    ): String {
        if (closed) return failure("BACKGROUND_CLOSED", "后台命令宿主已关闭")
        if (!lease()) return failure("BACKGROUND_START_NOT_ALLOWED", "系统未允许启动后台执行，请返回 Eta 后重试")
        return when (val result = launcher.start(command, cwd, identity, environment,
            commandTask = true, description = description, timeoutMs = timeoutMs.coerceAtLeast(0))) {
            is DaemonStartResult.Failed -> {
                if (monitor == null) release()
                failure(result.code, result.message)
            }
            is DaemonStartResult.Started -> {
                monitor()
                taskJson(result.task).put("action", "start").toString()
            }
        }
    }

    fun list(activeOnly: Boolean): String {
        refresh()
        val tasks = supervisor.list().filter { it.task.commandTask && (!activeOnly || it.task.completedAt == null) }
        return base("list").put("tasks", JSONArray(tasks.map { taskJson(it.task) })).toString()
    }

    fun read(id: String, maxBytes: Int = 16_000): String {
        refresh()
        val task = supervisor.findTask(id)?.takeIf { it.commandTask }
            ?: return failure("JOB_NOT_FOUND", "未找到后台命令，请用 terminal_job 的 list 查看任务")
        val logs = supervisor.readLogs(id, maxBytes)
        val result = taskJson(task).put("action", "read")
        if (!logs.ok) return result.put("ok", false).put("code", logs.code).put("message", logs.message).toString()
        return result.put("output", logs.text).put("output_truncated", logs.truncated).toString()
    }

    fun stop(id: String): String {
        val task = supervisor.findTask(id)?.takeIf { it.commandTask }
            ?: return failure("JOB_NOT_FOUND", "未找到后台命令")
        if (!supervisor.stopCommand(task.id)) return failure("JOB_STOP_FAILED", "无法停止后台命令，请检查当前身份授权")
        refresh()
        return taskJson(supervisor.findTask(id) ?: task).put("action", "stop").toString()
    }

    fun waitFor(id: String, timeoutMs: Long, isCancelled: () -> Boolean): String {
        resume()
        if (supervisor.findTask(id)?.commandTask != true) return failure("JOB_NOT_FOUND", "未找到后台命令")
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.coerceIn(0, 60_000))
        waitLock.withLock {
            while (supervisor.findTask(id)?.completedAt == null) {
                if (isCancelled()) return failure("CANCELLED", "已取消等待；后台命令仍保留，可单独停止")
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) break
                changed.awaitNanos(minOf(remaining, TimeUnit.MILLISECONDS.toNanos(200)))
            }
        }
        val task = supervisor.findTask(id) ?: return failure("JOB_NOT_FOUND", "后台命令已清理")
        return taskJson(task).put("action", "wait").put("wait_timed_out", task.completedAt == null).toString()
    }

    /** 重新打开 App 后从同一份任务记录恢复观察，不重新执行命令。 */
    @Synchronized
    fun resume() {
        if (closed) return
        refresh()
        if (supervisor.list().any { it.task.commandTask && it.task.completedAt == null } && lease()) monitor()
    }

    @Synchronized
    private fun refresh() {
        if (closed) return
        try {
            var tasks = supervisor.list().filter { it.task.commandTask }
            val now = System.currentTimeMillis()
            val expired = tasks.filter { it.running && it.task.timeoutMs > 0 && now - it.task.startedAt >= it.task.timeoutMs }
            expired.forEach { supervisor.stopCommand(it.task.id, "timed_out") }
            if (expired.isNotEmpty()) tasks = supervisor.list().filter { it.task.commandTask }
            tasks.filter { it.task.completedAt != null && !it.task.completionNotified }.forEach { status ->
                try {
                    onCompleted(status.task)
                    supervisor.markCompletionNotified(status.task.id)
                } catch (failure: Exception) {
                    logger.warn("Background command notification failed: type=${failure.javaClass.simpleName}")
                }
            }
            waitLock.withLock { changed.signalAll() }
            if (tasks.none { it.running }) {
                monitor?.cancel(false)
                monitor = null
                release()
            }
        } catch (failure: Exception) {
            logger.warn("Background command refresh failed: type=${failure.javaClass.simpleName}")
        }
    }

    private fun monitor() {
        if (monitor == null) monitor = executor.scheduleWithFixedDelay(::refresh, 0, 1, TimeUnit.SECONDS)
    }

    private fun lease(): Boolean {
        if (!leased) leased = acquireLease { stopAll() }
        return leased
    }

    private fun release() {
        if (leased) {
            leased = false
            releaseLease()
        }
    }

    @Synchronized
    fun stopAll() {
        supervisor.list().filter { it.task.commandTask && it.running }.forEach { supervisor.stopCommand(it.task.id) }
        refresh()
    }

    @Synchronized
    override fun close() {
        stopAll()
        closed = true
        monitor?.cancel(false)
        monitor = null
        executor.shutdownNow()
        release()
        waitLock.withLock { changed.signalAll() }
    }

    private fun taskJson(task: DetachedTask): JSONObject = base("status")
        .put("job_id", task.id).put("description", task.description)
        .put("environment", task.environment.wireName).put("identity", task.identity).put("cwd", task.cwd)
        .put("status", task.commandStatus).put("running", task.completedAt == null)
        .put("started_at", task.startedAt).put("completed_at", task.completedAt ?: JSONObject.NULL)
        .put("exit_code", task.exitCode ?: JSONObject.NULL).put("end_reason", task.endReason ?: JSONObject.NULL)
        .put("output_path", supervisor.hostDaemonPath(task, task.logPath)).put("output_environment", "android")
        .put("timeout_ms", task.timeoutMs)
        .put("completion_notifications_enabled", completionNotificationsEnabled())

    private fun base(action: String) = JSONObject().put("ok", true).put("tool", "terminal_job").put("action", action)
    private fun failure(code: String, message: String) = JSONObject().put("ok", false).put("tool", "terminal_job")
        .put("code", code).put("message", message).toString()
}
