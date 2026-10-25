package io.github.mangi.eta.agent.web

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WebHttpTransportTest {
    @Test fun redirectsResolveRelativeUrlsWithoutSharingCookies() {
        WebFixtureServer { path ->
            if (path == "/start") WebFixtureResponse(302, headers = mapOf("Location" to "/article", "Set-Cookie" to "secret=hidden"))
            else WebFixtureResponse(body = "正文".toByteArray())
        }.use { server ->
            val base = OkHttpClient.Builder().cookieJar(object : CookieJar {
                override fun loadForRequest(url: HttpUrl) = listOf(Cookie.Builder().name("private").value("hidden").domain(url.host).build())
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = error("不能修改共享 Cookie")
            }).build()
            WebHttpTransport(base).use { transport ->
                val response = transport.get(server.url("/start"))
                assertEquals(server.url("/article"), response.finalUrl)
                assertEquals("正文", response.bytes.toString(Charsets.UTF_8))
                assertEquals(2, server.requests.size)
                assertTrue(server.requests.none { it.contains("Cookie:", true) })
            }
        }
    }

    @Test fun boundedReadReturnsPrefixAndDistinguishesExactEof() {
        WebFixtureServer { path -> WebFixtureResponse(body = "a".repeat(if (path == "/exact") 16 else 17).toByteArray()) }.use { server ->
            WebHttpTransport(OkHttpClient()).use { transport ->
                val partial = transport.get(server.url("/large"), maxBytes = 16)
                assertEquals(16, partial.bytes.size)
                assertTrue(partial.truncated)
                assertFalse(transport.get(server.url("/exact"), maxBytes = 16).truncated)
            }
        }
    }

    @Test fun httpFailureDoesNotReturnRemoteErrorBody() {
        WebFixtureServer { WebFixtureResponse(403, body = "credential=private-server-echo".toByteArray()) }.use { server ->
            WebHttpTransport(OkHttpClient()).use { transport ->
                val response = transport.get(server.url("/private"))
                assertEquals(403, response.status)
                assertTrue(response.bytes.isEmpty())
            }
        }
    }

    @Test fun redirectLoopsAndCredentialUrlsFailExplicitly() {
        WebFixtureServer { WebFixtureResponse(302, headers = mapOf("Location" to "/loop")) }.use { server ->
            WebHttpTransport(OkHttpClient()).use { transport ->
                assertEquals("WEB_REDIRECT_LOOP", assertThrows(WebRequestException::class.java) { transport.get(server.url("/loop")) }.code)
            }
        }
        assertEquals("INVALID_URL", assertThrows(WebRequestException::class.java) { WebHttpTransport.validateUrl("file:///private/file") }.code)
        assertEquals("URL_CREDENTIALS_UNSUPPORTED", assertThrows(WebRequestException::class.java) { WebHttpTransport.validateUrl("https://user:secret@example.test/") }.code)
    }

    @Test fun closeCancelsBodyReadAndRejectsNewRequests() {
        val socketServer = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val bodyStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker = thread(isDaemon = true) {
            socketServer.accept().use { socket ->
                readRequest(socket)
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 100000\r\n\r\na".toByteArray())
                    flush()
                }
                bodyStarted.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
        }
        val pool = Executors.newSingleThreadExecutor()
        val transport = WebHttpTransport(OkHttpClient())
        try {
            val outcome = pool.submit<String> {
                try { transport.get("http://127.0.0.1:${socketServer.localPort}/slow"); "success" }
                catch (failure: WebRequestException) { failure.code }
            }
            assertTrue(bodyStarted.await(3, TimeUnit.SECONDS))
            transport.close()
            assertEquals("WEB_CANCELLED", outcome.get(3, TimeUnit.SECONDS))
            assertEquals("WEB_CANCELLED", assertThrows(WebRequestException::class.java) { transport.get("https://example.test") }.code)
        } finally {
            transport.close()
            release.countDown()
            socketServer.close()
            worker.join(1000)
            pool.shutdownNow()
        }
    }
}

internal data class WebFixtureResponse(
    val status: Int = 200,
    val headers: Map<String, String> = mapOf("Content-Type" to "text/plain; charset=utf-8"),
    val body: ByteArray = byteArrayOf(),
)

internal class WebFixtureServer(private val handler: (String) -> WebFixtureResponse) : Closeable {
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    val requests = CopyOnWriteArrayList<String>()
    private val worker = thread(isDaemon = true) {
        while (!server.isClosed) {
            val socket = try { server.accept() } catch (_: java.net.SocketException) { break }
            socket.use {
                val request = readRequest(socket)
                requests += request
                val response = handler(request.lineSequence().first().split(' ')[1])
                val headers = buildString {
                    append("HTTP/1.1 ${response.status} Fixture\r\n")
                    response.headers.forEach { (name, value) -> append("$name: $value\r\n") }
                    append("Content-Length: ${response.body.size}\r\nConnection: close\r\n\r\n")
                }
                try {
                    socket.getOutputStream().apply { write(headers.toByteArray()); write(response.body); flush() }
                } catch (_: java.io.IOException) { }
            }
        }
    }
    fun url(path: String = "/"): String = "http://127.0.0.1:${server.localPort}$path"
    override fun close() { server.close(); worker.join(1000) }
}

private fun readRequest(socket: Socket): String {
    socket.soTimeout = 3000
    val reader = socket.getInputStream().bufferedReader()
    return buildString {
        while (true) {
            val line = reader.readLine() ?: break
            append(line).append('\n')
            if (line.isEmpty()) break
        }
    }
}
