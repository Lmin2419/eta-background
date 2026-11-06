package io.github.mangi.eta.ui.components

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.*
import io.github.mangi.eta.ui.screens.permissions.PermissionHealthScreen
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h896dp-xhdpi")
class Stage5PermissionHealthTest {
    private lateinit var activity: ComponentActivity
    private lateinit var controller: org.robolectric.android.controller.ActivityController<ComponentActivity>
    private var panel: Stage3TestPanel? = null
    private val actions = mutableListOf<PermissionHealthAction>()

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity = controller.get()
    }

    @After
    fun tearDown() {
        panel?.close()
        panel = null
        controller.pause().stop().destroy()
    }

    @Test
    fun readinessItems_unknownNotAllGreen_fiveCoreReadyOrEngineMissing() {
        val unknown = readinessItems(PermissionHealthUiState(emptyList()), null)
        assertEquals(5, unknown.size)
        assertTrue(unknown.all { it.status != PermissionStatusUi.Available })
        assertTrue(unknown.first { it.id == "engine" }.checking)

        val core = listOf("root", "accessibility", "overlay", "notifications")
        val items = core.map { PermissionHealthItemUi(it, it, "", PermissionStatusUi.Available, null) }
        val ready = readinessItems(PermissionHealthUiState(items), true)
        assertTrue(ready.all { it.status == PermissionStatusUi.Available })
        assertTrue(ready.none { it.checking })

        val missingEngine = readinessItems(PermissionHealthUiState(items), false)
        assertEquals(PermissionStatusUi.Missing, missingEngine.first { it.id == "engine" }.status)
        assertFalse(missingEngine.first { it.id == "engine" }.checking)
    }

    @Test
    fun rememberEngineReadiness_lifecycle() {
        val probe = ControllableProbe()
        val owner = activity
        var ready: Boolean? = null
        var state by mutableStateOf(PermissionHealthUiState(emptyList()))
        var refresh by mutableIntStateOf(0)
        panel = Stage3TestPanel(activity) {
            CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalEngineReadinessProbe provides probe) {
                ready = rememberEngineReadiness(state, refresh)
            }
        }
        val p = panel!!
        p.advance(3)
        assertEquals(0, probe.calls)

        state = PermissionHealthUiState(listOf(PermissionHealthItemUi("root", "root", "", PermissionStatusUi.Disabled, null)))
        p.advance(3)
        assertEquals(0, probe.calls)

        state = PermissionHealthUiState(listOf(PermissionHealthItemUi("root", "root", "", PermissionStatusUi.Available, null)))
        p.advance(3)
        assertEquals(1, probe.calls)

        controller.pause()
        p.advance(3)
        probe.pending[0].complete(true)
        p.advance(3)
        assertNull(ready)

        controller.resume()
        p.advance(3)
        assertEquals(2, probe.calls)

        p.advance(30)
        assertEquals(2, probe.calls)

        refresh++
        p.advance(3)
        assertEquals(3, probe.calls)

        probe.pending[1].complete(true)
        p.advance(3)
        assertNull(ready)

        probe.pending[2].complete(false)
        p.advance(3)
        assertEquals(false, ready)
    }

    @Test
    fun permissionHealthScreen_keepsIds_prioritizesMissing_routesOriginalIds_byFinger() {
        val missing = "location"
        val state = allIdsState(setOf(missing))
        val probe = EngineReadinessProbe { false }
        val owner = activity
        panel = Stage3TestPanel(activity) {
            CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalEngineReadinessProbe provides probe) {
                PermissionHealthScreen(state, onAction = { actions.add(it) })
            }
        }
        val p = panel!!
        p.advance(30)

        val readinessTitle = activity.getString(R.string.eta_background_ready)
        val readinessNode = findNode(readinessTitle, p) ?: error("no readiness")
        val expected = expectedTitles(activity)
        val visibleRows = p.nodes().mapNotNull { node ->
            val texts = node.config.getOrNull(SemanticsProperties.Text) ?: return@mapNotNull null
            val title = texts.map { it.text }.firstOrNull { it in expected && it.startsWith("uid_") } ?: return@mapNotNull null
            if (node.boundsInRoot.height <= 0f || node.boundsInRoot.center.y !in 150f..1700f) return@mapNotNull null
            if (node.boundsInRoot.center.y <= readinessNode.boundsInRoot.center.y) return@mapNotNull null
            title to node.boundsInRoot.center.y
        }.sortedBy { it.second }
        assertEquals("uid_location", visibleRows.first().first)

        actions.clear()
        val button = scrollToText(p, "enable_location") ?: error("no location button")
        p.tapFinger(button.boundsInRoot.center.x, button.boundsInRoot.center.y)
        p.advance(3)
        assertEquals(listOf("location"), actions.filterIsInstance<PermissionHealthAction.OpenItemAction>().map { it.itemId })

        p.capture("permissions-stage5-light")
        val found = collectTitles(p, expected)
        assertTrue(found.containsAll(expected))

        actions.clear()
        val rootNode = scrollToText(p, "uid_root") ?: error("no root")
        assertNull(findNode("enable_root", p))
        p.tapFinger(rootNode.boundsInRoot.center.x, rootNode.boundsInRoot.center.y)
        p.advance(3)
        assertEquals(listOf("root"), actions.filterIsInstance<PermissionHealthAction.OpenItemAction>().map { it.itemId })
    }

    @Test
    fun dark200_scrollToEndAndCapture() {
        val state = allIdsState(setOf("location", "calendar", "notification_policy"))
        val probe = EngineReadinessProbe { false }
        val owner = activity
        panel = Stage3TestPanel(activity, dark = true, fontScale = 2f) {
            CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalEngineReadinessProbe provides probe) {
                PermissionHealthScreen(state, onAction = {})
            }
        }
        val p = panel!!
        p.advance(30)
        p.capture("permissions-stage5-dark-200-top")
        assertNotNull(scrollToText(p, "uid_root"))
        p.capture("stage5_permission_dark_200")
    }

    private fun allIdsState(missing: Set<String>): PermissionHealthUiState {
        val ids = listOf("background", "overlay", "app_list", "local_network", "calendar", "notification_policy", "location", "notification_history", "usage_access", "accessibility", "notifications", "root")
        return PermissionHealthUiState(ids.map { id ->
            PermissionHealthItemUi(id, "uid_$id", "summary", if (id in missing) PermissionStatusUi.Missing else PermissionStatusUi.Available, "enable_$id")
        })
    }

    private fun expectedTitles(context: Context): Set<String> {
        val ids = listOf("background", "overlay", "app_list", "local_network", "calendar", "notification_policy", "location", "notification_history", "usage_access", "accessibility", "notifications", "root")
        return ids.map { id ->
            when (id) {
                "calendar" -> context.getString(R.string.eta_permission_calendar)
                "notification_policy" -> context.getString(R.string.eta_permission_dnd)
                else -> "uid_$id"
            }
        }.toSet() + context.getString(R.string.eta_ready_engine)
    }

    private fun findNode(text: String, panel: Stage3TestPanel): SemanticsNode? =
        panel.nodes().firstOrNull { node ->
            val texts = node.config.getOrNull(SemanticsProperties.Text)
            texts?.any { it.text == text } == true && node.boundsInRoot.height > 0f && node.boundsInRoot.center.y in 150f..1700f
        }

    private fun scrollToText(panel: Stage3TestPanel, text: String): SemanticsNode? {
        repeat(20) {
            findNode(text, panel)?.let { return it }
            panel.dragFinger(810f, 1300f, 810f, 700f, 30)
            panel.advance(1)
        }
        return findNode(text, panel)
    }

    private fun collectTitles(panel: Stage3TestPanel, expected: Set<String>): Set<String> {
        val found = mutableSetOf<String>()
        repeat(20) {
            panel.nodes().forEach { node ->
                val texts = node.config.getOrNull(SemanticsProperties.Text) ?: return@forEach
                val y = node.boundsInRoot.center.y
                if (node.boundsInRoot.height <= 0f || y < 150f || y > 1700f) return@forEach
                texts.forEach { if (it.text in expected) found.add(it.text) }
            }
            if (found.containsAll(expected)) return found
            panel.dragFinger(810f, 1300f, 810f, 700f, 30)
            panel.advance(1)
        }
        return found
    }

    private class ControllableProbe : EngineReadinessProbe {
        var calls = 0
        val pending = mutableListOf<CompletableDeferred<Boolean>>()
        override suspend fun read(context: Context): Boolean {
            calls++
            val d = CompletableDeferred<Boolean>()
            pending.add(d)
            return d.await()
        }
    }

}
