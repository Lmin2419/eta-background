package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.runtime.AgentRunController
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Bounds successful model streams that keep sending bytes without producing model output. */
internal fun <T> withProviderStreamProgress(
    controller: AgentRunController,
    cancelCall: () -> Unit,
    onEvent: (ProviderEvent) -> Unit,
    timeoutMillis: Long,
    consume: ((ProviderEvent) -> Unit) -> T,
): T {
    val watchdog = ProviderStreamWatchdog(controller, cancelCall, timeoutMillis)
    val pauseBinding = controller.observePause { paused -> if (!paused) watchdog.resume() }
    try {
        watchdog.resume()
        val value = try {
            consume { event ->
                if (event.isStreamProgress()) watchdog.progress()
                onEvent(event)
            }
        } catch (failure: Exception) {
            if (watchdog.finish() && !controller.isCancelled) throw streamIdleTimeout(failure)
            throw failure
        }
        if (watchdog.finish()) {
            controller.throwIfCancelled()
            throw streamIdleTimeout()
        }
        return value
    } finally {
        watchdog.finish()
        pauseBinding.close()
    }
}

private fun ProviderEvent.isStreamProgress(): Boolean = when (this) {
    is ProviderEvent.BlockStart, is ProviderEvent.BlockEnd,
    is ProviderEvent.HostedToolStarted, is ProviderEvent.HostedToolFinished,
    is ProviderEvent.Completed -> true
    is ProviderEvent.BlockDelta -> delta.isNotEmpty()
    is ProviderEvent.SearchSuggestions -> html.isNotEmpty()
    ProviderEvent.RequestStarted, is ProviderEvent.ResponseHeaders, is ProviderEvent.Usage -> false
}

private fun streamIdleTimeout(cause: Throwable? = null) =
    AgentModelFailure("MODEL_TIMEOUT", true, "模型响应流长时间未产生有效输出，请稍后重试。", cause)

private class ProviderStreamWatchdog(
    private val controller: AgentRunController,
    private val cancelCall: () -> Unit,
    timeoutMillis: Long,
) {
    private val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
    private val lock = Any()
    private var lastProgress = System.nanoTime()
    private var scheduled: ScheduledFuture<*>? = null
    private var finished = false
    private var timedOut = false

    fun progress() = synchronized(lock) {
        lastProgress = System.nanoTime()
    }

    /** A deliberate run pause never consumes the stream's progress budget. */
    fun resume() = synchronized(lock) {
        if (!finished && !timedOut && !controller.isPaused && !controller.isCancelled) {
            lastProgress = System.nanoTime()
            if (scheduled == null) schedule(timeoutNanos)
        }
    }

    fun finish(): Boolean = synchronized(lock) {
        finished = true
        scheduled?.cancel(false)
        scheduled = null
        timedOut
    }

    private fun schedule(delayNanos: Long) {
        scheduled = scheduler.schedule(::checkProgress, delayNanos, TimeUnit.NANOSECONDS)
    }

    private fun checkProgress() {
        synchronized(lock) {
            scheduled = null
            if (finished || controller.isPaused || controller.isCancelled) return
            val remaining = timeoutNanos - (System.nanoTime() - lastProgress)
            if (remaining > 0) {
                schedule(remaining)
                return
            }
            timedOut = true
        }
        cancelCall()
    }

    private companion object {
        val scheduler = ScheduledThreadPoolExecutor(1) { runnable ->
            Thread(runnable, "model-stream-progress").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }
    }
}
