package io.github.mangi.eta.agent.runtime

import android.app.Application
import io.github.mangi.eta.agent.model.AgentModelClient
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AgentRuntimeServiceRetentionTest {
    @Test
    fun dismissReleasesContinuationHistoryBeforeTheServiceIsDestroyed() {
        val controller = Robolectric.buildService(AgentRuntimeService::class.java).create()
        try {
            val service = controller.get()
            val request = AgentRuntimeWire.RunRequest(
                runId = "completed-run",
                prompt = "请求",
                config = AgentModelClient.ModelConfig(
                    baseUrl = "https://example.invalid/v1",
                    apiKey = "test-key",
                    model = "test-model",
                    systemPrompt = "",
                ),
                images = emptyList(),
                history = listOf(AgentModelClient.ConversationMessage("user", "已完成的历史")),
            )
            val completedContextClass = AgentRuntimeService::class.java.declaredClasses.single {
                it.simpleName == "CompletedRunContext"
            }
            val completedContext = completedContextClass.declaredConstructors.single().apply {
                isAccessible = true
            }.newInstance(request, AgentModelClient.ModelResponse.Text("完成"))
            val retainedContext = AgentRuntimeService::class.java.getDeclaredField("lastCompletedRunContext").apply {
                isAccessible = true
            }
            retainedContext.set(service, completedContext)
            assertSame(completedContext, retainedContext.get(service))

            AgentRuntimeService::class.java.getDeclaredMethod("dismissAndStop").apply {
                isAccessible = true
            }.invoke(service)

            assertNull(retainedContext.get(service))
        } finally {
            controller.destroy()
        }
    }
}
