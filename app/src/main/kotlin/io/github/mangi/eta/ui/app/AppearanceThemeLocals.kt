package io.github.mangi.eta.ui.app

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.model.AppearanceTopBarBlurStyle

internal val LocalAppearanceSettings = staticCompositionLocalOf { AppearanceSettings() }
internal val LocalBlurEnabled = staticCompositionLocalOf { true }
internal val LocalTopBarBlurStyle = staticCompositionLocalOf { AppearanceTopBarBlurStyle.GAUSSIAN }
/** 浮动控件（对话页顶部按钮、输入框、侧栏底部栏）的材质，见 [io.github.mangi.eta.data.model.AppearanceControlMaterial]。 */
internal val LocalControlMaterial = staticCompositionLocalOf { io.github.mangi.eta.data.model.AppearanceControlMaterial.LIQUID_GLASS }
internal val LocalPlatformDensity = staticCompositionLocalOf<Density?> { null }
