package io.github.mangi.eta.agent.tool

/** Per-run guard. Key describes a target, not the click API used to reach it. */
internal class ActionRepeatGuard(private val now: () -> Long) {
    private data class Attempt(val state: String, val count: Int, val at: Long)
    private val entries = LinkedHashMap<String, Attempt>()
    fun blocked(target: String?, state: String): Boolean {
        val entry = entries[target] ?: return false
        if (entry.state != state || now() - entry.at > 120_000) { entries.remove(target); return false }
        return entry.count >= 2
    }
    fun record(target: String?, state: String, dispatched: Boolean, changed: Boolean) {
        if (target == null) return
        if (!dispatched) return // A stale observation/error must not reset an earlier pending action.
        if (changed) { entries.remove(target); return }
        val old = entries[target]?.takeIf { it.state == state && now() - it.at <= 120_000 }
        entries[target] = Attempt(state, (old?.count ?: 0) + 1, now())
        if (entries.size > 16) entries.remove(entries.keys.first())
    }
    fun clear() = entries.clear()
}
