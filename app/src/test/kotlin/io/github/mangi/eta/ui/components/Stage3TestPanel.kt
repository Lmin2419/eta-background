package io.github.mangi.eta.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.*
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.ui.app.AgentAppTheme
import io.github.mangi.eta.ui.theme.EtaMotion
import io.github.mangi.eta.ui.theme.EtaTheme
import java.io.File
import java.time.Duration
import kotlinx.coroutines.*
import org.robolectric.Shadows.shadowOf
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled

/** Uses the same native rendering and frame clock for the before/after comparison. */
internal class Stage3TestPanel(activity: ComponentActivity, dark: Boolean = false, fontScale: Float = 1f, reduceMotion: Boolean = true, content: @Composable () -> Unit) {
    private val clock = BroadcastFrameClock()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + clock)
    private val recomposer = Recomposer(scope.coroutineContext)
    private val view = ComposeView(activity)
    init {
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        view.setParentCompositionContext(recomposer)
        view.setContent {
            AgentAppTheme(AppearanceSettings(themeMode = if (dark) io.github.mangi.eta.data.model.AppearanceThemeMode.DARK else io.github.mangi.eta.data.model.AppearanceThemeMode.LIGHT), false) {
                EtaTheme(colors = EtaTheme.colors, motion = EtaMotion(reduceMotion)) {
                    CompositionLocalProvider(LocalSquircleEnabled provides false,
                        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(androidx.compose.ui.platform.LocalDensity.current.density, fontScale)) {
                        Box(Modifier.fillMaxSize()) { content() }
                    }
                }
            }
        }
        activity.setContentView(view)
    }
    private fun findRoot(node: View): ViewRootForTest? {
        if (node is ViewRootForTest) return node
        if (node is android.view.ViewGroup) for (i in 0 until node.childCount) findRoot(node.getChildAt(i))?.let { return it }
        return null
    }
    fun root(): ViewRootForTest = findRoot(view) ?: error("No Compose root")
    fun nodes(): List<SemanticsNode> {
        fun visit(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::visit)
        return visit(root().semanticsOwner.unmergedRootSemanticsNode)
    }
    fun advance(frames: Int = 1) = repeat(frames) {
        Snapshot.sendApplyNotifications()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        clock.sendFrame(SystemClock.uptimeMillis() * 1_000_000L)
        root().measureAndLayoutForTest()
    }
    fun bitmap(): Bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    fun draw(bitmap: Bitmap) = view.draw(Canvas(bitmap))
    fun capture(name: String) {
        val bitmap = bitmap()
        try {
            draw(bitmap)
            val target = File("build/reports/eta-stage3/$name.png")
            target.parentFile?.mkdirs()
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
    fun close() { view.disposeComposition(); recomposer.cancel(); scope.cancel() }
    fun touch(action: Int, x: Float, y: Float, downTime: Long = SystemClock.uptimeMillis()) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        (root() as View).dispatchTouchEvent(event)
        event.recycle()
    }
    fun click(node: SemanticsNode) {
        var target: SemanticsNode? = node
        while (target?.config?.getOrNull(SemanticsActions.OnClick) == null) target = target?.parent ?: error("No click action")
        target.config[SemanticsActions.OnClick].action!!.invoke()
        advance(2)
    }
}
