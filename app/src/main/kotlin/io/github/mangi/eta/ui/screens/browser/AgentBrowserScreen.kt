package io.github.mangi.eta.ui.screens.browser

import android.content.Intent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.browser.AgentBrowserSession
import io.github.mangi.eta.agent.browser.BrowserSessionSnapshot
import io.github.mangi.eta.ui.components.EtaTextButton
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.StatusError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

/**
 * Agent 与用户共享的浏览器会话。
 *
 * 浏览器通常在后台由模型驱动；进入本页后挂载的是同一个 WebView，用户可以直接接管，
 * 不会新建一份与 Agent 状态脱节的预览。
 */
@Composable
internal fun AgentBrowserScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val noExternalAppMessage = stringResource(R.string.browser_no_external_app)
    val snapshot by AgentBrowserSession.snapshots.collectAsState()
    var address by remember { mutableStateOf(TextFieldValue("")) }
    var addressFocused by remember { mutableStateOf(false) }
    var actionPending by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(context.applicationContext) {
        AgentBrowserSession.initialize(context.applicationContext)
    }
    // 未编辑时地址栏只显示域名，信息密度低、一眼可读；获得焦点后换成完整地址并全选，便于直接改写。
    // 编辑期间页面跳转不覆盖用户正在输入的内容，只在进入编辑的那一刻填入完整地址。
    LaunchedEffect(addressFocused) {
        if (addressFocused) {
            address = TextFieldValue(snapshot.displayUrl, TextRange(0, snapshot.displayUrl.length))
        }
    }
    LaunchedEffect(snapshot.displayUrl, snapshot.host, addressFocused) {
        if (!addressFocused) address = TextFieldValue(snapshot.host.ifBlank { snapshot.displayUrl })
    }

    fun launchBrowserAction(action: () -> Unit) {
        if (actionPending) return
        actionPending = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
            } finally {
                actionPending = false
            }
        }
    }

    fun navigate() {
        if (actionPending) return
        val input = address.text.trim()
        val target = if (input == snapshot.displayUrl) snapshot.url else input
        if (target.isBlank()) return
        focusManager.clearFocus()
        keyboard?.hide()
        launchBrowserAction {
            AgentBrowserSession.navigateFromUser(context.applicationContext, target)
        }
    }

    fun refreshOrStop() {
        if (snapshot.isLoading) {
            scope.launch(Dispatchers.IO) { AgentBrowserSession.stopFromUser() }
        } else {
            launchBrowserAction { AgentBrowserSession.reloadFromUser() }
        }
    }

    // 浏览器是整屏工具：页面外的界面压缩成一行地址栏和一条可选状态条，其余空间全部留给网页。
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            .imePadding()
            .navigationBarsPadding(),
    ) {
        BrowserAddressRow(
            snapshot = snapshot,
            address = address,
            onAddressChange = { address = it },
            focused = addressFocused,
            onFocusChange = { addressFocused = it },
            actionPending = actionPending,
            onGo = ::navigate,
            onBack = { launchBrowserAction { AgentBrowserSession.goBackFromUser() } },
            onForward = { launchBrowserAction { AgentBrowserSession.goForwardFromUser() } },
            onRefresh = ::refreshOrStop,
            onOpenExternal = {
                val currentUrl = snapshot.url.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                if (currentUrl != null) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, currentUrl.toUri()))
                    }.onFailure {
                        Toast.makeText(context, noExternalAppMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onReset = { showResetDialog = true },
        )
        BrowserStatusStrip(snapshot)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(MiuixTheme.colorScheme.dividerLine),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            BrowserWebViewHost(modifier = Modifier.fillMaxSize())
            BrowserLoadingProgress(snapshot)
            BrowserStateOverlay(snapshot = snapshot, onRetry = ::refreshOrStop)
        }
    }

    if (showResetDialog) {
        EtaWindowDialog(
            show = true,
            title = stringResource(R.string.ui_reset_browser_session_791b36),
            summary = stringResource(R.string.ui_this_will_close_the_current_page_and_clear_eta_brows_1cd331),
            onDismissRequest = { showResetDialog = false },
        ) {
            MiuixDialogActions(
                confirmText = stringResource(R.string.browser_reset),
                confirmEnabled = !actionPending,
                destructive = true,
                onCancel = { showResetDialog = false },
                onConfirm = {
                    showResetDialog = false
                    address = TextFieldValue("")
                    launchBrowserAction { AgentBrowserSession.resetFromUser() }
                },
            )
        }
    }
}

