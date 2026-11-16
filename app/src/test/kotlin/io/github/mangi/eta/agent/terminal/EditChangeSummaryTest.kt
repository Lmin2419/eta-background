package io.github.mangi.eta.agent.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** edit_file 成功后附带的改动摘要：落在第几行、旧的几行、新的几行。 */
class EditChangeSummaryTest {
    private val original = "line 1\nline 2\nval x = 1\nline 4\n"

    @Test fun singleLineChangeNamesItsLine() {
        assertEquals("第 3 行\n- val x = 1\n+ val x = 2", editChangeSummary(original, "val x = 1", "val x = 2", 1))
    }

    @Test fun multiLineChangeShowsTheNewRangeAndCount() {
        val summary = editChangeSummary(original + "val x = 1\n", "val x = 1", "val x = 2\nval y = 3", 2)
        assertTrue(summary, summary.startsWith("第 3–4 行（共 2 处，只列第一处）\n- val x = 1\n+ val x = 2\n+ val y = 3"))
    }

    @Test fun longChangesAreTrimmed() {
        val newText = (1..9).joinToString("\n") { "n$it" + "x".repeat(200) }
        val summary = editChangeSummary(original, "line 2", newText, 1)
        val added = summary.lines().filter { it.startsWith("+ ") }
        assertEquals(7, added.size)
        assertTrue(added.last(), added.last().contains("还有 3 行"))
        assertTrue(added.first().length <= 2 + 160 + 1)
    }

    @Test fun lineRangeCoversTheNewText() {
        assertEquals(3..4, editChangeLines(original, "val x = 1", "val x = 2\nval y = 3"))
        assertEquals(3..3, editChangeLines(original, "val x = 1", "val x = 2"))
        assertEquals(null, editChangeLines(original, "absent", "x"))
    }

    @Test fun missingTextGivesNoSummary() {
        assertEquals("", editChangeSummary(original, "absent", "x", 1))
    }
}
