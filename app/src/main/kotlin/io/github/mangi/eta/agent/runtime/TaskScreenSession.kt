package io.github.mangi.eta.agent.runtime

import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.agent.model.AgentScreenObservationContract
import io.github.mangi.eta.agent.model.AgentUnifiedOperationCatalog
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-run native GUI operation entry.
 *
 * The foreground path remains in [io.github.mangi.eta.agent.tool.AgentLocalTools] and keeps
 * [GuiControlCoordinator] semantics. This class only takes over a call when the task is
 * explicitly moved to the existing background screen backend. It never opens a second
 * virtual-display engine and never falls back to the main screen after a background error.
 */
internal enum class TaskExecutionLocation(val wireValue: String) {
    FOREGROUND("foreground"),
    BACKGROUND("background"),
}

internal class TaskScreenSession(
    private val runId: String,
    private val controller: AgentRunController,
    private val backend: ScreenToolBackend,
    private val readScreenshot: (String) -> AgentModelClient.ToolResult,
    private val rootAvailable: () -> Boolean = { true },
) {
    private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = (right - left).coerceAtLeast(0)
        val height: Int get() = (bottom - top).coerceAtLeast(0)
        val centerX: Int get() = (left.toLong() + right.toLong()).div(2L).toInt()
        val centerY: Int get() = (top.toLong() + bottom.toLong()).div(2L).toInt()
        val empty: Boolean get() = width <= 0 || height <= 0
        val area: Long get() = width.toLong() * height.toLong()
        fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom
        fun wire(): String = "[$left,$top][$right,$bottom]"
    }

    private data class Node(
        val index: Int,
        val text: String,
        val description: String,
        val className: String,
        val packageName: String,
        val viewId: String,
        val bounds: Bounds?,
        val clickable: Boolean,
        val longClickable: Boolean,
        val scrollable: Boolean,
        val focused: Boolean,
        val editable: Boolean,
        val password: Boolean,
        val enabled: Boolean,
        val checked: Boolean?,
        val selected: Boolean,
        val actions: List<Int> = emptyList(),
    ) {
        fun canonical(): String = buildString {
            append(index).append('|')
            append(viewId).append('|')
            append(className).append('|')
            append(text).append('|')
            append(description).append('|')
            append(bounds?.wire().orEmpty()).append('|')
            append(clickable).append('|')
            append(longClickable).append('|')
            append(scrollable).append('|')
            append(editable).append('|')
            append(enabled)
            append('|').append(focused).append('|').append(checked).append('|').append(selected)
        }

        fun toJson(): JSONObject = JSONObject()
            .put("index", index)
            .put("text", text)
            .put("desc", description)
            .put("class", className)
            .put("package", packageName)
            .put("view_id", viewId)
            .put("bounds", bounds?.wire().orEmpty())
            .put("center", JSONObject()
                .put("x", bounds?.centerX ?: 0)
                .put("y", bounds?.centerY ?: 0))
            .put("clickable", clickable)
            .put("long_clickable", longClickable)
            .put("scrollable", scrollable)
            .put("focused", focused)
            .put("editable", editable)
            .put("password", password)
            .put("enabled", enabled)
            .put("selected", selected)
            .put("actions", JSONArray(actions))
            .apply { checked?.let { put("checked", it) } }
    }

    private data class Geometry(
        val displayId: Int?,
        val width: Int,
        val height: Int,
        val rotation: Int,
    )

    private data class Observation(
        val id: String,
        val generation: Long,
        val nodes: List<Node>,
        val treeRead: Boolean,
        val screenshotRead: Boolean,
        val fingerprint: String,
        val limit: Int,
        val truncated: Boolean,
        val packageName: String,
        val displayId: Int?,
        val displayWidth: Int?,
        val displayHeight: Int?,
        val rotation: Int?,
        val screenshotGeometry: Geometry?,
        val screenshotWidth: Int?,
        val screenshotHeight: Int?,
        val frameId: String?,
        val scaleToDisplayX: Double?,
        val scaleToDisplayY: Double?,
    )

    private val lock = Any()
    private val observationSequence = AtomicLong(0)
    @Volatile private var location = TaskExecutionLocation.FOREGROUND
    private var observation: Observation? = null

    fun currentLocation(): TaskExecutionLocation = location

    /**
     * Returns a result only when this call belongs to the background path. A null result means
     * the existing foreground executor must handle it unchanged.
     */
    fun execute(toolCall: AgentModelClient.ToolCall): AgentModelClient.ToolResult? {
        if (toolCall.name !in AgentUnifiedOperationCatalog.names) return null
        val args = runCatching { JSONObject(toolCall.argumentsJson.ifBlank { "{}" }) }.getOrElse {
            return error("INVALID_ARGUMENT", "工具参数不是 JSON object", toolCall.name)
        }
        val requested = args.optString("execution_location").trim().lowercase(Locale.ROOT)
        val target = when (requested) {
            "", "auto" -> currentLocation()
            TaskExecutionLocation.FOREGROUND.wireValue -> TaskExecutionLocation.FOREGROUND
            TaskExecutionLocation.BACKGROUND.wireValue -> TaskExecutionLocation.BACKGROUND
            else -> return error(
                "INVALID_EXECUTION_LOCATION",
                "execution_location 只能是 foreground 或 background；省略时沿用本任务当前位置",
                toolCall.name,
            )
        }

        if (toolCall.name == "observe_screen" && args.optBoolean("status_only", false) &&
            target == TaskExecutionLocation.BACKGROUND) return backgroundStatus()

        if (target == TaskExecutionLocation.FOREGROUND) {
            if (currentLocation() == TaskExecutionLocation.BACKGROUND) {
                // release_gui is the explicit, documented way out of a background screen.
                if (toolCall.name == "release_gui") return finishBackground()
                return error(
                    "BACKGROUND_SESSION_ACTIVE",
                    "本任务仍在后台执行位置，不能静默改用主屏；请先 release_gui 结束后台屏幕，或用 execution_location=\"background\" 明确继续后台操作",
                    toolCall.name,
                )
            }
            return null
        }

        if (toolCall.name == "release_gui") return finishBackground()
        if (toolCall.name == "launch_app") {
            markBackgroundIntent()
            return backgroundLaunch(args)
        }
        if (currentLocation() != TaskExecutionLocation.BACKGROUND) {
            return error(
                "BACKGROUND_SESSION_REQUIRED",
                "本任务尚未启动后台屏幕；如需后台执行，请先 launch_app 并显式传 execution_location=\"background\"",
                toolCall.name,
            )
        }
        controller.throwIfCancelled()
        return try {
            executeBackground(toolCall.name, args)
        } catch (failure: IllegalArgumentException) {
            error("INVALID_COORDINATE", failure.message ?: "后台坐标或参数无效", toolCall.name)
        }
    }

    private fun backgroundLaunch(args: JSONObject): AgentModelClient.ToolResult {
        controller.throwIfCancelled()
        if (!rootAvailable()) return error("ROOT_REQUIRED", "后台启动需要 Root 授权；本次没有改走主屏", "launch_app")
        val packageName = args.optString("package_name").trim()
        if (packageName.isBlank()) {
            return error(
                "BACKGROUND_PACKAGE_REQUIRED",
                "后台启动必须提供精确的 package_name；未执行主屏启动作为兜底",
                "launch_app",
            )
        }
        val openArgs = JSONObject()
            .put("package", packageName)
            .put("orientation", args.optString("orientation", "auto"))
        val result = backendResult("screen_open", openArgs)
        if (!result.optBoolean("ok", false)) {
            return resultWithLocation(result, "launch_app")
        }
        // "onscreen" means the existing task was adopted on display 0. A background
        // request must fail instead of pretending that this is a hidden screen.
        val mode = result.optString("mode")
        val displayId = result.optInt("displayId", result.optInt("display", 0))
        if (mode != "hidden" || displayId <= 0) {
            return error(
                "BACKGROUND_UNAVAILABLE",
                "后台屏幕没有建立（返回 mode=$mode, display=$displayId）；本次没有改走主屏，也不会自动重试",
                "launch_app",
            )
        }
        synchronized(lock) {
            location = TaskExecutionLocation.BACKGROUND
            observation = null
        }
        // 2026111623：启动后等页面静下来（最多 1.5 秒）顺带看一眼，after 里的编号下一步直接用，
        // 不用再专门跑一轮 observe_screen。回看失败时照旧要求先观察。
        val launched = withAfter(
            JSONObject(result.toString()).put("execution_location", TaskExecutionLocation.BACKGROUND.wireValue),
            null, "launch_app", settle = true, settleMs = LAUNCH_SETTLE_MS,
        )
        if (runCatching { JSONObject(launched.content).has("after") }.getOrDefault(false)) return launched
        return launched.copy(content = JSONObject(launched.content).put("observation_required", true).toString())
    }

    /** Status cannot acquire a display, publish an input observation, or change task location. */
    private fun backgroundStatus(): AgentModelClient.ToolResult {
        controller.throwIfCancelled()
        val status = backendResult("screen_state", JSONObject())
        if (status.optString("code") in LOST_SESSION_CODES || status.optString("code") == "APP_TAKEN_OVER") invalidateObservation()
        if (status.has("sessionValid") && !status.optBoolean("sessionValid")) invalidateObservation()
        // Do not expose engine logs, authentication material or other tasks' sessions.
        fun select(source: JSONObject?, names: List<String>) = JSONObject().also { filtered ->
            names.forEach { key -> source?.opt(key)?.let { filtered.put(key, it) } }
        }
        val response = JSONObject().put("ok", status.optBoolean("ok", true)).put("status_only", true)
            .put("task_execution_location", currentLocation().wireValue)
            .put("engine", select(status.optJSONObject("engine"), listOf("ok", "healthy", "build", "code", "state")))
            .put("observer", select(status.optJSONObject("observer"), listOf("ok", "ready", "code")))
            .put("session", select(status.optJSONObject("session"), listOf("mode", "display")))
            .put("recovery", select(status.optJSONObject("recovery"), listOf("code", "state", "next_action", "automatic_force_stop", "backoffRemainingMs")))
        status.opt("sessionValid")?.let { response.put("sessionValid", it) }
        status.opt("code")?.let { response.put("code", it) }
        status.opt("reason")?.let { response.put("reason", it) }
        return resultWithLocation(response, "observe_screen")
    }

    private fun backgroundObserve(args: JSONObject): AgentModelClient.ToolResult {
        controller.throwIfCancelled()
        return when (val outcome = capture(AgentScreenObservationContract.resolve(args), autoScreenshot = true)) {
            is Capture.Failed -> outcome.result
            is Capture.Done -> AgentModelClient.ToolResult(
                content = outcome.response.toString(),
                images = outcome.attachment?.images.orEmpty(),
                sensitive = outcome.attachment?.sensitive == true,
            )
        }
    }

    private sealed interface Capture {
        class Failed(val result: AgentModelClient.ToolResult) : Capture
        class Done(val snapshot: Observation, val response: JSONObject, val attachment: AgentModelClient.ToolResult?) : Capture
    }

    /**
     * 读一次后台屏并登记成新的观察。[autoScreenshot] 为真时由程序决定要不要附截图（11 号方案）：
     * 节点树读得到内容就只给树；树几乎是空的（网页、Flutter、游戏这类）就同一次顺带截图，不再让模型先看空树再要一次。
     */
    private fun capture(options: AgentScreenObservationContract.Options, autoScreenshot: Boolean): Capture {
        fun failed(result: AgentModelClient.ToolResult) = Capture.Failed(result)
        val maxNodes = options.maxNodes.coerceIn(
            AgentScreenObservationContract.MIN_MAX_NODES,
            AgentScreenObservationContract.MAX_MAX_NODES,
        )

        // Boot.handle() adds the authoritative geometry to the dump response. A normal
        // tree observation must use that geometry and must not capture an image.
        var dump: JSONObject? = null
        var displayGeometry: Geometry? = null
        val dumpArgs = JSONObject().put("limit", maxNodes)
        val shotArgs = JSONObject().put("scale", 0.5)
        // The node read goes to the observer and the capture to Root; when both are wanted they
        // run together. The geometry checks below still reject a pair from different screens.
        val concurrent = if (options.includeUiTree && options.includeScreenshot) {
            backendResults(listOf("screen_dump" to dumpArgs, "screen_shot" to shotArgs))
        } else null
        var treeWaitedForWindow = false
        if (options.includeUiTree) {
            val firstDump = concurrent?.get(0) ?: backendResult("screen_dump", dumpArgs)
            dump = dumpWhenWindowReady(dumpArgs, firstDump)
            treeWaitedForWindow = dump !== firstDump
            if (!dump.optBoolean("ok", false)) {
                invalidateObservation()
                return failed(resultWithLocation(requireNotNull(dump), "observe_screen"))
            }
            displayGeometry = geometryOf(dump) ?: run {
                invalidateObservation()
                return failed(error(
                    "BACKGROUND_GEOMETRY_UNAVAILABLE",
                    "节点响应缺少 displayId/width/height/rotation 权威几何；本次没有生成观察",
                    "observe_screen",
                ))
            }
            val dumpDisplayId = dump.optInt("displayId", -1).takeIf { it >= 0 }
            if (dumpDisplayId != null && dumpDisplayId != displayGeometry.displayId) {
                invalidateObservation()
                return failed(error("BACKGROUND_OBSERVATION_CHANGED", "节点响应 displayId 与几何不一致；本次没有生成观察", "observe_screen"))
            }
        }

        var shot: JSONObject? = null
        var screenshotGeometry: Geometry? = null
        var screenshotError: String? = null
        // 程序自己判断：树里几乎没有可读内容时同一次顺带截图。
        val wantShot = options.includeScreenshot || (autoScreenshot && options.includeUiTree && dump != null &&
            displayGeometry != null && ScreenSparseness.needsScreenshot(parseNodes(dump).map { it.sparseView() },
                displayGeometry.width, displayGeometry.height))
        if (wantShot) {
            // 节点树等过窗口的话，一起截的那张图可能还是启动画面，重新截一张。
            shot = concurrent?.get(1)?.takeUnless { treeWaitedForWindow } ?: backendResult("screen_shot", shotArgs)
            if (!shot.optBoolean("ok", false)) {
                val backendFailure = shot
                screenshotError = shot.optString("error").ifBlank { shot.optString("code", "SCREENSHOT_FAILED") }
                shot = null
                if (!options.includeUiTree) {
                    invalidateObservation()
                    return failed(resultWithLocation(backendFailure, "observe_screen"))
                }
            } else {
                screenshotGeometry = geometryOf(shot) ?: run {
                    invalidateObservation()
                    return failed(error("BACKGROUND_GEOMETRY_UNAVAILABLE", "截图缺少权威几何；本次没有生成观察", "observe_screen"))
                }
                if (displayGeometry != null && !sameGeometry(displayGeometry, screenshotGeometry)) {
                    invalidateObservation()
                    return failed(error(
                        "BACKGROUND_OBSERVATION_CHANGED",
                        "节点与截图来自不同的后台屏几何（displayId/width/height/rotation 不一致）；请重新 observe_screen",
                        "observe_screen",
                    ))
                }
            }
        }

        val geometry = displayGeometry ?: screenshotGeometry ?: run {
            invalidateObservation()
            return failed(error("BACKGROUND_GEOMETRY_UNAVAILABLE", "观察必须包含节点树或截图；本次没有生成观察", "observe_screen"))
        }
        val nodes = dump?.let(::parseNodes).orEmpty()
        val packageName = packageOf(dump ?: JSONObject())
        val screenshotWidth = shot?.let(::screenshotWidth)
        val screenshotHeight = shot?.let(::screenshotHeight)
        if (shot != null && (screenshotWidth == null || screenshotHeight == null)) {
            invalidateObservation()
            return failed(error("BACKGROUND_GEOMETRY_UNAVAILABLE", "截图缺少实际图片宽高；本次没有生成观察", "observe_screen"))
        }
        val frameId = shot?.optString("frameId").orEmpty().ifBlank { null }
        val scaleToDisplayX = shot?.optDouble("scaleToDisplayX")
            ?.takeIf { it.isFinite() && it > 0 }
            ?: screenshotWidth?.let { geometry.width.toDouble() / it }
        val scaleToDisplayY = shot?.optDouble("scaleToDisplayY")
            ?.takeIf { it.isFinite() && it > 0 }
            ?: screenshotHeight?.let { geometry.height.toDouble() / it }
        val fingerprint = fingerprint(nodes, geometry, packageName)
        val id = "bg-$runId-${observationSequence.incrementAndGet()}"
        val snapshot = Observation(
            id = id,
            generation = controller.resumeGeneration,
            nodes = nodes,
            treeRead = options.includeUiTree,
            screenshotRead = shot != null,
            fingerprint = fingerprint,
            limit = maxNodes,
            truncated = dump?.optBoolean("truncated", false) == true ||
                dump?.optBoolean("scanLimited", false) == true ||
                nodes.size >= maxNodes,
            packageName = packageName,
            displayId = geometry.displayId,
            displayWidth = geometry.width,
            displayHeight = geometry.height,
            rotation = geometry.rotation,
            screenshotGeometry = screenshotGeometry,
            screenshotWidth = screenshotWidth,
            screenshotHeight = screenshotHeight,
            frameId = frameId,
            scaleToDisplayX = scaleToDisplayX,
            scaleToDisplayY = scaleToDisplayY,
        )
        synchronized(lock) { observation = snapshot }

        var screenshotAttachment: AgentModelClient.ToolResult? = null
        if (shot != null) {
            shot.optString("path").takeIf { it.isNotBlank() }?.let { path ->
                screenshotAttachment = runCatching { readScreenshot(path) }.getOrElse { throwable ->
                    screenshotError = throwable.javaClass.simpleName
                    null
                }
            }
        }

        val response = JSONObject()
            .put("ok", true)
            .put("tool", "observe_screen")
            .put("execution_location", TaskExecutionLocation.BACKGROUND.wireValue)
            .put("screen", JSONObject()
                .put("width", geometry.width)
                .put("height", geometry.height)
                .put("rotation", geometry.rotation))
            .put("display_id", snapshot.displayId ?: JSONObject.NULL)
            .put("focus", JSONObject().put("package", packageName))
            .put("keyboard", dump?.optJSONObject("keyboard") ?: JSONObject().put("known", false))
            .put("observation_id", id)
            .put("observation_source", "background")
            .put("ui_tree_read", snapshot.treeRead)
            .put("node_limit", maxNodes)
            .put("ui_tree_truncated", snapshot.truncated)
            .put("ui_nodes", JSONArray().also { array -> nodes.forEach { array.put(it.toJson()) } })
            .put("coordinate_contract", coordinateContract(snapshot))
            .put("screenshot", screenshotJson(shot, screenshotError, screenshotAttachment))
            .apply { snapshot.frameId?.let { put("frame_id", it) } }
            .apply { if (shot != null && !options.includeScreenshot) put("screenshot_reason", "sparse_ui_tree") }
        return Capture.Done(snapshot, response, screenshotAttachment)
    }

    private fun screenshotJson(
        shot: JSONObject?,
        error: String?,
        attachment: AgentModelClient.ToolResult?,
    ): JSONObject {
        val out = JSONObject().put("attached", false)
        if (shot == null) {
            error?.let { out.put("error", it) }
            return out
        }
        val image = attachment?.images?.firstOrNull()
        if (image == null) {
            out.put("error", error ?: attachment?.content?.take(300).orEmpty())
        } else {
            out.put("attached", true)
                .put("mime_type", image.mimeType)
                .put("bytes", image.bytes)
                .put("width", image.width ?: screenshotWidth(shot))
                .put("height", image.height ?: screenshotHeight(shot))
        }
        shot.optString("path").takeIf { it.isNotBlank() }?.let { out.put("path", it) }
        shot.optString("frameId").takeIf { it.isNotBlank() }?.let { out.put("frame_id", it) }
        shot.optString("dims").takeIf { it.isNotBlank() }?.let { out.put("dims", it) }
        shot.optJSONObject("geometry")?.let { out.put("geometry", it) }
        listOf("scaleToDisplayX", "scaleToDisplayY").forEach { key ->
            if (shot.has(key)) out.put(key, shot.optDouble(key))
        }
        return out
    }

    private fun executeBackground(name: String, args: JSONObject): AgentModelClient.ToolResult = when (name) {
        "observe_screen" -> backgroundObserve(args)
        "tap" -> coordinateAction(name, args, "screen_tap")
        "tap_area" -> tapArea(args)
        "long_press" -> coordinateAction(name, args, "screen_press")
        "swipe" -> swipe(args)
        "tap_element" -> nodeAction(name, args, "screen_tap")
        "long_press_element" -> nodeAction(name, args, "screen_press")
        "scroll" -> scroll(args)
        "scroll_element" -> scrollElement(args)
        "type_text" -> typeText(args)
        "wait" -> waitOnly(args)
        "wait_for_text" -> waitForText(args)
        "press_key" -> pressKey(args)
        "wait_for_package" -> waitForPackage(args)
        "open_uri" -> error("BACKGROUND_UNSUPPORTED_OPERATION", "后台屏只支持按包名打开应用，不支持 URI；本次未改走主屏", name)
        "open_system_panel" -> error("BACKGROUND_UNSUPPORTED_OPERATION", "后台屏不支持系统面板；本次未执行", name)
        else -> error("BACKGROUND_UNSUPPORTED_OPERATION", "该操作没有后台实现；本次未执行", name)
    }

    private fun coordinateAction(
        toolName: String,
        args: JSONObject,
        backendName: String,
    ): AgentModelClient.ToolResult {
        val snapshot = requireObservation(toolName, args) ?: return observationError(toolName, args)
        val point = convertPoint(args, snapshot)
        validateLiveSnapshot(snapshot, toolName, point)?.let { return it }
        val backendArgs = JSONObject()
            .put("x", point.first)
            .put("y", point.second)
            .put("coordinate_space", "display")
        if (backendName == "screen_press") backendArgs.put("hold_ms", args.optInt("duration_ms", 800))
        return mutateAndInvalidate(backendName, backendArgs, toolName)
    }

    private fun tapArea(args: JSONObject): AgentModelClient.ToolResult {
        val snapshot = requireObservation("tap_area", args) ?: return observationError("tap_area", args)
        val first = convertPoint(
            JSONObject().put("x", args.optInt("x1")).put("y", args.optInt("y1"))
                .put("coordinate_space", args.optString("coordinate_space"))
                .put("frame_id", args.optString("frame_id")),
            snapshot,
        )
        val second = convertPoint(
            JSONObject().put("x", args.optInt("x2")).put("y", args.optInt("y2"))
                .put("coordinate_space", args.optString("coordinate_space"))
                .put("frame_id", args.optString("frame_id")),
            snapshot,
        )
        val centerX = ((first.first.toLong() + second.first.toLong()) / 2L).toInt()
        val centerY = ((first.second.toLong() + second.second.toLong()) / 2L).toInt()
        validateLiveSnapshot(snapshot, "tap_area", centerX to centerY)?.let { return it }
        return mutateAndInvalidate(
            "screen_tap",
            JSONObject().put("x", centerX).put("y", centerY).put("coordinate_space", "display"),
            "tap_area",
        )
    }

    private fun swipe(args: JSONObject): AgentModelClient.ToolResult {
        val holdValue = args.opt("hold_ms")
        val holdMs = if (!args.has("hold_ms")) 0 else {
            if (holdValue !is Number || !holdValue.toDouble().isFinite() ||
                holdValue.toDouble() != holdValue.toInt().toDouble() || holdValue.toInt() !in 0..3000) {
                return error("INVALID_ARGUMENT", "hold_ms 必须为 0 到 3000 的整数；本次未执行", "swipe")
            }
            holdValue.toInt()
        }
        val snapshot = requireObservation("swipe", args) ?: return observationError("swipe", args)
        validateLiveSnapshot(snapshot, "swipe")?.let { return it }
        val start = convertPoint(
            JSONObject().put("x", args.optInt("x1")).put("y", args.optInt("y1"))
                .put("coordinate_space", args.optString("coordinate_space"))
                .put("frame_id", args.optString("frame_id")),
            snapshot,
        )
        val end = convertPoint(
            JSONObject().put("x", args.optInt("x2")).put("y", args.optInt("y2"))
                .put("coordinate_space", args.optString("coordinate_space"))
                .put("frame_id", args.optString("frame_id")),
            snapshot,
        )
        return mutateAndInvalidate(
            if (holdMs > 0) "screen_drag" else "screen_swipe",
            JSONObject()
                .put("x1", start.first).put("y1", start.second)
                .put("x2", end.first).put("y2", end.second)
                .put("coordinate_space", "display")
                .put("duration_ms", args.optInt("duration_ms", 500))
                .apply { if (holdMs > 0) put("hold_ms", holdMs) },
            "swipe",
        )
    }

    private fun nodeAction(
        toolName: String,
        args: JSONObject,
        backendName: String,
    ): AgentModelClient.ToolResult {
        val snapshot = requireObservation(toolName, args) ?: return observationError(toolName, args)
        val original = findNode(snapshot, args)
            ?: return error("INVALID_NODE_INDEX", "观察快照中不存在节点 index=${args.optInt("index", -1)}", toolName)
        // 只核对要点的这个元素：别处的时钟、角标、进度变了照常点；它挪了一点就点它现在的位置。
        val node = when (val live = locateLive(snapshot, toolName, original)) {
            is Live.Rejected -> return live.result
            is Live.Found -> live.node ?: original
        }
        val bounds = node.bounds
            ?: return error("NODE_BOUNDS_UNAVAILABLE", "该节点没有可用边界；请改为截图坐标操作", toolName)
        if (bounds.empty) return error("NODE_BOUNDS_UNAVAILABLE", "该节点边界为空；本次未执行", toolName)
        val backendArgs = JSONObject()
            .put("x", bounds.centerX)
            .put("y", bounds.centerY)
            .put("coordinate_space", "display")
        if (backendName == "screen_press") backendArgs.put("hold_ms", args.optInt("duration_ms", 800))
        val result = mutateAndInvalidate(backendName, backendArgs, toolName)
        return withNodeEvidence(result, snapshot, node)
    }

    private fun scroll(args: JSONObject): AgentModelClient.ToolResult {
        val snapshot = requireObservation("scroll", args) ?: return observationError("scroll", args)
        validateLiveSnapshot(snapshot, "scroll")?.let { return it }
        return mutateAndInvalidate(
            "screen_scroll",
            JSONObject().put("direction", args.optString("direction")),
            "scroll",
        )
    }

    private fun scrollElement(args: JSONObject): AgentModelClient.ToolResult {
        val snapshot = requireObservation("scroll_element", args) ?: return observationError("scroll_element", args)
        val original = findNode(snapshot, args)
            ?: return error("INVALID_NODE_INDEX", "观察快照中不存在可滚动节点 index=${args.optInt("index", -1)}", "scroll_element")
        val node = when (val live = locateLive(snapshot, "scroll_element", original)) {
            is Live.Rejected -> return live.result
            is Live.Found -> live.node ?: original
        }
        val bounds = node.bounds
            ?: return error("NODE_BOUNDS_UNAVAILABLE", "该节点没有可用边界；本次未执行", "scroll_element")
        if (!node.scrollable) return error("NODE_NOT_SCROLLABLE", "该节点不是可滚动容器", "scroll_element")
        val result = mutateAndInvalidate(
            "screen_scroll",
            JSONObject()
                .put("direction", args.optString("direction"))
                .put("x", bounds.centerX)
                .put("y", bounds.centerY),
            "scroll_element",
        )
        return withNodeEvidence(result, snapshot, node)
    }

    private fun typeText(args: JSONObject): AgentModelClient.ToolResult {
        val indexed = args.has("index") && !args.isNull("index")
        // 往当前焦点输入框写字不需要 observation_id（只核对屏幕和应用没变）；指定节点时仍要。
        val snapshot = (if (indexed || args.optString("observation_id").isNotBlank()) requireObservation("type_text", args)
            else currentObservation()) ?: return observationError("type_text", args)
        val mode = args.optString("mode", "replace").lowercase(Locale.ROOT)
        val target: Node?
        val liveNodes: List<Node>
        if (indexed) {
            val original = findNode(snapshot, args)
                ?: return error("INVALID_NODE_INDEX", "观察快照中不存在输入节点", "type_text")
            if (!original.editable) return error("NODE_NOT_EDITABLE", "所选节点不是输入框", "type_text")
            when (val live = locateLive(snapshot, "type_text", original)) {
                is Live.Rejected -> return live.result
                is Live.Found -> { target = live.node ?: original; liveNodes = live.nodes }
            }
        } else {
            when (val live = locateLive(snapshot, "type_text", null)) {
                is Live.Rejected -> return live.result
                is Live.Found -> { liveNodes = live.nodes; target = liveNodes.firstOrNull { it.editable && it.focused } }
            }
        }
        var text = args.optString("text")
        if (mode == "append") {
            // 追加 = 读回输入框现有的字，拼好后整体写入。
            val base = target ?: return error("NODE_NOT_EDITABLE", "找不到当前焦点输入框，无法追加；请指定输入框的 index", "type_text")
            text = base.text + text
        }
        val backendArgs = JSONObject().put("text", text)
        if (indexed && target != null) {
            if (target.viewId.isNotBlank()) backendArgs.put("view_id", target.viewId)
            else backendArgs.put("index", editableOrdinal(liveNodes.ifEmpty { snapshot.nodes }, target))
        }
        val steps = mutableListOf("screen_text" to backendArgs)
        // submit = 写入后按回车（搜索框、聊天框的发送）。
        if (args.optBoolean("submit", false)) steps += "screen_key" to JSONObject().put("code", 66)
        return mutateAndInvalidate(steps, "type_text")
    }

    /** 后台的 wait：最多等这么久，页面静下来就提前返回，并顺带看一眼（结果里带新的观察）。 */
    private fun waitOnly(args: JSONObject): AgentModelClient.ToolResult {
        val duration = args.optInt("duration_ms", 1_000).coerceIn(100, 30_000)
        controller.throwIfCancelled()
        val startedAt = System.nanoTime()
        fun elapsed() = ((System.nanoTime() - startedAt) / 1_000_000L).toInt()
        // 分段等（每段最多 1 秒）：页面静了就提前返回，每段之后都检查任务是否被停止。
        var settled = false
        while (elapsed() < duration) {
            val idle = backendResult("screen_wait", JSONObject().put("timeout_ms", (duration - elapsed()).coerceIn(1, 1_000)))
            controller.throwIfCancelled()
            if (idle.optBoolean("ok")) { settled = true; break }
            val code = idle.optString("code")
            if (code != "UI_NOT_QUIET" && code != "TIMEOUT") {
                // 引擎不支持静止等待：退回可被停止打断的死等，保持原来的语义。
                if (elapsed() < duration) controller.awaitRetryDelay((duration - elapsed()).toLong())
                break
            }
        }
        controller.throwIfCancelled()
        val idle = JSONObject().put("ok", settled)
        val result = JSONObject()
            .put("ok", true)
            .put("tool", "wait")
            .put("execution_location", TaskExecutionLocation.BACKGROUND.wireValue)
            .put("waited_ms", ((System.nanoTime() - startedAt) / 1_000_000L).toInt())
            .put("settled", idle.optBoolean("ok"))
        return withAfter(result, synchronized(lock) { observation }, "wait", settle = false)
    }

    private fun waitForText(args: JSONObject): AgentModelClient.ToolResult {
        controller.throwIfCancelled()
        val result = backendResult(
            "screen_wait",
            JSONObject()
                .put("text", args.optString("text"))
                .put("timeout_ms", args.optInt("timeout_ms", 10_000)),
        )
        if (!result.optBoolean("ok")) return resultWithLocation(result, "wait_for_text")
        return withAfter(result, synchronized(lock) { observation }, "wait_for_text", settle = false)
    }

    /** 后台屏没有「前台应用」的概念，按后台屏上当前页面的包名轮询。 */
    private fun waitForPackage(args: JSONObject): AgentModelClient.ToolResult {
        val target = args.optString("package_name").ifBlank { args.optString("package") }.trim()
        if (target.isBlank()) return error("INVALID_ARGUMENT", "缺少 package_name", "wait_for_package")
        val timeout = args.optInt("timeout_ms", 10_000).coerceIn(500, 30_000)
        val startedAt = System.nanoTime()
        while (true) {
            controller.throwIfCancelled()
            val dump = backendResult("screen_dump", JSONObject().put("limit", 1))
            val windowPending = !dump.optBoolean("ok") && dump.optString("code") == WINDOW_NOT_READY
            if (!dump.optBoolean("ok") && !windowPending) return resultWithLocation(dump, "wait_for_package")
            val current = packageOf(dump)
            val elapsed = ((System.nanoTime() - startedAt) / 1_000_000L).toInt()
            if (current == target) {
                return withAfter(JSONObject().put("ok", true).put("tool", "wait_for_package").put("package", current)
                    .put("waited_ms", elapsed).put("execution_location", TaskExecutionLocation.BACKGROUND.wireValue),
                    synchronized(lock) { observation }, "wait_for_package", settle = true)
            }
            if (elapsed >= timeout) {
                return resultWithLocation(JSONObject().put("ok", false).put("code", "TIMEOUT")
                    .put("message", "等待 $target 超时，后台屏当前是 ${current.ifBlank { "未知" }}").put("package", current), "wait_for_package")
            }
            controller.awaitRetryDelay(300)
        }
    }

    private fun pressKey(args: JSONObject): AgentModelClient.ToolResult {
        val button = args.optString("button").trim().uppercase(Locale.ROOT)
        val code = when (button) {
            "BACK" -> 4
            "HOME" -> 3
            "ENTER" -> 66
            "RECENTS" -> 187
            "PASTE", "NOTIFICATIONS", "QUICK_SETTINGS" -> return error(
                "BACKGROUND_UNSUPPORTED_OPERATION",
                "后台屏暂不支持 $button 按键；本次未执行，也未改走主屏",
                "press_key",
            )
            else -> return error("INVALID_ARGUMENT", "未知按键：$button", "press_key")
        }
        // 返回、主页、最近任务不点任何元素：不看 observation_id（带了旧编号也不拦），只核对屏幕和应用没变；
        // 手上没有观察时程序自己读一次，不再让模型多跑一轮。回车可能提交表单：带了编号仍核对是不是最近一次观察。
        // 连按两次返回、「输入 + 回车」可以同一轮发出。
        val held = if (code == 66 && args.optString("observation_id").isNotBlank()) {
            requireObservation("press_key", args) ?: return observationError("press_key", args)
        } else currentObservation()
        if (held != null) validateLiveSnapshot(held, "press_key")?.let { return it }
        // 刚自己读的画面就是现在的画面，不用再核对一遍。
        else observeForKey() ?: return observationError("press_key", JSONObject())
        return mutateAndInvalidate(
            "screen_key",
            JSONObject().put("code", code),
            "press_key",
        )
    }

    /** 按键前没有可用观察时自动读一次树（暂停恢复后除外：那时必须由模型重新看过再继续）。 */
    private fun observeForKey(): Observation? {
        val stale = synchronized(lock) { observation }
        if (stale != null && stale.generation != controller.resumeGeneration) return null
        val outcome = capture(AgentScreenObservationContract.Options(includeScreenshot = false, includeUiTree = true,
            maxNodes = AgentScreenObservationContract.DEFAULT_MAX_NODES), autoScreenshot = false)
        return (outcome as? Capture.Done)?.snapshot
    }

    private fun finishBackground(): AgentModelClient.ToolResult {
        controller.throwIfCancelled()
        if (currentLocation() != TaskExecutionLocation.BACKGROUND) {
            return error("BACKGROUND_SESSION_REQUIRED", "本任务不在后台执行位置", "release_gui")
        }
        val result = backendResult("screen_finish", JSONObject())
        if (!result.optBoolean("ok", false)) {
            val code = result.optString("code")
            if (code in LOST_SESSION_CODES) {
                // The user explicitly asked to leave the background screen and there is no
                // session left to release. This is not an automatic main-screen fallback.
                synchronized(lock) {
                    location = TaskExecutionLocation.FOREGROUND
                    observation = null
                }
                return resultWithLocation(
                    JSONObject(result.toString())
                        .put("execution_location", TaskExecutionLocation.FOREGROUND.wireValue)
                        .put("released_without_session", true),
                    "release_gui",
                )
            }
            return resultWithLocation(result, "release_gui")
        }
        synchronized(lock) {
            location = TaskExecutionLocation.FOREGROUND
            observation = null
        }
        return resultWithLocation(
            JSONObject(result.toString())
                .put("execution_location", TaskExecutionLocation.FOREGROUND.wireValue)
                .put("finished", true),
            "release_gui",
        )
    }

    private fun requireObservation(toolName: String, args: JSONObject): Observation? {
        val snapshot = synchronized(lock) { observation }
        if (snapshot == null) return null
        if (controller.resumeGeneration != snapshot.generation) {
            // Keep the snapshot until observationError can explain OBSERVE_AFTER_RESUME.
            return null
        }
        val requested = args.optString("observation_id").trim()
        if (requested.isBlank() || requested != snapshot.id) {
            return null
        }
        if (args.optString("coordinate_space").trim().lowercase(Locale.ROOT) == "screenshot") {
            val frame = args.optString("frame_id").trim()
            if (frame.isBlank() || frame != snapshot.frameId) return null
        }
        return snapshot
    }

    private fun observationError(toolName: String, args: JSONObject): AgentModelClient.ToolResult {
        val snapshot = synchronized(lock) { observation }
        if (snapshot == null) {
            return error(
                "BACKGROUND_OBSERVATION_REQUIRED",
                "后台输入必须先调用 observe_screen，并携带同一次返回的 observation_id",
                toolName,
            )
        }
        if (controller.resumeGeneration != snapshot.generation) {
            return error("OBSERVE_AFTER_RESUME", "任务暂停恢复后页面可能已变化，请先重新 observe_screen", toolName)
        }
        val requested = args.optString("observation_id").trim()
        if (requested.isBlank()) {
            return error("OBSERVATION_ID_REQUIRED", "后台输入必须携带 observation_id", toolName)
        }
        if (args.optString("coordinate_space").equals("screenshot", true) &&
            args.optString("frame_id").trim() != snapshot.frameId) {
            return error("BACKGROUND_FRAME_STALE", "截图坐标必须携带本次 observe_screen 返回的 frame_id", toolName)
        }
        return error("OBSERVATION_MISMATCH", "observation_id 不属于当前任务的最近观察", toolName)
    }

    private sealed interface Live {
        class Rejected(val result: AgentModelClient.ToolResult) : Live
        /** [node] 是在当前树里重新找到的目标（没有目标时为 null）；[nodes] 是当前树。 */
        class Found(val node: Node?, val nodes: List<Node>) : Live
    }

    /**
     * 动作前核对屏幕（11 号方案第二期）。以前把整棵树的文字、位置、焦点都算进指纹，有一处变化就拒绝，
     * 页面上有时钟、进度、加载圈就几乎次次失败。现在只核对：屏幕几何、当前应用没变；点某个元素时在当前树里
     * 重新找到同一个元素（类名、viewId、文字、描述一致，位置偏移不大），找不到或不止一个才拒绝。
     */
    private fun locateLive(snapshot: Observation, toolName: String, target: Node?, point: Pair<Int, Int>? = null): Live {
        val nodeLimit = if (snapshot.treeRead) snapshot.limit else 0
        val current = backendResult("screen_dump", JSONObject().put("limit", nodeLimit))
        if (!current.optBoolean("ok", false)) {
            invalidateObservation()
            return Live.Rejected(resultWithLocation(current, toolName))
        }
        fun stale(reason: String): Live {
            invalidateObservation()
            return Live.Rejected(error("OBSERVATION_STALE", "$reason，请重新 observe_screen；本次未执行", toolName))
        }
        val currentGeometry = geometryOf(current) ?: return stale("屏幕几何已改变")
        val saved = Geometry(snapshot.displayId, snapshot.displayWidth ?: 0, snapshot.displayHeight ?: 0, snapshot.rotation ?: -1)
        if (saved.width <= 0 || saved.height <= 0 || saved.rotation !in 0..3 || !sameGeometry(saved, currentGeometry)) {
            return stale("屏幕几何已改变")
        }
        if (snapshot.screenshotGeometry != null && !sameGeometry(snapshot.screenshotGeometry, currentGeometry)) {
            return stale("屏幕几何已改变")
        }
        if (!snapshot.treeRead) return Live.Found(null, emptyList())
        val nodes = parseNodes(current)
        val pkg = packageOf(current)
        if (snapshot.packageName.isNotBlank() && pkg.isNotBlank() && pkg != snapshot.packageName) {
            return stale("页面已经换成别的应用")
        }
        if (target != null) {
            return when (val found = NodeRelocation.find(target.identity(), nodes.map { it.identity() }, currentGeometry.width)) {
                NodeRelocation.Result.Missing -> stale("要操作的元素已经不在原处")
                NodeRelocation.Result.Ambiguous -> stale("页面上有不止一个同样的元素，无法确定是哪一个")
                is NodeRelocation.Result.At -> Live.Found(nodes[found.index], nodes)
            }
        }
        if (point != null) {
            // 坐标点：那个位置上的元素换成了别的（比如弹出了对话框）才拒绝。
            val before = hitNode(snapshot.nodes, point.first, point.second)
            val after = hitNode(nodes, point.first, point.second)
            if (before != null && (after == null || !NodeRelocation.same(before.identity(), after.identity()))) {
                return stale("点的位置已经换成别的内容")
            }
        }
        return Live.Found(null, nodes)
    }

    private fun validateLiveSnapshot(snapshot: Observation, toolName: String, point: Pair<Int, Int>? = null): AgentModelClient.ToolResult? =
        (locateLive(snapshot, toolName, null, point) as? Live.Rejected)?.result

    private fun hitNode(nodes: List<Node>, x: Int, y: Int): Node? =
        nodes.filter { it.bounds?.contains(x, y) == true }.minByOrNull { it.bounds!!.area }

    private fun Node.identity() = NodeRelocation.Identity(className, viewId, text, description,
        bounds?.let { NodeRelocation.Box(it.left, it.top, it.right, it.bottom) })

    private fun Node.sparseView() = ScreenSparseness.View(className, text.isNotBlank() || description.isNotBlank(),
        bounds?.let { it.width.toLong() * it.height.toLong() } ?: 0L)

    private fun currentObservation(): Observation? = synchronized(lock) { observation }
        ?.takeIf { it.generation == controller.resumeGeneration }

    private fun mutateAndInvalidate(
        backendName: String,
        backendArgs: JSONObject,
        toolName: String,
    ): AgentModelClient.ToolResult = mutateAndInvalidate(listOf(backendName to backendArgs), toolName)

    /**
     * 依次提交 [steps]（遇到失败即停），然后自动回看一次（11 号方案第一期）：等页面静下来、读一次树、
     * 登记成新的观察，放进结果的 after 里。前台一直这么做，后台以前不回看，每一步都要多一轮 observe_screen。
     */
    private fun mutateAndInvalidate(
        steps: List<Pair<String, JSONObject>>,
        toolName: String,
    ): AgentModelClient.ToolResult {
        controller.throwIfCancelled()
        // Consume the admitted observation before sending input: a transport exception or
        // malformed reply can occur after Android already executed the action.
        val before = synchronized(lock) { observation }
        invalidateObservation()
        var result = JSONObject()
        var completed = 0
        for ((index, step) in steps.withIndex()) {
            if (index > 0) controller.throwIfCancelled()
            result = backendResult(step.first, step.second)
            if (!result.optBoolean("ok")) break
            completed++
            if (index > 0) result.put("submitted", true)
        }
        // 多步动作（写字 + 回车）前面的步骤已经做了、后面的失败：屏幕已经变了，照样回看，并写明做到第几步。
        if (completed in 1 until steps.size) {
            result.put("completed_steps", completed)
                .put("note", "前 $completed 步已经执行（如文字已写入），第 ${completed + 1} 步没有成功；按 after 里的新画面决定下一步")
        }
        val dispatched = result.optBoolean("ok") || result.optBoolean("operationMayHaveRun") ||
            result.optString("code") == "ACTION_OUTCOME_UNKNOWN" || completed > 0
        if (!dispatched) {
            // 后端明确说没执行（例如防重试拦下的 NO_PROGRESS）：屏幕没动过，刚才的观察仍然有效，
            // 下一步（比如按返回）不用再先 observe_screen。说不清有没有执行的不恢复。
            if (before != null && result.has("operationMayHaveRun") && !result.optBoolean("operationMayHaveRun", true)) {
                synchronized(lock) { if (observation == null) observation = before }
            }
            return resultWithLocation(result, toolName)
        }
        return withAfter(result, before, toolName, settle = true)
    }

    /** 给结果附上 after（新的观察）；回看失败不影响结果本身，只是不带 after。 */
    private fun withAfter(
        result: JSONObject,
        before: Observation?,
        toolName: String,
        settle: Boolean,
        settleMs: Int = AFTER_SETTLE_MS,
    ): AgentModelClient.ToolResult {
        var attachment: AgentModelClient.ToolResult? = null
        if (!controller.isCancelled) runCatching {
            if (settle) backendResult("screen_wait", JSONObject().put("timeout_ms", settleMs))
            val limit = before?.takeIf { it.treeRead }?.limit ?: AgentScreenObservationContract.DEFAULT_MAX_NODES
            val options = AgentScreenObservationContract.Options(includeScreenshot = false, includeUiTree = true, maxNodes = limit)
            val recheck = lateChangeExpected(toolName, before)
            // 可能还要再看的时候先只读树（省掉一次截图），定下最终画面后再按需补截图。
            var outcome = capture(options, autoScreenshot = !recheck)
            // 点完页面没变：多半是详情页、路线页还在加载。再看两次（各约 0.6 秒），
            // 免得模型以为没点上、再点一次被防重试拦下。树读不出内容（画布、地图）时不等，判断不了。
            var rechecks = 0
            while (recheck && rechecks < LATE_CHANGE_CHECKS && !controller.isCancelled &&
                (outcome as? Capture.Done)?.snapshot?.fingerprint == before?.fingerprint
            ) {
                controller.awaitRetryDelay(LATE_CHANGE_DELAY_MS)
                rechecks++
                outcome = capture(options, autoScreenshot = false)
            }
            if (rechecks > 0) result.put("late_change_checks", rechecks)
            // 新页面的树读不出内容（网页、Flutter 这类）：和平时一样顺带截图，不让模型再要一轮。
            val final = outcome
            if (recheck && final is Capture.Done && !final.snapshot.screenshotRead && final.snapshot.displayWidth != null &&
                final.snapshot.displayHeight != null && ScreenSparseness.needsScreenshot(final.snapshot.nodes.map { it.sparseView() },
                    final.snapshot.displayWidth, final.snapshot.displayHeight)
            ) outcome = capture(options, autoScreenshot = true)
            // 回看没读成（页面正在切换时读树、截图偶尔失败）：隔 0.3 秒再读一次。还是不行就在结果里写明，
            // 让模型先 observe_screen，而不是拿旧截图的坐标去点、被「必须先观察」拒掉。
            if (outcome is Capture.Failed && !controller.isCancelled) {
                controller.awaitRetryDelay(AFTER_RETRY_DELAY_MS)
                outcome = capture(options, autoScreenshot = true)
            }
            if (outcome is Capture.Failed) {
                result.put("after_unavailable", true)
                    .put("next_step", "画面没读到，下一步先 observe_screen")
                runCatching { JSONObject(outcome.result.content).optString("code") }.getOrNull()
                    ?.takeIf { it.isNotBlank() }?.let { result.put("after_error", it) }
            }
            if (outcome is Capture.Done) {
                attachment = outcome.attachment
                val after = result.optJSONObject("after") ?: JSONObject()
                listOf("observation_id", "focus", "keyboard", "ui_nodes", "ui_tree_truncated", "coordinate_contract",
                    "screenshot", "frame_id", "screenshot_reason").forEach { key ->
                    if (outcome.response.has(key)) after.put(key, outcome.response.get(key))
                }
                if (!after.has("screen_changed")) after.put("screen_changed", before?.fingerprint != outcome.snapshot.fingerprint)
                result.put("after", after)
            }
        }
        val tool = resultWithLocation(result, toolName)
        val shot = attachment ?: return tool
        return tool.copy(images = tool.images + shot.images, sensitive = tool.sensitive || shot.sensitive)
    }

    private fun lateChangeExpected(toolName: String, before: Observation?): Boolean =
        toolName in LATE_CHANGE_TOOLS && before != null && before.treeRead &&
            before.nodes.count { it.text.isNotBlank() || it.description.isNotBlank() } >= MIN_READABLE_FOR_RECHECK

    private fun findNode(snapshot: Observation, args: JSONObject): Node? {
        val index = args.optInt("index", -1)
        if (index < 0) return null
        return snapshot.nodes.firstOrNull { it.index == index }
    }

    private fun editableOrdinal(nodes: List<Node>, target: Node): Int =
        nodes.filter { it.editable }.indexOfFirst { it === target }.coerceAtLeast(0)

    private fun withNodeEvidence(
        result: AgentModelClient.ToolResult,
        snapshot: Observation,
        node: Node,
    ): AgentModelClient.ToolResult {
        val json = runCatching { JSONObject(result.content) }.getOrNull() ?: return result
        json.put("source_observation_id", snapshot.id)
            .put("source_node_index", node.index)
            .put("source_node_bounds", node.bounds?.wire().orEmpty())
            .put("execution_location", TaskExecutionLocation.BACKGROUND.wireValue)
        return result.copy(content = json.toString())
    }

    private fun convertPoint(args: JSONObject, snapshot: Observation): Pair<Int, Int> {
        val x = args.optInt("x")
        val y = args.optInt("y")
        val space = args.optString("coordinate_space").trim().lowercase(Locale.ROOT)
        if (space.isBlank()) {
            throw IllegalArgumentException("缺少 coordinate_space：后台坐标请用 display，或截图坐标用 screenshot 并携带 frame_id")
        }
        if (space == "normalized") {
            require(x in 0..999 && y in 0..999) { "normalized 坐标必须在 0–999 之间：($x,$y)" }
            val width = snapshot.displayWidth
                ?: throw IllegalArgumentException("本次后台观察没有屏幕尺寸，不能使用 normalized；请改用 display")
            val height = snapshot.displayHeight
                ?: throw IllegalArgumentException("本次后台观察没有屏幕尺寸，不能使用 normalized；请改用 display")
            return Pair((x.toLong() * (width - 1) / 999).toInt(), (y.toLong() * (height - 1) / 999).toInt())
        }
        if (space == "screen" || space == "display") {
            val width = snapshot.displayWidth
                ?: throw IllegalArgumentException("本次后台观察没有显示宽度，不能使用 display；请重新观察")
            val height = snapshot.displayHeight
                ?: throw IllegalArgumentException("本次后台观察没有显示高度，不能使用 display；请重新观察")
            require(x in 0 until width && y in 0 until height) {
                "display 坐标超出范围：($x,$y) not in ${width}x$height"
            }
            return Pair(x, y)
        }
        if (space != "screenshot") {
            throw IllegalArgumentException("coordinate_space 只能是 normalized、screen、display 或 screenshot")
        }
        val frame = args.optString("frame_id").trim()
        require(frame.isNotBlank() && frame == snapshot.frameId) {
            "screenshot 坐标必须携带本次 observe_screen 返回的 frame_id"
        }
        val width = snapshot.screenshotWidth
            ?: throw IllegalArgumentException("本次观察没有截图尺寸")
        val height = snapshot.screenshotHeight
            ?: throw IllegalArgumentException("本次观察没有截图尺寸")
        require(x in 0 until width && y in 0 until height) {
            "截图坐标超出范围：($x,$y) not in ${width}x$height"
        }
        val scaleX = snapshot.scaleToDisplayX
            ?: throw IllegalArgumentException("本次观察没有截图横向缩放比例，不能使用 screenshot 坐标")
        val scaleY = snapshot.scaleToDisplayY
            ?: throw IllegalArgumentException("本次观察没有截图纵向缩放比例，不能使用 screenshot 坐标")
        return Pair((x * scaleX).roundToInt(), (y * scaleY).roundToInt())
    }

    private fun backendResult(name: String, args: JSONObject): JSONObject = parseBackend(backend.execute(name, args.toString()))

    /**
     * 刚在后台屏打开的应用还没把窗口交给观察服务时，引擎返回 WINDOW_NOT_READY。以前直接报给模型，
     * 模型要「等待 3 秒 → 再观察」来回几轮（真机上一次任务里白白多了 2 次失败、1 次等待）。
     * 现在程序自己短暂等窗口出来再读；超过 [WINDOW_READY_WAIT_MS] 仍没有才把原来的结果交回去。
     */
    private fun dumpWhenWindowReady(args: JSONObject, first: JSONObject): JSONObject {
        var dump = first
        val startedAt = System.nanoTime()
        while (!dump.optBoolean("ok", false) && dump.optString("code") == WINDOW_NOT_READY &&
            (System.nanoTime() - startedAt) / 1_000_000L < WINDOW_READY_WAIT_MS
        ) {
            controller.awaitRetryDelay(WINDOW_READY_POLL_MS)
            controller.throwIfCancelled()
            dump = backendResult("screen_dump", args)
        }
        return dump
    }

    private fun backendResults(calls: List<Pair<String, JSONObject>>): List<JSONObject> =
        backend.executeConcurrently(calls.map { (name, args) -> name to args.toString() }).map(::parseBackend)

    private fun parseBackend(raw: String?): JSONObject {
        raw ?: return JSONObject().put("ok", false)
            .put("code", "BACKGROUND_TOOL_UNAVAILABLE")
            .put("error", "后台屏幕后端没有返回结果")
        return parseJson(raw) ?: JSONObject().put("ok", false)
            .put("code", "BACKGROUND_TOOL_UNAVAILABLE")
            .put("error", raw.take(300))
    }

    private fun resultWithLocation(result: JSONObject, toolName: String): AgentModelClient.ToolResult {
        val copy = JSONObject(result.toString())
        if (!copy.has("execution_location")) {
            copy.put("execution_location", TaskExecutionLocation.BACKGROUND.wireValue)
        }
        if (!copy.has("tool")) copy.put("tool", toolName)
        return io.github.mangi.eta.agent.tool.feedbackToolResult(copy)
    }

    private fun invalidateObservation() {
        synchronized(lock) { observation = null }
    }

    private fun markBackgroundIntent() {
        synchronized(lock) {
            location = TaskExecutionLocation.BACKGROUND
            observation = null
        }
    }

    private fun coordinateContract(snapshot: Observation): JSONObject = JSONObject()
        .put("recommended_coordinate_space", "display")
        .put("display", JSONObject()
            .put("width", snapshot.displayWidth ?: 0)
            .put("height", snapshot.displayHeight ?: 0)
            .put("rotation", snapshot.rotation ?: JSONObject.NULL)
            .put("display_id", snapshot.displayId ?: JSONObject.NULL))
        .apply {
            if (snapshot.screenshotWidth != null && snapshot.screenshotHeight != null) {
                put("screenshot", JSONObject()
                    .put("width", snapshot.screenshotWidth)
                    .put("height", snapshot.screenshotHeight)
                    .put("frame_id", snapshot.frameId ?: JSONObject.NULL))
            }
        }
        .put("note", "display 使用后台屏像素；screenshot 必须携带本次观察返回的 frame_id；normalized 仅在知道屏幕尺寸时可用")

    private fun geometryOf(json: JSONObject): Geometry? {
        val geometry = json.optJSONObject("geometry")
        val width = geometry?.optInt("width", 0)?.takeIf { it > 0 }
            ?: json.optInt("displayWidth", 0).takeIf { it > 0 }
            ?: return null
        val height = geometry?.optInt("height", 0)?.takeIf { it > 0 }
            ?: json.optInt("displayHeight", 0).takeIf { it > 0 }
            ?: return null
        val rotation = geometry?.optInt("rotation", -1)?.takeIf { it in 0..3 }
            ?: json.optInt("rotation", -1).takeIf { it in 0..3 }
            ?: return null
        // A background observation must identify one hidden display. Missing identities
        // must not compare equal as null == null, and display 0 must never become a fallback.
        val geometryDisplayId = geometry?.takeIf { it.has("displayId") }?.optInt("displayId", -1)
        val responseDisplayId = json.takeIf { it.has("displayId") }?.optInt("displayId", -1)
        if (geometryDisplayId != null && geometryDisplayId <= 0) return null
        if (responseDisplayId != null && responseDisplayId <= 0) return null
        if (geometryDisplayId != null && responseDisplayId != null && geometryDisplayId != responseDisplayId) return null
        val displayId = geometryDisplayId ?: responseDisplayId ?: return null
        return Geometry(displayId, width, height, rotation)
    }

    private fun sameGeometry(first: Geometry, second: Geometry): Boolean =
        first.width == second.width &&
            first.height == second.height &&
            first.rotation == second.rotation &&
            first.displayId == second.displayId

    private fun dims(json: JSONObject): Pair<Int, Int>? {
        val dims = json.optString("dims").trim()
        val match = Regex("""(\d+)x(\d+)""").find(dims) ?: return null
        return match.groupValues[1].toIntOrNull()?.let { width ->
            match.groupValues[2].toIntOrNull()?.let { height -> width to height }
        }
    }

    private fun screenshotWidth(json: JSONObject): Int? =
        json.optInt("width", 0).takeIf { it > 0 } ?: dims(json)?.first

    private fun screenshotHeight(json: JSONObject): Int? =
        json.optInt("height", 0).takeIf { it > 0 } ?: dims(json)?.second

    private fun packageOf(json: JSONObject): String =
        json.optString("package").ifBlank {
            parseNodes(json).firstOrNull()?.packageName.orEmpty()
        }

    private fun parseNodes(json: JSONObject): List<Node> {
        val array = json.optJSONArray("nodes") ?: json.optJSONArray("ui_nodes") ?: return emptyList()
        val result = mutableListOf<Node>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val bounds = parseBounds(item.opt("bounds"))
            val actions = item.optJSONArray("actions").let { array ->
                if (array == null) emptyList() else (0 until array.length()).mapNotNull { i -> array.optInt(i).takeIf { it >= 0 } }
            }
            result += Node(
                index = index,
                text = item.optString("text"),
                description = item.optString("desc").ifBlank { item.optString("description") },
                className = firstNonBlank(item.optString("cls"), item.optString("class")),
                packageName = firstNonBlank(
                    item.optString("pkg"),
                    item.optString("package"),
                    json.optString("package"),
                ),
                viewId = firstNonBlank(
                    item.optString("vid"),
                    item.optString("view_id"),
                    item.optString("id"),
                ),
                bounds = bounds,
                clickable = item.optBoolean("clickable", item.optBoolean("click", false)),
                longClickable = item.optBoolean("long_clickable", actions.contains(32)),
                scrollable = item.optBoolean("scrollable", false),
                focused = item.optBoolean("focused", false),
                editable = item.optBoolean("editable", item.optBoolean("edit", false)),
                password = item.optBoolean("password", false),
                enabled = if (item.has("enabled")) item.optBoolean("enabled") else true,
                checked = if (item.has("checked")) item.optBoolean("checked") else null,
                selected = item.optBoolean("selected", false),
                actions = actions,
            )
        }
        return result
    }

    private fun parseBounds(value: Any?): Bounds? {
        if (value is JSONArray && value.length() >= 4) {
            return Bounds(
                left = value.optInt(0),
                top = value.optInt(1),
                right = value.optInt(2),
                bottom = value.optInt(3),
            )
        }
        if (value is JSONObject) {
            return Bounds(
                left = value.optInt("left"),
                top = value.optInt("top"),
                right = value.optInt("right"),
                bottom = value.optInt("bottom"),
            )
        }
        val text = value?.toString().orEmpty()
        val match = Regex("""\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""").find(text) ?: return null
        return Bounds(
            left = match.groupValues[1].toIntOrNull() ?: return null,
            top = match.groupValues[2].toIntOrNull() ?: return null,
            right = match.groupValues[3].toIntOrNull() ?: return null,
            bottom = match.groupValues[4].toIntOrNull() ?: return null,
        )
    }

    private fun fingerprint(
        nodes: List<Node>,
        geometry: Geometry?,
        packageName: String? = null,
    ): String {
        val source = buildString {
            append(packageName.orEmpty()).append('|')
            append(geometry?.displayId ?: -1).append('|')
            append(geometry?.width ?: 0).append('x').append(geometry?.height ?: 0)
            append('@').append(geometry?.rotation ?: -1)
            nodes.forEach { append('\n').append(it.canonical()) }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte) }
    }

    private fun parseJson(raw: String?): JSONObject? {
        if (raw.isNullOrBlank()) return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun firstNonBlank(vararg values: String?): String =
        values.firstOrNull { !it.isNullOrBlank() }.orEmpty()

    private fun error(code: String, message: String, toolName: String): AgentModelClient.ToolResult =
        AgentModelClient.ToolResult(
            JSONObject()
                .put("ok", false)
                .put("code", code)
                .put("message", message)
                .put("tool", toolName)
                .put("execution_location", TaskExecutionLocation.BACKGROUND.wireValue)
                .toString(),
        )

    private companion object {
        /** 动作后等页面静下来的上限；静了就提前继续。 */
        const val AFTER_SETTLE_MS = 800
        const val LAUNCH_SETTLE_MS = 1_500
        const val AFTER_RETRY_DELAY_MS = 300L
        const val LATE_CHANGE_CHECKS = 2
        const val LATE_CHANGE_DELAY_MS = 600L
        private const val MIN_READABLE_FOR_RECHECK = 6
        private val LATE_CHANGE_TOOLS = setOf("tap", "tap_element", "tap_area")
        val LOST_SESSION_CODES = setOf("NO_SESSION", "SESSION_CLOSED", "STALE_SCREEN", "SYSTEM_RESTARTED")
        const val WINDOW_NOT_READY = "WINDOW_NOT_READY"
        /** 刚打开的应用交出窗口通常不到 2 秒；最多替模型等这么久。 */
        const val WINDOW_READY_WAIT_MS = 4_000L
        const val WINDOW_READY_POLL_MS = 250L
    }
}
