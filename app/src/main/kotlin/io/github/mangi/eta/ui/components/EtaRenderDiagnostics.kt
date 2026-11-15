package io.github.mangi.eta.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.Text

/**
 * 渲染诊断（etax/design/07 第四期）：页面上有哪些背景采样源（每个源都要把它下面的内容多录一遍），
 * 以及最近 2 秒的帧数、超时帧和平均帧耗时。先在真机上量，再决定还能省掉哪些源。
 */
internal object EtaRenderDiagnostics {
    /**
     * 名字 → 当前有几个在用。所有采样源创建处都登记，不论诊断开关开没开（只是一次计数）。
     * 普通的主线程 Map，不是 Compose 状态：每个页面进出都会写它，写 Compose 全局状态会白白触发全局快照通知
     * （本机测试里还会让同一进程后面的测试收不到通知）。诊断条每 250ms 刷新时重新读。
     */
    private val counts = LinkedHashMap<String, Int>()

    val sources: Map<String, Int> get() = counts.toMap()

    internal fun attach(name: String) { counts[name] = (counts[name] ?: 0) + 1 }

    internal fun detach(name: String) {
        val left = (counts[name] ?: 1) - 1
        if (left <= 0) counts.remove(name) else counts[name] = left
    }

    fun total(): Int = counts.values.sum()

    fun sourceSummary(): String = counts.entries.sortedBy { it.key }
        .joinToString("、") { (name, count) -> if (count > 1) "$name×$count" else name }
}

/** 登记一个采样源，离开组合时注销；返回原值，方便直接包在创建处。 */
@Composable
internal fun <T : Any> trackRenderSource(name: String, source: T?): T? {
    if (source != null) DisposableEffect(source, name) {
        EtaRenderDiagnostics.attach(name)
        onDispose { EtaRenderDiagnostics.detach(name) }
    }
    return source
}

internal data class FrameWindowStats(val frames: Int = 0, val late: Int = 0, val avgMs: Float = 0f, val maxMs: Float = 0f)

/** 最近 [windowMs] 内的帧耗时统计；超过一个刷新周期算超时。 */
internal class FrameWindow(private val windowMs: Long = 2_000) {
    private val samples = ArrayDeque<Pair<Long, Long>>() // 结束时刻 ns, 耗时 ns

    fun add(endNs: Long, durationNs: Long) {
        samples.addLast(endNs to durationNs)
        while (samples.isNotEmpty() && endNs - samples.first().first > windowMs * 1_000_000) samples.removeFirst()
    }

    fun stats(periodNs: Long): FrameWindowStats {
        if (samples.isEmpty()) return FrameWindowStats()
        val durations = samples.map { it.second }
        return FrameWindowStats(
            frames = durations.size,
            late = durations.count { it > periodNs },
            avgMs = durations.average().toFloat() / 1_000_000f,
            maxMs = durations.max() / 1_000_000f,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 诊断开关打开时浮在页面顶部的一行小字；点一下把完整报告复制到剪贴板。 */
@Composable
internal fun RenderDiagnosticsOverlay(blurEnabled: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val refreshHz = view.display?.refreshRate?.takeIf { it > 1f } ?: 60f
    val periodNs = (1_000_000_000f / refreshHz).toLong()
    var stats by remember { mutableStateOf(FrameWindowStats()) }
    var gpuMs by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(view) {
        val window: Window? = context.findActivity()?.window
        val frames = FrameWindow()
        val gpu = FrameWindow()
        var lastPublish = 0L
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            val now = System.nanoTime()
            frames.add(now, total)
            if (Build.VERSION.SDK_INT >= 31) gpu.add(now, metrics.getMetric(FrameMetrics.GPU_DURATION))
            // 每 250ms 刷新一次显示，避免诊断本身每帧重组。
            if (now - lastPublish > 250_000_000L) {
                lastPublish = now
                val s = frames.stats(periodNs)
                val g = if (Build.VERSION.SDK_INT >= 31) gpu.stats(periodNs).avgMs else null
                view.post { stats = s; gpuMs = g }
            }
        }
        val handler = Handler(Looper.getMainLooper())
        runCatching { window?.addOnFrameMetricsAvailableListener(listener, handler) }
        onDispose { runCatching { window?.removeOnFrameMetricsAvailableListener(listener) } }
    }
    val sources = EtaRenderDiagnostics.sourceSummary()
    val line = stringResource(R.string.eta_render_diag_line, refreshHz.toInt(), stats.frames, stats.late,
        "%.1f".format(stats.avgMs), EtaRenderDiagnostics.total())
    val copied = stringResource(R.string.eta_render_diag_copied)
    Text(
        line,
        fontSize = 11.sp,
        color = Color.White,
        maxLines = 1,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.62f), EtaTheme.shapes.radiusFull)
            .clickable {
                val report = buildString {
                    appendLine("胖小胖 渲染诊断")
                    appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("刷新率：${refreshHz.toInt()}Hz · 模糊：${if (blurEnabled) "开" else "关"} · COUI 着色器：${EtaCouiSupport.shaders}")
                    appendLine("最近 2 秒：${stats.frames} 帧 · 超时 ${stats.late} · 平均 ${"%.1f".format(stats.avgMs)}ms · 最长 ${"%.1f".format(stats.maxMs)}ms" +
                        (gpuMs?.let { " · GPU 平均 ${"%.1f".format(it)}ms" } ?: ""))
                    appendLine("采样源：$sources")
                }
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("render-diagnostics", report))
                EtaToastCenter.show(context, copied)
            }
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
