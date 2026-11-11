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
import androidx.compose.ui.Modifier
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
        val scrollBehavior = MiuixScrollBehavior()
        val pull = onRefresh?.let { rememberEtaPullRefreshState(refreshing, it) }
        val backdrop = rememberTopBarBackdrop()

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = containerColor,
            topBar = {
                // 顶栏与页面同色（浅灰底），滚动后才出现分隔线。
                TopBarBackdrop(backdrop, color = containerColor) {
                    // 页面自带主视觉（如角色详情）时用紧凑标题，避免大标题与主视觉重复。
                    AdaptiveTopAppBar(
                        title = title, color = containerColor, compact = compactTitle,
                        navigationIcon = { MiuixBackButton(onClick = onBack) },
                        actions = actions, scrollBehavior = scrollBehavior,
                    )
                }
            },
        ) { innerPadding ->
          androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            WidePageContent { centeredPadding ->
                val sidePadding = centeredPadding + if (rememberIsWideScreen()) 8.dp else 0.dp
                // 保留 MiuixTheme 注入的默认越界工厂，保留短内容页的回弹行为。
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalCutoutPadding()
                        .captureForTopBar(backdrop)
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .then(if (pull != null) Modifier.nestedScroll(pull.connection) else Modifier)
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        start = sidePadding,
                        top = innerPadding.calculateTopPadding(),
                        end = sidePadding,
                    ),
                ) {
                    content()
                    item(key = "bottom_spacer") {
                        MiuixPageBottomSpacer()
                    }
                }
            }
            if (pull != null) EtaPullRefreshIndicator(pull, Modifier.align(androidx.compose.ui.Alignment.TopCenter),
                topInset = innerPadding.calculateTopPadding())
            overlay()
          }
        }
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
        val scrollBehavior = MiuixScrollBehavior()
        val backdrop = rememberTopBarBackdrop()
        val topBarColor = topBarContainerColor(backdrop)

        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopBarBackdrop(backdrop) {
                    AdaptiveTopAppBar(
                        title = title,
                        color = topBarColor,
                        navigationIcon = { MiuixBackButton(onClick = onBack) },
                        actions = actions,
                        scrollBehavior = scrollBehavior,
                    )
                }
            },
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().captureForTopBar(backdrop)) {
                WidePageContent { centeredPadding ->
                val sidePadding = centeredPadding + if (rememberIsWideScreen()) 8.dp else 0.dp
                    content(paddingValues, scrollBehavior, sidePadding)
                }
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