/**
 * 地址行：后退、前进与地址胶囊同处一行，刷新/停止放在胶囊尾部，低频的外部打开与重置收进菜单。
 * 整行固定 48dp 高，替代原先的大输入框加页面内工具栏两层结构。
 */
@Composable
private fun BrowserAddressRow(
    snapshot: BrowserSessionSnapshot,
    address: TextFieldValue,
    onAddressChange: (TextFieldValue) -> Unit,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    actionPending: Boolean,
    onGo: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onOpenExternal: () -> Unit,
    onReset: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = !focused,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Row {
                BrowserControlButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    description = stringResource(R.string.browser_back),
                    enabled = snapshot.canGoBack && !actionPending,
                    onClick = onBack,
                )
                BrowserControlButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    description = stringResource(R.string.browser_forward),
                    enabled = snapshot.canGoForward && !actionPending,
                    onClick = onForward,
                )
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .height(40.dp)
                .squircleSurface(color = colors.surfaceContainerHigh, cornerRadius = 20.dp)
                .padding(start = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (snapshot.url.startsWith("https://")) Icons.Rounded.Lock else Icons.Rounded.Language,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (address.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ui_url_or_domain_name_3ee97a),
                        style = MiuixTheme.textStyles.body2,
                        color = colors.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    singleLine = true,
                    textStyle = MiuixTheme.textStyles.body2.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onGo() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { onFocusChange(it.isFocused) },
                )
            }
            if (focused) {
                BrowserControlButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    description = stringResource(R.string.ui_access_7f5641),
                    enabled = address.text.isNotBlank() && !actionPending,
                    onClick = onGo,
                    size = 36.dp,
                )
            } else {
                BrowserControlButton(
                    icon = if (snapshot.isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                    description = stringResource(
                        if (snapshot.isLoading) R.string.browser_stop_loading else R.string.browser_refresh,
                    ),
                    enabled = snapshot.available && (snapshot.isLoading || !actionPending),
                    onClick = onRefresh,
                    size = 36.dp,
                )
            }
        }
        BrowserOverflowMenu(
            enabled = snapshot.available,
            resetEnabled = snapshot.available && !actionPending,
            onOpenExternal = onOpenExternal,
            onReset = onReset,
        )
    }
}

