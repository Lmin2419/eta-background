package io.github.mangi.eta.ui.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import io.github.mangi.eta.data.model.AppearanceControlMaterial
import io.github.mangi.eta.ui.components.Stage3TestPanel
import io.github.mangi.eta.ui.components.coui.EtaBackdrop
import io.github.mangi.eta.ui.components.coui.rememberEtaBackdrop
import io.github.mangi.eta.ui.components.coui.etaBackdrop
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 14.4c：外观设置里的「控件材质」决定浮动控件的采样源——液态玻璃记录旧库的采样源，COUI 17 磨砂记录新库
 * coui-blur 的图层，两者只记一份；默认是液态玻璃，存取不丢。
 * （模糊本身要着色器，本机渲染器编译不了，效果在真机上看。）
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h896dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ControlMaterialDeviceModeTest {

    private fun backdropFor(material: AppearanceControlMaterial): EtaBackdrop {
        var captured: EtaBackdrop? = null
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Stage3TestPanel(controller.get()) {
            CompositionLocalProvider(LocalControlMaterial provides material) {
                val backdrop = rememberEtaBackdrop("material-test")
                SideEffect { captured = backdrop }
            }
        }
        try {
            panel.advance(5)
        } finally {
            panel.close()
            controller.pause().stop().destroy()
        }
        return requireNotNull(captured)
    }

    @Test fun frostRecordsTheNewLibraryLayerOnly() {
        val backdrop = backdropFor(AppearanceControlMaterial.COUI_FROST)
        assertNotNull(backdrop.coui)
        assertNull(backdrop.legacy)
    }

    @Test fun liquidGlassRecordsTheLegacySourceOnly() {
        val backdrop = backdropFor(AppearanceControlMaterial.LIQUID_GLASS)
        assertNotNull(backdrop.legacy)
        assertNull(backdrop.coui)
    }

    @Test fun defaultIsLiquidGlassAndValuesRoundTrip() {
        assertEquals(AppearanceControlMaterial.LIQUID_GLASS, io.github.mangi.eta.data.model.AppearanceSettings().controlMaterial)
        AppearanceControlMaterial.entries.forEach {
            assertEquals(it, AppearanceControlMaterial.fromPersistedValue(it.persistedValue))
        }
        assertEquals(AppearanceControlMaterial.LIQUID_GLASS, AppearanceControlMaterial.fromPersistedValue("unknown"))
    }

    @Test fun frostedSurfaceDrawsAndClicks() {
        var clicks = 0
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val panel = Stage3TestPanel(controller.get()) {
            CompositionLocalProvider(LocalControlMaterial provides AppearanceControlMaterial.COUI_FROST) {
                val backdrop = rememberEtaBackdrop("material-test")
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().etaBackdrop(backdrop)) {
                    io.github.mangi.eta.ui.components.coui.EtaFloatingSurface(
                        modifier = androidx.compose.ui.Modifier.size(44.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        capsule = false,
                        contentDescription = "frost-button",
                        onClick = { clicks++ },
                        backdrop = backdrop,
                    ) {}
                }
            }
        }
        try {
            panel.advance(5)
            val node = panel.nodes().first {
                it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.contains("frost-button") == true
            }
            assertEquals(88f, node.boundsInRoot.width, 1f)
            node.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!.invoke()
            panel.advance(5)
            assertEquals(1, clicks)
        } finally {
            panel.close()
            controller.pause().stop().destroy()
        }
    }
}
