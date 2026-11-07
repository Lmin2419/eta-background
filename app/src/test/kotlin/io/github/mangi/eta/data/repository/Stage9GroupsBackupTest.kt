package io.github.mangi.eta.data.repository

import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.db.*
import io.github.mangi.eta.data.model.*
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Stage9GroupsBackupTest {
    @get:Rule val settingsStore = Stage9SettingsStoreRule()
    private val context get() = RuntimeEnvironment.getApplication()
    private val dao get() = EtaDatabase.get(context).conversationDao()
    private val roleSettings = SubagentSettings(modelId = "missing-default", profiles = mapOf("coder" to SubagentProfileModel("missing-role", "high")))
    @Before fun init() { SettingsDataStore.init(context); ProviderRepository.init(context); AgentMemoryRepository.init(context) }
    private suspend fun seed() {
        dao.insertConversationGroups(listOf(ConversationGroupEntity("work", "工作", 0, false, 1), ConversationGroupEntity("learn", "学习", 1, true, 2)))
        dao.insertConversations(listOf(
            ConversationEntity("c1", "旧工作", false, createdAt = 1, updatedAt = 2, pinnedAt = 8, groupId = "work"),
            ConversationEntity("c2", "已归档", false, createdAt = 2, updatedAt = 3, archivedAt = 9, groupId = "learn")))
        dao.insertMessages(listOf(ConversationMessageEntity("m1", "c1", 0, "user", "完整消息")))
        dao.insertContextCheckpoints(listOf(ConversationContextCheckpointEntity("c1", "[]")))
        SettingsDataStore.setSelection(null, null)
        SettingsDataStore.setSubagentSettings(roleSettings)
    }
    private suspend fun exported(): JsonObject = ByteArrayOutputStream().also { EtaBackupRepository.export(context, it) }
        .toString("UTF-8").let { Json.parseToJsonElement(it).jsonObject }
    private suspend fun restore(root: JsonObject) = EtaBackupRepository.import(context, root.toString().byteInputStream())
    @Test fun v6RoundTripPreservesGroupsPinsCollapseAndRoleModelsIncludingMissingModelIds() = runBlocking {
        seed(); val before = dao.conversationEntities(); val groups = dao.conversationGroups()
        val root = exported()
        assertEquals(6, root.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals(2, root.getValue("conversationGroups").jsonArray.size)
        assertNotNull(root["subagentSettings"])
        dao.clearConversationGroups(); SettingsDataStore.setSubagentSettings(SubagentSettings())
        restore(root)
        assertEquals(before, dao.conversationEntities()); assertEquals(groups, dao.conversationGroups())
        assertEquals(roleSettings, SettingsDataStore.subagentSettings()); assertEquals("完整消息", dao.messages().single().content)
    }
    @Test fun v5ImportHasNoPinsOrGroupsAndKeepsCurrentDeviceSubagentSettings() = runBlocking {
        seed(); val root = exported()
        val old = JsonObject(root.toMutableMap().apply {
            put("schemaVersion", JsonPrimitive(5)); remove("conversationGroups"); remove("subagentSettings")
            put("conversations", JsonArray(root.getValue("conversations").jsonArray.map { row ->
                JsonObject(row.jsonObject.filterKeys { it !in setOf("pinned_at", "group_id") })
            }))
        })
        restore(old)
        assertTrue(dao.conversationGroups().isEmpty())
        dao.conversationEntities().forEach { assertNull(it.pinnedAt); assertNull(it.groupId) }
        assertEquals(roleSettings, SettingsDataStore.subagentSettings())
        assertEquals(9L, dao.conversation("c2")?.archivedAt)
    }
    @Test fun missingGroupReferenceIsClearedButOtherConversationDataRemains() = runBlocking {
        seed(); val root = exported()
        val modified = JsonObject(root.toMutableMap().apply {
            put("schemaVersion", JsonPrimitive(6))
            put("conversations", JsonArray(root.getValue("conversations").jsonArray.map { row ->
                JsonObject(row.jsonObject.toMutableMap().apply { put("group_id", JsonPrimitive("missing")) })
            }))
        })
        restore(modified)
        dao.conversationEntities().forEach { assertNull(it.groupId) }
        assertEquals(8L, dao.conversation("c1")?.pinnedAt); assertEquals("完整消息", dao.messages().single().content)
    }
    @Test fun removingAGroupClearsAllReferencesWithoutDeletingMessagesOrChangingDates() = runBlocking {
        seed(); dao.updateGroupId("c2", "work")
        dao.deleteConversationGroup("work")
        dao.conversationEntities().forEach { assertNull(it.groupId) }
        assertEquals(listOf("learn"), dao.conversationGroups().map { it.id })
        assertEquals(2L, dao.conversation("c1")?.updatedAt); assertEquals(3L, dao.conversation("c2")?.updatedAt)
        assertEquals(8L, dao.conversation("c1")?.pinnedAt); assertEquals(1, dao.messages().size)
    }
    @Test fun databaseFailureRollsBackGroupsConversationsAndKeepsRoleSettings() = runBlocking {
        seed(); val before = dao.conversationEntities(); val groups = dao.conversationGroups()
        val root = JsonObject(exported().toMutableMap().apply { put("schemaVersion", JsonPrimitive(6)) })
        EtaDatabase.get(context).openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_import BEFORE INSERT ON conversation_messages BEGIN SELECT RAISE(ABORT,'fixture'); END")
        val failure = runCatching { restore(root) }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(generateSequence(failure) { it.cause }.any { it.message.orEmpty().contains("fixture") })
        assertEquals(before, dao.conversationEntities()); assertEquals(groups, dao.conversationGroups())
        assertEquals(roleSettings, SettingsDataStore.subagentSettings())
    }
}
