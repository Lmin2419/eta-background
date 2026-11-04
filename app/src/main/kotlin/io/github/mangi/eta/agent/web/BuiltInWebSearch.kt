package io.github.mangi.eta.agent.web

import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrl

internal data class SearchAttempt(val provider: String, val code: String, val httpStatus: Int? = null)

internal class BuiltInSearchUnavailable(val attempts: List<SearchAttempt>) : IOException()

/** Two independent anonymous sources, one bounded request each. No credentials or browser state. */
internal object BuiltInWebSearch {
    fun search(transport: WebHttpTransport, query: String, limit: Int): SearchPage {
        // Validate before any network request (including the fallback).
        val duckUrl = PublicWebSearch.searchUrl(query, limit)
        val bingUrl = "https://www.bing.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim()).addQueryParameter("format", "rss").build().toString()
        val attempts = mutableListOf<SearchAttempt>()
        for ((provider, url) in listOf(BingWebSearch.PROVIDER to bingUrl, PublicWebSearch.PROVIDER to duckUrl)) {
            transport.ensureActive()
            try {
                val response = transport.get(url, maxBytes = 1024 * 1024, timeoutMillis = 10_000)
                when (response.status) {
                    202 -> throw WebRequestException("SEARCH_CHALLENGE", "搜索站点要求人工验证", 202)
                    429 -> throw WebRequestException("SEARCH_RATE_LIMITED", "搜索站点限制请求", 429)
                    401, 403 -> throw WebRequestException("WEB_ACCESS_DENIED", "搜索站点拒绝请求", response.status)
                }
                if (response.status !in 200..299) throw WebRequestException("WEB_HTTP_ERROR", "搜索请求失败", response.status)
                if (response.truncated) throw WebRequestException("SEARCH_RESPONSE_TOO_LARGE", "搜索响应超过大小限制")
                val decoded = WebResponseDecoder.decode(response, allowXml = provider == BingWebSearch.PROVIDER)
                if (decoded.lossy) throw WebRequestException("SEARCH_ENCODING_ERROR", "搜索响应无法可靠解码")
                val page = if (provider == BingWebSearch.PROVIDER) {
                    BingWebSearch.parse(decoded.text, response.finalUrl, limit, transport::ensureActive)
                } else {
                    PublicWebSearch.parse(decoded.text, response.finalUrl, limit, transport::ensureActive)
                }
                transport.ensureActive()
                val relevantPage = SearchRelevance.filter(query, page)
                attempts += SearchAttempt(provider, "OK")
                return relevantPage.copy(attempts = attempts.toList())
            } catch (failure: WebRequestException) {
                if (failure.code == "WEB_CANCELLED") throw failure
                attempts += SearchAttempt(provider, failure.code, failure.status)
            } catch (failure: WebSearchException) {
                attempts += SearchAttempt(provider, failure.code)
            }
        }
        transport.ensureActive()
        throw BuiltInSearchUnavailable(attempts.toList())
    }
}
