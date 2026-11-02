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

    init { refresh() }

    fun refresh() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                tasks = withContext(Dispatchers.IO) { repository.load() }
                loadFailed = false
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { loadFailed = true
            } finally { busy = false }
        }
    }

    fun save(id: String?, name: String, instruction: String, onSaved: (CommonTask) -> Unit) = mutate {
        val saved = withContext(Dispatchers.IO) { repository.save(id, name, instruction) }
        tasks = withContext(Dispatchers.IO) { repository.load() }
        onSaved(saved)
    }

    fun delete(id: String, onDeleted: () -> Unit) = mutate {
        withContext(Dispatchers.IO) { repository.delete(id) }
        tasks = withContext(Dispatchers.IO) { repository.load() }
        onDeleted()
    }

    suspend fun resolveShortcut(id: String, token: String): CommonTask? = withContext(Dispatchers.IO) {
        repository.resolveShortcut(id, token)
    }

    fun dismissError() { error = false }

    private fun mutate(operation: suspend () -> Unit) {
        if (busy || loadFailed) return
        busy = true
        scope.launch {
            try { operation()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { error = true
            } finally { busy = false }
        }
    }
}
