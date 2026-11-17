package io.github.mangi.eta.agent.runtime

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.eta.shadow.ShadowTools
import io.github.mangi.eta.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/** Bounded, explicitly allowlisted diagnostics. No prompts, package names, IDs, keys or screenshots. */
internal object RuntimeDiagnostics {
    private data class Entry(val elapsed: Long, val event: String, val state: String)
    private val events = ArrayDeque<Entry>()
    private val states = setOf("ready", "acknowledged", "retrying", "WaitingForApp", "ReleasingScreen",
        "OpeningApp", "RestoringDisplay", "checkpoint_failed")

    @Synchronized fun record(event: String, state: String) {
        if (event !in setOf("screen_phase", "screen_cleanup", "entry_persistence") || state !in states) return
        events.addLast(Entry(SystemClock.elapsedRealtime(), event, state))
        while (events.size > 120) events.removeFirst()
    }

    /**
     * 最近 20 次运行的数字（etax/design/10 第零期）：轮数、模型与工具用时、最慢的 3 个工具、失败的工具调用数、
     * 交卷前自检次数、结尾是否像放弃了。只记数字和工具名，不记参数、对话内容或结果；存在应用私有目录的一个小 JSON 里。
     */
    fun recordRun(context: Context, summary: RunPerformanceSummary, outcome: String, gaveUp: Boolean) {
        runCatching {
            synchronized(this) {
                val file = java.io.File(context.filesDir, RUN_DIGESTS_FILE)
                val existing = runCatching { file.readText() }.getOrNull()
                file.writeText(appendRunDigest(existing, runDigest(summary, outcome, gaveUp, System.currentTimeMillis())))
            }
        }
    }

    internal fun runDigest(summary: RunPerformanceSummary, outcome: String, gaveUp: Boolean, atMillis: Long): JSONObject =
        JSONObject()
            .put("at", atMillis / 60_000L * 60_000L)
            .put("outcome", outcome.takeIf { it in setOf("ok", "failed", "cancelled") } ?: "failed")
            .put("elapsed_ms", summary.elapsedMs).put("model_ms", summary.modelMs).put("tools_ms", summary.toolsMs)
            .put("requests", summary.requests).put("retries", summary.retries)
            .put("tool_calls", summary.toolCalls).put("failed_tools", summary.failedTools)
            .put("completion_reviews", summary.completionReviews).put("gave_up", gaveUp)
            .put("slow_tools", JSONArray().apply {
                summary.slowTools.take(3).forEach { put(JSONObject().put("name", it.name).put("elapsed_ms", it.elapsedMs)) }
            })

    /** 追加一条，只留最近 [limit] 条；旧文件坏了就从空的开始。 */
    internal fun appendRunDigest(existing: String?, digest: JSONObject, limit: Int = RUN_DIGEST_LIMIT): String {
        val runs = runCatching { JSONArray(existing.orEmpty()) }.getOrNull() ?: JSONArray()
        runs.put(digest)
        val kept = JSONArray()
        for (index in maxOf(0, runs.length() - limit) until runs.length()) kept.put(runs.get(index))
        return kept.toString()
    }

    private fun recentRuns(context: Context): JSONArray =
        runCatching { JSONArray(java.io.File(context.filesDir, RUN_DIGESTS_FILE).readText()) }.getOrNull() ?: JSONArray()

    private const val RUN_DIGESTS_FILE = "run_digests.json"
    private const val RUN_DIGEST_LIMIT = 20

    fun snapshot(context: Context, engineSnapshot: () -> JSONObject = ShadowTools::diagnosticSnapshot): String {
        val entries = synchronized(this) { events.toList() }
        val manager = context.getSystemService(NotificationManager::class.java)
        return JSONObject()
            .put("version", BuildConfig.VERSION_NAME)
            .put("versionCode", BuildConfig.VERSION_CODE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("physicalDisplayOn", context.getSystemService(android.hardware.display.DisplayManager::class.java)
                .getDisplay(android.view.Display.DEFAULT_DISPLAY)?.state == android.view.Display.STATE_ON)
            .put("notificationsEnabled", AgentExecutionService.taskNotificationsEnabled(context))
            .put("liveUpdatesAllowed", if (AgentExecutionService.usesSystemTaskSurface())
                runCatching { manager.canPostPromotedNotifications() }.getOrDefault(false) else false)
            .put("engine", engineSnapshot())
            .put("engineHistory", com.eta.shadow.EngineJournal.snapshot(context))
            .put("recentRuns", recentRuns(context))
            .put("events", JSONArray().apply {
                entries.forEach { put(JSONObject().put("elapsedMs", it.elapsed).put("event", it.event).put("state", it.state)) }
            }).toString(2)
    }
}
