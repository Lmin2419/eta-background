package io.github.mangi.eta.agent.tool

/** Bounded, task-scoped copies only. Never retain AccessibilityEvent/Node objects. */
internal class ActionFeedbackBuffer(private val now: () -> Long) {
    data class Signal(val kind: String, val text: String, val elapsedMs: Long)
    data class LateFeedback(val actionId: Long, val signals: List<Signal>)
    class Ticket internal constructor(
        val owner: String, val packageName: String, val display: Int, val window: Int?,
        val started: Long, val actionId: Long,
    ) {
        internal val signals = ArrayList<Signal>()
        internal val delivered = HashSet<Pair<String, String>>()
        internal var finished = false
        internal var closed = false
    }

    private val lock = Object()
    private val tickets = ArrayList<Ticket>()
    private var nextActionId = 0L

    fun listening(packageName: String): Boolean = synchronized(lock) {
        expire()
        tickets.any { it.packageName == packageName && collecting(it) }
    }

    fun begin(owner: String, packageName: String, display: Int, window: Int?): Ticket? = synchronized(lock) {
        expire()
        if (owner.isBlank() || packageName.isBlank() || display < 0) return@synchronized null
        // One action per owner. Input callers must consume late evidence before beginning.
        removeOwner(owner)
        if (tickets.size >= MAX_OWNERS) return@synchronized null
        Ticket(owner, packageName, display, window, now(), ++nextActionId).also(tickets::add)
    }

    fun record(packageName: String, display: Int?, window: Int?, eventAt: Long, kind: String, texts: List<String>) = synchronized(lock) {
        expire()
        val received = now()
        // Unknown display requires an exact window. Windowless toasts are never assigned
        // to whichever background task happened to run last.
        val matches = tickets.filter {
            collecting(it) && it.packageName == packageName && eventAt >= it.started && eventAt <= received &&
                (display == it.display || (display == null && window != null && window >= 0 && it.window == window))
        }
        if (matches.size != 1) return@synchronized
        val ticket = matches.single()
        texts.asSequence().map { it.trim().take(512) }.filter(String::isNotEmpty).take(4).forEach { text ->
            if (ticket.signals.none { it.kind == kind && it.text == text }) {
                if (ticket.signals.size >= 8 && kind in PRIORITY_KINDS) {
                    ticket.signals.indexOfFirst { it.kind !in PRIORITY_KINDS }
                        .takeIf { it >= 0 }?.let(ticket.signals::removeAt)
                }
                if (ticket.signals.size >= 8) return@forEach
                ticket.signals += Signal(kind, text, eventAt - ticket.started)
            }
        }
        lock.notifyAll()
    }

    fun read(ticket: Ticket?): List<Signal> = synchronized(lock) { ticket?.signals?.toList().orEmpty() }

    /** Event-driven wake-up. Caller owns the total deadline; no recurring timer is installed. */
    fun awaitSignal(ticket: Ticket?, timeoutMs: Long): Boolean = synchronized(lock) {
        if (ticket != null && !ticket.closed && ticket.signals.isEmpty() && timeoutMs > 0) lock.wait(timeoutMs)
        ticket != null && !ticket.closed
    }

    /** Publish what is known now, keeping only the short text listener alive for late notices. */
    fun complete(ticket: Ticket?): List<Signal> = synchronized(lock) {
        if (ticket == null) return@synchronized emptyList()
        ticket.finished = true
        ticket.signals.toList().also { signals -> signals.forEach { ticket.delivered += it.kind to it.text } }
    }

    /** Consume once before the next mutation. A changed page/display cannot inherit old evidence. */
    fun takeLate(owner: String, packageName: String, display: Int, window: Int?): LateFeedback? = synchronized(lock) {
        expire()
        val ticket = tickets.singleOrNull {
            it.owner == owner && it.finished && it.packageName == packageName && it.display == display &&
                (it.window == null || (window != null && it.window == window))
        }
        val late = ticket?.signals?.filter { (it.kind to it.text) !in ticket.delivered }.orEmpty()
        // Retire the old listener even when no new message exists, before dispatching again.
        removeOwner(owner)
        if (ticket != null && late.isNotEmpty()) LateFeedback(ticket.actionId, late) else null
    }

    fun close(ticket: Ticket?) = synchronized(lock) {
        ticket?.closed = true
        tickets.remove(ticket)
        lock.notifyAll()
    }
    fun clearOwner(owner: String) = synchronized(lock) { removeOwner(owner); lock.notifyAll() }
    fun clear() = synchronized(lock) { tickets.forEach { it.closed = true }; tickets.clear(); lock.notifyAll() }

    private fun collecting(ticket: Ticket) = !ticket.closed && now() - ticket.started <= LISTEN_MS
    private fun removeOwner(owner: String) {
        tickets.removeAll { (it.owner == owner).also { removed -> if (removed) it.closed = true } }
    }
    private fun expire() {
        tickets.removeAll {
            (it.closed || now() - it.started > if (it.finished) RETAIN_MS else LISTEN_MS).also { expired ->
                if (expired) it.closed = true
            }
        }
    }

    private companion object {
        const val MAX_OWNERS = 3 // foreground plus the two background tasks
        const val LISTEN_MS = 5_000L
        const val RETAIN_MS = 120_000L // copied late text only; no capture work during retention
        val PRIORITY_KINDS = setOf("notification_or_toast", "announcement")
    }
}
