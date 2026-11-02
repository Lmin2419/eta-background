package io.github.mangi.eta.ui.app

import io.github.mangi.eta.agent.tool.AgentCommonTaskTools
import io.github.mangi.eta.data.repository.CommonTaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [36])
class CommonTasksStoreTest {
    @Test fun agentChangesRefreshAnAlreadyOpenLibraryAndRejectStaleEditor() {
        val context = RuntimeEnvironment.getApplication()
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Main.immediate)
        try {
            val repository = CommonTaskRepository(context)
            val first = repository.save(null, "First", "Old")
            val store = CommonTasksStore(context, scope)
            await { !store.busy && store.tasks.size == 1 }
            val tools = AgentCommonTaskTools(repository)
            val saved = JSONObject(tools.execute(JSONObject().put("action", "save")
                .put("name", "Second").put("instruction", "Agent workflow")))
            assertTrue(saved.getBoolean("ok"))
            await { !store.busy && store.tasks.size == 2 }
            val newFirst = repository.save(first.id, "First", "Changed by Agent", first.revision)
            await { !store.busy && store.tasks.first().revision == newFirst.revision }
            store.save(first.id, "First", "Stale editor", first.revision) { fail("Stale save must fail") }
            await { !store.busy && store.error }
            assertTrue(store.conflict)
            assertEquals("Changed by Agent", repository.load().first().instruction)
            store.dismissError()
            repository.delete(newFirst.id, newFirst.revision)
            await { !store.busy && store.tasks.size == 1 }
            assertEquals("Second", store.tasks.single().name)
        } finally {
            scope.cancel()
            await { job.isCompleted }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition() && System.nanoTime() < deadline) {
            ShadowLooper.idleMainLooper(10, TimeUnit.MILLISECONDS)
            Thread.sleep(1)
        }
        assertTrue("Timed out waiting for library update", condition())
    }
}
