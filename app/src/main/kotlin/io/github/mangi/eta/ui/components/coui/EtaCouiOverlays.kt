package io.github.mangi.eta.ui.components.coui

import androidx.compose.ui.composed

import androidx.compose.runtime.mutableIntStateOf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.components.EtaCouiSupport
import io.github.mangi.eta.ui.theme.EtaTheme
import io.github.suqi8.coui.kmp.basic.DropdownGroupDivider
import io.github.suqi8.coui.kmp.basic.DropdownHeaderItem
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.MaterialBlendMode
import io.github.suqi8.coui.kmp.blur.MaterialBlurDefaults
import io.github.suqi8.coui.kmp.blur.MaterialBlurStyle
import io.github.suqi8.coui.kmp.blur.couiMaterialBlur
import io.github.suqi8.coui.kmp.blur.isRuntimeShaderSupported
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.effect.LocalCOUIMaterialEffectEnabled
import io.github.suqi8.coui.kmp.effect.LocalMaterialBackdrop
import io.github.suqi8.coui.kmp.effect.LocalPopupMaterialBlur
import io.github.suqi8.coui.kmp.effect.MaterialBlurProvider
import io.github.suqi8.coui.kmp.layout.BottomSheetDefaults
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.overlay.OverlayInputDialog
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.roundToInt

/*
 * 14 号方案 14.4b：菜单、对话框、输入对话框、半窗换成 COUI 2.1.0。
 *
 * 新库的弹层不是独立窗口，而是画在同一棵组合树里的「弹层宿主」上（COUI Scaffold 自带）。
 * [EtaCouiOverlayRoot] 放在应用最外层：它提供宿主、记录整页内容供材质模糊（只记录页面，不含弹层自己），
 * 并统一挂载菜单（[EtaCouiMenu] 在调用处不放任何布局节点，菜单由根上的菜单层画出来）。
 *
 * 新库的主题会改全局的按压效果、滚动回弹和选字颜色，所以只包住弹层宿主和菜单层；
 * 对话框里的内容是胖小胖自己的组件，画的时候换回调用处的环境（主题、导航、页面状态都和调用处一样）。
 */

/** COUI 弹层用的配色：COUI 原生配色，强调色（推荐按钮、选中项）换成胖小胖的强调色。 */
@Composable
internal fun etaCouiColors(dark: Boolean = EtaTheme.colors.isDark): io.github.suqi8.coui.kmp.theme.Colors {
    val accent = EtaTheme.colors.accent
    return remember(dark, accent) {
        val base = if (dark) io.github.suqi8.coui.kmp.theme.darkColorScheme() else io.github.suqi8.coui.kmp.theme.lightColorScheme()
        base.copy(primary = accent)
    }
}

/** 在新库主题里画 COUI 组件；没有着色器（单测截图、软件绘制）时关掉圆角遮罩和材质光效。 */
@Composable
internal fun EtaCouiTheme(dark: Boolean = EtaTheme.colors.isDark, content: @Composable () -> Unit) {
    COUITheme(colors = etaCouiColors(dark)) {
        CompositionLocalProvider(
            io.github.suqi8.coui.kmp.squircle.LocalSquircleEnabled provides EtaCouiSupport.shaders,
            LocalCOUIMaterialEffectEnabled provides EtaCouiSupport.shaders,
            content = content,
        )
    }
}

/** 整页内容的记录（材质模糊的采样源）；关闭模糊或设备不支持时为 null。 */
internal val LocalEtaCouiPageBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** 菜单的根挂载点。 */
internal val LocalEtaCouiMenuHost = staticCompositionLocalOf<EtaCouiMenuHost?> { null }

/**
 * 应用最外层的 COUI 弹层宿主。[content] 收到的 Modifier 要挂在页面内容上（记录页面供弹层模糊），
 * 不要挂在提示条、渲染诊断这类浮在上面的东西上。
 */
