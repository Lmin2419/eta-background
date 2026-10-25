package io.github.mangi.eta.agent.terminal

/**
 * 原生 PTY 组件的 stdin 帧格式：1 字节类型 + 2 字节大端长度 + 载荷。
 * stdin 是唯一能同时送达 Root 与普通身份进程的通道，终端字节与窗口尺寸因此共用它，
 * 由帧类型区分，避免在终端字节流里夹带可被用户输入伪造的控制序列。
 */
internal object PtyInputFrames {
    private const val TYPE_DATA = 0
    private const val TYPE_RESIZE = 1
    private const val HEADER_SIZE = 3
    const val MAX_PAYLOAD = 4096

    /** 终端输入字节；超出单帧上限时拆成连续数据帧。 */
    fun data(bytes: ByteArray): ByteArray {
        if (bytes.isEmpty()) return bytes
        val frameCount = (bytes.size + MAX_PAYLOAD - 1) / MAX_PAYLOAD
        val out = ByteArray(bytes.size + frameCount * HEADER_SIZE)
        var source = 0
        var target = 0
        while (source < bytes.size) {
            val size = minOf(MAX_PAYLOAD, bytes.size - source)
            target = writeHeader(out, target, TYPE_DATA, size)
            bytes.copyInto(out, target, source, source + size)
            source += size
            target += size
        }
        return out
    }

    /** 窗口尺寸；原生侧拒绝超出 1..1000 的值。 */
    fun resize(rows: Int, cols: Int): ByteArray {
        require(rows in 1..1000 && cols in 1..1000) { "终端尺寸超出范围" }
        val out = ByteArray(HEADER_SIZE + 4)
        val offset = writeHeader(out, 0, TYPE_RESIZE, 4)
        out[offset] = (rows ushr 8).toByte()
        out[offset + 1] = rows.toByte()
        out[offset + 2] = (cols ushr 8).toByte()
        out[offset + 3] = cols.toByte()
        return out
    }

    private fun writeHeader(out: ByteArray, offset: Int, type: Int, size: Int): Int {
        out[offset] = type.toByte()
        out[offset + 1] = (size ushr 8).toByte()
        out[offset + 2] = size.toByte()
        return offset + HEADER_SIZE
    }
}
