package io.github.mangi.eta.ui.navigation

import android.app.Application
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AgentNavigationRestorationTest {
    @Test
    fun `Activity 重建后保留完整页面栈`() {
        val first = Robolectric.buildActivity(NavigationActivity::class.java).setup().visible()
        first.get().compose()
        first.get().backStack.add(AppRoute.Settings)
        first.get().backStack.add(AppRoute.ModelProviderDetail("provider"))
        val savedState = Bundle()
        first.pause().saveInstanceState(savedState).stop().destroy()

        val restored = Robolectric.buildActivity(NavigationActivity::class.java)
            .create(savedState).start().resume().visible()
        try {
            restored.get().compose()

            assertEquals(
                listOf(AppRoute.Home, AppRoute.Settings, AppRoute.ModelProviderDetail("provider")),
                restored.get().backStack.toList(),
            )
        } finally {
            restored.pause().stop().destroy()
        }
    }

    class NavigationActivity : ComponentActivity() {
        lateinit var backStack: NavBackStack
        private lateinit var composeView: ComposeView

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            composeView = ComposeView(this)
            setContentView(composeView)
            composeView.setContent {
                backStack = rememberNavBackStack<AppRoute>(AppRoute.Home)
            }
        }

        fun compose() {
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
            composeView.measure(
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
            )
            composeView.layout(0, 0, 320, 640)
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
        }
    }
}
