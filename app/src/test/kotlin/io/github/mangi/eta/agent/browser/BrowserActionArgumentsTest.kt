package io.github.mangi.eta.agent.browser

import android.app.Application
import java.lang.reflect.InvocationTargetException
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BrowserActionArgumentsTest {
    @Test
    fun invalidCoordinatesFailBeforeClickCanAccessThePage() {
        listOf("bad", JSONObject().put("value", 10), 0.5, Int.MAX_VALUE.toLong() + 1).forEach { value ->
            listOf("coordinate_x", "coordinate_y").forEach { key ->
                val args = JSONObject().put("coordinate_x", 10).put("coordinate_y", 20).put(key, value)
                val failure = actionFailure("click", args)
                assertTrue(failure.toString(), failure is JSONException)
                assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains(key))
                assertInvalidArgumentResult("click", failure)
            }
        }
    }

    @Test
    fun missingOrNonTextInputFailsBeforeTypeCanAccessThePage() {
        listOf(null, JSONObject.NULL, JSONObject().put("value", "replacement")).forEach { text ->
            val args = JSONObject().put("selector", "input")
            if (text != null) args.put("text", text)
            val failure = actionFailure("type", args)
            assertTrue(failure.toString(), failure is JSONException)
            assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains("text"))
            assertInvalidArgumentResult("type", failure)
        }
    }

    private fun actionFailure(action: String, args: JSONObject): Throwable {
        val method = AgentBrowserSession::class.java.getDeclaredMethod(action, JSONObject::class.java)
            .apply { isAccessible = true }
        try {
            method.invoke(AgentBrowserSession, args)
            throw AssertionError("accepted invalid $action arguments")
        } catch (failure: InvocationTargetException) {
            return checkNotNull(failure.cause)
        }
    }

    private fun assertInvalidArgumentResult(action: String, failure: Throwable) {
        val method = AgentBrowserSession::class.java.getDeclaredMethod("failureResult", String::class.java, Throwable::class.java)
            .apply { isAccessible = true }
        val result = method.invoke(AgentBrowserSession, action, failure) as BrowserToolResult
        val json = JSONObject(result.content)
        assertEquals(json.toString(), "INVALID_ARGUMENT", json.getString("code"))
    }
}
