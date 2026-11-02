package io.github.mangi.eta.ui

import io.github.mangi.eta.data.repository.CommonTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class CommonTaskShortcutsTest {
    @Test fun shortcutContainsOnlyLocalIdentityAndNeverTheSavedPromptOrConversation() {
        val task = CommonTask("local-id", "Task", "Private saved instruction", "t".repeat(36))
        val intent = CommonTaskShortcuts.intent(RuntimeEnvironment.getApplication(), task)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(MainActivity.ACTION_RUN_COMMON_TASK, intent.action)
        assertEquals(setOf(MainActivity.EXTRA_COMMON_TASK_ID, MainActivity.EXTRA_COMMON_TASK_TOKEN), intent.extras?.keySet())
        assertFalse(intent.toUri(0).contains(task.instruction))
        assertEquals(task.id, MainActivityLaunchRequests().accept(intent).commonTask?.taskId)
    }
}
