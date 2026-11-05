package io.github.mangi.eta.agent.model

import com.sun.net.httpserver.HttpServer
import io.github.mangi.eta.agent.runtime.AgentRunCancelledException
import io.github.mangi.eta.agent.runtime.AgentRunController
import io.github.mangi.eta.agent.runtime.AgentTokenUsage
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderStreamProgressTest {
    @Test
    fun commentsAndMetadataDoNotKeepAStalledHttpStreamAlive() {
        withSseServer({ output ->
            repeat(150) {
                output.frame(": keepalive\n\nevent: usage\ndata: 12\n\n")
                Thread.sleep(20)
            }
        }) { request ->
            val failure = assertThrows(AgentModelFailure::class.java) {
                read(request, AgentRunController(), timeoutMillis = 400)
            }
            assertEquals("MODEL_TIMEOUT", failure.code)
            assertTrue(failure.retryable)
        }
    }

    @Test
    fun thinkingAndToolDeltasCanKeepAStreamGoingBeyondItsInitialBudget() {
        withSseServer({ output ->
            repeat(9) { index ->
                val event = if (index % 2 == 0) "thinking" else "tool"
                output.frame("event: $event\ndata: chunk\n\n")
                Thread.sleep(160)
            }
            output.frame("event: done\ndata: answer\n\n")
        }) { request ->
            assertEquals("answer", read(request, AgentRunController(), timeoutMillis = 1_200))
        }
    }

    @Test
    fun intentionalPauseDoesNotExpireTheStreamAndUserStopStillInterruptsIt() {
        val controller = AgentRunController()
        val usageEvents = AtomicInteger()
        val received = CountDownLatch(1)
        val resumeStream = CountDownLatch(1)
        val afterResume = CountDownLatch(1)
        val finishStream = CountDownLatch(1)
        val worker = Executors.newSingleThreadExecutor()
        try {
            withSseServer({ output ->
                output.frame("event: usage\ndata: 12\n\n")
                if (resumeStream.await(4, TimeUnit.SECONDS)) {
                    output.frame("event: usage\ndata: 13\n\n")
                    finishStream.await(4, TimeUnit.SECONDS)
                }
            }) { request ->
                val result = worker.submit<Throwable?> {
                    runCatching {
                        read(request, controller, timeoutMillis = 1_000) {
                            if (usageEvents.incrementAndGet() == 1) received.countDown()
                            else afterResume.countDown()
                        }
                    }.exceptionOrNull()
                }
                assertTrue(received.await(3, TimeUnit.SECONDS))
                controller.pause()
                Thread.sleep(1_250)
                assertFalse(result.isDone)
                controller.resume()
                resumeStream.countDown()
                assertTrue(afterResume.await(3, TimeUnit.SECONDS))
                controller.cancel()
                assertTrue(result.get(3, TimeUnit.SECONDS) is AgentRunCancelledException)
            }
        } finally {
            resumeStream.countDown()
            finishStream.countDown()
            controller.cancel()
            worker.shutdownNow()
        }
    }

    private fun read(
        request: Request,
        controller: AgentRunController,
        timeoutMillis: Long,
        onUsage: () -> Unit = {},
    ): String = AuthorizedProviderHttp.executeStreaming(
        OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build(), request, controller, {},
        progressTimeoutMillis = timeoutMillis,
    ) { response, report ->
        var result = ""
        readProviderSse(response.body.byteStream(), controller) { event, data ->
            when (event) {
                "usage" -> {
                    report(ProviderEvent.Usage(AgentTokenUsage(inputTokens = data.toInt())))
                    onUsage()
                }
                "thinking" -> report(ProviderEvent.BlockDelta(AssistantBlockKind.THINKING, 0, data))
                "tool" -> report(ProviderEvent.BlockDelta(AssistantBlockKind.TOOL_CALL, 1, data))
                "done" -> result = data
            }
            event != "done"
        }
        result
    }

    private fun OutputStream.frame(value: String) {
        write(value.toByteArray(Charsets.UTF_8))
        flush()
    }

    private fun <T> withSseServer(write: (OutputStream) -> Unit, run: (Request) -> T): T {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            try {
                exchange.responseHeaders.add("Content-Type", "text/event-stream")
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.use(write)
            } catch (_: IOException) {
                // Cancelling the client also closes the fixture's response.
            } finally {
                exchange.close()
            }
        }
        server.start()
        try {
            return run(Request.Builder().url("http://127.0.0.1:${server.address.port}/").build())
        } finally {
            server.stop(0)
        }
    }
}
