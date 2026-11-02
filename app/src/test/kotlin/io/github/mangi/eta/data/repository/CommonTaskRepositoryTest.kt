package io.github.mangi.eta.data.repository

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class CommonTaskRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private fun file() = File(folder.root, "tasks.json")

    @Test fun saveEditCopyDeleteSurviveNewRepositoryInstances() {
        val repo = CommonTaskRepository(file())
        val task = repo.save(null, "  Morning  ", "  Read messages\nCount today  ")
        assertEquals(listOf(task), CommonTaskRepository(file()).load())
        assertEquals("Morning", task.name)
        assertEquals("Read messages\nCount today", task.instruction)

        val edited = repo.save(task.id, "Evening", "Read tonight")
        assertEquals(task.id, edited.id)
        assertEquals(task.shortcutToken, edited.shortcutToken)
        val copy = repo.save(null, "Copy", edited.instruction)
        assertNotEquals(edited.id, copy.id)
        assertNotEquals(edited.shortcutToken, copy.shortcutToken)
        assertEquals(listOf(edited, copy), CommonTaskRepository(file()).load())

        repo.delete(edited.id)
        assertEquals(listOf(copy), CommonTaskRepository(file()).load())
        assertNull(repo.resolveShortcut(edited.id, edited.shortcutToken))
    }

    @Test fun shortcutResolvesLatestInstructionAndRejectsUnknownTokens() {
        val repo = CommonTaskRepository(file())
        val task = repo.save(null, "Task", "Old instruction")
        assertNull(repo.resolveShortcut(task.id, "forged"))
        assertNull(repo.resolveShortcut("unknown", task.shortcutToken))
        val updated = repo.save(task.id, "Renamed", "New instruction")
        assertEquals(updated, CommonTaskRepository(file()).resolveShortcut(task.id, task.shortcutToken))
        assertThrows(IllegalArgumentException::class.java) { repo.save("deleted", "No", "Do not recreate") }
        assertEquals(listOf(updated), repo.load())
    }

    @Test fun invalidOrOverLimitChangesLeaveSavedTasksUntouched() {
        val repo = CommonTaskRepository(file())
        val first = repo.save(null, "Task", "Prompt")
        val before = file().readBytes()
        listOf("" to "prompt", "name" to " ", "n".repeat(61) to "prompt", "name" to "p".repeat(8001)).forEach { (name, prompt) ->
            assertThrows(IllegalArgumentException::class.java) { repo.save(null, name, prompt) }
            assertArrayEquals(before, file().readBytes())
        }
        repeat(CommonTaskRepository.MAX_TASKS - 1) { repo.save(null, "Task $it", "Prompt") }
        assertThrows(IllegalArgumentException::class.java) { repo.save(null, "Overflow", "Prompt") }
        repo.save(first.id, "Can still edit", "Changed")
        assertEquals(CommonTaskRepository.MAX_TASKS, repo.load().size)
    }

    @Test fun corruptFileIsReportedAndNeverReplacedByAnEmptyLibrary() {
        file().writeText("{broken data")
        val repo = CommonTaskRepository(file())
        assertThrows(Exception::class.java) { repo.load() }
        assertThrows(Exception::class.java) { repo.save(null, "New", "Prompt") }
        assertEquals("{broken data", file().readText())
    }

    @Test fun abandonedAtomicWriteKeepsLastCommittedTasks() {
        val repo = CommonTaskRepository(file())
        val committed = repo.save(null, "Task", "Prompt")
        File(file().path + ".new").writeText("{interrupted")
        assertEquals(listOf(committed), CommonTaskRepository(file()).load())
    }
}
