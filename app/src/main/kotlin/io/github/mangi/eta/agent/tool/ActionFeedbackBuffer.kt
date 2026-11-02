package io.github.mangi.eta.agent.tool

/** Small, operation-scoped memory only. Never keep AccessibilityEvent/Node objects. */
internal class ActionFeedbackBuffer(private val now: () -> Long) {
    data class Signal(val kind: String, val text: String, val elapsedMs: Long)
    class Ticket internal constructor(
        val owner: String, val packageName: String, val display: Int, val window: Int?,
        val started: Long,
    ) {
        internal val signals = ArrayList<Signal>()
        internal var closed = false
    }

    private val tickets = ArrayList<Ticket>()
    @Synchronized fun listening(packageName: String): Boolean {
        expire()
        return tickets.any { it.packageName == packageName && !it.closed }
    }

    @Synchronized
    fun begin(owner: String, packageName: String, display: Int, window: Int?): Ticket? {
        expire()
        if (owner.isBlank() || packageName.isBlank() || display < 0 || tickets.size >= 3) return null
        return Ticket(owner, packageName, display, window, now()).also(tickets::add)
    }

    @Synchronized
    fun record(packageName: String, display: Int?, window: Int?, eventAt: Long, kind: String, texts: List<String>) {
        expire()
        val received = now()
        // Unknown display is usable only when a real window ID matches. In particular,
        // do not assign a windowless toast to whichever background task ran last.
        val matches = tickets.filter {
            !it.closed && it.packageName == packageName && eventAt >= it.started && eventAt <= received &&
                (display == it.display || (display == null && window != null && window >= 0 && it.window == window))
        }
        if (matches.size != 1) return
        val ticket = matches.single()
        texts.asSequence().map { it.trim().take(512) }.filter(String::isNotEmpty).take(4).forEach { text ->
            if (ticket.signals.none { it.kind == kind && it.text == text }) {
                if (ticket.signals.size >= 8 && kind in setOf("notification_or_toast", "announcement")) {
                    ticket.signals.indexOfFirst { it.kind !in setOf("notification_or_toast", "announcement") }
                        .takeIf { it >= 0 }?.let(ticket.signals::removeAt)
                }
                if (ticket.signals.size >= 8) return@forEach
                ticket.signals += Signal(kind, text, eventAt - ticket.started)
            }
        }
    }

    @Synchronized fun read(ticket: Ticket?): List<Signal> = ticket?.signals?.toList().orEmpty()
    @Synchronized fun close(ticket: Ticket?) {
        ticket?.closed = true
        tickets.remove(ticket)
    }
    @Synchronized fun clear() { tickets.forEach { it.closed = true }; tickets.clear() }
    private fun expire() {
        tickets.removeAll { (it.closed || now() - it.started > 5_000).also { expired -> if (expired) it.closed = true } }
    }
}
