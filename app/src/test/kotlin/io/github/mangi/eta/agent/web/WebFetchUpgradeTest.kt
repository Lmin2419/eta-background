package io.github.mangi.eta.agent.web

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebFetchUpgradeTest {
    @Test fun staticReadNeverInvokesAnUpgrade() {
        var upgrades = 0
        val tools = tools("<main>Useful content</main>", upgrade = { upgrades++; rendered() })
        tools.use {
            val result = read(it)
            assertTrue(result.toString(), result.getBoolean("ok"))
            assertEquals("http", result.getString("read_route"))
            assertEquals(0, upgrades)
        }
    }

    @Test fun shellUpgradesOnceAndRetainsTheRenderedSnapshotForPagination() {
        var upgrades = 0
        tools(shell, upgrade = { request ->
            upgrades++
            assertEquals(WebFetchRoute.BROWSER, request.preferredRoute)
            assertEquals("WEB_JAVASCRIPT_REQUIRED", request.reason)
            assertTrue(request.timeoutMillis in 1..35_000)
            rendered("<main>${"page content ".repeat(250)}</main>")
        }).use {
            val first = read(it).also { result -> assertTrue(result.toString(), result.getBoolean("ok")) }
            assertEquals("browser", first.getString("read_route"))
            assertEquals("partial", first.getString("result_state"))
            val second = JSONObject(it.execute("fetch_url", JSONObject().put("document_id", first.getString("document_id"))
                .put("offset_chars", first.getInt("next_offset_chars")).put("max_chars", 1_000)))
            assertTrue(second.getBoolean("ok"))
            assertFalse(second.has("timing_ms"))
            assertEquals(1, upgrades)
        }
    }

    @Test fun challengeSignalUsesCloudflareRouteAndCannotReturnVerificationAsSuccess() {
        var upgrades = 0
        tools("private error body", status = 403, challenge = true, upgrade = { request ->
            upgrades++
            assertEquals(WebFetchRoute.CLOUDFLARE, request.preferredRoute)
            rendered("<title>Just a moment...</title><div id='challenge-stage'>Checking your browser</div>")
        }).use {
            val result = read(it)
            assertFalse(result.getBoolean("ok"))
            assertEquals("WEB_CHALLENGE", result.getString("code"))
            assertFalse(result.has("document_id"))
            assertFalse(result.toString().contains("private error body"))
            assertEquals(1, upgrades)
        }
    }

    @Test fun noUpgradeForNotFoundRateLimitOrLogin() {
        var upgrades = 0
        for ((status, body, code) in listOf(
            Triple(404, "missing", "WEB_NOT_FOUND"),
            Triple(429, "limit", "WEB_RATE_LIMITED"),
            Triple(200, "<title>Login</title><form><input type='password'></form>", "WEB_LOGIN_REQUIRED"),
        )) tools(body, status = status, upgrade = { upgrades++; rendered() }).use {
            assertEquals(code, read(it).getString("code"))
        }
        assertEquals(0, upgrades)
    }

    @Test fun unavailableEnhancementIsNotRetriedOrReportedAsAReadablePage() {
        var upgrades = 0
        tools(shell, upgrade = {
            upgrades++
            throw WebRequestException("WEB_ENHANCEMENT_UNAVAILABLE", "浏览器不可用")
        }).use {
            assertEquals("WEB_ENHANCEMENT_UNAVAILABLE", read(it).getString("code"))
            assertEquals(1, upgrades)
        }
    }

    @Test fun generic403IsNotAssumedToBeCloudflareOrLogin() {
        tools("denied", status = 403, upgrade = { request ->
            assertEquals(WebFetchRoute.HTTP_FINGERPRINT, request.preferredRoute)
            assertEquals("WEB_ACCESS_DENIED", request.reason)
            throw WebRequestException("WEB_ACCESS_DENIED", "仍被拒绝", 403)
        }).use { assertEquals("WEB_ACCESS_DENIED", read(it).getString("code")) }
    }

    @Test fun renderedTextKeepsTitleWithoutInventingAnHttpStatus() {
        tools(shell, upgrade = {
            WebFetchUpgradeResult("Browser readable body", "https://site.test/rendered", "text/plain", null,
                "builtin_browser", title = "Dynamic title")
        }).use {
            val result = read(it)
            assertTrue(result.toString(), result.getBoolean("ok"))
            assertFalse(result.has("http_status"))
            assertEquals("Dynamic title", result.getString("title"))
        }
        assertEquals("WEB_CHALLENGE", WebReadQuality.inspect(null, "text/html",
            "<title>Just a moment...</title><div id='challenge-stage'>Checking</div>")?.code)
    }

    @Test fun oneDynamicPageDoesNotPromoteOtherPathsOnTheSameSiteToBrowser() {
        var upgrades = 0
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .header("Content-Type", "text/html")
                .body((if (chain.request().url.encodedPath == "/page") shell else "<main>Static page</main>").toResponseBody()).build()
        }).build()
        AgentWebTools(WebHttpTransport(client), enhancedFetch = { upgrades++; rendered() }).use {
            assertTrue(read(it).getBoolean("ok"))
            val static = read(it, "https://site.test/static")
            assertEquals("http", static.getString("read_route"))
            assertEquals(1, upgrades)
        }
    }

    @Test fun provenRouteExpiresAndIsNeverSharedAcrossRuns() {
        var nanos = 0L
        var native = 0
        var upgrades = 0
        val client = client(shell) { native++ }
        AgentWebTools(WebHttpTransport(client), enhancedFetch = { upgrades++; rendered() }, monotonicNanos = { nanos }).use {
            assertTrue(read(it).getBoolean("ok"))
            assertTrue(read(it).getBoolean("ok"))
            assertEquals(1, native)
            nanos += 301_000_000_000L
            assertTrue(read(it).getBoolean("ok"))
            assertEquals(2, native)
            assertEquals(3, upgrades)
        }
        AgentWebTools(WebHttpTransport(client), enhancedFetch = { rendered() }).use {
            assertTrue(read(it).getBoolean("ok"))
            assertEquals(3, native)
        }
    }

    @Test fun lateOrCancelledUpgradeCannotPublishASnapshot() {
        var nanos = 0L
        lateinit var tools: AgentWebTools
        tools = AgentWebTools(WebHttpTransport(client(shell)), enhancedFetch = {
            nanos = 46_000_000_000L
            rendered()
        }, monotonicNanos = { nanos })
        tools.use { assertEquals("WEB_TIMEOUT", read(it).getString("code")) }
        tools = AgentWebTools(WebHttpTransport(client(shell)), enhancedFetch = {
            tools.close()
            rendered()
        })
        tools.use { assertEquals("WEB_CANCELLED", read(it).getString("code")) }
    }

    private fun read(tools: AgentWebTools, url: String = "https://site.test/page") =
        JSONObject(tools.execute("fetch_url", JSONObject().put("url", url).put("max_chars", 1_000)))
    private fun rendered(body: String = "<main>Rendered content</main>") =
        WebFetchUpgradeResult(body, "https://site.test/rendered", "text/html", 200, "test_browser")
    private fun tools(body: String, status: Int = 200, challenge: Boolean = false,
        upgrade: ((WebFetchUpgradeRequest) -> WebFetchUpgradeResult)? = null) =
        AgentWebTools(WebHttpTransport(client(body, status, challenge)), enhancedFetch = upgrade)
    private fun client(body: String, status: Int = 200, challenge: Boolean = false, requested: () -> Unit = {}) =
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            requested()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Fixture")
                .header("Content-Type", "text/html; charset=utf-8")
                .apply { if (challenge) header("cf-mitigated", "challenge") }
                .body(body.toResponseBody()).build()
        }).build()
    private val shell = "<html><div id='root'></div><script src='/bundle.js'></script></html>"
}
