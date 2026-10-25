package io.github.mangi.eta.data.provider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityCatalogParserTest {
    @Test
    fun acceptsHttpsBaseUrlAndRejectsUnsafeOrCompleteRequestUrls() {
        val provider = CommunityCatalogParser.parse(
            catalogJson("sample" to compatibleProvider("https://models.example.com/custom/v1")),
            CommunityCatalogSource.SNAPSHOT,
        ).providers.single()

        assertEquals("https://models.example.com/custom/v1", provider.baseUrl)

        listOf(
            "http://models.example.com/v1",
            "https://user:secret@models.example.com/v1",
            "https://models.example.com/v1?token=secret",
            "https://models.example.com/v1#fragment",
            "https://localhost/v1",
            "https://192.168.1.1/v1",
            "https://models.example.com/v1/chat/completions",
            "https://models.example.com/v1/responses",
        ).forEach { unsafeUrl ->
            assertThrows(unsafeUrl, IllegalArgumentException::class.java) {
                CommunityCatalogParser.parse(
                    catalogJson("sample" to compatibleProvider(unsafeUrl)),
                    CommunityCatalogSource.ONLINE,
                )
            }
        }
    }

    @Test
    fun knownNativeProviderUsesVerifiedEndpointAndExcludesModelLevelProviderOverride() {
        val models = JSONObject()
            .put("safe", chatModel())
            .put("routed", chatModel().put("provider", "untrusted-gateway"))
        val nativeProvider = JSONObject()
            .put("name", "xAI")
            .put("npm", "@ai-sdk/xai")
            .put("api", "https://untrusted.example.com/v1")
            .put("models", models)

        val provider = CommunityCatalogParser.parse(
            catalogJson("xai" to nativeProvider),
            CommunityCatalogSource.ONLINE,
        ).providers.single()

        assertEquals("https://api.x.ai/v1", provider.baseUrl)
        assertEquals(listOf("safe"), provider.models.map { it.modelId })

        nativeProvider.put("npm", "@ai-sdk/openai-compatible")
        val stillPinned = CommunityCatalogParser.parse(
            catalogJson("xai" to nativeProvider),
            CommunityCatalogSource.ONLINE,
        ).providers.single()
        assertEquals("https://api.x.ai/v1", stillPinned.baseUrl)
    }

    @Test
    fun keepsOnlyToolCapableTextResponsesFromActiveModels() {
        val models = JSONObject()
            .put("active", chatModel())
            .put("beta", chatModel().put("status", "beta"))
            .put("deprecated", chatModel().put("status", "deprecated"))
            .put("alpha", chatModel().put("status", "alpha"))
            .put("no-tools", chatModel().put("tool_call", false))
            .put("image-output", chatModel().put(
                "modalities",
                modalities(listOf("text"), listOf("image")),
            ))
            .put("image-only-input", chatModel().put(
                "modalities",
                modalities(listOf("image"), listOf("text")),
            ))
            .put("unknown-context", chatModel().put("limit", JSONObject().put("context", 0)))

        val provider = CommunityCatalogParser.parse(
            catalogJson("sample" to compatibleProvider("https://models.example.com/v1", models)),
            CommunityCatalogSource.SNAPSHOT,
        ).providers.single()

        assertEquals(setOf("active", "beta"), provider.models.map { it.modelId }.toSet())
        assertTrue(provider.models.all { it.inputModalities.contains("text") })
        assertFalse(provider.models.any { it.modelId == "deprecated" })
    }

    private fun catalogJson(vararg providers: Pair<String, JSONObject>): String =
        JSONObject().apply {
            providers.forEach { (id, provider) -> put(id, provider) }
        }.toString()

    private fun compatibleProvider(
        api: String,
        models: JSONObject = JSONObject().put("chat-model", chatModel()),
    ): JSONObject = JSONObject()
        .put("name", "Sample")
        .put("npm", "@ai-sdk/openai-compatible")
        .put("api", api)
        .put("models", models)

    private fun chatModel(): JSONObject = JSONObject()
        .put("name", "Chat Model")
        .put("tool_call", true)
        .put("attachment", true)
        .put("reasoning", true)
        .put("limit", JSONObject().put("context", 128_000))
        .put("modalities", modalities(listOf("text"), listOf("text")))

    private fun modalities(input: List<String>, output: List<String>): JSONObject = JSONObject()
        .put("input", JSONArray(input))
        .put("output", JSONArray(output))
}
