package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.backdrop.couiBackdrop
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.Text

/** 顶部控件（圆钮、标题胶囊）里的字最多放大到 1.3 倍：200% 字体时三个控件不挤、模型名不被截断。 */
internal const val ChromeMaxFontScale = 1.3f

/** 把字号放大倍数限制在 [max] 以内，给顶部这类空间固定的控件用；正文不受影响。 */
@Composable
internal fun CappedFontScale(max: Float = ChromeMaxFontScale, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    if (density.fontScale <= max) content()
    else CompositionLocalProvider(LocalDensity provides Density(density.density, max)) { content() }
}

/**
 * 二级整屏页（电脑会话、终端）的顶部：与手机对话一致的液态玻璃控件——左边返回圆钮，
 * 中间标题胶囊（可带一行副标题，[onTitle] 不为空时可点），右边 [trailing]。
 * 没有右侧按钮的页面传 [balanceTrailing]，右边留一个圆钮宽的空位，标题胶囊居中。
 */
@Composable
internal fun GlassPageTopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    onTitle: (() -> Unit)?,
    sourceName: String,
    balanceTrailing: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val source = trackRenderSource(sourceName,
        if (io.github.mangi.eta.ui.app.LocalBlurEnabled.current && EtaCouiSupport.shaders)
            com.itos.coui.backdrop.rememberCouiBackdrop(diagnosticName = sourceName) else null)
    CappedFontScale {
        Box(Modifier.fillMaxWidth()) {
            if (source != null) Box(Modifier.matchParentSize().couiBackdrop(source).background(EtaTheme.colors.canvas))
            CompositionLocalProvider(io.github.mangi.eta.ui.components.coui.LocalEtaCouiBackdrop provides source) {
                Row(
                    Modifier.fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                        .heightIn(min = 60.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircleIconButton(Icons.AutoMirrored.Outlined.ArrowBackIos, stringResource(R.string.action_back), onClick = onBack)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        io.github.mangi.eta.ui.components.coui.EtaFloatingSurface(
                            modifier = Modifier.heightIn(min = 44.dp).width(IntrinsicSize.Max).widthIn(max = 260.dp),
                            shape = EtaTheme.shapes.radiusFull,
                            capsule = true,
                            contentDescription = title,
                            onClick = onTitle,
                            wrapContent = true,
                        ) {
                            Column(
                                Modifier.heightIn(min = 44.dp).padding(horizontal = 18.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(title, style = EtaTheme.type.subhead.copy(fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
                                    color = EtaTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!subtitle.isNullOrBlank()) Text(subtitle,
                                    style = EtaTheme.type.caption.copy(fontWeight = FontWeight.Normal, lineHeight = 14.sp),
                                    color = EtaTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    trailing()
                    if (balanceTrailing) Spacer(Modifier.size(44.dp))
                }
            }
        }
    }
}
