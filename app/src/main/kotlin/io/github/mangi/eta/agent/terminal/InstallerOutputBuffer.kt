package io.github.mangi.eta.agent.terminal

/** Fixed-capacity command output. Profile diagnostics retain the tail; other installers keep the prefix. */
internal class InstallerOutputBuffer(private val capacity: Int = 64 * 1024, private val captureTail: Boolean = false) {
    init { require(capacity > 0) }
    private val bytes = ByteArray(capacity)
    private var length = 0
    private var next = 0
    private var discarded = false

    @Synchronized fun append(source: ByteArray, count: Int = source.size) {
        require(count in 0..source.size)
        if (!captureTail) {
            val copied = minOf(count, capacity - length)
            source.copyInto(bytes, length, 0, copied)
            length += copied
            if (copied < count) discarded = true
            return
        }
        if (count >= capacity) {
            if (length > 0 || count > capacity) discarded = true
            source.copyInto(bytes, 0, count - capacity, count)
            length = capacity
            next = 0
            return
        }
        if (length + count > capacity) discarded = true
        val first = minOf(count, capacity - next)
        source.copyInto(bytes, next, 0, first)
        if (first < count) source.copyInto(bytes, 0, first, count)
        next = (next + count) % capacity
        length = minOf(capacity, length + count)
    }

    @Synchronized fun text(): String {
        if (!captureTail || length < capacity) return bytes.decodeToString(0, length).trimEnd()
        return (bytes.copyOfRange(next, capacity) + bytes.copyOfRange(0, next)).decodeToString().trimEnd()
    }

    @Synchronized fun truncated(): Boolean = discarded
}