@Composable
internal fun EtaCouiOverlayRoot(blurEnabled: Boolean, content: @Composable (pageRecording: Modifier) -> Unit) {
    val dark = EtaTheme.colors.isDark
    val canvas = EtaTheme.colors.canvas
    val demand = remember { EtaPageRecordingDemand() }
    val backdrop = io.github.mangi.eta.ui.components.trackRenderSource(
        "应用弹层(COUI 菜单与对话框)",
        if (blurEnabled && EtaCouiSupport.shaders && isRuntimeShaderSupported()) {
            rememberLayerBackdrop {
                drawRect(canvas)
                drawContent()
            }
        } else {
            null
        },
    )
    val popupBlur = remember(backdrop) {
        backdrop?.let {
            MaterialBlurProvider { spec, shape ->
                Modifier.demandPageRecording(demand).couiMaterialBlur(
                    backdrop = it,
                    style = MaterialBlurStyle(
                        blurRadiusPx = spec.blurRadiusPx,
                        mixColor = spec.mixColor,
                        blendColor = spec.blendColor,
                        blendMode = MaterialBlendMode.valueOf(spec.blendMode.name),
                    ),
                    shape = shape,
                )
            }
        }
    }
    val menuHost = remember { EtaCouiMenuHost() }
    // 先在外层读一次系统栏边距：这样边距监听在第一次组合时就注册好。下面的 Scaffold 用子组合摆放内容，
    // 页面第一次读边距发生在子组合里；那时才注册的话，要等下一次窗口重新派发边距（本机测试环境里不会再派发，页面读到 0）。
    WindowInsets.systemBars.hashCode()
    // 新库的 Scaffold 本身不画任何东西（透明、不留边距），只用来提供弹层宿主；宿主在新库主题里。
    io.github.suqi8.coui.kmp.basic.Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp),
        popupHost = { EtaCouiTheme(dark) { io.github.suqi8.coui.kmp.utils.COUIPopupUtils.COUIPopupHost() } },
    ) {
        CompositionLocalProvider(
            LocalEtaCouiPageBackdrop provides backdrop,
            LocalEtaPageRecordingDemand provides demand,
            LocalPopupMaterialBlur provides popupBlur,
            LocalEtaCouiMenuHost provides menuHost,
        ) {
            Box(Modifier.fillMaxSize()) {
                content(if (backdrop != null) Modifier.recordWhenDemanded(backdrop, demand) else Modifier)
                EtaCouiMenuLayer(menuHost, dark)
            }
        }
    }
}

/** 弹层（对话框、半窗）里的内容：换回调用处的环境，并且不再模糊页面（嵌套弹层用实色，和新库一致）。 */
@Composable
private fun CallSiteContent(locals: CompositionLocalContext, content: @Composable () -> Unit) {
    CompositionLocalProvider(locals) {
        CompositionLocalProvider(
            LocalPopupMaterialBlur provides null,
            LocalMaterialBackdrop provides null,
            LocalEtaCouiPageBackdrop provides null,
            content = content,
        )
    }
}

/** 弹层必须画在 [EtaCouiOverlayRoot] 的宿主上；没有宿主时新库会静默不显示，这里记一条日志便于排查。 */
@Composable
private fun WarnIfNoHost(show: Boolean, what: String) {
    if (show && LocalEtaCouiMenuHost.current == null) SideEffect {
        io.github.mangi.eta.core.AndroidAgentLogger.warnThrottled("coui_overlay_no_host_$what") {
            "COUI $what shown outside EtaCouiOverlayRoot; it will not be visible"
        }
    }
}

/** 对话框、半窗的材质模糊：背后有页面记录时启用（面板透明、模糊背后页面），否则用实色面板。 */
@Composable
private fun etaPanelBlur(style: (Boolean) -> MaterialBlurStyle, dark: Boolean): Modifier? {
    val backdrop = LocalEtaCouiPageBackdrop.current ?: return null
    val demand = LocalEtaPageRecordingDemand.current
    return (if (demand != null) Modifier.demandPageRecording(demand) else Modifier)
        .couiMaterialBlur(backdrop = backdrop, style = style(dark))
}

