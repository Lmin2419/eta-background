package io.github.mangi.eta.agent.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentUnifiedOperationCatalogTest {
    @Test
    fun nativeGuiToolsExposeOptionalPerTaskLocationWithoutRemovingLegacyScreenTools() {
        val tools = AgentToolCatalog.build(terminalTools = false, browserTools = false)
        val names = (0 until tools.length()).map {
            tools.getJSONObject(it).getJSONObject("function").getString("name")
        }
        assertTrue("tap" in names)
        assertTrue("observe_screen" in names)
        assertTrue("screen_open" in names)
        assertTrue("screen_dump" in names)

        val tap = tools.function("tap")
        val tapProperties = tap.getJSONObject("parameters").getJSONObject("properties")
        assertEquals(
            listOf("foreground", "background"),
            tapProperties.getJSONObject("execution_location").getJSONArray("enum").stringValues(),
        )
        assertTrue(tapProperties.has("observation_id"))
        assertEquals("string", tapProperties.getJSONObject("observation_id").getString("type"))
        assertEquals("string", tapProperties.getJSONObject("frame_id").getString("type"))
        assertTrue(tap.getJSONObject("parameters").getJSONArray("required").stringValues().contains("coordinate_space"))
    }

    @Test
    fun elementToolsKeepObservationIdRequired() {
        val tools = AgentToolCatalog.build(terminalTools = false, browserTools = false)
        listOf("tap_element", "long_press_element", "scroll_element").forEach { name ->
            val function = tools.function(name)
            val parameters = function.getJSONObject("parameters")
            assertTrue(name, parameters.getJSONArray("required").stringValues().contains("observation_id"))
            assertTrue(name, function.getJSONObject("parameters")
                .getJSONObject("properties")
                .has("execution_location"))
        }
    }

    @Test
    fun legacyScreenToolsAreNotDecoratedWithTheNativeLocationParameter() {
        val tools = AgentToolCatalog.build(terminalTools = false, browserTools = false)
        val screen = tools.function("screen_open")
        assertFalse(screen.getJSONObject("parameters").getJSONObject("properties").has("execution_location"))
    }

    private fun org.json.JSONArray.function(name: String): JSONObject =
        (0 until length())
            .asSequence()
            .map { getJSONObject(it) }
            .first { it.getJSONObject("function").getString("name") == name }
            .getJSONObject("function")

    private fun org.json.JSONArray.stringValues(): List<String> =
        (0 until length()).map { getString(it) }
}
