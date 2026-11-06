package io.github.mangi.eta.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import io.github.mangi.eta.agent.runtime.*
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.ui.app.CommonTasksStore
import io.github.mangi.eta.ui.model.*
import io.github.mangi.eta.ui.screens.tasks.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.basic.Text

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "zh-rCN-w393dp-h851dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Stage8SavedTaskTest {
    @Test fun s10_4_savedTaskButtonsAlignAndLastMenuClearsCreateButton() {
        val differences = mutableListOf<Float>()
        for (dark in listOf(false, true)) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        // Seed the existing file format once; this test exercises touch/UI, not AtomicFile replacement.
        val savedTasks = JSONArray()
        repeat(12) { savedTasks.put(JSONObject().put("id", "saved-$it").put("name", "Saved task $it")
            .put("instruction", "Read the notes for task $it").put("shortcutToken", UUID.randomUUID().toString())) }
        File(activity.get().applicationContext.filesDir, "common_tasks.json")
            .writeText(JSONObject().put("version", 1).put("tasks", savedTasks).toString())
        val store = CommonTasksStore(activity.get(), scope)
        store.refresh()
        val panel = Stage3TestPanel(activity.get(), dark) { EtaAppOverlayHost { CommonTasksScreen(store, {}, {}, embedded = true) } }
        val label = activity.get().getString(R.string.common_tasks_create)
        fun button() = panel.nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true }
        try {
            repeat(100) { if (store.busy) { Thread.sleep(5); panel.advance() } }
            panel.advance(20); assertEquals(12, store.tasks.size)
            fun clickable(node: SemanticsNode): SemanticsNode = if (node.config.getOrNull(SemanticsActions.OnClick) != null) node else clickable(requireNotNull(node.parent))
            fun menu() = panel.nodes().filter { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(activity.get().getString(R.string.common_tasks_manage)) == true }.map(::clickable)
            val run = panel.nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == activity.get().getString(R.string.common_tasks_run) } == true }
            differences += kotlin.math.abs(clickable(run).boundsInRoot.center.y - menu().first().boundsInRoot.center.y) / activity.get().resources.displayMetrics.density
            panel.capture("stage8-saved-${if (dark) "dark" else "light"}")
            repeat(10) { panel.dragFinger() }
            val last = menu().last().boundsInRoot
            assertTrue("last menu must remain above create button", last.bottom < button().boundsInRoot.top)
            panel.capture("stage8-saved-bottom-${if (dark) "dark" else "light"}")
        } finally { panel.close(); scope.cancel(); activity.pause().stop().destroy() }
        }
        assertTrue("run/menu vertical center differences=$differences dp", differences.all { it <= 1f })
    }
}
