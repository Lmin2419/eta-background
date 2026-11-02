package io.github.mangi.eta.data.repository

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A saved instruction, not a conversation snapshot or a second execution engine. */
internal data class CommonTask(
    val id: String,
    val name: String,
    val instruction: String,
    val shortcutToken: String,
) {
    // Content revision, not the private launcher capability. Old version-1 files need no migration.
    val revision: String get() = MessageDigest.getInstance("SHA-256")
        .digest(JSONArray(listOf(id, name, instruction)).toString().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

internal class CommonTaskRepository(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "common_tasks.json"))

    fun load(): List<CommonTask> = synchronized(lock) { read() }

    fun save(id: String?, name: String, instruction: String, expectedRevision: String? = null,
        uniqueName: Boolean = false): CommonTask = synchronized(lock) {
        val title = name.trim()
        val prompt = instruction.trim()
        require(title.isNotEmpty() && title.length <= MAX_NAME_LENGTH)
        require(prompt.isNotEmpty() && prompt.length <= MAX_INSTRUCTION_LENGTH)
        val tasks = read()
        val previous = id?.let { target -> tasks.firstOrNull { it.id == target } }
        require(id == null || previous != null) { "COMMON_TASK_NOT_FOUND" }
        require(expectedRevision == null || previous?.revision == expectedRevision) { "COMMON_TASK_CONFLICT" }
        if (id == null && uniqueName) {
            val matches = tasks.filter { it.name.equals(title, ignoreCase = true) }
            // A retried creation must not create a second shortcut; a different body must not overwrite one.
            if (matches.size == 1 && matches.single().instruction == prompt) return@synchronized matches.single()
            require(matches.isEmpty()) { "COMMON_TASK_NAME_EXISTS" }
        }
        require(previous != null || tasks.size < MAX_TASKS) { "COMMON_TASK_LIMIT" }
        val task = CommonTask(
            id = previous?.id ?: UUID.randomUUID().toString(),
            name = title,
            instruction = prompt,
            shortcutToken = previous?.shortcutToken ?: UUID.randomUUID().toString(),
        )
        write(if (previous == null) tasks + task else tasks.map { if (it.id == task.id) task else it })
        task
    }

    fun delete(id: String, expectedRevision: String? = null) = synchronized(lock) {
        val tasks = read()
        if (expectedRevision != null) {
            val task = tasks.firstOrNull { it.id == id }
            require(task != null) { "COMMON_TASK_NOT_FOUND" }
            require(task.revision == expectedRevision) { "COMMON_TASK_CONFLICT" }
        }
        if (tasks.any { it.id == id }) write(tasks.filterNot { it.id == id })
    }

    fun resolveShortcut(id: String, token: String): CommonTask? = synchronized(lock) {
        read().firstOrNull { it.id == id && it.shortcutToken == token }
    }

    private fun read(): List<CommonTask> = try {
        readValidated()
    } catch (error: IOException) {
        throw error
    } catch (error: Exception) {
        throw IOException("COMMON_TASK_INVALID_DATA", error)
    }

    private fun readValidated(): List<CommonTask> {
        val atomic = AtomicFile(file)
        if (!file.exists() && !File(file.path + ".bak").exists()) return emptyList()
        // A malformed file must never silently turn into an empty list and be overwritten.
        return atomic.openRead().use { stream ->
            val bytes = stream.readNBytes(MAX_FILE_BYTES + 1)
            if (bytes.size > MAX_FILE_BYTES) throw IOException("COMMON_TASK_FILE_TOO_LARGE")
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            require(root.getInt("version") == 1) { "COMMON_TASK_VERSION_UNSUPPORTED" }
            val items = root.getJSONArray("tasks")
            require(items.length() <= MAX_TASKS)
            val tasks = List(items.length()) { index ->
                val item = items.getJSONObject(index)
                CommonTask(item.getString("id"), item.getString("name"), item.getString("instruction"),
                    item.getString("shortcutToken")).also {
                    require(it.id.length in 1..64 && it.shortcutToken.length in 32..64)
                    require(it.name.isNotBlank() && it.name.length <= MAX_NAME_LENGTH)
                    require(it.instruction.isNotBlank() && it.instruction.length <= MAX_INSTRUCTION_LENGTH)
                }
            }
            require(tasks.map { it.id }.distinct().size == tasks.size)
            tasks
        }
    }

    private fun write(tasks: List<CommonTask>) {
        val root = JSONObject().put("version", 1).put("tasks", JSONArray().apply {
            tasks.forEach { task -> put(JSONObject().put("id", task.id).put("name", task.name)
                .put("instruction", task.instruction).put("shortcutToken", task.shortcutToken)) }
        })
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_FILE_BYTES)
        file.parentFile?.mkdirs()
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
            // UI and AgentRuntime execute in the same process; no polling or second database needed.
            mutableChanges.value += 1
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    }

    companion object {
        const val MAX_TASKS = 64
        const val MAX_NAME_LENGTH = 60
        const val MAX_INSTRUCTION_LENGTH = 8_000
        private const val MAX_FILE_BYTES = 2 * 1024 * 1024
        private val lock = Any()
        private val mutableChanges = MutableStateFlow(0L)
        val changes = mutableChanges.asStateFlow()
    }
}