/**
 * 整页记录（弹层背后的模糊用）按需开启：以前只要开着模糊，整个界面每一帧都要多录一遍，
 * 点开页面、过渡动画时这份开销一直在（高分辨率屏上尤其明显）。现在只有画着整页模糊的弹层
 * （对话框、小窗、菜单、下拉列表）挂在界面上时才录，收起（退场动画结束、节点移除）后停止。
 */
@Stable
internal class EtaPageRecordingDemand {
    var users by mutableIntStateOf(0)
        internal set
}

internal val LocalEtaPageRecordingDemand = staticCompositionLocalOf<EtaPageRecordingDemand?> { null }

/** 挂在用到整页模糊的弹层背景上：挂上时登记，移除时撤销。 */
internal fun Modifier.demandPageRecording(demand: EtaPageRecordingDemand): Modifier = this then RecordingDemandElement(demand)

private class RecordingDemandElement(private val demand: EtaPageRecordingDemand) :
    androidx.compose.ui.node.ModifierNodeElement<RecordingDemandNode>() {
    override fun create() = RecordingDemandNode(demand)
    override fun update(node: RecordingDemandNode) = node.retarget(demand)
    override fun equals(other: Any?) = other is RecordingDemandElement && other.demand === demand
    override fun hashCode() = System.identityHashCode(demand)
}

private class RecordingDemandNode(private var demand: EtaPageRecordingDemand) : Modifier.Node() {
    private var held = false
    override fun onAttach() = acquire()
    override fun onDetach() = release()
    fun retarget(next: EtaPageRecordingDemand) {
        if (next === demand) return
        release(); demand = next; if (isAttached) acquire()
    }
    private fun acquire() { if (!held) { held = true; demand.users++ } }
    private fun release() { if (held) { held = false; demand.users-- } }
}

/** 只在有弹层要用时记录整页；读计数的只是这一个修饰符，不会让整页重组。 */
private fun Modifier.recordWhenDemanded(backdrop: io.github.suqi8.coui.kmp.blur.LayerBackdrop, demand: EtaPageRecordingDemand): Modifier =
    composed { if (demand.users > 0) Modifier.layerBackdrop(backdrop) else Modifier }

// ---------------------------------------------------------------------------------------------
// 菜单
// ---------------------------------------------------------------------------------------------

/**
 * 弹出菜单的锚点：挂在被点或被长按的元素上（[etaMenuAnchor]），打开时用 [open] 记下元素位置，
 * 菜单从手指按下的位置弹出（没有触点时，比如无障碍点击，按元素位置弹出）。
 */
@Stable
internal class EtaMenuAnchor {
    internal val precise = PreciseClickState()
    internal var coordinates: LayoutCoordinates? = null
    internal var bounds by mutableStateOf<Rect?>(null)
        private set

    fun open(show: () -> Unit) {
        val current = coordinates?.takeIf { it.isAttached }?.boundsInWindow()
        bounds = current
        // 触点不在元素上（键盘、无障碍点击留下的旧触点）时不用它，改按元素位置弹出。
        val touch = precise.touchPoint
        if (touch.isSpecified && current != null && !current.inflate(1f).contains(touch)) precise.clear()
        show()
    }
}

@Composable
internal fun rememberEtaMenuAnchor(): EtaMenuAnchor = remember { EtaMenuAnchor() }

internal fun Modifier.etaMenuAnchor(anchor: EtaMenuAnchor): Modifier =
    onGloballyPositioned { anchor.coordinates = it }.preciseClickAnchor(anchor.precise)

/** COUI 弹出菜单的一项（与 [io.github.mangi.eta.ui.components.EtaMenuCollector.Entry] 一一对应）。 */
internal class EtaCouiMenuEntry(
    val text: String,
    val icon: ImageVector?,
    val enabled: Boolean,
    val destructive: Boolean,
    val selected: Boolean,
    val dividerBefore: Boolean,
    val onClick: () -> Unit,
    /** 第二行小字（如预设对应的模型和思考强度）。 */
    val summary: String? = null,
)

