package io.github.mangi.eta.data.repository

import android.content.Context
import androidx.room.withTransaction
import io.github.mangi.eta.data.db.ConversationDao
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.data.db.openTextStream
import java.io.Reader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException

/** 只返回命中的会话标识，搜索不把未打开会话的正文带回界面状态。 */
internal object ConversationSearchRepository {
    suspend fun search(context: Context, query: String, matchingNoticeCodes: List<String>): Set<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptySet()
        val database = EtaDatabase.get(context.applicationContext)
        database.withTransaction {
            val dao = database.conversationDao()
            // 引用与分块共享读快照；内存仅保留当前块和跨块匹配所需的后缀。
            searchMessageStreams(dao, query) + dao.searchSystemNotices(matchingNoticeCodes)
        }
    }

    private suspend fun searchMessageStreams(dao: ConversationDao, query: String): Set<String> {
        val matches = mutableSetOf<String>()
        val cancellation = currentCoroutineContext()
        dao.messageSearchCursor().use { cursor ->
            while (cursor.moveToNext()) {
                cancellation.ensureActive()
                val conversationId = cursor.getString(1)
                if (conversationId in matches) continue
                val type = cursor.getString(2)
                val contentMatches = type in textMessageTypes &&
                    dao.openTextStream("conversation_messages", cursor.getString(0), "content", cursor.getString(3))
                        .reader(Charsets.UTF_8).use { it.containsText(query, cancellation::ensureActive) }
                val detailMatches = when (type) {
                    "tool" -> (4..6).any { column -> cursor.getString(column)?.contains(query, ignoreCase = true) == true }
                    "system_notice" -> cursor.getString(6)?.contains(query, ignoreCase = true) == true
                    "tool_summary" -> toolNamesMatch(cursor.getString(7), query)
                    else -> false
                }
                if (contentMatches || detailMatches) {
                    matches += conversationId
                }
            }
        }
        return matches
    }

    private fun toolNamesMatch(stored: String, query: String): Boolean {
        val names = try { JSONArray(stored) } catch (_: JSONException) { return false }
        return (0 until names.length()).any { names.optString(it).contains(query, ignoreCase = true) }
    }

    private fun Reader.containsText(query: String, checkCancelled: () -> Unit): Boolean {
        val buffer = CharArray(DEFAULT_BUFFER_SIZE)
        var overlap = ""
        while (true) {
            checkCancelled()
            val count = read(buffer)
            if (count < 0) return false
            val candidate = overlap + String(buffer, 0, count)
            if (candidate.contains(query, ignoreCase = true)) return true
            overlap = candidate.takeLast(query.length - 1)
        }
    }

    private val textMessageTypes = setOf("user", "assistant", "thinking", "tool")
}
