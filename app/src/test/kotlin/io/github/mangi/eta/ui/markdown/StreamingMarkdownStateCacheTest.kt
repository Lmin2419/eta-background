package io.github.mangi.eta.ui.markdown

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class StreamingMarkdownStateCacheTest {
    @Test
    fun completedOffscreenTurnsDoNotAccumulateStreamingSessions() {
        val cache = StreamingMarkdownStateCache()
        repeat(200) { index ->
            val id = "answer-$index"
            val state = cache.getOrCreate(id)
            state.attachContent()
            cache.discardUnused(emptySet())
            assertSame(state, cache.states[id])

            state.detachContent()
            cache.discardUnused(emptySet())
            assertTrue(cache.states.isEmpty())
        }
    }

    @Test
    fun detachedLiveMessageKeepsItsParsingAndRevealState() {
        val cache = StreamingMarkdownStateCache()
        val state = cache.getOrCreate("live")
        state.revealedContent = "已显示的前缀"

        cache.discardUnused(setOf("live"))

        assertSame(state, cache.getOrCreate("live"))
        assertEquals("已显示的前缀", cache.states["live"]?.revealedContent)
        assertTrue(cache.unusedMessageIds(setOf("live")).isEmpty())
    }

    @Test
    fun everyMountedOwnerMustDetachBeforeACompletedMessageCanBeEvicted() {
        val cache = StreamingMarkdownStateCache()
        val state = cache.getOrCreate("visible")
        state.attachContent()
        state.attachContent()
        state.detachContent()

        cache.discardUnused(emptySet())
        assertSame(state, cache.states["visible"])

        state.detachContent()
        cache.discardUnused(emptySet())
        assertNull(cache.states["visible"])
    }

    @Test
    fun evictionHandsTheFinalDocumentToTheBoundedStaticCache() {
        val cache = StreamingMarkdownStateCache()
        val content = "**完成后的答复：缓存交接**"
        val snapshot = StreamingGfmParserSession().parse(content, isComplete = true)
        cache.getOrCreate("completed").snapshot = snapshot

        cache.discardUnused(emptySet())

        assertTrue(cache.states.isEmpty())
        assertSame(snapshot.document, staticMarkdownDocumentCache.get(content, snapshot.document.inlineStyle))
    }

    @Test
    fun anIncompleteDetachedSnapshotNeverBecomesAStaticDocument() {
        val cache = StreamingMarkdownStateCache()
        val content = "**尚未完成的快照：不进入静态缓存"
        val snapshot = StreamingGfmParserSession().parse(content, isComplete = false)
        cache.getOrCreate("unfinished").snapshot = snapshot

        cache.discardUnused(emptySet())

        assertFalse(cache.states.containsKey("unfinished"))
        assertNull(staticMarkdownDocumentCache.get(content, snapshot.document.inlineStyle))
    }

    @Test
    fun movingCompletedDocumentsToStaticRenderingStillHasABoundedHistoryCache() {
        val cache = StreamingMarkdownStateCache()
        repeat(128) { index ->
            val content = "有界文档缓存回归测试 $index"
            cache.getOrCreate("answer-$index").snapshot =
                StreamingGfmParserSession().parse(content, isComplete = true)
            cache.discardUnused(emptySet())
        }

        assertTrue(cache.states.isEmpty())
        assertNull(staticMarkdownDocumentCache.get("有界文档缓存回归测试 0", MarkdownInlineStyle.Default))
        assertNotNull(staticMarkdownDocumentCache.get("有界文档缓存回归测试 127", MarkdownInlineStyle.Default))
    }
}
