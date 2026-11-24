/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * 来源：io.github.itoseo:coui 0.0.1-alpha11（包名 com.itos.coui），胖小胖把液态玻璃用到的部分搬进仓库，
 * 只改了包名（见 etax/third_party/coui/NOTICE.md）。
 */
package io.github.mangi.eta.thirdparty.coui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** 进程级默认策略。只在 Application.onCreate 初始化一次，不持有宿主主题或 Context。 */
object Coui {
    @Volatile
    private var initialized = false
    @Volatile
    internal var defaultStyle: CouiStyle = NativeCouiStyle
        private set

    @Synchronized
    fun initialize(defaultStyle: CouiStyle) {
        check(!initialized) { "Coui 已初始化，不能重复设置默认样式" }
        this.defaultStyle = defaultStyle
        initialized = true
    }
}

/** 可选适配模块仅解析主题默认值、适配内容作用域和提供最小交互尺寸。 */
interface CouiStyle {
    @Composable
    fun themeDefaults(dark: Boolean): CouiThemeDefaults

    @Composable
    fun ProvideContent(color: Color, textStyle: TextStyle?, mergeTextStyle: Boolean, content: @Composable () -> Unit)

    @Composable
    fun minimumInteractiveComponentSize(): Modifier
}

@Immutable
data class CouiThemeDefaults(
    val colorScheme: CouiColorScheme,
    val typography: CouiTypography = CouiTypography(),
    val materialTint: Color = Color.Transparent,
)

internal object NativeCouiStyle : CouiStyle {
    @Composable
    override fun themeDefaults(dark: Boolean) = CouiThemeDefaults(if (dark) CouiPresets.Dark else CouiPresets.Light)

    @Composable
    override fun ProvideContent(color: Color, textStyle: TextStyle?, mergeTextStyle: Boolean, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalCouiContentColor provides if (color.isSpecified) color else CouiTheme.contentColor,
            LocalCouiTextStyle provides if (mergeTextStyle) LocalCouiTextStyle.current.merge(textStyle) else textStyle ?: TextStyle.Default,
            content = content,
        )
    }

    @Composable
    override fun minimumInteractiveComponentSize(): Modifier = Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val width = maxOf(placeable.width, 48.dp.roundToPx())
        val height = maxOf(placeable.height, 48.dp.roundToPx())
        layout(width, height) {
            placeable.place(((width - placeable.width) / 2f).roundToInt(),
                ((height - placeable.height) / 2f).roundToInt())
        }
    }
}

@Composable
internal fun ProvideCouiContent(color: Color = Color.Unspecified, textStyle: TextStyle? = null,
    mergeTextStyle: Boolean = true,
    content: @Composable () -> Unit) {
    Coui.defaultStyle.ProvideContent(color, textStyle, mergeTextStyle, content)
}
