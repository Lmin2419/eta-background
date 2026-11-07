package io.github.mangi.eta.data.repository

import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.db.ConversationContextCheckpointEntity
import io.github.mangi.eta.data.db.ConversationEntity
import io.github.mangi.eta.data.db.ConversationMessageEntity
import io.github.mangi.eta.data.db.ConversationStateEntity
import io.github.mangi.eta.data.db.EtaDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConversationArchiveBackupTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        EtaDatabase.closeForTests()
        context.deleteDatabase("eta.db")
        SettingsDataStore.init(context)
        runBlocking {
            SettingsDataStore.setSelection(null, null)
            SettingsDataStore.setOfficialModelCatalogRevision(0)
        }
        ProviderRepository.init(context)
        AgentMemoryRepository.init(context)
    }

    @After
    fun tearDown() {
        EtaDatabase.closeForTests()
        context.deleteDatabase("eta.db")
    }

    private suspend fun dao() = EtaDatabase.get(context).conversationDao()

    private suspend fun freshDatabase() {
        EtaDatabase.closeForTests()
        context.deleteDatabase("eta.db")
        EtaDatabase.get(context)
        SettingsDataStore.setSelection(null, null)
        SettingsDataStore.setOfficialModelCatalogRevision(0)
    }


    @Test
    fun roundTripKeepsArchivedAtMessagesCheckpointsAndSelection() = runBlocking {
        val base = 1_700_000_000_000L
        dao().insertConversations(listOf(
            ConversationEntity(id = "c1", title = "归档会话", thinkingEnabled = true,
                createdAt = base - 1000, updatedAt = base,
                modelId = "gpt-4o-mini", archivedAt = 1_650_000_000_000L),
            ConversationEntity(id = "c2", title = "活动会话", thinkingEnabled = false,
                createdAt = base - 2000, updatedAt = base - 500)
        ))
        dao().insertMessages(listOf(
            ConversationMessageEntity(id = "m1", conversationId = "c1", sortIndex = 0, type = "user", content = "你好"),
            ConversationMessageEntity(id = "m2", conversationId = "c1", sortIndex = 1, type = "assistant", content = "收到"),
            ConversationMessageEntity(id = "m3", conversationId = "c2", sortIndex = 0, type = "user", content = "闲聊")
        ))
        dao().insertContextCheckpoints(listOf(
            ConversationContextCheckpointEntity(conversationId = "c1", historyJson = "[{\"role\":\"user\",\"content\":\"你好\"}]", journalJson = "[{\"role\":\"user\",\"content\":\"你好\"}]"),
            ConversationContextCheckpointEntity(conversationId = "c2", historyJson = "[]")
        ))
        dao().insertState(ConversationStateEntity(id = "main", selectedConversationId = "c2"))

        val out = ByteArrayOutputStream()
        EtaBackupRepository.export(context, out)
        val raw = out.toString(Charsets.UTF_8.name())
        assertTrue("导出JSON应包含 archived_at", raw.contains("\"archived_at\""))
        val root = Json.parseToJsonElement(raw).jsonObject
        assertEquals(6, root.getValue("schemaVersion").jsonPrimitive.int)
        val c1json = root.getValue("conversations").jsonArray
            .first { it.jsonObject["id"]?.jsonPrimitive?.content == "c1" }.jsonObject
        assertEquals(1_650_000_000_000L, c1json.getValue("archived_at").jsonPrimitive.long)

        val bytes = out.toByteArray()
        freshDatabase()
        EtaBackupRepository.import(context, ByteArrayInputStream(bytes))

        val convs = dao().conversationEntities().associateBy { it.id }
        assertEquals(2, convs.size)
        assertEquals(1_650_000_000_000L, convs.getValue("c1").archivedAt!!)
        assertNull(convs.getValue("c2").archivedAt)
        assertEquals("gpt-4o-mini", convs.getValue("c1").modelId)
        val messages = dao().messages()
        assertEquals(3, messages.size)
        assertTrue(messages.any { it.id == "m1" && it.content == "你好" && it.type == "user" })
        assertTrue(messages.any { it.id == "m2" && it.content == "收到" })
        assertTrue(dao().contextCheckpoints().any {
            it.conversationId == "c1" && it.historyJson == "[{\"role\":\"user\",\"content\":\"你好\"}]" && it.journalJson == "[{\"role\":\"user\",\"content\":\"你好\"}]"
        })
        assertEquals("c2", dao().state()?.selectedConversationId)
    }

    @Test
    fun legacyV4WithoutArchivedAtImportsAsNullAndStaysNull() = runBlocking {
        val base = 1_700_000_100_000L
        dao().insertConversations(listOf(ConversationEntity(
            id = "legacy", title = "旧会话", thinkingEnabled = false,
            createdAt = base - 1000, updatedAt = base
        )))
        dao().insertMessages(listOf(
            ConversationMessageEntity(id = "lm1", conversationId = "legacy", sortIndex = 0, type = "user", content = "旧消息")
        ))
        dao().insertContextCheckpoints(listOf(
            ConversationContextCheckpointEntity(conversationId = "legacy", historyJson = "[{\"role\":\"user\",\"content\":\"旧消息\"}]", journalJson = "[{\"role\":\"user\",\"content\":\"你好\"}]")
        ))
        dao().insertState(ConversationStateEntity(id = "main", selectedConversationId = "legacy"))

        val out = ByteArrayOutputStream()
        EtaBackupRepository.export(context, out)
        val root = Json.parseToJsonElement(out.toString(Charsets.UTF_8.name())).jsonObject
        val sk = "schemaVersion"
        val downgraded = JsonObject(root.toMutableMap().apply {
            put(sk, JsonPrimitive(4))
            put("conversations", JsonArray(root.getValue("conversations").jsonArray.map { el ->
                JsonObject(el.jsonObject.filterKeys { it != "archived_at" })
            }))
        })

        freshDatabase()
        EtaBackupRepository.import(context,
            ByteArrayInputStream(downgraded.toString().toByteArray(Charsets.UTF_8)))

        val conv = dao().conversationEntities().single()
        assertEquals("legacy", conv.id)
        assertEquals("旧会话", conv.title)
        assertNull(conv.archivedAt)
        assertEquals("旧消息", dao().messages().single().content)
        assertEquals("[{\"role\":\"user\",\"content\":\"你好\"}]", dao().contextCheckpoints().single().journalJson)
        assertEquals("legacy", dao().state()?.selectedConversationId)

        val out2 = ByteArrayOutputStream()
        EtaBackupRepository.export(context, out2)
        val second = out2.toString(Charsets.UTF_8.name())
        assertEquals(6, Json.parseToJsonElement(second).jsonObject.getValue(sk).jsonPrimitive.int)
        freshDatabase()
        EtaBackupRepository.import(context, ByteArrayInputStream(out2.toByteArray()))
        val again = dao().conversationEntities().single()
        assertNotNull(again)
        assertNull(again.archivedAt)
        assertEquals("旧会话", again.title)
        assertEquals("旧消息", dao().messages().single().content)
    }
}
