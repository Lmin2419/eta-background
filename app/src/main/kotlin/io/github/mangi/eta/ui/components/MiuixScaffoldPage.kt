package io.github.mangi.eta.ui.components

import io.github.mangi.eta.ui.theme.EtaTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.layout.rememberIsWideScreen
import io.github.mangi.eta.ui.layout.WidePageContent
import io.github.mangi.eta.ui.layout.horizontalCutoutPadding
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Eta 二级列表页的统一骨架：手机折叠大标题、宽屏固定小标题、纯色顶栏与底线、横屏安全区、
 * 宽屏内容居中、滚动边界触感与越界回弹均由此处统一提供。
 */
@Composable
fun MiuixScaffoldPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    overlay: @Composable () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    compactTitle: Boolean = false,
    // 二级页是浅灰底 + 白色卡片分组（与 Claude 的设置页一致）。
    containerColor: Color = EtaTheme.colors.surfaceSunken,
    // 传入 onRefresh 即开启下拉刷新（顶部爪印指示器）。
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    EtaPreferenceTheme {
        val pull = onRefresh?.let { rememberEtaPullRefreshState(refreshing, it) }
        // 14.4c：COUI 2.1.0 骨架（与上游 CouiScaffold 一致）——大标题随滚动收缩成顶栏小标题，内容滚到顶栏下面时
        // 顶栏是 COUI 17 的凝光模糊；宽屏或页面自带主视觉时用不收缩的小标题。
        io.github.mangi.eta.ui.components.coui.EtaCouiLazyPage(
            title = title,
            onBack = onBack,
            modifier = modifier,
            containerColor = containerColor,
            compact = compactTitle || rememberIsWideScreen(),
            listState = listState,
            actions = actions,
            extraNestedScroll = pull?.connection,
            overlay = { padding ->
                if (pull != null) EtaPullRefreshIndicator(pull, Modifier.align(androidx.compose.ui.Alignment.TopCenter),
                    topInset = padding.calculateTopPadding())
                overlay()
            },
            content = content,
        )
    }
}

/**
 * 自定义内容二级页的低层骨架。调用方负责把顶部 padding、横向安全区与 nested scroll
 * 接入自己的内容；[sidePadding] 用于在宽屏限制实际内容宽度，滚动容器本身仍应保持全宽。
 */
@Composable
fun MiuixScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (
        paddingValues: PaddingValues,
        scrollBehavior: ScrollBehavior,
        sidePadding: Dp,
    ) -> Unit,
) {
    EtaPreferenceTheme {
        // 自带内容的二级页同样用 COUI 2.1.0 骨架：不收缩的小标题，内容从顶栏下面滚过时顶栏凝光模糊。
        // 内容仍会把这个 Miuix 滚动行为挂到自己的列表上；它不折叠（canScroll = false），不会吞掉往上推的手势
        // （2026111603 服务商详情页、记忆页滑不动）。顶栏模糊跟随的滚动量由外面这层嵌套滚动统计。
        val scrollBehavior = MiuixScrollBehavior(canScroll = { false })
        io.github.mangi.eta.ui.components.coui.EtaCouiScaffold(
            title = title,
            onBack = onBack,
            modifier = modifier,
            containerColor = EtaTheme.colors.surfaceSunken,
            compact = true,
            canScroll = { true },
            actions = actions,
        ) { paddingValues, nestedScroll, centeredPadding ->
            Box(Modifier.fillMaxSize().nestedScroll(nestedScroll)) {
                val sidePadding = centeredPadding + if (rememberIsWideScreen()) 8.dp else 0.dp
                content(paddingValues, scrollBehavior, sidePadding)
            }
        }
    }
}

@Composable
fun MiuixPageBottomSpacer(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .height(24.dp)
            .navigationBarsPadding(),
    )
}
