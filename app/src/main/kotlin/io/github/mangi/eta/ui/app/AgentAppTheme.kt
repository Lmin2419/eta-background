package io.github.mangi.eta.ui.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.fillMaxSize
import io.github.mangi.eta.data.model.AppearanceAccentColor
import io.github.mangi.eta.data.model.AppearancePaletteStyle
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.model.AppearanceThemeMode
import io.github.mangi.eta.ui.theme.EtaTheme
import io.github.mangi.eta.ui.theme.resolveEtaColors
import io.github.mangi.eta.ui.theme.seedColor
import io.github.mangi.eta.ui.theme.toMiuixColors
import io.github.mangi.eta.ui.theme.usesGeneratedAccent
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.platformDynamicColors

@Composable
fun AgentAppTheme(
    appearance: AppearanceSettings,
    applyInterfaceScale: Boolean,
    onResolvedDarkModeChange: (Boolean) -> Unit = {},
    /**
     * 是否放 COUI 2.1.0 的弹层宿主（菜单、对话框、半窗画在它上面，14.4b）。整页的 Activity 要放；
     * 悬浮窗、任务预览这类自己定尺寸、不弹菜单和对话框的小窗口不放（宿主会撑满窗口）。
     */
    couiOverlayHost: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (appearance.themeMode) {
        AppearanceThemeMode.SYSTEM -> systemDark
        AppearanceThemeMode.LIGHT -> false
        AppearanceThemeMode.DARK -> true
    }
    val colorSchemeMode = when {
        !appearance.usesGeneratedAccent && appearance.themeMode == AppearanceThemeMode.LIGHT -> ColorSchemeMode.Light
        !appearance.usesGeneratedAccent && appearance.themeMode == AppearanceThemeMode.DARK -> ColorSchemeMode.Dark
        !appearance.usesGeneratedAccent -> ColorSchemeMode.System
        appearance.themeMode == AppearanceThemeMode.LIGHT -> ColorSchemeMode.MonetLight
        appearance.themeMode == AppearanceThemeMode.DARK -> ColorSchemeMode.MonetDark
        else -> ColorSchemeMode.MonetSystem
    }
    val systemSeedColor = if (
        appearance.monetEnabled && appearance.accentColor == AppearanceAccentColor.SYSTEM
    ) {
        platformDynamicColors(isDark).primary
    } else {
        null
    }
    val keyColor = when {
        !appearance.usesGeneratedAccent -> null
        appearance.accentColor == AppearanceAccentColor.SYSTEM -> systemSeedColor
        else -> appearance.accentColor.seedColor()
    }
    val controller = remember(appearance, colorSchemeMode, keyColor, isDark) {
        ThemeController(
            colorSchemeMode = colorSchemeMode,
            keyColor = keyColor,
            colorSpec = ThemeColorSpec.Spec2025,
            paletteStyle = appearance.paletteStyle.toMiuixPaletteStyle(),
            isDark = isDark,
        )
    }
    val colors = controller.currentColors()
    val etaColors = resolveEtaColors(appearance, isDark, colors)
    val themedColors = remember(colors, etaColors) { etaColors.toMiuixColors(colors) }

    LaunchedEffect(isDark) { onResolvedDarkModeChange(isDark) }

    MiuixTheme(colors = themedColors) {
        val platformDensity = LocalDensity.current
        val appDensity = remember(platformDensity, appearance.interfaceScale, applyInterfaceScale) {
            if (applyInterfaceScale) {
                Density(
                    density = platformDensity.density * appearance.interfaceScale,
                    fontScale = platformDensity.fontScale,
                )
            } else {
                platformDensity
            }
        }
        CompositionLocalProvider(
            LocalAppearanceSettings provides appearance,
            // 系统省电模式下真模糊自动降为磨砂底色。
            LocalBlurEnabled provides (appearance.blurEnabled && !rememberPowerSaveMode()),
            LocalTopBarBlurStyle provides appearance.topBarBlurStyle,
            LocalControlMaterial provides appearance.controlMaterial,
            LocalPlatformDensity provides platformDensity,
            LocalDensity provides appDensity,
        ) {
            // 旧 COUI 库（按钮、顶栏、液态玻璃）的主题：强调色沿用胖小胖的珊瑚色。
            // 菜单、对话框、半窗从 14.4b 起换成 COUI 2.1.0，画在下面 EtaCouiOverlayRoot 的弹层宿主上。
            val blurOn = appearance.blurEnabled && top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported()
            val couiScheme = remember(isDark, etaColors) {
                (if (isDark) io.github.mangi.eta.thirdparty.coui.theme.CouiPresets.Dark else io.github.mangi.eta.thirdparty.coui.theme.CouiPresets.Light).copy(
                    accent = etaColors.accent,
                    accentOnSurface = etaColors.accent,
                    checkboxAccent = etaColors.accent,
                    menuCheckboxAccent = etaColors.accent,
                    background = etaColors.canvas,
                )
            }
            CompositionLocalProvider(
                io.github.mangi.eta.thirdparty.coui.theme.LocalCouiDarkTheme provides isDark,
                io.github.mangi.eta.thirdparty.coui.effect.LocalCouiContourGlowEnabled provides blurOn,
            ) {
                io.github.mangi.eta.thirdparty.coui.theme.CouiTheme(darkTheme = isDark, colorScheme = couiScheme) {
                    EtaTheme(colors = etaColors) {
                        if (couiOverlayHost) {
                            // 整窗内容被记录下来，供 COUI 菜单、对话框、半窗做材质模糊（不含这些弹层自己）。
                            io.github.mangi.eta.ui.components.coui.EtaCouiOverlayRoot(LocalBlurEnabled.current) { pageRecording ->
                                androidx.compose.foundation.layout.Box(
                                    androidx.compose.ui.Modifier.fillMaxSize().then(pageRecording),
                                ) { content() }
                            }
                        } else {
                            content()
                        }
                    }
                }
            }
        }
    }
}

private fun AppearancePaletteStyle.toMiuixPaletteStyle(): ThemePaletteStyle = when (this) {
    AppearancePaletteStyle.TONAL_SPOT -> ThemePaletteStyle.TonalSpot
    AppearancePaletteStyle.NEUTRAL -> ThemePaletteStyle.Neutral
    AppearancePaletteStyle.VIBRANT -> ThemePaletteStyle.Vibrant
    AppearancePaletteStyle.EXPRESSIVE -> ThemePaletteStyle.Expressive
    AppearancePaletteStyle.RAINBOW -> ThemePaletteStyle.Rainbow
    AppearancePaletteStyle.FRUIT_SALAD -> ThemePaletteStyle.FruitSalad
    AppearancePaletteStyle.MONOCHROME -> ThemePaletteStyle.Monochrome
    AppearancePaletteStyle.FIDELITY -> ThemePaletteStyle.Fidelity
    AppearancePaletteStyle.CONTENT -> ThemePaletteStyle.Content
}

/** 系统省电模式；变化时重组。 */
@androidx.compose.runtime.Composable
private fun rememberPowerSaveMode(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val power = androidx.compose.runtime.remember(context) {
        context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
    }
    val state = androidx.compose.runtime.remember(power) { androidx.compose.runtime.mutableStateOf(power?.isPowerSaveMode == true) }
    androidx.compose.runtime.DisposableEffect(context, power) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) {
                state.value = power?.isPowerSaveMode == true
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver,
            android.content.IntentFilter(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return state.value
}
