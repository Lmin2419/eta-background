package io.github.mangi.eta.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.*
import io.github.mangi.eta.agent.voice.*
import io.github.mangi.eta.ui.theme.EtaTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

/** Native mdpi snapshots also expose whether the original cat is legible at only 24 physical pixels. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "zh-rCN-w411dp-h896dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrandPolishRenderingTest {
    @Test fun warmSurfacesAndSmallCatInBothThemes() {
        for (dark in listOf(false, true)) {
            val owner = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            val panel = Stage3TestPanel(owner.get(), dark = dark) {
                Column(Modifier.fillMaxSize().background(EtaTheme.colors.canvas).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("胖小胖 · ${if (dark) "深色" else "浅色"}", style = EtaTheme.type.title2, color = EtaTheme.colors.textPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        EtaBrandMark(Modifier.size(48.dp))
                        EtaBrandMark(Modifier.size(24.dp))
                        Icon(painterResource(R.drawable.ic_notification), "剪影对照", Modifier.size(24.dp), tint = EtaTheme.colors.accent)
                        Text("48dp / 24dp / 剪影", color = EtaTheme.colors.textSecondary)
                    }
                    AgentOverlayCapsule(AgentOverlayState.Initial.copy(detailText = "正在查看当前页面"),
                        expanded = true, yielded = false, onToggleExpanded = {}, onCollapsedSettled = {},
                        onPause = {}, onResume = {}, onStop = {}, onSupplementModeChange = {}, onSupplement = {})
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(AgentOverlayPhase.RUNNING to "执行", AgentOverlayPhase.PAUSED to "暂停",
                            AgentOverlayPhase.FINISHED to "完成", AgentOverlayPhase.FAILED to "失败").forEach { (phase, label) ->
                            Text(label, color = overlayPhaseAccent(phase))
                        }
                    }
                    EtaSpeechFeedback(EtaSpeechState(errorRes = R.string.voice_speech_network_error), {}, {})
                    EtaSpeechFeedback(EtaSpeechState(message = "语音模型准备就绪", feedbackIsError = false), {}, {})
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround) {
                        Text("语音波形", color = EtaTheme.colors.textSecondary)
                        EtaAssistantWave(true, { .65f }, Modifier.size(80.dp), ring = true)
                    }
                    Box(Modifier.fillMaxWidth().height(140.dp).background(EtaTheme.colors.surface)) {
                        Text("助手边缘流光", Modifier.padding(16.dp), color = EtaTheme.colors.textSecondary)
                        EtaAssistantEdgeGlow(true, {})
                    }
                    Box(Modifier.fillMaxWidth().height(140.dp).background(EtaTheme.colors.surface)) {
                        Text("任务边缘流光", Modifier.padding(16.dp), color = EtaTheme.colors.textSecondary)
                        AgentOverlayGlow(AgentOverlayState.Initial, ScreenCornerRadii(20f, 20f, 20f, 20f))
                    }
                }
            }
            try {
                panel.advance(60)
                panel.capture("brand-${if (dark) "dark" else "light"}")
            } finally { panel.close(); owner.pause().stop().destroy() }
        }
    }
}
