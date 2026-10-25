package io.github.mangi.eta.data.provider

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

internal enum class CommunityCatalogSource {
    SNAPSHOT,
    CACHE,
    ONLINE,
}

internal data class CommunityCatalog(
    val providers: List<CommunityCatalogProvider>,
    val source: CommunityCatalogSource,
    val fetchedAt: Long? = null,
)

internal data class CommunityCatalogProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val models: List<CommunityCatalogModel>,
)

internal data class CommunityCatalogModel(
    val modelId: String,
    val displayName: String,
    val contextWindow: Int,
    val inputModalities: List<String>,
    val releaseDate: String?,
    val reasoning: Boolean,
    val structuredOutput: Boolean?,
    val supportsTemperature: Boolean?,
)

/** 社区目录仅负责候选项；Eta 的请求协议与认证地址由本地过滤规则决定。 */
internal object CommunityCatalogParser {
    private const val COMPATIBLE_NPM = "@ai-sdk/openai-compatible"
    private val providerIdPattern = Regex("[a-z0-9][a-z0-9_-]{0,79}")
    private val releaseDatePattern = Regex("\\d{4}-\\d{2}(-\\d{2})?")
    private val knownChatEndpoints = mapOf(
        "xai" to "https://api.x.ai/v1",
        "mistral" to "https://api.mistral.ai/v1",
    )

    fun parse(raw: String, source: CommunityCatalogSource, fetchedAt: Long? = null): CommunityCatalog {
        val root = JSONObject(raw)
        val providers = buildList {
            val ids = root.keys()
            while (ids.hasNext()) {
                val id = ids.next()
                val item = root.optJSONObject(id) ?: continue
                parseProvider(id, item)?.let(::add)
            }
        }.sortedWith(compareBy(CommunityCatalogProvider::name, CommunityCatalogProvider::id))
        require(providers.isNotEmpty()) { "目录中没有可导入的对话提供商" }
        return CommunityCatalog(providers, source, fetchedAt)
    }

    private fun parseProvider(id: String, source: JSONObject): CommunityCatalogProvider? {
        if (!providerIdPattern.matches(id)) return null
        val rawBaseUrl = knownChatEndpoints[id] ?: source.optString("api")
            .takeIf { source.optString("npm") == COMPATIBLE_NPM }
            ?: return null
        val baseUrl = rawBaseUrl.safeChatBaseUrl() ?: return null
        val models = source.optJSONObject("models") ?: return null
        val candidates = buildList {
            val ids = models.keys()
            while (ids.hasNext()) {
                val modelId = ids.next()
                val model = models.optJSONObject(modelId) ?: continue
                parseModel(modelId, model)?.let(::add)
            }
        }.sortedWith(
            compareByDescending<CommunityCatalogModel> { it.releaseDate.orEmpty() }
                .thenBy(CommunityCatalogModel::displayName)
        )
        if (candidates.isEmpty()) return null
        return CommunityCatalogProvider(
            id = id,
            name = source.optString("name").trim().takeIf(String::isNotBlank)?.take(120) ?: id,
            baseUrl = baseUrl,
            models = candidates,
        )
    }

    private fun parseModel(modelId: String, source: JSONObject): CommunityCatalogModel? {
        if (modelId.isBlank() || modelId.length > 200 || modelId.any(Char::isISOControl)) return null
        if (source.has("provider")) return null
        if (source.optString("status").lowercase() in setOf("alpha", "deprecated", "retired", "removed")) {
            return null
        }
        if (!source.optBoolean("tool_call")) return null
        val modalities = source.optJSONObject("modalities") ?: return null
        val input = modalities.optJSONArray("input")?.strings().orEmpty()
        val output = modalities.optJSONArray("output")?.strings().orEmpty()
        if ("text" !in input || output != listOf("text")) return null
        val context = source.optJSONObject("limit")?.optLong("context") ?: return null
        if (context !in 1..Int.MAX_VALUE.toLong()) return null
        return CommunityCatalogModel(
            modelId = modelId,
            displayName = source.optString("name").trim().takeIf(String::isNotBlank)?.take(120) ?: modelId,
            contextWindow = context.toInt(),
            inputModalities = input,
            releaseDate = source.optString("release_date").takeIf(releaseDatePattern::matches),
            reasoning = source.optBoolean("reasoning"),
            structuredOutput = source.opt("structured_output") as? Boolean,
            supportsTemperature = source.opt("temperature") as? Boolean,
        )
    }

    private fun String.safeChatBaseUrl(): String? {
        val url = trim().toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.query != null || url.fragment != null || url.host == "localhost" ||
            url.host.endsWith(".local") || url.host.contains(':') ||
            url.host.all { it.isDigit() || it == '.' }
        ) return null
        val path = url.encodedPath.trimEnd('/').lowercase()
        if (listOf("/chat/completions", "/responses", "/messages", "/models").any(path::endsWith)) {
            return null
        }
        return url.toString().trimEnd('/')
    }

    private fun org.json.JSONArray.strings(): List<String> = buildList {
        for (index in 0 until length()) {
            optString(index).lowercase().takeIf(String::isNotBlank)?.let(::add)
        }
    }
}
