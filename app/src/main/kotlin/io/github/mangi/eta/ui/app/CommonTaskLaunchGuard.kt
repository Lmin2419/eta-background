package io.github.mangi.eta.ui.app

/** Activity/ViewModel lifetime guard: recomposition/recreation must not submit twice. */
internal class CommonTaskLaunchGuard {
    private val consumed = linkedSetOf<String>()
    private val lastStarted = linkedMapOf<String, Long>()

    fun claim(requestId: String, taskId: String, elapsedMs: Long): Boolean {
        if (requestId in consumed) return false
        consumed.add(requestId)
        while (consumed.size > 64) consumed.remove(consumed.first())
        val previous = lastStarted[taskId]
        if (previous != null && elapsedMs - previous in 0 until DOUBLE_TAP_MS) return false
        lastStarted[taskId] = elapsedMs
        while (lastStarted.size > 64) lastStarted.remove(lastStarted.keys.first())
        return true
    }

    private companion object { const val DOUBLE_TAP_MS = 1_200L }
}
