package io.github.mangi.eta.data.model

import kotlinx.serialization.Serializable

const val MIN_INTERFACE_SCALE = 0.8f
const val MAX_INTERFACE_SCALE = 1.1f
const val DEFAULT_INTERFACE_SCALE = 1f

@Serializable
data class AppearanceSettings(
    val themeMode: AppearanceThemeMode = AppearanceThemeMode.SYSTEM,
    val monetEnabled: Boolean = false,
    val paletteStyle: AppearancePaletteStyle = AppearancePaletteStyle.TONAL_SPOT,
    val accentColor: AppearanceAccentColor = AppearanceAccentColor.SYSTEM,
    val pureBlackEnabled: Boolean = false,
    val blurEnabled: Boolean = true,
    val runtimeGlowEnabled: Boolean = true,
    /** 流光亮度：柔和 / 标准；「关闭」由 [runtimeGlowEnabled] = false 表示。2026111618 起默认柔和。 */
    val runtimeGlowIntensity: AppearanceGlowIntensity = AppearanceGlowIntensity.SOFT,
    /** 流光样式：智能（操作屏幕时四周细光、后台只亮摄像头小光圈）/ 整圈。 */
    val runtimeGlowStyle: AppearanceGlowStyle = AppearanceGlowStyle.SMART,
    /** 「智能」流光在后台干活时的提示：摄像头外圈小光圈，或屏幕最上沿的流动光条（2026111624）。 */
    val runtimeGlowBackgroundMark: AppearanceGlowMark = AppearanceGlowMark.CAMERA,
    /** 摄像头小光圈的微调（dp）：左右、上下偏移，宽、高（0 = 按开孔自动）。 */
    val cameraRingOffsetXDp: Float = 0f,
    val cameraRingOffsetYDp: Float = 0f,
    val cameraRingWidthDp: Float = 0f,
    val cameraRingHeightDp: Float = 0f,
    val taskCompletionAlertsEnabled: Boolean = true,
    /** 回答前自动复核（etax/design/10 第一期）：还有没解决的失败时，先让 Agent 复核一次再交卷。 */
    val completionReviewEnabled: Boolean = true,
    /** 11 号方案实验：紧跟在纯界面操作之后的一轮用较低思考强度。默认关。 */
    val lightGuiRoundEffort: Boolean = false,
    val topBarBlurStyle: AppearanceTopBarBlurStyle = AppearanceTopBarBlurStyle.GAUSSIAN,
    /** 14.4c：对话页顶部按钮、输入框、侧栏底部栏的材质（液态玻璃 / COUI 17 磨砂），对比试用中。 */
    val controlMaterial: AppearanceControlMaterial = AppearanceControlMaterial.LIQUID_GLASS,
    val swipeDismissEnabled: Boolean = true,
    val predictiveBackEnabled: Boolean = true,
    val interfaceScale: Float = DEFAULT_INTERFACE_SCALE,
    /** 浮层玻璃的通透程度；「关闭」由 [blurEnabled] = false 表示。 */
    val glassStrength: AppearanceGlassStrength = AppearanceGlassStrength.STANDARD,
    /** 每条回复下方显示速度与用量小字。 */
    val showTurnStats: Boolean = true,
    /** 回复完成后，思考与步骤收成一行（09 第二期 2.1）。 */
    val foldFinishedProcess: Boolean = true,
    /** 渲染诊断：页面顶部显示帧数、超时帧与背景采样源（07 第四期，默认关）。 */
    val renderDiagnosticsEnabled: Boolean = false,
) {
    fun normalized(): AppearanceSettings = copy(
        interfaceScale = normalizeInterfaceScale(interfaceScale),
    )
}

/** 屏幕流光的样式（2026111618）。 */
@Serializable
enum class AppearanceGlowMark(val persistedValue: String) {
    /** 摄像头开孔外圈的小光圈；流体云展开时可能被它挡住。 */
    CAMERA("camera"),
    /** 屏幕最上沿一道流动细光，两端顺着屏幕圆角淡出；在流体云上方，不会被挡住。 */
    TOP_BAR("top_bar");

    companion object {
        fun fromPersistedValue(value: String?): AppearanceGlowMark =
            entries.firstOrNull { it.persistedValue == value } ?: CAMERA
    }
}

