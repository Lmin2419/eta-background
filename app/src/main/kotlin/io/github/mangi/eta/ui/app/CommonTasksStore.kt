package io.github.mangi.eta.ui.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.mangi.eta.data.repository.CommonTask
import io.github.mangi.eta.data.repository.CommonTaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class CommonTasksStore(context: Context, private val scope: CoroutineScope) {
    private val repository = CommonTaskRepository(context.applicationContext)
    var tasks by mutableStateOf<List<CommonTask>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var loadFailed by mutableStateOf(false)
        private set
    var error by mutableStateOf(false)
        private set
    var conflict by mutableStateOf(false)
        private set

    private var refreshPending = false

    init { scope.launch { CommonTaskRepository.changes.collect { refresh() } } }

    fun refresh() {
        if (busy) { refreshPending = true; return }
        refreshPending = false
        busy = true
        scope.launch {
            try {
                tasks = withContext(Dispatchers.IO) { repository.load() }
                loadFailed = false
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { loadFailed = true
            } finally { finishOperation() }
        }
    }

    fun save(id: String?, name: String, instruction: String, expectedRevision: String? = null,
        onSaved: (CommonTask) -> Unit) = mutate {
        val saved = withContext(Dispatchers.IO) { repository.save(id, name, instruction, expectedRevision) }
        tasks = withContext(Dispatchers.IO) { repository.load() }
        onSaved(saved)
    }

    fun delete(id: String, expectedRevision: String? = null, onDeleted: () -> Unit) = mutate {
        withContext(Dispatchers.IO) { repository.delete(id, expectedRevision) }
        tasks = withContext(Dispatchers.IO) { repository.load() }
        onDeleted()
    }

    suspend fun resolveShortcut(id: String, token: String): CommonTask? = withContext(Dispatchers.IO) {
        repository.resolveShortcut(id, token)
    }

    fun dismissError() { error = false; conflict = false }

    private fun mutate(operation: suspend () -> Unit) {
        if (busy || loadFailed) return
        busy = true
        scope.launch {
            try { operation()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                conflict = failure.message == "COMMON_TASK_CONFLICT"
                error = true
            } finally { finishOperation() }
        }
    }

    private fun finishOperation() {
        busy = false
        if (refreshPending && scope.coroutineContext[kotlinx.coroutines.Job]?.isActive != false) refresh()
    }
}
