package io.github.mangi.eta.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.model.AppearanceThemeMode
import io.github.mangi.eta.ui.app.AgentAppTheme
import io.github.mangi.eta.ui.theme.EtaMotion
import io.github.mangi.eta.ui.theme.EtaTheme
import java.io.File
import java.time.Duration
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled

/** The same fixtures are captured before and after stage 2; these are component samples, not device pages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EtaComponentsRenderingTest {
    @Test fun preferenceControlsRenderInBothThemesAndLargeText() = withPanel { panel ->
        listOf(false, true).forEach { dark ->
            panel.dark.value = dark
            panel.advance()
            val texts = panel.nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
            listOf("Service providers", "Voice playback", "Appearance", "Save").forEach { assertTrue(it, it in texts) }
            panel.capture(if (dark) "dark" else "light")
        }
        panel.dark.value = false
        panel.fontScale.value = 2f
        panel.advance()
        panel.capture("font-200")
    }

    @Test fun newControlsAndOverlaysRenderInBothThemes() = withPanel { panel ->
        panel.gallery.value = true
        listOf(false, true).forEach { dark ->
            panel.dark.value = dark
            panel.advance()
            panel.capture(if (dark) "new-dark" else "new-light")
            panel.dialog.value = true
            panel.advance()
            panel.capture(if (dark) "dialog-dark" else "dialog-light")
            panel.dialog.value = false
            panel.sheet.value = true
            panel.advance()
            panel.capture(if (dark) "sheet-dark" else "sheet-light")
            panel.sheet.value = false
        }
    }

    @Test
    @Config(qualifiers = "w840dp-h1000dp-xhdpi")
    fun preferencesAlsoRenderWithCompactWideHeader() = withPanel { panel ->
        panel.capture("wide-light")
        panel.dark.value = true
        panel.advance()
        panel.capture("wide-dark")
    }

    private fun withPanel(block: (Panel) -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Panel(controller.get())
        try { panel.advance(); block(panel) }
        finally { panel.close(); controller.pause().stop().destroy() }
    }

    private class Panel(activity: ComponentActivity) {
        val dark = mutableStateOf(false)
        val gallery = mutableStateOf(false)
        val dialog = mutableStateOf(false)
        val sheet = mutableStateOf(false)
        val fontScale = mutableStateOf(1f)
        private val clock = BroadcastFrameClock()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + clock)
        private val recomposer = Recomposer(scope.coroutineContext)
        private val view = ComposeView(activity)
        init {
            scope.launch { recomposer.runRecomposeAndApplyChanges() }
            view.setParentCompositionContext(recomposer)
            view.setContent {
                AgentAppTheme(AppearanceSettings(themeMode = if (dark.value) AppearanceThemeMode.DARK else AppearanceThemeMode.LIGHT), false) {
                    EtaTheme(colors = EtaTheme.colors, motion = EtaMotion(true)) {
                        CompositionLocalProvider(LocalSquircleEnabled provides false,
                            LocalDensity provides Density(LocalDensity.current.density, fontScale.value)) {
                            if (gallery.value) {
                                EtaExtraSamples(dialog.value, sheet.value)
                                return@CompositionLocalProvider
                            }
                            Column(Modifier.fillMaxSize().background(EtaTheme.colors.canvas)) {
                                AdaptiveTopAppBar(title = "Settings", navigationIcon = { MiuixBackButton({}) })
                                EtaPreferenceGroupTitle("General")
                                EtaPreferenceGroup {
                                    EtaArrowPreference("Service providers", summary = "Models and connections",
                                        startAction = { EtaPreferenceIcon(Icons.Outlined.Cloud, tint = EtaPreferenceColors.Blue) }, onClick = {})
                                    EtaPreferenceDivider()
                                    EtaSwitchPreference("Voice playback", checked = true, onCheckedChange = {},
                                        startAction = { EtaPreferenceIcon(Icons.Outlined.Extension, tint = EtaPreferenceColors.Green) })
                                    EtaPreferenceDivider()
                                    EtaWindowSpinnerPreference("Appearance", items = listOf("System", "Light", "Dark").map { top.yukonga.miuix.kmp.basic.DropdownItem(text = it) },
                                        selectedIndex = 0, onSelectedIndexChange = {},
                                        startAction = { EtaPreferenceIcon(Icons.Outlined.Palette, tint = EtaPreferenceColors.Orange) })
                                }
                                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    EtaChoiceChip("All", true, {})
                                    EtaChoiceChip("Installed", false, {})
                                }
                                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    EtaButton("Save", {})
                                    EtaButton("Disabled", {}, enabled = false)
                                }
                                ListEmptyState("No skills yet", summary = "Add a skill to get started", icon = Icons.Outlined.Extension)
                            }
                        }
                    }
                }
            }
            activity.setContentView(view)
        }
        private fun root(): ViewRootForTest = findRoot(view) ?: error("No Compose root")
        private fun findRoot(view: View): ViewRootForTest? {
            if (view is ViewRootForTest) return view
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) findRoot(view.getChildAt(i))?.let { return it }
            return null
        }
        fun nodes(): List<SemanticsNode> {
            fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
            return all(root().semanticsOwner.unmergedRootSemanticsNode)
        }
        fun advance() = repeat(30) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            clock.sendFrame(SystemClock.uptimeMillis() * 1_000_000L)
            root().measureAndLayoutForTest()
        }
        fun capture(name: String) {
            assertTrue(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val stage = System.getenv("ETA_PREVIEW_STAGE") ?: "after"
            val target = File("build/reports/eta-stage2/$stage-$name.png")
            requireNotNull(target.parentFile).mkdirs()
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        fun close() { recomposer.cancel(); scope.cancel() }
    }
}


@androidx.compose.runtime.Composable
private fun EtaExtraSamples(dialog: Boolean, sheet: Boolean) {
    top.yukonga.miuix.kmp.basic.Scaffold {
        Column(Modifier.fillMaxSize().background(EtaTheme.colors.canvas)) {
            AdaptiveTopAppBar("Components", compact = true, navigationIcon = { MiuixBackButton({}) })
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                EtaSearchField("Model", {}, "Search models")
                EtaSegmented(listOf("Configuration", "Models"), 1, {})
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EtaButton("Save", {}, kind = EtaButtonKind.Primary)
                    EtaButton("Import", {}, kind = EtaButtonKind.Tonal)
                    EtaButton("Cancel", {}, kind = EtaButtonKind.Ghost)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EtaButton("Secondary", {}, kind = EtaButtonKind.Secondary)
                    EtaButton("Delete", {}, kind = EtaButtonKind.Destructive)
                    EtaButton("Busy", {}, loading = true)
                }
                EtaListGroup(title = "Connections") {
                    EtaArrowPreference("Provider", summary = "Configuration",
                        startAction = { EtaIconTile(Icons.Outlined.Cloud) }, onClick = {})
                    EtaPreferenceDivider()
                    EtaCheckboxPreference("Use connection", true, {})
                }
                EtaSkeleton()
                EtaToast("Changes saved", {}, actionLabel = "Undo", onAction = {})
            }
        }
        EtaOverlayDialog(dialog, title = "Remove provider?", summary = "This changes the saved provider configuration.",
            onDismissRequest = {}) {
            MiuixDialogActions("Remove", {}, {}, destructive = true)
        }
        EtaSheet(sheet, title = "Import models", onDismissRequest = {}) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EtaSearchField("", {}, "Search models")
                EtaCheckboxPreference("Example model", true, {})
                EtaButton("Import selected", {}, Modifier.fillMaxWidth(), kind = EtaButtonKind.Primary)
            }
        }
    }
}