/** 一个正在显示（或正在收起）的菜单。 */
@Stable
internal class EtaCouiMenuRequest {
    var show by mutableStateOf(false)
    var entries by mutableStateOf<List<EtaCouiMenuEntry>>(emptyList())
    var header by mutableStateOf<String?>(null)
    var anchor by mutableStateOf<EtaMenuAnchor?>(null)
    var blur by mutableStateOf<MaterialBlurProvider?>(null)
    var onDismiss: () -> Unit = {}
}

internal class EtaCouiMenuHost {
    val requests = mutableStateListOf<EtaCouiMenuRequest>()
}

/**
 * COUI 弹出菜单：从 [anchor] 处弹出，按住滑动到某一项松手即选中；危险项用警示色。
 * 调用处不放任何布局节点（有的父布局按子节点顺序摆放），菜单由根上的菜单层画出来。
 */
@Composable
internal fun EtaCouiMenu(
    show: Boolean,
    entries: List<EtaCouiMenuEntry>,
    onDismiss: () -> Unit,
    anchor: EtaMenuAnchor?,
    header: String?,
    @Suppress("UNUSED_PARAMETER") dark: Boolean,
) {
    WarnIfNoHost(show, "menu")
    val host = LocalEtaCouiMenuHost.current ?: return
    // 在对话框、半窗里打开的菜单不模糊（弹层内容里这个值是 null），和新库一致。
    val blur = LocalPopupMaterialBlur.current
    val request = remember { EtaCouiMenuRequest() }
    val visible = show && entries.isNotEmpty()
    SideEffect {
        request.show = visible
        request.entries = entries
        request.header = header
        request.anchor = anchor
        request.blur = blur
        request.onDismiss = onDismiss
    }
    LaunchedEffect(visible) {
        if (visible && host.requests.none { it === request }) host.requests.add(request)
    }
    DisposableEffect(host) {
        onDispose { host.requests.remove(request) }
    }
}

