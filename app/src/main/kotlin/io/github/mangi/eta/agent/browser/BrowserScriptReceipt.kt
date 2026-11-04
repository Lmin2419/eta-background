package io.github.mangi.eta.agent.browser

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Waiting for a receipt never repeats the submitted JS or stops a newer document's navigation. */
internal object BrowserScriptReceipt {
    enum class Reason { DOCUMENT_CHANGED, TIMEOUT }
    class Failure(val reason: Reason) : RuntimeException(reason.name)

    fun <T> await(
        future: CompletableFuture<T>,
        timeoutMs: Long,
        dispatchedDocument: () -> Long,
        currentDocument: () -> Long,
        checkCancelled: () -> Unit,
    ): T {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        try {
            while (true) {
                checkCancelled()
                val dispatched = dispatchedDocument()
                if (dispatched >= 0L && dispatched != currentDocument()) throw Failure(Reason.DOCUMENT_CHANGED)
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) throw Failure(Reason.TIMEOUT)
                try {
                    val value = future.get(minOf(50L, TimeUnit.NANOSECONDS.toMillis(remaining).coerceAtLeast(1L)), TimeUnit.MILLISECONDS)
                    checkCancelled()
                    if (dispatchedDocument() >= 0L && dispatchedDocument() != currentDocument()) throw Failure(Reason.DOCUMENT_CHANGED)
                    return value
                } catch (_: TimeoutException) {
                    // Check document replacement and cancellation before the next short wait.
                } catch (error: ExecutionException) {
                    throw error.cause ?: error
                }
            }
        } finally {
            if (!future.isDone) future.cancel(false)
        }
    }
}
