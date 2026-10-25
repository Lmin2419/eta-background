package io.github.mangi.eta.ui.screens.terminal

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.terminal.TerminalKey
import io.github.mangi.eta.agent.terminal.TerminalModifiers
import io.github.mangi.eta.ui.components.EtaMonospace
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class StickyModifier { OFF, ONCE, LOCKED }

/**
 * 键条上的 Ctrl/Alt：轻点只作用于下一次按键，长按锁定直到再次轻点。
 * 软键盘、键条与硬件键盘的输入都从这里取修饰，三条路径的组合键行为一致。
 */
@Stable
internal class ConsoleModifierKeys {
    var ctrl by mutableStateOf(StickyModifier.OFF)
        private set
    var alt by mutableStateOf(StickyModifier.OFF)
        private set

    fun tapCtrl() {
        ctrl = if (ctrl == StickyModifier.OFF) StickyModifier.ONCE else StickyModifier.OFF
    }

    fun tapAlt() {
        alt = if (alt == StickyModifier.OFF) StickyModifier.ONCE else StickyModifier.OFF
    }

    fun lockCtrl() {
        ctrl = StickyModifier.LOCKED
    }

    fun lockAlt() {
        alt = StickyModifier.LOCKED
    }

    /** 合并本次按键自带的修饰并消耗一次性修饰；锁定的修饰保持。 */
    fun consume(base: TerminalModifiers = TerminalModifiers.NONE): TerminalModifiers {
        val merged = base.copy(
            ctrl = base.ctrl || ctrl != StickyModifier.OFF,
            alt = base.alt || alt != StickyModifier.OFF,
        )
        if (ctrl == StickyModifier.ONCE) ctrl = StickyModifier.OFF
        if (alt == StickyModifier.ONCE) alt = StickyModifier.OFF
        return merged
    }
}

private sealed interface BarKey {
    data class Special(
        val key: TerminalKey,
        val label: String? = null,
        val icon: ImageVector? = null,
        val iconRotation: Float = 0f,
        val repeatable: Boolean = false,
    ) : BarKey

    data class Symbol(val text: String) : BarKey
    data object Ctrl : BarKey
    data object Alt : BarKey
    data object Paste : BarKey
}

/** 常用键在前，首屏覆盖 TUI 导航所需的 Esc、Tab、修饰键、方向键与回车。 */
private val BAR_KEYS: List<BarKey> = listOf(
    BarKey.Special(TerminalKey.ESCAPE, label = "Esc"),
    BarKey.Special(TerminalKey.TAB, label = "Tab"),
    BarKey.Ctrl,
    BarKey.Alt,
    BarKey.Special(TerminalKey.LEFT, icon = Icons.Rounded.KeyboardArrowUp, iconRotation = -90f, repeatable = true),
    BarKey.Special(TerminalKey.UP, icon = Icons.Rounded.KeyboardArrowUp, repeatable = true),
    BarKey.Special(TerminalKey.DOWN, icon = Icons.Rounded.KeyboardArrowUp, iconRotation = 180f, repeatable = true),
    BarKey.Special(TerminalKey.RIGHT, icon = Icons.Rounded.KeyboardArrowUp, iconRotation = 90f, repeatable = true),
    BarKey.Special(TerminalKey.ENTER, icon = Icons.AutoMirrored.Rounded.KeyboardReturn),
    BarKey.Special(TerminalKey.HOME, label = "Home"),
    BarKey.Special(TerminalKey.END, label = "End"),
    BarKey.Special(TerminalKey.PAGE_UP, label = "PgUp", repeatable = true),
    BarKey.Special(TerminalKey.PAGE_DOWN, label = "PgDn", repeatable = true),
    BarKey.Symbol("/"),
    BarKey.Symbol("|"),
    BarKey.Symbol("-"),
    BarKey.Symbol("~"),
    BarKey.Paste,
)

private val KEY_HEIGHT = 36.dp
private val KEY_MIN_WIDTH = 40.dp
private val KEY_CORNER = 10.dp
private const val REPEAT_DELAY_MS = 380L
private const val REPEAT_INTERVAL_MS = 55L

