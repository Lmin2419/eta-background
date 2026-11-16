package io.github.mangi.eta.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 导航：整页走 [backStack]；设置类路由（[AppRoutePresentation.Window]）压进 [windowStack]，
 * 在当前页面上方的玻璃窗口里打开，不离开对话。从窗口里打开整页时先播完窗口关闭动画再进整页。
 */
class AgentNavigator(
    val backStack: NavBackStack,
    val windowStack: NavBackStack? = null,
) {
    /** 窗口正在播放关闭动画；播完由宿主调用 [onWindowClosed]。 */
    var windowClosing by mutableStateOf(false)
        private set
    private var pendingPage: AppRoute? = null

    val windowOpen: Boolean get() = windowStack?.isNotEmpty() == true

    fun push(route: AppRoute) {
        val windows = windowStack
        if (windows != null && route.presentation == AppRoutePresentation.Window) {
            windowClosing = false
            pendingPage = null
            if (route !in windows) windows.add(route)
            return
        }
        if (windows != null && windows.isNotEmpty()) {
            pendingPage = route
            windowClosing = true
            return
        }
        if (route !in backStack) {
            backStack.add(route)
        }
    }

    fun replace(route: AppRoute) {
        val windows = windowStack
        val stack = if (windows != null && windows.isNotEmpty() && route.presentation == AppRoutePresentation.Window) windows else backStack
        val existingIndex = stack.indexOf(route)
        if (existingIndex >= 0) {
            while (stack.lastIndex > existingIndex) {
                stack.removeLastOrNull()
            }
            return
        }

        if (stack.isNotEmpty()) {
            stack[stack.lastIndex] = route
        } else {
            stack.add(route)
        }
    }

    fun pop(): Boolean {
        val windows = windowStack
        if (windows != null && windows.isNotEmpty()) {
            if (windows.size > 1) windows.removeLastOrNull() else windowClosing = true
            return true
        }
        if (backStack.size <= 1) return false
        backStack.removeLastOrNull()
        return true
    }

    /** 关掉所有窗口（播放关闭动画）。 */
    fun closeWindows() {
        if (windowOpen) windowClosing = true
    }

    /** 窗口关闭动画播完：清空窗口栈，有待进入的整页就进去。 */
    fun onWindowClosed() {
        windowStack?.clear()
        windowClosing = false
        pendingPage?.let { page ->
            pendingPage = null
            if (page !in backStack) backStack.add(page)
        }
    }

    fun popToHome() {
        windowStack?.clear()
        windowClosing = false
        pendingPage = null
        while (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
        backStack.firstOrNull()?.let { first ->
            if (first !is AppRoute.Home) {
                backStack.clear()
                backStack.add(AppRoute.Home)
            }
        }
    }

    fun current(): NavKey? = windowStack?.lastOrNull() ?: backStack.lastOrNull()
}