@Composable
private fun EtaCouiMenuLayer(host: EtaCouiMenuHost, dark: Boolean) {
    if (host.requests.isEmpty()) return
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        for (request in host.requests) key(request) {
            val bounds = request.anchor?.bounds
            // 菜单按锚点元素定位：放一个和元素同位置、同大小的占位框，菜单挂在它上面。
            val placement = if (bounds != null) {
                with(density) {
                    Modifier
                        .offset { IntOffset((bounds.left - origin.x).roundToInt(), (bounds.top - origin.y).roundToInt()) }
                        .size(bounds.width.toDp(), bounds.height.toDp())
                }
            } else {
                Modifier.fillMaxSize()
            }
            Box(placement) {
                CompositionLocalProvider(LocalPopupMaterialBlur provides request.blur) {
                    EtaCouiTheme(dark) {
                        EtaCouiMenuPopup(request, onFinished = { host.requests.remove(request) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EtaCouiMenuPopup(request: EtaCouiMenuRequest, onFinished: () -> Unit) {
    val entries = request.entries
    val colors = COUITheme.colorScheme
    val reserveIconSlot = entries.any { it.icon != null }
    OverlayListPopup(
        show = request.show,
        onDismissRequest = { request.onDismiss() },
        onDismissFinished = onFinished,
        preciseClickState = request.anchor?.precise,
    ) {
        ListPopupColumn {
            request.header?.let { DropdownHeaderItem(it) }
            entries.forEachIndexed { index, entry ->
                if (index != 0 && entry.dividerBefore) DropdownGroupDivider()
                DropdownImpl(
                    item = DropdownItem(
                        text = entry.text,
                        enabled = entry.enabled,
                        selected = entry.selected,
                        summary = entry.summary,
                        alert = entry.destructive,
                        icon = entry.icon?.let { icon ->
                            { modifier ->
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = modifier,
                                    tint = when {
                                        !entry.enabled -> colors.disabledNeutral
                                        entry.destructive -> colors.error
                                        else -> colors.labelPrimary
                                    },
                                )
                            }
                        },
                    ),
                    optionSize = entries.size,
                    index = index,
                    isSelected = entry.selected,
                    reserveIconSlot = reserveIconSlot,
                    onSelectedIndexChange = {
                        entry.onClick()
                        request.onDismiss()
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 对话框、半窗
// ---------------------------------------------------------------------------------------------

/**
 * 对话框里 [io.github.mangi.eta.ui.components.MiuixDialogActions] 声明的按钮，交给 COUI 对话框底部的按钮栏绘制
 * （与上游一致的并排 / 竖排规则）。只把可比较的文案与开关放进状态，点击回调走普通字段，避免反复重组。
 */
internal class EtaDialogActionSink {
    data class Spec(
        val confirmText: String,
        val confirmEnabled: Boolean,
        val destructive: Boolean,
        val cancelText: String,
        val cancelEnabled: Boolean,
    )
    var spec by mutableStateOf<Spec?>(null)
    var onConfirm: () -> Unit = {}
    var onCancel: () -> Unit = {}
}

internal val LocalEtaDialogActionSink = staticCompositionLocalOf<EtaDialogActionSink?> { null }

/** 按钮栏：声明了按钮时是「取消 / 确定」（危险操作的确定用警示色），否则是 [closeText]（为空时不显示）。 */
@Composable
private fun EtaDialogButtons(sink: EtaDialogActionSink, closeText: String?, onClose: () -> Unit, closeEnabled: Boolean) {
    val spec = sink.spec
    if (spec != null) {
        val confirm = DialogButtonBarAction(spec.confirmText, enabled = spec.confirmEnabled, recommended = !spec.destructive) { sink.onConfirm() }
        DialogButtonBar(
            negative = DialogButtonBarAction(spec.cancelText, enabled = spec.cancelEnabled) { sink.onCancel() },
            positive = if (spec.destructive) null else confirm,
            neutral = if (spec.destructive) confirm else null,
        )
    } else if (closeText != null) {
        DialogButtonBar(
            negative = null,
            // 只有「关闭」时用灰色次要按钮（和换库前一样），不用强调色。
            positive = DialogButtonBarAction(closeText, enabled = closeEnabled, onClick = onClose),
        )
    }
}

/**
 * 上游样式的对话框：手机上从底部升起（大屏居中），材质模糊背后的页面；标题、说明、内容，底部按钮栏。
 * 内容里的 MiuixDialogActions 会变成底部按钮；没有声明按钮时底部是「关闭」。
 */
@Composable
internal fun EtaCouiDialog(
    show: Boolean,
    title: String?,
    message: String?,
    onDismissRequest: () -> Unit,
    closeText: String,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 24.dp,
    maxContentHeight: Dp = Dp.Unspecified,
    dismissible: Boolean = true,
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val dark = EtaTheme.colors.isDark
    val locals = currentCompositionLocalContext
    val sink = remember { EtaDialogActionSink() }
    val blur = etaPanelBlur(MaterialBlurDefaults::dialog, dark)
    WarnIfNoHost(show, "dialog")
    EtaCouiTheme(dark) {
        OverlayDialog(
            show = show,
            modifier = modifier.semantics { title?.let { paneTitle = it } },
            title = title,
            summary = message,
            onDismissRequest = { if (dismissible) onDismissRequest() },
            onDismissFinished = onDismissFinished,
            backgroundModifier = blur ?: Modifier,
            backgroundColor = if (blur == null) DialogDefaults.backgroundColor() else Color.Transparent,
            materialStroke = blur != null,
            customContent = {
                CallSiteContent(locals) {
                    CompositionLocalProvider(LocalEtaDialogActionSink provides sink) {
                        // 内容按列排布；限制最大高度，里面的列表可以自己滚动。
                        Column(
                            Modifier.fillMaxWidth()
                                .then(if (maxContentHeight != Dp.Unspecified) Modifier.heightIn(max = maxContentHeight) else Modifier)
                                .padding(horizontal = contentPadding)
                                .padding(top = 4.dp, bottom = 8.dp),
                        ) { content() }
                    }
                }
            },
        ) {
            EtaDialogButtons(sink, closeText, onDismissRequest, dismissible)
        }
    }
}

/**
 * 上游样式的半窗：COUI 底部面板，带拖动条，材质模糊背后的页面；拖下、点外面或返回键关闭。
 * 内容里的 MiuixDialogActions 会变成底部按钮。
 */
@Composable
internal fun EtaCouiSheet(
    show: Boolean,
    title: String?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    insideMargin: DpSize = DpSize(16.dp, 16.dp),
    maxContentHeight: Dp = Dp.Unspecified,
    dismissible: Boolean = true,
    enableNestedScroll: Boolean = true,
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val dark = EtaTheme.colors.isDark
    val locals = currentCompositionLocalContext
    val sink = remember { EtaDialogActionSink() }
    val blur = etaPanelBlur(MaterialBlurDefaults::bottomSheet, dark)
    WarnIfNoHost(show, "sheet")
    EtaCouiTheme(dark) {
        OverlayBottomSheet(
            show = show,
            modifier = modifier.semantics { title?.let { paneTitle = it } },
            title = title,
            onDismissRequest = { if (dismissible) onDismissRequest() },
            onDismissFinished = onDismissFinished,
            backgroundModifier = blur ?: Modifier,
            backgroundColor = if (blur == null) BottomSheetDefaults.backgroundColor() else Color.Transparent,
            materialStroke = blur != null,
            showDragHandle = true,
            allowDismiss = dismissible,
            enableNestedScroll = enableNestedScroll,
            insideMargin = DpSize(0.dp, 0.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                CallSiteContent(locals) {
                    CompositionLocalProvider(LocalEtaDialogActionSink provides sink) {
                        Column(
                            Modifier.fillMaxWidth()
                                .then(if (maxContentHeight != Dp.Unspecified) Modifier.heightIn(max = maxContentHeight) else Modifier)
                                .padding(horizontal = insideMargin.width)
                                .padding(top = 4.dp, bottom = insideMargin.height),
                        ) { content() }
                    }
                }
                EtaDialogButtons(sink, closeText = null, onClose = onDismissRequest, closeEnabled = dismissible)
            }
        }
    }
}

/**
 * 上游的 COUI 输入对话框：标题、单行输入框（带一键清除）、底部「取消 / 确定」，打开时自动聚焦。
 * [error] 不为空时代替说明文字显示（警示色）。
 */
@Composable
internal fun EtaCouiInputDialog(
    visible: Boolean,
    title: String?,
    value: String,
    onValueChange: (String) -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    message: String? = null,
    placeholder: String? = null,
    confirmEnabled: Boolean = true,
    error: String? = null,
    cancelText: String = stringResource(R.string.action_cancel),
) {
    val dark = EtaTheme.colors.isDark
    val blur = etaPanelBlur(MaterialBlurDefaults::dialog, dark)
    WarnIfNoHost(visible, "input_dialog")
    EtaCouiTheme(dark) {
        OverlayInputDialog(
            show = visible,
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.semantics { title?.let { paneTitle = it } },
            title = title,
            summary = error ?: message,
            summaryColor = if (error != null) COUITheme.colorScheme.error else DialogDefaults.summaryColor(),
            label = placeholder.orEmpty(),
            negative = DialogButtonBarAction(cancelText, onClick = onDismissRequest),
            positive = DialogButtonBarAction(confirmText, enabled = confirmEnabled, recommended = true, onClick = onConfirm),
            onDismissRequest = onDismissRequest,
            backgroundModifier = blur ?: Modifier,
            backgroundColor = if (blur == null) DialogDefaults.backgroundColor() else Color.Transparent,
            materialStroke = blur != null,
        )
    }
}
