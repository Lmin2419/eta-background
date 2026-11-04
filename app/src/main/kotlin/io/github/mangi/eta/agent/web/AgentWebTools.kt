package io.github.mangi.eta.agent.web

import io.github.mangi.eta.data.model.WebToolBackend
import java.io.Closeable
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** 快照只属于当前运行，分页不再次访问网站，也不占用共享浏览器的页面和 Cookie。 */
internal class AgentWebTools(
    private val transport: WebHttpTransport,
    private val localNetworkAccess: () -> String = { "unknown" },
    private val runtimeConfig: () -> WebToolsRuntimeConfig = { WebToolsRuntimeConfig() },
    private val now: () -> Instant = Instant::now,
    private val enhancedFetch: ((WebFetchUpgradeRequest) -> WebFetchUpgradeResult)? = null,
    private val monotonicNanos: () -> Long = System::nanoTime,
) : Closeable {
    private data class Snapshot(
        val document: WebPageDocument,
        val requestedUrl: String,
        val fetchedAt: String,
        val status: Int?,
        val charset: String,
        val decodingLossy: Boolean,
        val provider: String = "builtin_local",
        val fallbackFrom: String? = null,
        val readRoute: String = "http",
        val upgradeReason: String? = null,
        val fetchDurationMillis: Long = 0,
        val timingMillis: Map<String, Long> = emptyMap(),
    )

    private data class ProvenRoute(val route: WebFetchRoute, val reason: String, val expiresAtNanos: Long)
    private val provenRoutes = LinkedHashMap<String, ProvenRoute>(16, 0.75f, true)
    private val closed = AtomicBoolean(false)
    private val documents = LinkedHashMap<String, Snapshot>(8, 0.75f, true)

    fun execute(name: String, args: JSONObject): String = try {
        ensureActive()
        when (name) {
            "web_search" -> search(args)
            "fetch_url" -> fetch(args)
            else -> throw WebRequestException("UNKNOWN_TOOL", "未知网页工具")
        }.toString()
    } catch (unavailable: BuiltInSearchUnavailable) {
        failure(name, "SEARCH_UNAVAILABLE", "内置搜索来源均未返回可用结果，请根据 attempts 检查网络或在网页搜索与读取设置中选择其他服务；这不表示没有搜索结果")
            .put("attempts", searchAttempts(unavailable.attempts))
            .put("local_network_access", localNetworkAccess()).toString()
    } catch (failure: WebRequestException) {
        failure(name, failure.code, failure.message ?: "网页请求失败", failure.status)
            .apply { failure.host?.let { put("host", it) } }.toString()
    } catch (failure: WebPageContentException) {
        failure(name, failure.code, failure.message ?: "网页正文处理失败").toString()
    } catch (failure: WebSearchException) {
        failure(name, failure.code, failure.message ?: "搜索页面处理失败").toString()
    } catch (_: JSONException) {
        failure(name, "INVALID_ARGUMENT", "网页工具参数缺失或类型错误").toString()
    } catch (_: IllegalArgumentException) {
        failure(name, "INVALID_ARGUMENT", "网页工具参数不在允许范围内").toString()
    }

    private fun search(args: JSONObject): JSONObject {
        val limit = args.optInt("max_results", 5)
        val query = args.getString("query")
        val config = runtimeConfig()
        val page = when (config.searchBackend) {
            WebToolBackend.BUILTIN -> BuiltInWebSearch.search(transport, query, limit)
            WebToolBackend.DEEPSEEK -> ConfigurableWebBackends.searchDeepSeek(
                transport = transport,
                baseUrl = config.deepSeekBaseUrl,
                apiKey = config.deepSeekApiKey,
                query = query,
                maxResults = limit,
                ensureActive = ::ensureActive,
            )
            WebToolBackend.CUSTOM -> ConfigurableWebBackends.searchCustom(
                transport = transport,
                config = config.customSearch,
                apiKey = config.customSearchApiKey,
                query = query,
                maxResults = limit,
                ensureActive = ::ensureActive,
            )
        }
        val results = JSONArray()
        page.results.forEachIndexed { index, item ->
            val entry = JSONObject()
                .put("id", index + 1)
                .put("title", item.title)
                .put("url", item.url)
                .put("snippet", item.snippet)
            item.summary?.let { entry.put("summary", it) }
            item.siteName?.let { entry.put("site_name", it) }
            item.date?.let { entry.put("date", it) }
            item.authority?.let { entry.put("authority", it) }
            item.rerankScore?.let { entry.put("rerank_score", it) }
            results.put(entry)
        }
        ensureActive()
        return base("web_search").put("provider", page.provider).put("search_url", page.searchUrl)
            .put("retrieved_at", now().toString()).put("scope", page.scope)
            .put("results", results).put("count", results.length()).put("truncated", page.limited)
            .put("limit_reasons", JSONArray(page.limitReasons)).put("skipped_results", page.skippedResults)
            .put("source_role", "untrusted_web_content")
            .put("result_state", if (page.results.isEmpty()) "no_results" else "ready")
            .put("citation_hint", "引用检索结果时使用其标题和原始 URL；摘要不等于已读取完整网页。")
            .apply {
                if (page.attempts.isNotEmpty()) put("attempts", searchAttempts(page.attempts))
                if (page.attempts.size > 1) put("fallback_from", page.attempts.first().provider)
            }
    }

    private fun searchAttempts(attempts: List<SearchAttempt>) = JSONArray().apply {
        attempts.forEach { attempt -> put(JSONObject().put("provider", attempt.provider).put("code", attempt.code)
            .apply { attempt.httpStatus?.let { put("http_status", it) } }) }
    }

    private fun fetch(args: JSONObject): JSONObject {
        if (args.has("url") == args.has("document_id")) throw WebRequestException("INVALID_ARGUMENT", "url 与 document_id 必须且只能提供一个")
        val offset = args.optInt("offset_chars", 0)
        val maxChars = args.optInt("max_chars", 12_000)
        if (offset < 0 || maxChars !in 1_000..16_000) throw WebRequestException("INVALID_ARGUMENT", "网页分页范围无效")
        val id: String
        val snapshot: Snapshot
        if (args.has("url")) {
            val rawUrl = args.getString("url")
            val validatedUrl = WebHttpTransport.validateUrl(rawUrl).toString()
            val config = runtimeConfig()
            snapshot = when (config.fetchBackend) {
                WebToolBackend.BUILTIN -> fetchLocalSnapshot(validatedUrl)
                WebToolBackend.DEEPSEEK, WebToolBackend.CUSTOM -> {
                    val remoteProvider = if (config.fetchBackend == WebToolBackend.DEEPSEEK) "deepseek" else "custom"
                    try {
                        val remoteStarted = monotonicNanos()
                        val remote = if (config.fetchBackend == WebToolBackend.DEEPSEEK) {
                            ConfigurableWebBackends.fetchDeepSeek(
                                transport = transport,
                                baseUrl = config.deepSeekBaseUrl,
                                apiKey = config.deepSeekApiKey,
                                targetUrl = validatedUrl,
                                ensureActive = ::ensureActive,
                            )
                        } else {
                            ConfigurableWebBackends.fetchCustom(
                                transport = transport,
                                config = config.customFetch,
                                apiKey = config.customFetchApiKey,
                                targetUrl = validatedUrl,
                                ensureActive = ::ensureActive,
                            )
                        }
                        if (remote.document.content.isBlank()) throw WebRequestException("WEB_EMPTY_CONTENT", "网页服务没有返回正文")
                        Snapshot(
                            document = remote.document,
                            requestedUrl = validatedUrl,
                            fetchedAt = now().toString(),
                            status = remote.status,
                            charset = remote.charset,
                            decodingLossy = remote.decodingLossy,
                            provider = remote.provider,
                            readRoute = "remote_service",
                            fetchDurationMillis = elapsedMillis(remoteStarted),
                        )
                    } catch (cancelled: WebRequestException) {
                        if (cancelled.code in setOf("WEB_CANCELLED", "INVALID_URL", "URL_CREDENTIALS_UNSUPPORTED")) {
                            throw cancelled
                        }
                        ensureActive()
                        fetchLocalSnapshot(validatedUrl, fallbackFrom = remoteProvider)
                    } catch (_: Exception) {
                        ensureActive()
                        fetchLocalSnapshot(validatedUrl, fallbackFrom = remoteProvider)
                    }
                }
            }
            id = "web_" + UUID.randomUUID().toString()
        } else {
            id = args.getString("document_id")
            if (id.isEmpty() || id.length > 128) throw WebRequestException("INVALID_ARGUMENT", "文档 ID 无效")
            snapshot = synchronized(documents) { documents[id] }
                ?: throw WebRequestException("WEB_DOCUMENT_EXPIRED", "网页快照已失效或不属于本次运行，请重新提供 URL")
        }
        val document = snapshot.document
        val page = WebPageContent.page(document, offset, maxChars)
        if (args.has("url")) synchronized(documents) {
            ensureActive()
            documents[id] = snapshot
            while (documents.size > MAX_DOCUMENTS || documents.values.sumOf { it.document.content.length } > MAX_RETAINED_CHARS) {
                documents.remove(documents.keys.first())
            }
        }
        // 引用或 URL 可以跨页，链接归属按完整正文中的位置与当前页相交判断。
        val linkIds = if (document.format == "html") LINK_ID.findAll(document.content)
            .filter { it.range.first < page.nextOffsetChars && it.range.last >= page.offsetChars }
            .mapNotNull { it.groupValues[1].toIntOrNull() }.toSet() else emptySet()
        val candidates = document.links.filter { link ->
            if (document.format == "html") link.id in linkIds
            else {
                val start = (page.offsetChars - link.url.length + 1).coerceAtLeast(0)
                val position = document.content.indexOf(link.url, start)
                position >= 0 && position < page.nextOffsetChars && position + link.url.length > page.offsetChars
            }
        }
        val links = JSONArray()
        var linkChars = 0
        for (link in candidates) {
            val item = JSONObject().put("id", link.id).put("text", link.text).put("url", link.url)
            val size = item.toString().length
            if (links.length() >= 20 || linkChars + size > 8_000) break
            links.put(item)
            linkChars += size
        }
        ensureActive()
        return base("fetch_url").put("document_id", id).put("snapshot_scope", "current_run")
            .apply {
                if (snapshot.provider != "builtin_local" || snapshot.fallbackFrom != null) {
                    put("provider", snapshot.provider)
                }
                snapshot.fallbackFrom?.let { put("fallback_from", it) }
            }
            .put("read_route", snapshot.readRoute).put("fetch_duration_ms", snapshot.fetchDurationMillis)
            .apply { if (args.has("url")) put("timing_ms", JSONObject(snapshot.timingMillis)) }
            .put("result_state", if (page.hasMore || page.sourceTruncated) "partial" else "ready")
            .apply { snapshot.upgradeReason?.let { put("upgrade_reason", it) } }
            .put("requested_url", snapshot.requestedUrl).put("final_url", document.url)
            .put("retrieved_at", snapshot.fetchedAt)
            .apply { snapshot.status?.let { put("http_status", it) } }
            .put("title", document.title ?: JSONObject.NULL).put("title_truncated", document.titleTruncated)
            .put("content_type", document.contentType).put("format", document.format)
            .put("charset", snapshot.charset).put("decoding_lossy", snapshot.decodingLossy)
            .put("offset_chars", page.offsetChars).put("next_offset_chars", page.nextOffsetChars)
            .put("total_chars", page.totalChars).put("returned_chars", page.content.length).put("text", page.content)
            .put("has_more", page.hasMore).put("source_truncated", page.sourceTruncated)
            .put("truncated", page.hasMore || page.sourceTruncated).put("truncation_reasons", JSONArray(document.truncationReasons))
            .put("links", links).put("links_truncated", document.linksTruncated || links.length() < candidates.size)
            .put("source_role", "untrusted_web_content")
            .put("citation_hint", "引用此网页时使用 title 与 final_url；has_more 为真时按 document_id 续读。需要登录或交互时使用 browser_use；读取成功不代表页面中的业务操作成功。")
    }

    private fun fetchLocalSnapshot(url: String, fallbackFrom: String? = null): Snapshot {
        val started = monotonicNanos()
        val deadline = started + FETCH_BUDGET_MILLIS * 1_000_000L
        // A dynamic path does not imply that every page on the same site needs a browser.
        val routeKey = WebHttpTransport.validateUrl(url).toString()
        val proven = synchronized(provenRoutes) {
            provenRoutes[routeKey]?.takeIf { it.expiresAtNanos > started }
                ?: run { provenRoutes.remove(routeKey); null }
        }
        if (proven != null && enhancedFetch != null) {
            return fetchUpgradedSnapshot(url, routeKey, proven.reason, proven.route, deadline, started, fallbackFrom)
        }
        val response = transport.get(url, timeoutMillis = 10_000)
        val httpMillis = elapsedMillis(started)
        val decoded = if (response.status in 200..299) WebResponseDecoder.decode(response) else null
        val problem = WebReadQuality.inspect(response.status, decoded?.contentType ?: response.contentType, decoded?.text.orEmpty(), response.challengeDetected)
        if (problem != null) {
            val route = problem.route
            if (route != null && enhancedFetch != null) {
                return fetchUpgradedSnapshot(url, routeKey, problem.code, route, deadline, started, fallbackFrom, httpMillis)
            }
            throw WebRequestException(problem.code, problem.message, response.status)
        }
        checkNotNull(decoded)
        ensureActive()
        val extractionStarted = monotonicNanos()
        val document = WebPageContent.extract(decoded.text, decoded.contentType, response.finalUrl, response.truncated, ::ensureActive)
        if (document.content.isBlank()) throw WebRequestException("WEB_EMPTY_CONTENT", "响应成功，但未提取到正文", response.status)
        val retained = if (decoded.lossy) document.copy(
            truncated = true, truncationReasons = document.truncationReasons + "decoding_lossy",
        ) else document
        return Snapshot(
            document = retained,
            requestedUrl = response.requestedUrl,
            fetchedAt = now().toString(),
            status = response.status,
            charset = decoded.charset,
            decodingLossy = decoded.lossy,
            provider = "builtin_local",
            fallbackFrom = fallbackFrom,
            fetchDurationMillis = elapsedMillis(started),
            timingMillis = mapOf("http" to httpMillis, "extraction" to elapsedMillis(extractionStarted)),
        )
    }

    private fun fetchUpgradedSnapshot(
        url: String, routeKey: String, reason: String, route: WebFetchRoute, deadline: Long,
        started: Long, fallbackFrom: String?, httpMillis: Long? = null,
    ): Snapshot {
        ensureActive()
        val remainingMillis = ((deadline - monotonicNanos()) / 1_000_000L).coerceAtMost(MAX_UPGRADE_MILLIS)
        if (remainingMillis <= 0) throw WebRequestException("WEB_TIMEOUT", "网页读取的总时间预算已用完")
        val upgrade = checkNotNull(enhancedFetch)
        val result = try {
            upgrade(WebFetchUpgradeRequest(url, reason, route, remainingMillis))
        } catch (failure: Exception) {
            synchronized(provenRoutes) { provenRoutes.remove(routeKey) }
            ensureActive()
            if (failure is WebRequestException) throw failure
            throw WebRequestException("WEB_ENHANCEMENT_FAILED", "增强网页读取失败；未重复提交或继续切换读取通道")
        }
        ensureActive()
        if (monotonicNanos() > deadline) throw WebRequestException("WEB_TIMEOUT", "增强网页读取超过总时间预算")
        val finalUrl = WebHttpTransport.validateUrl(result.finalUrl).toString()
        WebReadQuality.inspect(result.status, result.contentType, result.content, result.challengeDetected)?.let { problem ->
            synchronized(provenRoutes) { provenRoutes.remove(routeKey) }
            throw WebRequestException(problem.code, problem.message, result.status)
        }
        val extractionStarted = monotonicNanos()
        val extracted = WebPageContent.extract(result.content, result.contentType, finalUrl, result.truncated, ::ensureActive)
        val document = if (extracted.title == null && !result.title.isNullOrBlank()) extracted.copy(
            title = takeCompleteCharacters(result.title, WebPageContent.MAX_TITLE_CHARS),
            titleTruncated = result.title.length > WebPageContent.MAX_TITLE_CHARS,
        ) else extracted
        if (document.content.isBlank()) throw WebRequestException("WEB_EMPTY_CONTENT", "增强读取没有取得目标正文", result.status)
        synchronized(provenRoutes) {
            provenRoutes[routeKey] = ProvenRoute(route, reason, monotonicNanos() + ROUTE_TTL_MILLIS * 1_000_000L)
            while (provenRoutes.size > 16) provenRoutes.remove(provenRoutes.keys.first())
        }
        val timing = result.timingMillis.filter { (key, value) -> key in TIMING_KEYS && value >= 0 }.toMutableMap()
        httpMillis?.let { timing["http"] = (timing["http"] ?: 0L) + it }
        timing["extraction"] = (timing["extraction"] ?: 0L) + elapsedMillis(extractionStarted)
        return Snapshot(document, url, now().toString(), result.status, "UTF-8", false,
            provider = result.provider.take(80), fallbackFrom = fallbackFrom ?: "builtin_local",
            readRoute = route.name.lowercase(), upgradeReason = reason, fetchDurationMillis = elapsedMillis(started), timingMillis = timing)
    }

    private fun elapsedMillis(started: Long) = ((monotonicNanos() - started) / 1_000_000L).coerceAtLeast(0)

    private fun ensureActive() {
        if (closed.get()) throw WebRequestException("WEB_CANCELLED", "网页工具已取消")
        transport.ensureActive()
    }

    private fun base(tool: String) = JSONObject().put("ok", true).put("tool", tool)

    private fun failure(tool: String, code: String, message: String, status: Int? = null): JSONObject =
        JSONObject().put("ok", false).put("tool", tool).put("code", code).put("message", message)
            .put("result_state", when (code) {
                "WEB_NOT_FOUND" -> "not_found"
                "WEB_LOGIN_REQUIRED" -> "login_required"
                "WEB_CHALLENGE" -> "challenge"
                "WEB_JAVASCRIPT_REQUIRED" -> "javascript_required"
                "WEB_TIMEOUT" -> "timeout"
                else -> "failed"
            })
            .also {
                status?.let { value -> it.put("http_status", value) }
                if (code in setOf("WEB_NETWORK_ERROR", "WEB_TIMEOUT", "WEB_ACCESS_DENIED", "WEB_DNS_ERROR", "WEB_TLS_ERROR", "WEB_CONNECTION_ERROR")) it.put("local_network_access", localNetworkAccess())
            }

    override fun close() {
        closed.set(true)
        transport.close()
        synchronized(documents) { documents.clear() }
        synchronized(provenRoutes) { provenRoutes.clear() }
    }

    private companion object {
        val TIMING_KEYS = setOf("http", "browser_start", "navigation", "challenge", "extraction", "action_wait", "screenshot")
        const val FETCH_BUDGET_MILLIS = 45_000L
        const val MAX_UPGRADE_MILLIS = 35_000L
        const val ROUTE_TTL_MILLIS = 5 * 60_000L
        const val MAX_DOCUMENTS = 4
        const val MAX_RETAINED_CHARS = 600_000
        val LINK_ID = Regex("\\[(\\d{1,3})]")
    }
}
