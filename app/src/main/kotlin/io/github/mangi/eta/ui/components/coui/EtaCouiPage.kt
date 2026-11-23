package io.github.mangi.eta.ui.components.coui

import androidx.compose.foundation.Indication
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.components.EtaCouiSupport
import io.github.mangi.eta.ui.theme.EtaTheme
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.glowScrollOffsetPx
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.TopAppBarGlow
import io.github.suqi8.coui.kmp.blur.isRuntimeShaderSupported
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import io.github.mangi.eta.ui.layout.horizontalCutoutPadding

/*
 * 14 号方案 14.4c 第一段：二级页骨架换成 COUI 2.1.0（与上游 CouiScaffold 同一结构）。
 * - 顶栏是新库的 TopAppBar（大标题随滚动收缩）/ SmallTopAppBar（宽屏、自带主视觉的页面），
 *   内容从顶栏下面滚过时顶栏有 COUI 17 的凝光模糊（TopAppBarGlow），没有模糊时是页面底色。
 * - 返回键、右侧按钮是普通图标按钮（新库顶栏的工具栏样式），不再是液态玻璃圆钮。
 * - 新库主题只包住骨架；页面内容换回胖小胖自己的按压效果、滚动回弹和选字颜色。
 */

/** 新库主题会改的三样（按压效果、滚动回弹、选字颜色）；页面内容要用胖小胖自己的。 */
@Immutable
internal class EtaContentLocals(
    val indication: Indication,
    val overscroll: OverscrollFactory?,
    val selection: TextSelectionColors,
)

@Composable
internal fun rememberEtaContentLocals(): EtaContentLocals =
    EtaContentLocals(LocalIndication.current, LocalOverscrollFactory.current, LocalTextSelectionColors.current)

@Composable
internal fun ProvideEtaContentLocals(locals: EtaContentLocals, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalIndication provides locals.indication,
        LocalOverscrollFactory provides locals.overscroll,
        LocalTextSelectionColors provides locals.selection,
        content = content,
    )
}

/** 二级页内容的记录（顶栏凝光模糊的采样源）；关闭模糊或没有着色器时为 null。 */
@Composable
private fun rememberEtaPageBackdrop(base: Color): LayerBackdrop? {
    val blur = io.github.mangi.eta.ui.app.LocalBlurEnabled.current && EtaCouiSupport.shaders && isRuntimeShaderSupported()
    val backdrop = if (blur) rememberLayerBackdrop {
        drawRect(base)
        drawContent()
    } else null
    return io.github.mangi.eta.ui.components.trackRenderSource("设置顶栏(COUI)", backdrop)
}

/** 顶栏左侧的返回键：新库顶栏的图标按钮。 */
@Composable
internal fun EtaCouiPageBackButton(onClick: () -> Unit) {
    val description = stringResource(R.string.action_back)
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Icon(
            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ArrowBackIos,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = EtaTheme.colors.textPrimary,
        )
    }
}

/**
 * COUI 2.1.0 二级页骨架。[content] 收到：内容边距（含顶栏高度）、给滚动容器挂的嵌套滚动（让大标题跟着收缩）、
 * 宽屏两侧留白。[compact] 为真时用不收缩的小标题。
 */
@Composable
internal fun EtaCouiScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier,
    containerColor: Color,
    compact: Boolean,
    canScroll: () -> Boolean,
    actions: @Composable RowScope.() -> Unit,
    overlay: @Composable BoxScope.(PaddingValues) -> Unit = {},
    content: @Composable (padding: PaddingValues, nestedScroll: NestedScrollConnection, sidePadding: Dp) -> Unit,
) {
    val dark = EtaTheme.colors.isDark
    val inWindow = io.github.mangi.eta.ui.components.LocalEtaWindow.current != null
    val locals = rememberEtaContentLocals()
    val backdrop = rememberEtaPageBackdrop(containerColor)
    val topBarState = rememberTopAppBarState()
    EtaCouiTheme(dark) {
        val behavior: ScrollBehavior = COUIScrollBehavior(state = topBarState, canScroll = canScroll)
        val barColor = if (backdrop != null) Color.Transparent else containerColor
        val glow: (@Composable BoxScope.() -> Unit)? = backdrop?.let { source ->
            {
                TopAppBarGlow(
                    backdrop = source,
                    scrollOffsetPx = behavior.glowScrollOffsetPx(),
                    isDark = dark,
                    baseColor = containerColor,
                    enabled = true,
                )
            }
        }
        val titleColor = EtaTheme.colors.textPrimary
        val navigation: @Composable () -> Unit = { EtaCouiPageBackButton(onBack) }
        // 右侧按钮是胖小胖自己的组件，用回胖小胖的按压效果。
        val barActions: @Composable RowScope.() -> Unit = { ProvideEtaContentLocals(locals) { androidx.compose.foundation.layout.Row { actions() } } }
        io.github.suqi8.coui.kmp.basic.Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = if (inWindow) Color.Transparent else containerColor,
            topBar = {
                if (compact) {
                    SmallTopAppBar(title = title, color = barColor, titleColor = titleColor, navigationIcon = navigation,
                        actions = barActions, scrollBehavior = behavior, glow = glow)
                } else {
                    TopAppBar(title = title, color = barColor, titleColor = titleColor, largeTitleColor = titleColor,
                        navigationIcon = navigation, actions = barActions, scrollBehavior = behavior, glow = glow)
                }
            },
        ) { padding ->
            ProvideEtaContentLocals(locals) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
                        io.github.mangi.eta.ui.layout.WidePageContent { sidePadding ->
                            content(padding, behavior.nestedScrollConnection, sidePadding)
                        }
                    }
                    overlay(padding)
                }
            }
        }
    }
}

/** 列表二级页：[EtaCouiScaffold] 里放一个 LazyColumn（COUI 的越界回弹、惯性和到边触感）。 */
@Composable
internal fun EtaCouiLazyPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier,
    containerColor: Color,
    compact: Boolean,
    listState: LazyListState,
    actions: @Composable RowScope.() -> Unit,
    extraNestedScroll: NestedScrollConnection?,
    overlay: @Composable BoxScope.(PaddingValues) -> Unit,
    content: LazyListScope.() -> Unit,
) {
    EtaCouiScaffold(
        title = title, onBack = onBack, modifier = modifier, containerColor = containerColor, compact = compact,
        canScroll = { listState.canScrollForward || listState.canScrollBackward },
        actions = actions, overlay = overlay,
    ) { padding, nestedScroll, sidePadding ->
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .horizontalCutoutPadding()
                .then(if (extraNestedScroll != null) Modifier.nestedScroll(extraNestedScroll) else Modifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(nestedScroll),
            contentPadding = PaddingValues(
                start = sidePadding,
                top = padding.calculateTopPadding(),
                end = sidePadding,
                bottom = padding.calculateBottomPadding(),
            ),
            flingBehavior = io.github.suqi8.coui.kmp.utils.rememberCOUIFlingBehavior(),
            overscrollEffect = null,
        ) {
            content()
            item(key = "bottom_spacer") { io.github.mangi.eta.ui.components.MiuixPageBottomSpacer() }
        }
    }
}
