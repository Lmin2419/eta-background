package io.github.mangi.eta.data.repository

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** A saved instruction, not a conversation snapshot or a second execution engine. */
internal data class CommonTask(
    val id: String,
    val name: String,
    val instruction: String,
    val shortcutToken: String,
)

internal class CommonTaskRepository(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "common_tasks.json"))

    fun load(): List<CommonTask> = synchronized(lock) { read() }

    fun save(id: String?, name: String, instruction: String): CommonTask = synchronized(lock) {
        val title = name.trim()
        val prompt = instruction.trim()
        require(title.isNotEmpty() && title.length <= MAX_NAME_LENGTH)
        require(prompt.isNotEmpty() && prompt.length <= MAX_INSTRUCTION_LENGTH)
        val tasks = read()
        val previous = id?.let { target -> tasks.firstOrNull { it.id == target } }
        require(id == null || previous != null) { "COMMON_TASK_NOT_FOUND" }
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

    fun delete(id: String) = synchronized(lock) {
        val tasks = read()
        if (tasks.any { it.id == id }) write(tasks.filterNot { it.id == id })
    }

    fun resolveShortcut(id: String, token: String): CommonTask? = synchronized(lock) {
        read().firstOrNull { it.id == id && it.shortcutToken == token }
    }

    private fun read(): List<CommonTask> {
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
    }
}
