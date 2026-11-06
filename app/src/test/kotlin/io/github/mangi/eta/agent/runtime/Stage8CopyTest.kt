package io.github.mangi.eta.agent.runtime

import android.app.Application
import android.app.Notification
import android.content.Context
import android.content.res.Configuration
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class Stage8CopyTest {
    private fun context(language: String): Context {
        val app = RuntimeEnvironment.getApplication()
        return app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })
    }

    @Test fun s10_7_settingsUseTheInstalledAppNameWithoutAnOldEditionOrChineseSpaces() {
        for (language in listOf("zh-CN", "zh-TW", "en")) {
            val c = context(language)
            for (id in listOf(R.string.overlay_settings_choose_app, R.string.overlay_settings_unavailable, R.string.execution_title)) {
                val value = c.getString(id)
                assertFalse(value, Regex("胖小胖\\s+(后台|後台|Background)").containsMatchIn(value))
                if (language.startsWith("zh")) assertFalse(value, Regex("[\\p{IsHan}] +胖小胖|胖小胖 +[\\p{IsHan}]").containsMatchIn(value))
            }
        }
    }

    @Test fun s10_8_allFiveResourcesMatchTheThreeLanguageTable() {
        val ids = listOf(R.string.overlay_stop_task, R.string.overlay_open_conversation, R.string.execution_stop,
            R.string.preview_wait, R.string.background_preview_paused)
        val expected = listOf(
            "zh-CN" to listOf("停止任务", "打开会话", "全部停止", "正在交接…", "你在操作"),
            "zh-TW" to listOf("停止任務", "開啟會話", "全部停止", "正在交接…", "你在操作"),
            "en" to listOf("Stop task", "Open chat", "Stop all", "Handing over…", "You're in control"))
        expected.forEach { (language, values) -> assertEquals(language, values, ids.map(context(language)::getString)) }
    }

    @Test fun s10_10_everyStatusAndCompletionUsesAWholeDedicatedShortLabel() {
        val groups = listOf(
            "Queued" to listOf(AgentOverlayStatus.Queued, AgentOverlayStatus.WaitingForGui),
            "Running" to (listOf(AgentOverlayStatus.Preparing, AgentOverlayStatus.SupplementReceived, AgentOverlayStatus.Continuing,
                AgentOverlayStatus.RunningTool("click"), AgentOverlayStatus.HostedToolRunning("search")) + BackgroundScreenPhase.entries.map { AgentOverlayStatus.BackgroundScreen(it) }),
            "Thinking" to listOf(AgentOverlayStatus.Reasoning, AgentOverlayStatus.WaitingForModel),
            "Answering" to listOf(AgentOverlayStatus.GeneratingAnswer),
            "Paused" to listOf(AgentOverlayStatus.Paused),
            "In control" to listOf(AgentOverlayStatus.PreviewPaused, AgentOverlayStatus.AppTakeoverPaused),
            "Needs you" to listOf(AgentOverlayStatus.ContinuationUnavailable),
            "Finishing" to listOf(AgentOverlayStatus.ResultReady, AgentOverlayStatus.Stopping, AgentOverlayStatus.Finishing),
            "Stopped" to listOf(AgentOverlayStatus.Stopped), "Failed" to listOf(AgentOverlayStatus.RunFailed))
        assertEquals("every sealed status must have a test case", AgentOverlayStatus::class.java.declaredClasses
            .filter { AgentOverlayStatus::class.java.isAssignableFrom(it) }.toSet(), groups.flatMap { it.second }.map { it.javaClass }.toSet())
        val translations = mapOf("zh-CN" to listOf("排队中", "执行中", "思考中", "回答中", "已暂停", "你在操作", "需要你", "收尾中", "已停止", "未完成"),
            "zh-TW" to listOf("排隊中", "執行中", "思考中", "回答中", "已暫停", "你在操作", "需要你", "收尾中", "已停止", "未完成"))
        val builder = AgentExecutionService.Companion::class.java.getDeclaredMethod("notification", Context::class.java,
            TaskNotificationState::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        fun short(c: Context, status: AgentOverlayStatus, completed: Boolean = false): String? {
            val task = TaskNotificationState("copy", "chat", "App", status, completed = completed)
            val notification = builder.invoke(AgentExecutionService.Companion, c, task, 123, false) as Notification
            return notification.extras.getString("android.shortCriticalText")
        }
        for (language in listOf("zh-CN", "zh-TW", "en")) {
            val c = context(language)
            groups.forEachIndexed { index, (en, statuses) -> statuses.forEach { status ->
                assertEquals("$language $status", translations[language]?.get(index) ?: en, short(c, status))
            } }
            listOf(AgentOverlayStatus.ResultReady, AgentOverlayStatus.Stopped, AgentOverlayStatus.RunFailed).forEachIndexed { index, status ->
                assertEquals("$language completed $status", (if (language == "en") listOf("Done", "Stopped", "Failed") else listOf("已完成", "已停止", "未完成"))[index], short(c, status, true))
            }
        }
    }
}
