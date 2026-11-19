package io.github.mangi.eta.agent.scheduler

import io.github.mangi.eta.data.model.ScheduledTaskCapabilities
import io.github.mangi.eta.data.model.ScheduledTaskDeliveryMode
import io.github.mangi.eta.data.model.ScheduledTaskSessionMode
import io.github.mangi.eta.data.model.ScheduledTaskTriggerSource
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 胖小胖：定时任务的界面操作走后台虚拟屏（上游没有的组合）。 */
@RunWith(RobolectricTestRunner::class)
class ScheduledTaskBackgroundScreenTest {
    private fun context(caps: ScheduledTaskCapabilities) = ScheduledTaskRunContext(
        taskId = "t1",
        taskName = "每天签到",
        runId = "sched-1",
        triggerSource = ScheduledTaskTriggerSource.SCHEDULE,
        deliveryMode = ScheduledTaskDeliveryMode.ALWAYS,
        sessionMode = ScheduledTaskSessionMode.ISOLATED,
        capabilities = caps,
        memo = "",
        scheduledForAt = null,
        startedAt = 1_700_000_000_000L,
    )

    @Test
    fun backgroundScreenRoundTripsThroughAdapterPayloadAndDefaultsOn() {
        val payload = context(ScheduledTaskCapabilities(guiTools = true, backgroundScreen = false)).toAdapterPayload()
        assertFalse(ScheduledTaskRunContext.fromAdapterPayload(payload)!!.capabilities.backgroundScreen)

        // 旧版本写下的运行上下文没有这个键：默认按后台虚拟屏执行。
        val legacy = JSONObject(payload.toString()).apply { remove("cap_background_screen") }
        assertTrue(ScheduledTaskRunContext.fromAdapterPayload(legacy)!!.capabilities.backgroundScreen)
    }

    @Test
    fun guidanceTellsModelToUseBackgroundScreenOnlyWhenGuiAndBackgroundAreOn() {
        val background = context(ScheduledTaskCapabilities(guiTools = true, backgroundScreen = true)).buildSystemGuidance()
        assertTrue(background.contains("execution_location=\"background\""))

        val foreground = context(ScheduledTaskCapabilities(guiTools = true, backgroundScreen = false)).buildSystemGuidance()
        assertFalse(foreground.contains("execution_location"))

        val noGui = context(ScheduledTaskCapabilities(guiTools = false)).buildSystemGuidance()
        assertFalse(noGui.contains("execution_location"))
        assertTrue(noGui.contains("未启用应用界面"))
    }

    @Test
    fun lockedDeviceRunsGuiTaskOnlyWhenBackgroundScreenAndRootAreAvailable() {
        assertTrue(ScheduledTaskRunner.canUseBackgroundScreen(taskWantsBackground = true, rootGranted = true))
        assertFalse(ScheduledTaskRunner.canUseBackgroundScreen(taskWantsBackground = true, rootGranted = false))
        assertFalse(ScheduledTaskRunner.canUseBackgroundScreen(taskWantsBackground = false, rootGranted = true))
    }

    @Test
    fun capabilitiesStoredBeforeTheOptionExistedDecodeWithBackgroundScreenOn() {
        val json = Json { ignoreUnknownKeys = true }
        val old = """{"browserTools":true,"deviceReadTools":true,"deviceActionTools":false,"terminalTools":true,"mcpTools":true,"guiTools":true}"""
        val decoded = json.decodeFromString<ScheduledTaskCapabilities>(old)
        assertTrue(decoded.guiTools)
        assertTrue(decoded.backgroundScreen)
        assertEquals(decoded, json.decodeFromString<ScheduledTaskCapabilities>(json.encodeToString(ScheduledTaskCapabilities.serializer(), decoded)))
    }
}
