package io.github.mangi.eta.data.db

import android.database.Cursor
import java.io.InputStream

/** JSON 解码器逐块消费正文，避免先拼接整份字符串再创建第二份对象图。 */
internal fun ChunkedTextDao.openTextStream(table: String, owner: String, field: String, stored: String): InputStream {
    if (!stored.startsWith(ChunkedTextDao.REFERENCE_PREFIX)) return stored.byteInputStream(Charsets.UTF_8)
    val sizes = stored.removePrefix(ChunkedTextDao.REFERENCE_PREFIX).split(':')
    check(sizes.size == 2) { "Invalid history payload reference" }
    val count = sizes[0].toInt()
    val length = sizes[1].toInt()
    check(count > 0 && length > 0) { "Invalid history payload size" }
    return ChunkedTextInputStream(textChunkCursor(table, owner, field), count, length)
}

private class ChunkedTextInputStream(
    private val cursor: Cursor,
    private val expectedCount: Int,
    private val expectedLength: Int,
) : InputStream() {
    private var chunk = byteArrayOf()
    private var position = 0
    private var chunkCount = 0
    private var textLength = 0
    private var ended = false

    override fun read(): Int = if (ensureChunk()) chunk[position++].toInt() and 0xff else -1

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
        if (length == 0) return 0
        if (!ensureChunk()) return -1
        val count = minOf(length, chunk.size - position)
        chunk.copyInto(buffer, offset, position, position + count)
        position += count
        return count
    }

    private fun ensureChunk(): Boolean {
        while (position == chunk.size && !ended) {
            if (!cursor.moveToNext()) {
                check(chunkCount == expectedCount && textLength == expectedLength) { "History payload length mismatch" }
                ended = true
                chunk = byteArrayOf()
                break
            }
            check(cursor.getInt(0) == chunkCount && chunkCount < expectedCount) { "History payload chunk out of order" }
            val text = cursor.getString(1)
            textLength += text.length
            check(textLength <= expectedLength) { "History payload length mismatch" }
            chunk = text.toByteArray(Charsets.UTF_8)
            chunkCount++
            position = 0
        }
        return !ended
    }

    override fun close() = cursor.close()
}