enum class AppearanceGlowStyle(val persistedValue: String) {
    /** AI 操作你的屏幕时四周一圈细光；后台干活时只在摄像头外圈亮小光圈；开始、结束各提示一下。 */
    SMART("smart"),
    /** 任务运行期间屏幕四周一直亮。 */
    FULL("full");

    companion object {
        fun fromPersistedValue(value: String?): AppearanceGlowStyle =
            entries.firstOrNull { it.persistedValue == value } ?: SMART
    }
}

/**
 * 屏幕流光的亮度档位。柔和档光带更细、更透明，适合嫌标准档太显眼但又想看到运行状态的时候。
 */
@Serializable
enum class AppearanceGlowIntensity(
    val persistedValue: String,
    val haloAlpha: Float,
    val haloWidthDp: Float,
    val lineAlpha: Float,
    val lineWidthDp: Float,
    /** 语音面板唤醒光效整体透明度倍数。 */
    val wakeAlpha: Float,
) {
    SOFT("soft", haloAlpha = 0.22f, haloWidthDp = 8f, lineAlpha = 0.6f, lineWidthDp = 2f, wakeAlpha = 0.5f),
    STANDARD("standard", haloAlpha = 0.55f, haloWidthDp = 14f, lineAlpha = 0.95f, lineWidthDp = 3f, wakeAlpha = 1f);

    companion object {
        fun fromPersistedValue(value: String?): AppearanceGlowIntensity =
            entries.firstOrNull { it.persistedValue == value } ?: SOFT
    }
}

@Serializable
enum class AppearanceThemeMode(val persistedValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromPersistedValue(value: String?): AppearanceThemeMode =
            entries.firstOrNull { it.persistedValue == value } ?: SYSTEM
    }
}

@Serializable
enum class AppearancePaletteStyle(val persistedValue: String) {
    TONAL_SPOT("tonal_spot"),
    NEUTRAL("neutral"),
    VIBRANT("vibrant"),
    EXPRESSIVE("expressive"),
    RAINBOW("rainbow"),
    FRUIT_SALAD("fruit_salad"),
    MONOCHROME("monochrome"),
    FIDELITY("fidelity"),
    CONTENT("content");

    companion object {
        fun fromPersistedValue(value: String?): AppearancePaletteStyle =
            entries.firstOrNull { it.persistedValue == value } ?: TONAL_SPOT
    }
}

@Serializable
enum class AppearanceAccentColor(val persistedValue: String) {
    SYSTEM("system"),
    BLUE("blue"),
    PURPLE("purple"),
    PINK("pink"),
    RED("red"),
    ORANGE("orange"),
    YELLOW("yellow"),
    GREEN("green"),
    TEAL("teal");

    companion object {
        fun fromPersistedValue(value: String?): AppearanceAccentColor =
            entries.firstOrNull { it.persistedValue == value } ?: SYSTEM
    }
}

/** 浮动控件的材质：旧库的流体液态玻璃，或新库 COUI 2.1.0 的 COUI 17 磨砂材质（上游同款）。 */
@Serializable
enum class AppearanceControlMaterial(val persistedValue: String) {
    LIQUID_GLASS("liquid_glass"),
    COUI_FROST("coui_frost");

    companion object {
        fun fromPersistedValue(value: String?): AppearanceControlMaterial =
            entries.firstOrNull { it.persistedValue == value } ?: LIQUID_GLASS
    }
}

@Serializable
enum class AppearanceTopBarBlurStyle(val persistedValue: String) {
    GAUSSIAN("gaussian"),
    PROGRESSIVE("progressive");

    companion object {
        fun fromPersistedValue(value: String?): AppearanceTopBarBlurStyle =
            entries.firstOrNull { it.persistedValue == value } ?: GAUSSIAN
    }
}

fun normalizeInterfaceScale(value: Float): Float =
    if (value.isFinite()) {
        value.coerceIn(MIN_INTERFACE_SCALE, MAX_INTERFACE_SCALE)
    } else {
        DEFAULT_INTERFACE_SCALE
    }

/** 玻璃底色的不透明度：通透 0.50 / 标准 0.64 / 磨砂 0.80。 */
@Serializable
enum class AppearanceGlassStrength(val persistedValue: String, val tintAlpha: Float) {
    CLEAR("clear", 0.50f),
    STANDARD("standard", 0.64f),
    FROSTED("frosted", 0.80f);

    companion object {
        fun fromPersistedValue(value: String?): AppearanceGlassStrength =
            entries.firstOrNull { it.persistedValue == value } ?: STANDARD
    }
}
