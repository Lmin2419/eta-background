package io.github.mangi.eta.agent.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentUnifiedOperationCatalogTest {
    @Test
    fun oneGuiCatalogSupportsBothLocationsWithoutPublishingEngineProtocol() {
        val tools = AgentToolCatalog.build(terminalTools = false, browserTools = false)
        val names = (0 until tools.length()).map {
            tools.getJSONObject(it).getJSONObject("function").getString("name")
        }
        assertTrue("tap" in names)
        assertTrue("observe_screen" in names)
        assertFalse(names.any { it.startsWith("screen_") })
        assertEquals(names.size, names.distinct().size)

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
    fun unifiedLaunchExposesOrientationAndBackWithoutSecondToolSet() {
        val tools = AgentToolCatalog.build(terminalTools = false, browserTools = false)
        val launch = tools.function("launch_app").getJSONObject("parameters").getJSONObject("properties")
        assertEquals(listOf("auto", "portrait", "landscape"), launch.getJSONObject("orientation").getJSONArray("enum").stringValues())
        val key = tools.function("press_key")
        assertTrue("BACK" in key.getJSONObject("parameters").getJSONObject("properties")
            .getJSONObject("button").getJSONArray("enum").stringValues())
        assertTrue(key.getJSONObject("parameters").getJSONObject("properties").has("observation_id"))
        assertTrue(key.getString("description").contains("不作用于主屏"))
    }

    @Test
    fun nativeObservationExplainsImageDeliveryWithoutChangingDefaultObservationOptions() {
        val tools = AgentToolCatalog.build(terminalTools = false, browserTools = false)
        val observation = tools.function("observe_screen")
        val description = observation.getString("description")
        assertTrue(description.contains("screenshot.attached 和图片附件"))
        assertTrue(description.contains("已收到图片无需再用 read_image 读取同帧"))
        assertTrue(description.contains("仅路径不表示已看图"))

        val properties = observation.getJSONObject("parameters").getJSONObject("properties")
        assertFalse(properties.getJSONObject("include_screenshot").getBoolean("default"))
        assertTrue(properties.getJSONObject("include_ui_tree").getBoolean("default"))
        assertEquals("string", properties.getJSONObject("execution_location").getString("type"))
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