@Composable
private fun BrowserOverflowMenu(
    enabled: Boolean,
    resetEnabled: Boolean,
    onOpenExternal: () -> Unit,
    onReset: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val items = listOf(
        DropdownItem(
            text = stringResource(R.string.browser_open_external),
            enabled = enabled,
            icon = { modifier ->
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = modifier.size(20.dp))
            },
        ),
        DropdownItem(
            text = stringResource(R.string.browser_reset_session),
            enabled = resetEnabled,
            icon = { modifier ->
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = modifier.size(20.dp))
            },
        ),
    )
    Box {
        BrowserControlButton(
            icon = Icons.Rounded.MoreVert,
            description = stringResource(R.string.action_more),
            enabled = enabled,
            onClick = { expanded = true },
        )
        WindowListPopup(
            show = expanded,
            alignment = PopupPositionProvider.Align.End,
            onDismissRequest = { expanded = false },
        ) {
            ListPopupColumn {
                items.forEachIndexed { index, item ->
                    DropdownImpl(
                        item = item,
                        optionSize = items.size,
                        isSelected = false,
                        index = index,
                        onSelectedIndexChange = {
                            expanded = false
                            if (index == 0) onOpenExternal() else onReset()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserControlButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    size: Dp = 40.dp,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        minWidth = size,
        minHeight = size,
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.34f)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                if (!enabled) disabled()
            },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}

private enum class BrowserOverlay {
    None,
    Empty,
    Loading,
    Failed,
}

/**
 * 加载进度条悬浮在网页顶部，不占布局；提取到 BoxScope 扩展中，避免与外层
 * ColumnScope 的 AnimatedVisibility 重载冲突。
 */
@Composable
private fun BoxScope.BrowserLoadingProgress(snapshot: BrowserSessionSnapshot) {
    AnimatedVisibility(
        visible = snapshot.isLoading && snapshot.available,
        modifier = Modifier.align(Alignment.TopCenter),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        LinearProgressIndicator(
            progress = snapshot.progress
                .takeIf { it in 1..99 }
                ?.let { it / 100f },
            modifier = Modifier.fillMaxWidth(),
            height = 2.dp,
        )
    }
}

/**
 * 内容状态浮层。已有提交页面时导航/刷新保持旧页面可见，只显示顶部进度条，
 * 避免每次加载都用占位页盖住当前内容造成闪烁。
 */
@Composable
private fun BoxScope.BrowserStateOverlay(
    snapshot: BrowserSessionSnapshot,
    onRetry: () -> Unit,
) {
    val overlay = when {
        !snapshot.available -> BrowserOverlay.Empty
        !snapshot.hasCommittedPage && snapshot.error != null -> BrowserOverlay.Failed
        !snapshot.hasCommittedPage -> BrowserOverlay.Loading
        else -> BrowserOverlay.None
    }
    Crossfade(
        targetState = overlay,
        label = "browser_overlay",
        modifier = Modifier.fillMaxSize(),
    ) { state ->
        when (state) {
            BrowserOverlay.Empty -> BrowserEmptyState(modifier = Modifier.fillMaxSize())
            BrowserOverlay.Loading -> BrowserLoadingState(
                host = snapshot.host,
                modifier = Modifier.fillMaxSize(),
            )
            BrowserOverlay.Failed -> BrowserFailedState(
                error = snapshot.error,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )
            BrowserOverlay.None -> Unit
        }
    }
}

/** 接管或出错时在地址栏下出现一条细状态条，不再用整张卡片挤占网页高度。 */
@Composable
private fun ColumnScope.BrowserStatusStrip(snapshot: BrowserSessionSnapshot) {
    val error = snapshot.error?.takeIf { snapshot.hasCommittedPage }
    val message = when {
        error != null -> error
        snapshot.isUserControlling && snapshot.available -> stringResource(R.string.browser_user_controlling)
        else -> null
    }
    val color = if (error != null) StatusError else MiuixTheme.colorScheme.primary
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = 0.08f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (error != null) Icons.Rounded.GppMaybe else Icons.Rounded.AdsClick,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = color,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message.orEmpty(),
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BrowserOverlayIcon(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .squircleSurface(
                color = tint.copy(alpha = 0.10f),
                cornerRadius = 20.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = tint,
        )
    }
}

/**
 * 占位状态覆盖在 WebView 之上，拦截触摸，避免用户点到尚未完成渲染的页面。
 */
private fun Modifier.consumeTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { change ->
                change.consume()
            }
        }
    }
}

@Composable
private fun BrowserEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .consumeTouches()
            .background(MiuixTheme.colorScheme.surface)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrowserOverlayIcon(
            icon = Icons.Rounded.Language,
            tint = MiuixTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.ui_the_browser_has_not_opened_the_web_page_yet_31e095),
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.ui_enter_the_url_in_the_address_bar_or_let_the_agent_br_e2ae90),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BrowserLoadingState(
    host: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .consumeTouches()
            .background(MiuixTheme.colorScheme.surface)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        InfiniteProgressIndicator(
            color = MiuixTheme.colorScheme.primary,
            size = 34.dp,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (host.isBlank()) stringResource(R.string.browser_opening) else stringResource(R.string.browser_opening_host, host),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BrowserFailedState(
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .consumeTouches()
            .background(MiuixTheme.colorScheme.surface)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrowserOverlayIcon(
            icon = Icons.Rounded.GppMaybe,
            tint = StatusError,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.ui_the_webpage_cannot_be_opened_3db06d),
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
        )
        if (!error.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = error,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        EtaTextButton(
            text = stringResource(R.string.ui_reload_5982c4),
            onClick = onRetry,
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}

@Composable
private fun BrowserWebViewHost(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val backgroundColor = MiuixTheme.colorScheme.surface.toArgb()
    val container = remember(context) {
        FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(backgroundColor)
        }
    }
    DisposableEffect(container, context) {
        AgentBrowserSession.attachTo(container, context)
        onDispose { AgentBrowserSession.detachFrom(container) }
    }
    AndroidView(
        factory = { container },
        update = { view -> view.setBackgroundColor(backgroundColor) },
        modifier = modifier,
    )
}
