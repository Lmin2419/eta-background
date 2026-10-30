package io.github.mangi.eta.agent.web

import io.github.mangi.eta.data.model.WebToolBackend
import java.net.UnknownHostException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BuiltInWebSearchTest {
    @Test fun bingReturnsOriginalSourcesWithoutContactingFallback() {
        val calls = mutableListOf<String>()
        val query = "安卓 + 测试 & extra=x"
        val client = client { chain ->
            calls += chain.request().url.host
            assertEquals(query, chain.request().url.queryParameter("q"))
            assertEquals("rss", chain.request().url.queryParameter("format"))
            assertTrue(chain.call().timeout().timeoutNanos() <= 10_000_000_000L)
            response(chain, feed(item("指南", "https://example.test/doc", "说明")), mime = "application/rss+xml")
        }
        AgentWebTools(WebHttpTransport(client)).use {
            val result = search(it, query)
            assertTrue(result.toString(), result.getBoolean("ok"))
            assertEquals("bing_rss", result.getString("provider"))
            assertEquals("https://example.test/doc", result.getJSONArray("results").getJSONObject(0).getString("url"))
            assertEquals(listOf("www.bing.com"), calls)
            assertFalse(result.has("fallback_from"))
        }
    }

    @Test fun dnsFailureFallsBackOnceAndReportsSourceWithoutLeakingExceptionText() {
        val calls = mutableListOf<String>()
        val client = client { chain ->
            calls += chain.request().url.host
            if (calls.size == 1) throw UnknownHostException("private-query-and-key")
            response(chain, duckResult)
        }
        AgentWebTools(WebHttpTransport(client)).use {
            val result = search(it)
            assertTrue(result.toString(), result.getBoolean("ok"))
            assertEquals("duckduckgo_html", result.getString("provider"))
            assertEquals("bing_rss", result.getString("fallback_from"))
            assertEquals("WEB_DNS_ERROR", result.getJSONArray("attempts").getJSONObject(0).getString("code"))
            assertEquals(2, calls.size)
            assertFalse(result.toString().contains("private-query-and-key"))
        }
    }

    @Test fun malformedOrChallengedSearchNeverBecomesAnEmptySuccess() {
        var calls = 0
        val client = client { chain ->
            calls++
            if (calls == 1) response(chain, "<html>Verify you are human</html>")
            else response(chain, "private-server-body", 429)
        }
        AgentWebTools(WebHttpTransport(client)).use {
            val result = search(it)
            assertFalse(result.getBoolean("ok"))
            assertEquals("SEARCH_UNAVAILABLE", result.getString("code"))
            val attempts = result.getJSONArray("attempts")
            assertEquals(2, attempts.length())
            assertEquals("SEARCH_PARSE_FAILED", attempts.getJSONObject(0).getString("code"))
            assertEquals(429, attempts.getJSONObject(1).getInt("http_status"))
            assertFalse(result.has("results"))
            assertFalse(result.toString().contains("private-server-body"))
        }
    }

    @Test fun cancellationAndInvalidParametersDoNotContactFallback() {
        lateinit var transport: WebHttpTransport
        var calls = 0
        transport = WebHttpTransport(client { _ ->
            calls++
            transport.close()
            throw UnknownHostException("cancelled")
        })
        AgentWebTools(transport).use {
            assertEquals("INVALID_ARGUMENT", search(it, " ").getString("code"))
            assertEquals(0, calls)
            assertEquals("WEB_CANCELLED", search(it).getString("code"))
            assertEquals(1, calls)
        }
    }

    @Test fun configuredProviderDoesNotSendQueriesOrKeysToPublicFallbacks() {
        var calls = 0
        val client = client { chain ->
            calls++
            assertEquals("api.deepseek.com", chain.request().url.host)
            response(chain, "ignored", 401)
        }
        AgentWebTools(WebHttpTransport(client), runtimeConfig = {
            WebToolsRuntimeConfig(searchBackend = WebToolBackend.DEEPSEEK, deepSeekApiKey = "private-key")
        }).use {
            assertEquals("SEARCH_AUTH_FAILED", search(it).getString("code"))
            assertEquals(1, calls)
        }
    }

    @Test fun parserRejectsEntitiesWrongHostAndInvalidItemsAndBoundsOutput() {
        val valid = feed(item("第一条", "https://example.test/a", "说明") +
            item("重复", "https://example.test/a", "说明") + item("第二条", "https://example.test/b", "说明"))
        val result = BingWebSearch.parse(valid, "https://cn.bing.com/search", 1)
        assertEquals(1, result.results.size)
        assertTrue("result_limit" in result.limitReasons)
        for ((xml, url) in listOf(
            "<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///secret'>]>$valid" to "https://www.bing.com/search",
            valid to "https://notbing.com/search",
            feed(item("bad", "javascript:alert(1)", "")) to "https://www.bing.com/search",
            feed(item("bad", "https://user:secret@example.test", "")) to "https://www.bing.com/search",
            "<html>no results</html>" to "https://www.bing.com/search",
            valid.removeSuffix("</rss>") to "https://www.bing.com/search",
        )) {
            assertEquals("SEARCH_PARSE_FAILED", assertThrows(WebSearchException::class.java) {
                BingWebSearch.parse(xml, url, 5)
            }.code)
        }
        assertTrue(BingWebSearch.parse(feed(""), "https://www.bing.com/search", 5).results.isEmpty())
    }

    private fun search(tools: AgentWebTools, query: String = "example") =
        JSONObject(tools.execute("web_search", JSONObject().put("query", query)))
    private fun client(handler: (Interceptor.Chain) -> Response) =
        OkHttpClient.Builder().addInterceptor(Interceptor(handler)).build()
    private fun response(chain: Interceptor.Chain, body: String, status: Int = 200, mime: String = "text/html") =
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Fixture")
            .header("Content-Type", "$mime; charset=utf-8").body(body.toResponseBody()).build()
    private fun feed(items: String) = "<?xml version='1.0'?><rss version='2.0'><channel><title>Search</title>$items</channel></rss>"
    private fun item(title: String, url: String, snippet: String) =
        "<item><title>$title</title><link>$url</link><description>$snippet</description></item>"
    private val duckResult = "<div id='links'><div class='web-result'><h2><a class='result__a' href='https://example.test/page'>来源</a></h2><div class='result__snippet'>摘要</div></div></div>"
}
