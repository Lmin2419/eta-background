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

        // 与上游 3.4.x 一致的 COUI 二级页：大标题随滚动收缩成顶栏小标题，返回键是浮动的液态玻璃圆钮，
        // 顶栏是 COUI 的模糊渐隐材质；内容是分组卡片列表。宽屏或页面自带主视觉时用紧凑标题。
        val dark = com.itos.coui.theme.CouiTheme.isDark
        val useCompact = compactTitle || rememberIsWideScreen()
        val behavior = if (useCompact) null else com.itos.coui.topappbar.CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            canScroll = { listState.canScrollForward || listState.canScrollBackward },
        )
        val overscroll = com.itos.coui.scroll.rememberCouiOverscrollEffect()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val barColors = com.itos.coui.topappbar.CouiTopAppBarDefaults.colors(titleColor = EtaTheme.colors.textPrimary, dark = dark)
        val titleContent: @Composable () -> Unit = {
            androidx.compose.foundation.text.BasicText(
                text = title,
                style = com.itos.coui.theme.LocalCouiTextStyle.current,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        val navigation: @Composable () -> Unit = { io.github.mangi.eta.ui.components.coui.EtaCouiBackButton(onClick = onBack) }
        val topBarActions: @Composable RowScope.() -> Unit = { com.itos.coui.topappbar.TopAppBarButtonGroup { actions() } }
        com.itos.coui.layout.Scaffold(
            modifier = modifier.fillMaxSize()
                .then(if (behavior != null) Modifier.nestedScroll(behavior.nestedScrollConnection) else Modifier),
            containerColor = containerColor,
            topBar = {
                if (useCompact) com.itos.coui.topappbar.TopAppBar(title = titleContent, navigationIcon = navigation,
                    actions = topBarActions, dark = dark, colors = barColors)
                else com.itos.coui.topappbar.LargeTopAppBar(title = titleContent, navigationIcon = navigation,
                    actions = topBarActions, scrollBehavior = behavior, dark = dark, colors = barColors)
            },
        ) { innerPadding ->
            val padding = remember(behavior, innerPadding, density) {
                behavior?.state?.contentPadding(innerPadding, density) ?: innerPadding
            }
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                WidePageContent { sidePadding ->
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalCutoutPadding()
                            .then(if (pull != null) Modifier.nestedScroll(pull.connection) else Modifier),
                        contentPadding = PaddingValues(
                            start = sidePadding,
                            top = padding.calculateTopPadding(),
                            end = sidePadding,
                            bottom = padding.calculateBottomPadding(),
                        ),
                        overscrollEffect = overscroll,
                    ) {
                        content()
                        item(key = "bottom_spacer") {
                            MiuixPageBottomSpacer()
                        }
                    }
                }
                if (pull != null) EtaPullRefreshIndicator(pull, Modifier.align(androidx.compose.ui.Alignment.TopCenter),
                    topInset = padding.calculateTopPadding())
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

        val inWindow = LocalEtaWindow.current != null
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = if (inWindow) Color.Transparent else MiuixTheme.colorScheme.surface,
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
