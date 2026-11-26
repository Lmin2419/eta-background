package io.github.mangi.eta.ui

import io.github.mangi.eta.ui.components.EtaAnimatedVisibility as AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceAccentColor
import io.github.mangi.eta.data.model.AppearancePaletteStyle
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.model.AppearanceThemeMode
import io.github.mangi.eta.data.model.AppearanceTopBarBlurStyle
import io.github.mangi.eta.data.model.MAX_INTERFACE_SCALE
import io.github.mangi.eta.data.model.MIN_INTERFACE_SCALE
import io.github.mangi.eta.data.model.normalizeInterfaceScale
import io.github.mangi.eta.data.repository.AppearanceSettingsRepository
import io.github.mangi.eta.ui.app.LocalAppearanceSettings
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaCard
import io.github.mangi.eta.ui.components.EtaOverlayDropdownPreference
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.EtaSwitchPreference
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Slider
import androidx.compose.runtime.collectAsState
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppearanceSettingsScreen(onBack: () -> Unit, onOpenCameraRing: () -> Unit = {}) {
    val appearance = LocalAppearanceSettings.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var scaleDraft by remember(appearance.interfaceScale) {
        mutableFloatStateOf(appearance.interfaceScale * 100f)
    }
    var showScaleDialog by remember { mutableStateOf(false) }
    var scaleInput by remember { mutableStateOf("") }
    val blurSupported = isRuntimeShaderSupported()

    fun update(transform: (AppearanceSettings) -> AppearanceSettings) {
        coroutineScope.launch {
            AppearanceSettingsRepository.update(transform)
        }
    }

    fun commitScale(percent: Float) {
        val scale = normalizeInterfaceScale(percent.roundToInt() / 100f)
        scaleDraft = scale * 100f
        update { current -> current.copy(interfaceScale = scale) }
    }

    val themeModes = AppearanceThemeMode.entries
    val themeModeLabels = listOf(
        stringResource(R.string.appearance_theme_system),
        stringResource(R.string.appearance_theme_light),
        stringResource(R.string.appearance_theme_dark),
    )
    val paletteStyles = AppearancePaletteStyle.entries
    val paletteLabels = listOf(
        stringResource(R.string.appearance_palette_tonal_spot),
        stringResource(R.string.appearance_palette_neutral),
        stringResource(R.string.appearance_palette_vibrant),
        stringResource(R.string.appearance_palette_expressive),
        stringResource(R.string.appearance_palette_rainbow),
        stringResource(R.string.appearance_palette_fruit_salad),
        stringResource(R.string.appearance_palette_monochrome),
        stringResource(R.string.appearance_palette_fidelity),
        stringResource(R.string.appearance_palette_content),
    )
    val accentColors = AppearanceAccentColor.entries
    val accentLabels = listOf(
        stringResource(R.string.appearance_accent_system),
        stringResource(R.string.appearance_accent_blue),
        stringResource(R.string.appearance_accent_purple),
        stringResource(R.string.appearance_accent_pink),
        stringResource(R.string.appearance_accent_red),
        stringResource(R.string.appearance_accent_orange),
        stringResource(R.string.appearance_accent_yellow),
        stringResource(R.string.appearance_accent_green),
        stringResource(R.string.appearance_accent_teal),
    )
    val blurStyles = AppearanceTopBarBlurStyle.entries
    val blurStyleLabels = listOf(
        stringResource(R.string.appearance_blur_style_gaussian),
        stringResource(R.string.appearance_blur_style_progressive),
    )
    val blurStyleSummaries = listOf(
        stringResource(R.string.appearance_blur_style_gaussian_summary),
        stringResource(R.string.appearance_blur_style_progressive_summary),
    )

    MiuixScaffoldPage(
        title = stringResource(R.string.appearance_title),
        onBack = onBack,
    ) {
        item(key = "appearance_color_title") {
            EtaPreferenceGroupTitle(text = stringResource(R.string.appearance_group_color))
        }
        item(key = "appearance_color_card") {
            EtaPreferenceGroup(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                EtaOverlayDropdownPreference(
                    title = stringResource(R.string.appearance_theme_mode),
                    summary = themeModeLabels[appearance.themeMode.ordinal],
                    items = themeModeLabels,
                    selectedIndex = appearance.themeMode.ordinal,
                    onSelectedIndexChange = { index ->
                        themeModes.getOrNull(index)?.let { mode ->
                            update { current -> current.copy(themeMode = mode) }
                        }
                    },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = stringResource(R.string.appearance_monet),
                    summary = stringResource(R.string.appearance_monet_summary),
                    checked = appearance.monetEnabled,
                    onCheckedChange = { enabled ->
                        update { current -> current.copy(monetEnabled = enabled) }
                    },
                )
                AnimatedVisibility(
                    visible = appearance.monetEnabled,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                ) {
                    Column {
                        EtaPreferenceDivider(hasLeading = false)
                        EtaOverlayDropdownPreference(
                            title = stringResource(R.string.appearance_palette_style),
                            summary = paletteLabels[appearance.paletteStyle.ordinal],
                            items = paletteLabels,
                            selectedIndex = appearance.paletteStyle.ordinal,
                            onSelectedIndexChange = { index ->
                                paletteStyles.getOrNull(index)?.let { style ->
                                    update { current -> current.copy(paletteStyle = style) }
                                }
                            },
                        )
                        EtaPreferenceDivider(hasLeading = false)
                        EtaOverlayDropdownPreference(
                            title = stringResource(R.string.appearance_accent_color),
                            summary = accentLabels[appearance.accentColor.ordinal],
                            items = accentLabels,
                            selectedIndex = appearance.accentColor.ordinal,
                            onSelectedIndexChange = { index ->
                                accentColors.getOrNull(index)?.let { accent ->
                                    update { current -> current.copy(accentColor = accent) }
                                }
                            },
                        )
                        EtaPreferenceDivider(hasLeading = false)
                        EtaSwitchPreference(
                            title = stringResource(R.string.appearance_pure_black),
                            summary = stringResource(R.string.appearance_pure_black_summary),
                            checked = appearance.pureBlackEnabled,
                            onCheckedChange = { enabled ->
                                update { current -> current.copy(pureBlackEnabled = enabled) }
                            },
                        )
                    }
                }
            }
        }

        item(key = "appearance_interface_title") {
            EtaPreferenceGroupTitle(text = stringResource(R.string.appearance_group_interface))
        }
        item(key = "appearance_interface_card") {
            EtaPreferenceGroup(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                // 流光：智能 / 整圈 / 关闭（关闭 = 原开关关）；开着时下面再选亮度。
                val glowLabels = listOf(
                    stringResource(R.string.appearance_glow_smart),
                    stringResource(R.string.appearance_glow_full),
                    stringResource(R.string.appearance_glow_off),
                )
                val glowSummaries = listOf(
                    stringResource(R.string.appearance_glow_summary_smart),
                    stringResource(R.string.appearance_glow_summary_full),
                    stringResource(R.string.appearance_glow_summary_off),
                )
                val glowIndex = if (appearance.runtimeGlowEnabled) appearance.runtimeGlowStyle.ordinal else 2
                EtaOverlayDropdownPreference(
                    title = stringResource(R.string.appearance_runtime_glow),
                    summary = glowSummaries[glowIndex],
                    items = glowLabels,
                    selectedIndex = glowIndex,
                    onSelectedIndexChange = { index ->
                        update { current ->
                            val style = io.github.mangi.eta.data.model.AppearanceGlowStyle.entries.getOrNull(index)
                            if (style == null) current.copy(runtimeGlowEnabled = false)
                            else current.copy(runtimeGlowEnabled = true, runtimeGlowStyle = style)
                        }
                    },
                )
                AnimatedVisibility(
                    visible = appearance.runtimeGlowEnabled,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                ) {
                    Column {
                        EtaPreferenceDivider(hasLeading = false)
                        val brightnessLabels = listOf(
                            stringResource(R.string.appearance_glow_soft),
                            stringResource(R.string.appearance_glow_standard),
                        )
                        EtaOverlayDropdownPreference(
                            title = stringResource(R.string.appearance_glow_brightness),
                            summary = stringResource(if (appearance.runtimeGlowIntensity == io.github.mangi.eta.data.model.AppearanceGlowIntensity.SOFT)
                                R.string.appearance_glow_summary_soft else R.string.appearance_glow_summary_standard),
                            items = brightnessLabels,
                            selectedIndex = appearance.runtimeGlowIntensity.ordinal,
                            onSelectedIndexChange = { index ->
                                io.github.mangi.eta.data.model.AppearanceGlowIntensity.entries.getOrNull(index)?.let { intensity ->
                                    update { current -> current.copy(runtimeGlowIntensity = intensity) }
                                }
                            },
                        )
                        // 电脑任务也亮流光（2026111620）：与「设置 → 电脑」里的开关是同一个；开了电脑同步才显示。
                        val desktopPrefs by androidx.compose.runtime.remember {
                            io.github.mangi.eta.agent.remote.DesktopLinkSettings.init(context)
                        }.collectAsState()
                        if (desktopPrefs.enabled) {
                            EtaPreferenceDivider(hasLeading = false)
                            EtaSwitchPreference(
                                title = stringResource(R.string.desktop_link_glow),
                                summary = stringResource(R.string.desktop_link_glow_summary),
                                checked = desktopPrefs.glow,
                                onCheckedChange = { on ->
                                    io.github.mangi.eta.agent.remote.DesktopLinkSettings.update { it.copy(glow = on) }
                                },
                            )
                        }
                        if (appearance.runtimeGlowStyle == io.github.mangi.eta.data.model.AppearanceGlowStyle.SMART) {
                            // 2026111624：后台干活时的提示可选摄像头小光圈或顶部流动光条。
                            EtaPreferenceDivider(hasLeading = false)
                            val mark = appearance.runtimeGlowBackgroundMark
                            EtaOverlayDropdownPreference(
                                title = stringResource(R.string.appearance_glow_mark),
                                summary = stringResource(if (mark == io.github.mangi.eta.data.model.AppearanceGlowMark.TOP_BAR)
                                    R.string.appearance_glow_mark_summary_top_bar else R.string.appearance_glow_mark_summary_camera),
                                items = listOf(
                                    stringResource(R.string.appearance_glow_mark_camera),
                                    stringResource(R.string.appearance_glow_mark_top_bar),
                                ),
                                selectedIndex = mark.ordinal,
                                onSelectedIndexChange = { index ->
                                    io.github.mangi.eta.data.model.AppearanceGlowMark.entries.getOrNull(index)?.let { picked ->
                                        update { current -> current.copy(runtimeGlowBackgroundMark = picked) }
                                    }
                                },
                            )
                            // 摄像头小光圈：各家机型报的开孔不一定准，可以自己对齐。
                            if (mark == io.github.mangi.eta.data.model.AppearanceGlowMark.CAMERA) {
                                EtaPreferenceDivider(hasLeading = false)
                                EtaArrowPreference(
                                    title = stringResource(R.string.camera_ring_title),
                                    summary = stringResource(R.string.camera_ring_entry_summary),
                                    onClick = onOpenCameraRing,
                                )
                            }
                        }
                    }
                }
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = stringResource(R.string.appearance_turn_stats),
                    summary = stringResource(R.string.appearance_turn_stats_summary),
                    checked = appearance.showTurnStats,
                    onCheckedChange = { enabled ->
                        update { current -> current.copy(showTurnStats = enabled) }
                    },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = stringResource(R.string.appearance_fold_process),
                    summary = stringResource(R.string.appearance_fold_process_summary),
                    checked = appearance.foldFinishedProcess,
                    onCheckedChange = { enabled ->
                        update { current -> current.copy(foldFinishedProcess = enabled) }
                    },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = stringResource(R.string.appearance_task_completion_alerts),
                    summary = stringResource(R.string.appearance_task_completion_alerts_summary),
                    checked = appearance.taskCompletionAlertsEnabled,
                    onCheckedChange = { enabled ->
                        update { current -> current.copy(taskCompletionAlertsEnabled = enabled) }
                    },
                )
                EtaPreferenceDivider(hasLeading = false)
                // 玻璃四档：通透 / 标准 / 磨砂 / 关闭（关闭 = 原「模糊效果」关）。
                val glassLabels = listOf(
                    stringResource(R.string.appearance_glass_clear),
                    stringResource(R.string.appearance_glass_standard),
                    stringResource(R.string.appearance_glass_frosted),
                    stringResource(R.string.appearance_glass_off),
                )
                val glassSummaries = listOf(
                    stringResource(R.string.appearance_glass_summary_clear),
                    stringResource(R.string.appearance_glass_summary_standard),
                    stringResource(R.string.appearance_glass_summary_frosted),
                    stringResource(R.string.appearance_glass_summary_off),
                )
                val glassIndex = if (appearance.blurEnabled && blurSupported) appearance.glassStrength.ordinal else 3
                EtaOverlayDropdownPreference(
                    title = stringResource(R.string.appearance_glass),
                    summary = glassSummaries[glassIndex],
                    items = glassLabels,
                    selectedIndex = glassIndex,
                    enabled = blurSupported,
                    onSelectedIndexChange = { index ->
                        update { current ->
                            val strength = io.github.mangi.eta.data.model.AppearanceGlassStrength.entries.getOrNull(index)
                            if (strength == null) current.copy(blurEnabled = false)
                            else current.copy(blurEnabled = true, glassStrength = strength)
                        }
                    },
                )
                AnimatedVisibility(
                    visible = appearance.blurEnabled && blurSupported,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                ) {
                    // AnimatedVisibility 的内容按层叠摆放，分割线和行必须包进 Column 才会上下排列。
                    Column {
                        EtaPreferenceDivider(hasLeading = false)
                        EtaOverlayDropdownPreference(
                            title = stringResource(R.string.appearance_blur_style),
                            summary = blurStyleSummaries[appearance.topBarBlurStyle.ordinal],
                            items = blurStyleLabels,
                            selectedIndex = appearance.topBarBlurStyle.ordinal,
                            onSelectedIndexChange = { index ->
                                blurStyles.getOrNull(index)?.let { style ->
                                    update { current -> current.copy(topBarBlurStyle = style) }
                                }
                            },
                        )
                    }
                }
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = stringResource(R.string.appearance_high_refresh_rate),
                    summary = stringResource(R.string.appearance_high_refresh_rate_summary),
                    checked = io.github.mangi.eta.ui.app.EtaRefreshRate.enabled(appearance),
                    onCheckedChange = { enabled ->
                        update { current -> current.copy(highRefreshRate = enabled) }
                    },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = stringResource(R.string.appearance_swipe_dismiss),
                    summary = stringResource(R.string.appearance_swipe_dismiss_summary),
                    checked = appearance.swipeDismissEnabled,
                    onCheckedChange = { enabled ->
                        update { current -> current.copy(swipeDismissEnabled = enabled) }
                    },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = stringResource(R.string.appearance_predictive_back),
                    summary = stringResource(R.string.appearance_predictive_back_summary),
                    checked = appearance.predictiveBackEnabled,
                    onCheckedChange = { enabled ->
                        update { current -> current.copy(predictiveBackEnabled = enabled) }
                    },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaSwitchPreference(
                    title = stringResource(R.string.appearance_render_diagnostics),
                    summary = stringResource(R.string.appearance_render_diagnostics_summary),
                    checked = appearance.renderDiagnosticsEnabled,
                    onCheckedChange = { enabled ->
                        update { current -> current.copy(renderDiagnosticsEnabled = enabled) }
                    },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaArrowPreference(
                    title = stringResource(R.string.appearance_interface_scale),
                    summary = stringResource(R.string.appearance_interface_scale_summary),
                    endActions = {
                        Text(
                            text = "${scaleDraft.roundToInt()}%",
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    },
                    bottomAction = {
                        Slider(
                            value = scaleDraft.coerceIn(
                                MIN_INTERFACE_SCALE * 100f,
                                MAX_INTERFACE_SCALE * 100f,
                            ),
                            onValueChange = { scaleDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            valueRange = (MIN_INTERFACE_SCALE * 100f)..(MAX_INTERFACE_SCALE * 100f),
                            onValueChangeFinished = { commitScale(scaleDraft) },
                            showKeyPoints = true,
                            keyPoints = listOf(80f, 90f, 100f, 110f),
                            magnetThreshold = 0.01f,
                            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                        )
                    },
                    onClick = {
                        scaleInput = scaleDraft.roundToInt().toString()
                        showScaleDialog = true
                    },
                    holdDownState = showScaleDialog,
                )
            }
        }
    }

    val parsedScale = scaleInput.toIntOrNull()
    EtaWindowDialog(
        show = showScaleDialog,
        title = stringResource(R.string.appearance_interface_scale_dialog_title),
        summary = stringResource(R.string.appearance_interface_scale_dialog_summary),
        onDismissRequest = { showScaleDialog = false },
    ) {
        Column {
            TextField(
                value = scaleInput,
                onValueChange = { value -> scaleInput = value.filter(Char::isDigit).take(3) },
                label = stringResource(R.string.appearance_interface_scale_input_label),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            MiuixDialogActions(
                confirmText = stringResource(R.string.action_confirm),
                confirmEnabled = parsedScale != null && parsedScale in 80..110,
                onCancel = { showScaleDialog = false },
                onConfirm = {
                    parsedScale?.let { commitScale(it.toFloat()) }
                    showScaleDialog = false
                },
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
