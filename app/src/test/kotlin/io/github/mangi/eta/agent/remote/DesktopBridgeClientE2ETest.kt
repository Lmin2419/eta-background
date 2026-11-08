package io.github.mangi.eta.agent.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 对真实 DSH + dsh-bridge 的端到端检查，默认跳过。本地运行：
 * `DSH_BRIDGE_E2E_URL=http://127.0.0.1:7788 DSH_BRIDGE_E2E_TOKEN=… DSH_BRIDGE_E2E_CWD=/path`，
 * 并让 DSH 有一个可用模型（`DSH_BRIDGE_E2E_PROVIDER` / `DSH_BRIDGE_E2E_MODEL`）。
 */
class DesktopBridgeClientE2ETest {
    private fun e2eConfig(): DesktopBridgeConfig? {
        System.getenv("DSH_BRIDGE_E2E_PAIRING")?.let { return DesktopBridgeConfig.fromPairingCode(it) }
        val url = System.getenv("DSH_BRIDGE_E2E_URL")
        val token = System.getenv("DSH_BRIDGE_E2E_TOKEN")
        return if (url.isNullOrBlank() || token.isNullOrBlank()) null else DesktopBridgeConfig(url, token)
    }

    @Test
    fun wrongCertificatePinIsRefused() = runBlocking {
        val config = e2eConfig()
        assumeTrue("needs a pinned pairing code", config?.pin != null)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val client = DesktopBridgeClient(OkHttpClient(), scope)
        try {
            client.start(config!!.copy(pin = "A".repeat(43)))
            val state = withTimeout(10_000) { client.state.first { it is DesktopBridgeClient.State.Unauthorized } }
            println("wrong pin -> $state")
            assertTrue((state as DesktopBridgeClient.State.Unauthorized).message.contains("指纹"))
        } finally {
            client.stop()
            scope.cancel()
        }
    }

    @Test
    fun streamsAPromptThroughTheRealBridge() = runBlocking {
        val config = e2eConfig()
        assumeTrue("set DSH_BRIDGE_E2E_URL/TOKEN or DSH_BRIDGE_E2E_PAIRING to run", config != null)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val client = DesktopBridgeClient(OkHttpClient(), scope)
        try {
            client.start(config!!)
            assertTrue("connects", client.awaitConnected(10_000))
            val connected = client.state.value as DesktopBridgeClient.State.Connected
            println("connected to ${connected.host} in ${connected.latencyMs} ms")

            val sessionId = client.call("sessions.create", mapOf("cwd" to JsonPrimitive(System.getenv("DSH_BRIDGE_E2E_CWD") ?: "/tmp")))
                .jsonObject.string("sessionId")!!
            System.getenv("DSH_BRIDGE_E2E_PROVIDER")?.let { provider ->
                client.call(
                    "sessions.selectModel",
                    mapOf(
                        "sessionId" to JsonPrimitive(sessionId),
                        "provider" to JsonPrimitive(provider),
                        "model" to JsonPrimitive(System.getenv("DSH_BRIDGE_E2E_MODEL")!!),
                    ),
                )
            }
            val state = MutableStateFlow(DesktopSessionState(sessionId))
            var sawDelta = false
            var firstDeltaMs = -1L
            val startedAt = System.currentTimeMillis()
            val collector = scope.launch {
                client.pushes.collect { push ->
                    if (push.string("sessionId") != sessionId) return@collect
                    if (push.string("t") == "live" && push.string("op") == "delta" && !sawDelta) {
                        sawDelta = true
                        firstDeltaMs = System.currentTimeMillis() - startedAt
                    }
                    state.update { it.reduce(push) }
                }
            }
            client.call("sessions.follow", mapOf("sessionId" to JsonPrimitive(sessionId)))
            withTimeout(5_000) { state.first { it.loaded } }
            val requestId = "e2e-kotlin-1"
            state.update { it.addEcho(DesktopEcho(requestId, "你好", queued = false)) }
            client.call(
                "sessions.prompt",
                mapOf("sessionId" to JsonPrimitive(sessionId), "text" to JsonPrimitive("你好"), "requestId" to JsonPrimitive(requestId)),
            )
            val done = withTimeout(30_000) {
                state.first { s -> s.items.any { it is DesktopItem.TurnEnd } && s.live == null }
            }
            collector.cancel()
            println("first delta after ${firstDeltaMs} ms, items=${done.items.map { it::class.simpleName }}")
            assertTrue("streamed deltas", sawDelta)
            assertTrue("echo settled", done.echoes.isEmpty())
            assertEquals("你好", done.items.filterIsInstance<DesktopItem.User>().single().text)
            assertTrue(done.items.any { it is DesktopItem.Assistant })
        } finally {
            client.stop()
            scope.cancel()
        }
    }
}
