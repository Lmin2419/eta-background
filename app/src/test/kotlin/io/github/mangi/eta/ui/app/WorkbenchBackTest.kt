package io.github.mangi.eta.ui.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkbenchBackTest {
    private val exists = { id: String -> id == "a" }

    @Test
    fun backInConversationGoesToWorkbench() {
        assertEquals(WorkbenchBack.Action.ToWorkbench, WorkbenchBack.decide("a", null, exists))
        assertEquals(WorkbenchBack.Action.ToWorkbench, WorkbenchBack.decide("b", "a", exists))
    }

    @Test
    fun backOnWorkbenchReturnsToOrigin() {
        assertEquals(WorkbenchBack.Action.ToConversation("a"), WorkbenchBack.decide(null, "a", exists))
    }

    @Test
    fun backOnWorkbenchWithoutOriginOrDeletedOriginExits() {
        assertEquals(WorkbenchBack.Action.Exit, WorkbenchBack.decide(null, null, exists))
        assertEquals(WorkbenchBack.Action.Exit, WorkbenchBack.decide(null, "gone", exists))
    }

    @Test
    fun plusKeepsOriginalReturnTargetWhenAlreadyOnWorkbench() {
        assertEquals("a", WorkbenchBack.returnTargetOnNew("a", null))
        assertEquals("a", WorkbenchBack.returnTargetOnNew(null, "a"))
        assertEquals("b", WorkbenchBack.returnTargetOnNew("b", "a"))
    }
}
