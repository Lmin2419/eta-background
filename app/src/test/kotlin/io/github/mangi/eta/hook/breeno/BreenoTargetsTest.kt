package io.github.mangi.eta.hook.breeno

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.LinkedList

class BreenoTargetsTest {
    @Test
    fun resolvesRenamedHistoryMethodByItsBoxedBooleanContract() {
        val method = uniqueBreenoInstanceMethod(
            RenamedHistory::class.java,
            LinkedList::class.java.name,
            String::class.java.name,
            Boolean::class.javaObjectType.name,
        )

        assertEquals("newEntry", method?.name)
        assertEquals(listOf("room"), method?.invoke(RenamedHistory(), "room", false))
    }

    @Test
    fun rejectsAmbiguousMethodsInsteadOfPickingDeclarationOrder() {
        assertNull(uniqueBreenoInstanceMethod(AmbiguousResult::class.java, Any::class.java.name))
    }

    @Test
    fun rejectsStaticFactoryWhenLookingForAResultPayloadGetter() {
        assertNull(uniqueBreenoInstanceMethod(StaticResult::class.java, Any::class.java.name))
    }

    @Test
    fun ignoresSyntheticGenericBridge() {
        assertNull(uniqueBreenoInstanceMethod(StringResult::class.java, Any::class.java.name))
        assertEquals(
            "payload",
            uniqueBreenoInstanceMethod(StringResult::class.java, String::class.java.name)
                ?.invoke(StringResult()),
        )
    }

    private class RenamedHistory {
        fun newEntry(roomId: String, animate: Boolean?): LinkedList<String> =
            LinkedList<String>().apply { if (animate == false) add(roomId) }

        fun oldEntry(roomId: String, animate: Boolean): LinkedList<String> =
            LinkedList<String>().apply { if (animate) add(roomId) }
    }

    private class AmbiguousResult {
        fun first(): Any = "first"
        fun second(): Any = "second"
    }

    private class StaticResult {
        companion object {
            @JvmStatic
            fun create(): Any = "static"
        }
    }

    private interface GenericResult<T> {
        fun read(): T
    }

    private class StringResult : GenericResult<String> {
        override fun read(): String = "payload"
    }
}