@Composable
internal fun ConsoleKeyBar(
    modifierKeys: ConsoleModifierKeys,
    keyboardVisible: Boolean,
    onKey: (TerminalKey) -> Unit,
    onText: (String) -> Unit,
    onPaste: () -> Unit,
    onToggleKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val fadeColor = MiuixTheme.colorScheme.surface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fadingEnd(visible = { listState.canScrollForward }, color = fadeColor),
            contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(BAR_KEYS) { key ->
                when (key) {
                    is BarKey.Special -> KeyCap(
                        label = key.label,
                        icon = key.icon,
                        iconRotation = key.iconRotation,
                        contentDescription = key.key.description(),
                        repeatable = key.repeatable,
                        onPress = { onKey(key.key) },
                    )
                    is BarKey.Symbol -> KeyCap(
                        label = key.text,
                        monospace = true,
                        contentDescription = key.text,
                        onPress = { onText(key.text) },
                    )
                    BarKey.Ctrl -> KeyCap(
                        label = "Ctrl",
                        sticky = modifierKeys.ctrl,
                        contentDescription = "Ctrl",
                        onPress = modifierKeys::tapCtrl,
                        onLongPress = modifierKeys::lockCtrl,
                    )
                    BarKey.Alt -> KeyCap(
                        label = "Alt",
                        sticky = modifierKeys.alt,
                        contentDescription = "Alt",
                        onPress = modifierKeys::tapAlt,
                        onLongPress = modifierKeys::lockAlt,
                    )
                    BarKey.Paste -> KeyCap(
                        icon = Icons.Rounded.ContentPaste,
                        contentDescription = stringResource(R.string.terminal_key_paste),
                        onPress = onPaste,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .width(0.5.dp)
                .height(20.dp)
                .background(MiuixTheme.colorScheme.dividerLine),
        )
        KeyCap(
            icon = if (keyboardVisible) Icons.Rounded.KeyboardHide else Icons.Rounded.Keyboard,
            contentDescription = stringResource(
                if (keyboardVisible) R.string.terminal_keyboard_hide else R.string.terminal_keyboard_show,
            ),
            subtle = true,
            onPress = onToggleKeyboard,
            modifier = Modifier.padding(start = 6.dp, end = 10.dp),
        )
    }
}

@Composable
private fun KeyCap(
    contentDescription: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    iconRotation: Float = 0f,
    monospace: Boolean = false,
    sticky: StickyModifier = StickyModifier.OFF,
    subtle: Boolean = false,
    repeatable: Boolean = false,
    onLongPress: (() -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    var pressed by remember { mutableStateOf(false) }

    val idle = if (subtle) Color.Transparent else colors.surfaceContainer
    val background by animateColorAsState(
        targetValue = when {
            sticky == StickyModifier.LOCKED -> colors.primary
            sticky == StickyModifier.ONCE -> colors.primary.copy(alpha = 0.16f)
            pressed -> colors.surfaceContainerHighest
            else -> idle
        },
        label = "keyBackground",
    )
    val content = when (sticky) {
        StickyModifier.LOCKED -> colors.onPrimary
        StickyModifier.ONCE -> colors.primary
        StickyModifier.OFF -> if (subtle) colors.onSurfaceVariantSummary else colors.onSurface
    }

    Box(
        modifier = modifier
            .height(KEY_HEIGHT)
            .widthIn(min = KEY_MIN_WIDTH)
            .squircleBackground(background, KEY_CORNER)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            }
            .pointerInput(repeatable, onLongPress != null) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        // 可连发键按下即发送，长按后按固定节奏重复；其余键在抬起时才触发，以便区分长按。
                        val repeater = if (repeatable) {
                            currentOnPress()
                            scope.launch {
                                delay(REPEAT_DELAY_MS)
                                while (true) {
                                    currentOnPress()
                                    delay(REPEAT_INTERVAL_MS)
                                }
                            }
                        } else {
                            null
                        }
                        tryAwaitRelease()
                        repeater?.cancel()
                        pressed = false
                    },
                    onTap = { if (!repeatable) currentOnPress() },
                    onLongPress = currentOnLongPress?.let { longPress ->
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            longPress()
                        }
                    },
                )
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(iconRotation),
            )
            label != null -> Text(
                text = label,
                style = MiuixTheme.textStyles.footnote1.let {
                    if (monospace) it.copy(fontFamily = EtaMonospace) else it
                },
                fontWeight = FontWeight.Medium,
                color = content,
                maxLines = 1,
            )
        }
    }
}

/** 还能向后滚动时在末端淡出，提示键条可横向滑动；滚动状态只在绘制阶段读取。 */
private fun Modifier.fadingEnd(visible: () -> Boolean, color: Color): Modifier = drawWithContent {
    drawContent()
    if (visible()) {
        val fadeWidth = 28.dp.toPx()
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, color),
                startX = size.width - fadeWidth,
                endX = size.width,
            ),
            topLeft = Offset(size.width - fadeWidth, 0f),
            size = Size(fadeWidth, size.height),
        )
    }
}

@Composable
private fun TerminalKey.description(): String = when (this) {
    TerminalKey.LEFT -> stringResource(R.string.terminal_key_left)
    TerminalKey.UP -> stringResource(R.string.terminal_key_up)
    TerminalKey.DOWN -> stringResource(R.string.terminal_key_down)
    TerminalKey.RIGHT -> stringResource(R.string.terminal_key_right)
    TerminalKey.ENTER -> stringResource(R.string.terminal_key_enter)
    else -> name.lowercase().replaceFirstChar { it.uppercase() }
}
