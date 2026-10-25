package io.github.mangi.eta.data.db

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChunkedTextInputStreamTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        EtaDatabase.closeForTests()
        context.deleteDatabase("eta.db")
    }

    @Test
    fun streamsUtf8AcrossSurrogateAndChunkBoundaries() = runBlocking(Dispatchers.IO) {
        val dao = EtaDatabase.get(context).conversationDao()
        val text = "前".repeat(16_383) + "😀" + "后".repeat(20_000)
        val stored = dao.storeText("conversations", "test", "roleplay", text)

        val decoded = dao.openTextStream("conversations", "test", "roleplay", stored).bufferedReader().use { it.readText() }

        assertEquals(text, decoded)
    }

    @Test
    fun missingChunksFailInsteadOfReturningAPartialPayload() = runBlocking<Unit>(Dispatchers.IO) {
        val dao = EtaDatabase.get(context).conversationDao()
        val stored = dao.storeText("conversations", "test", "roleplay", "x".repeat(40_000))
        EtaDatabase.get(context).openHelper.writableDatabase.execSQL(
            "DELETE FROM agent_text_chunks WHERE owner_id = 'test' AND chunk_index = 1",
        )

        assertThrows(IllegalStateException::class.java) {
            dao.openTextStream("conversations", "test", "roleplay", stored).use { it.readBytes() }
        }
    }
}
