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
        val fingerprint: String,
        val limit: Int,
        val truncated: Boolean,
        val packageName: String,
        val displayId: Int?,
        val displayWidth: Int?,
        val displayHeight: Int?,
        val rotation: Int?,
        var screenshotWidth: Int?,
        var screenshotHeight: Int?,
        var frameId: String?,
        var scaleToDisplayX: Double?,
        var scaleToDisplayY: Double?,
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

    /** Update task-local state after a legacy screen_* call without replacing its executor path. */
    fun observeLegacyResult(name: String, raw: String?) {
        val result = parseJson(raw) ?: return
        val ok = result.optBoolean("ok", false)
        when (name) {
            "screen_open" -> if (ok && result.optString("mode") == "hidden" &&
                (result.optInt("displayId", result.optInt("display", 0)) > 0)) {
                synchronized(lock) {
                    location = TaskExecutionLocation.BACKGROUND
                    observation = null
                }
            }
            "screen_finish" -> if (ok) {
                synchronized(lock) {
                    location = TaskExecutionLocation.FOREGROUND
                    observation = null
                }
            }
            "screen_state" -> if (result.has("sessionValid") && !result.optBoolean("sessionValid")) {
                synchronized(lock) { observation = null }
            }
            else -> {
                if (result.optString("code") in LOST_SESSION_CODES ||
                    result.optString("code") == "APP_TAKEN_OVER") {
                    synchronized(lock) { observation = null }
                }
            }
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
        return resultWithLocation(
            JSONObject(result.toString())
                .put("execution_location", TaskExecutionLocation.BACKGROUND.wireValue)
                .put("observation_required", true),
            "launch_app",
        )
    }

    private fun backgroundObserve(args: JSONObject): AgentModelClient.ToolResult {
        controller.throwIfCancelled()
        val options = AgentScreenObservationContract.resolve(args)
        val maxNodes = options.maxNodes.coerceIn(
            AgentScreenObservationContract.MIN_MAX_NODES,
            AgentScreenObservationContract.MAX_MAX_NODES,
        )

        // Boot.dump() has no geometry. The engine's shot response is the authoritative
        // display geometry (displayId/width/height/rotation) and the only source of the
        // screenshot pixel dimensions. It is captured even for tree-only observations.
        val shot = backendResult("screen_shot", JSONObject().put("scale", 0.5))
        if (!shot.optBoolean("ok", false)) {
            invalidateObservation()
            return error(
                "BACKGROUND_GEOMETRY_UNAVAILABLE",
                "无法取得后台屏权威几何（${shot.optString("code", "SCREENSHOT_FAILED")}）；本次没有生成观察，也未猜测尺寸",
                "observe_screen",
            )
        }
        val shotGeometry = geometryOf(shot) ?: run {
            invalidateObservation()
            return error(
                "BACKGROUND_GEOMETRY_UNAVAILABLE",
                "后台截图缺少 displayId/width/height/rotation 权威几何；本次没有生成观察",
                "observe_screen",
            )
        }
        val screenshotWidth = screenshotWidth(shot) ?: run {
            invalidateObservation()
            return error("BACKGROUND_GEOMETRY_UNAVAILABLE", "后台截图缺少实际图片宽度；本次没有生成观察", "observe_screen")
        }
        val screenshotHeight = screenshotHeight(shot) ?: run {
            invalidateObservation()
            return error("BACKGROUND_GEOMETRY_UNAVAILABLE", "后台截图缺少实际图片高度；本次没有生成观察", "observe_screen")
        }

        var dump: JSONObject? = null
        if (options.includeUiTree) {
            dump = backendResult("screen_dump", JSONObject().put("limit", maxNodes))
            if (!dump.optBoolean("ok", false)) {
                invalidateObservation()
                return resultWithLocation(requireNotNull(dump), "observe_screen")
            }
            val dumpGeometry = geometryOf(dump)
            if (dumpGeometry != null && !sameGeometry(shotGeometry, dumpGeometry)) {
                invalidateObservation()
                return error(
                    "BACKGROUND_OBSERVATION_CHANGED",
                    "截图与节点来自不同的后台屏几何（displayId/width/height/rotation 不一致）；请重新 observe_screen",
                    "observe_screen",
                )
            }
            val dumpDisplayId = dump.optInt("displayId", -1).takeIf { it >= 0 }
            if (dumpDisplayId != null && dumpDisplayId != shotGeometry.displayId) {
                invalidateObservation()
                return error(
                    "BACKGROUND_OBSERVATION_CHANGED",
                    "截图与节点来自不同的后台屏（displayId 不一致）；请重新 observe_screen",
                    "observe_screen",
                )
            }
        }

        val nodes = dump?.let(::parseNodes).orEmpty()
        val packageName = packageOf(dump ?: shot)
        val frameId = shot.optString("frameId").orEmpty().ifBlank { null }
        val scaleToDisplayX = shot.optDouble("scaleToDisplayX")
            .takeIf { it.isFinite() && it > 0 }
            ?: (shotGeometry.width.toDouble() / screenshotWidth)
        val scaleToDisplayY = shot.optDouble("scaleToDisplayY")
            .takeIf { it.isFinite() && it > 0 }
            ?: (shotGeometry.height.toDouble() / screenshotHeight)
        val fingerprint = fingerprint(nodes, shotGeometry, packageName)
        val id = "bg-$runId-${observationSequence.incrementAndGet()}"
        val snapshot = Observation(
            id = id,
            generation = controller.resumeGeneration,
            nodes = nodes,
            treeRead = options.includeUiTree,
            fingerprint = fingerprint,
            limit = maxNodes,
            truncated = dump?.optBoolean("truncated", false) == true ||
                dump?.optBoolean("scanLimited", false) == true ||
                nodes.size >= maxNodes,
            packageName = packageName,
            displayId = shotGeometry.displayId,
            displayWidth = shotGeometry.width,
            displayHeight = shotGeometry.height,
            rotation = shotGeometry.rotation,
            screenshotWidth = screenshotWidth,
            screenshotHeight = screenshotHeight,
            frameId = frameId,
            scaleToDisplayX = scaleToDisplayX,
            scaleToDisplayY = scaleToDisplayY,
        )
        synchronized(lock) { observation = snapshot }

        var screenshotAttachment: AgentModelClient.ToolResult? = null
        var screenshotError: String? = null
        if (options.includeScreenshot) {
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
                .put("width", shotGeometry.width)
                .put("height", shotGeometry.height)
                .put("rotation", shotGeometry.rotation))
            .put("display_id", snapshot.displayId ?: JSONObject.NULL)
            .put("focus", JSONObject().put("package", packageName))
            .put("observation_id", id)
            .put("observation_source", "background")
            .put("ui_tree_read", snapshot.treeRead)
            .put("node_limit", maxNodes)
            .put("ui_tree_truncated", snapshot.truncated)
            .put("ui_nodes", JSONArray().also { array -> nodes.forEach { array.put(it.toJson()) } })
            .put("coordinate_contract", coordinateContract(snapshot))
            .put("screenshot", screenshotJson(shot, screenshotError, screenshotAttachment))
            .apply { snapshot.frameId?.let { put("frame_id", it) } }
        return AgentModelClient.ToolResult(
            content = response.toString(),
            images = screenshotAttachment?.images.orEmpty(),
            sensitive = screenshotAttachment?.sensitive == true,
        )
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
        "input_text" -> typeText(args)
        "replace_text" -> typeText(args)
        "clear_text" -> error("BACKGROUND_UNSUPPORTED_OPERATION", "后台暂不支持 clear_text；请先观察输入框并重新输入", name)
        "wait" -> waitOnly(args)
        "wait_for_text" -> waitForText(args)
        "press_key" -> pressKey(args)
        "wait_for_package" -> error("BACKGROUND_UNSUPPORTED_OPERATION", "后台屏无法可靠判断主屏前台包名；本次未执行", name)
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
        if (!validateLiveSnapshot(snapshot, toolName)) {
            return error("OBSERVATION_STALE", "屏幕已经变化，旧观察不能执行输入；请先重新 observe_screen", toolName)
        }
        val point = convertPoint(args, snapshot)
        val backendArgs = JSONObject()
            .put("x", point.first)
            .put("y", point.second)
            .put("coordinate_space", "display")
        if (backendName == "screen_press") backendArgs.put("hold_ms", args.optInt("duration_ms", 800))
        return mutateAndInvalidate(backendName, backendArgs, toolName)
    }

    private fun tapArea(args: JSONObject): AgentModelClient.ToolResult {
        val snapshot = requireObservation("tap_area", args) ?: return observationError("tap_area", args)
        if (!validateLiveSnapshot(snapshot, "tap_area")) {
            return error("OBSERVATION_STALE", "屏幕已经变化，旧观察不能执行输入；请先重新 observe_screen", "tap_area")
        }
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
        return mutateAndInvalidate(
            "screen_tap",
            JSONObject().put("x", centerX).put("y", centerY).put("coordinate_space", "display"),
            "tap_area",
        )
    }

    private fun swipe(args: JSONObject): AgentModelClient.ToolResult {
        val snapshot = requireObservation("swipe", args) ?: return observationError("swipe", args)
        if (!validateLiveSnapshot(snapshot, "swipe")) {
            return error("OBSERVATION_STALE", "屏幕已经变化，旧观察不能执行输入；请先重新 observe_screen", "swipe")
        }
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
            "screen_swipe",
            JSONObject()
                .put("x1", start.first).put("y1", start.second)
                .put("x2", end.first).put("y2", end.second)
                .put("coordinate_space", "display")
                .put("duration_ms", args.optInt("duration_ms", 500)),
            "swipe",
        )
    }

    private fun nodeAction(
        toolName: String,
        args: JSONObject,
        backendName: String,
    ): AgentModelClient.ToolResult {
        val snapshot = requireObservation(toolName, args) ?: return observationError(toolName, args)
        if (!validateLiveSnapshot(snapshot, toolName)) {
            return error("OBSERVATION_STALE", "屏幕已经变化，旧观察不能执行输入；请先重新 observe_screen", toolName)
        }
        val node = findNode(snapshot, args)
            ?: return error("INVALID_NODE_INDEX", "观察快照中不存在节点 index=${args.optInt("index", -1)}", toolName)
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
        if (!validateLiveSnapshot(snapshot, "scroll")) {
            return error("OBSERVATION_STALE", "屏幕已经变化，旧观察不能执行输入；请先重新 observe_screen", "scroll")
        }
        return mutateAndInvalidate(
            "screen_scroll",
            JSONObject().put("direction", args.optString("direction")),
            "scroll",
        )
    }

    private fun scrollElement(args: JSONObject): AgentModelClient.ToolResult {
        val snapshot = requireObservation("scroll_element", args) ?: return observationError("scroll_element", args)
        if (!validateLiveSnapshot(snapshot, "scroll_element")) {
            return error("OBSERVATION_STALE", "屏幕已经变化，旧观察不能执行输入；请先重新 observe_screen", "scroll_element")
        }
        val node = findNode(snapshot, args)
            ?: return error("INVALID_NODE_INDEX", "观察快照中不存在可滚动节点 index=${args.optInt("index", -1)}", "scroll_element")
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
        val snapshot = requireObservation("type_text", args) ?: return observationError("type_text", args)
        if (!validateLiveSnapshot(snapshot, "type_text")) {
            return error("OBSERVATION_STALE", "屏幕已经变化，旧观察不能执行输入；请先重新 observe_screen", "type_text")
        }
        val mode = args.optString("mode", "replace").lowercase(Locale.ROOT)
        if (mode == "append") {
            return error("BACKGROUND_UNSUPPORTED_OPERATION", "后台暂不支持 append 模式；本次未执行", "type_text")
        }
        if (args.optBoolean("submit", false)) {
            return error("BACKGROUND_UNSUPPORTED_OPERATION", "后台暂不支持 submit 按键；请写入后单独点击发送按钮", "type_text")
        }
        val backendArgs = JSONObject().put("text", args.optString("text"))
        if (args.has("index") && !args.isNull("index")) {
            val node = findNode(snapshot, args)
                ?: return error("INVALID_NODE_INDEX", "观察快照中不存在输入节点", "type_text")
            if (!node.editable) return error("NODE_NOT_EDITABLE", "所选节点不是输入框", "type_text")
            if (node.viewId.isNotBlank()) backendArgs.put("view_id", node.viewId)
            else backendArgs.put("index", editableOrdinal(snapshot, node))
        }
        return mutateAndInvalidate("screen_text", backendArgs, "type_text")
    }

    private fun waitOnly(args: JSONObject): AgentModelClient.ToolResult {
        val duration = args.optInt("duration_ms", 1_000).coerceIn(100, 30_000)
        controller.awaitRetryDelay(duration.toLong())
        return AgentModelClient.ToolResult(
            JSONObject()
                .put("ok", true)
                .put("tool", "wait")
                .put("execution_location", TaskExecutionLocation.BACKGROUND.wireValue)
                .put("waited_ms", duration)
                .toString(),
        )
    }

    private fun waitForText(args: JSONObject): AgentModelClient.ToolResult {
        controller.throwIfCancelled()
        val result = backendResult(
            "screen_wait",
            JSONObject()
                .put("text", args.optString("text"))
                .put("timeout_ms", args.optInt("timeout_ms", 10_000)),
        )
        return resultWithLocation(result, "wait_for_text")
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
        val snapshot = requireObservation("press_key", args) ?: return observationError("press_key", args)
        if (!validateLiveSnapshot(snapshot, "press_key")) {
            return error("OBSERVATION_STALE", "屏幕已经变化，旧观察不能执行输入；请先重新 observe_screen", "press_key")
        }
        return mutateAndInvalidate(
            "screen_key",
            JSONObject().put("code", code),
            "press_key",
        )
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

    private fun validateLiveSnapshot(snapshot: Observation, toolName: String): Boolean {
        // A tree-only observation must still validate its geometry. A screenshot-only
        // observation must not be treated as an empty tree.
        val current = if (snapshot.treeRead) {
            backendResult("screen_dump", JSONObject().put("limit", snapshot.limit)).also { dump ->
                if (!dump.optBoolean("ok", false)) {
                    invalidateObservation()
                    return false
                }
            }
        } else {
            null
        }
        val shot = backendResult("screen_shot", JSONObject().put("scale", 0.5))
        if (!shot.optBoolean("ok", false)) {
            invalidateObservation()
            return false
        }
        val geometry = geometryOf(shot) ?: run {
            invalidateObservation()
            return false
        }
        val saved = Geometry(snapshot.displayId, snapshot.displayWidth ?: 0, snapshot.displayHeight ?: 0, snapshot.rotation ?: -1)
        if (saved.width <= 0 || saved.height <= 0 || saved.rotation !in 0..3 || !sameGeometry(saved, geometry)) {
            invalidateObservation()
            return false
        }
        if (current != null) {
            val dumpDisplayId = current.optInt("displayId", -1)
            if (dumpDisplayId >= 0 && snapshot.displayId != null && dumpDisplayId != snapshot.displayId) {
                invalidateObservation()
                return false
            }
            val nodes = parseNodes(current)
            if (fingerprint(nodes, geometry, packageOf(current)) != snapshot.fingerprint) {
                invalidateObservation()
                return false
            }
        }
        val nextFrame = shot.optString("frameId").orEmpty().ifBlank { snapshot.frameId }
        val nextWidth = screenshotWidth(shot) ?: snapshot.screenshotWidth
        val nextHeight = screenshotHeight(shot) ?: snapshot.screenshotHeight
        synchronized(lock) {
            // Only an unchanged authoritative geometry may refresh the frame. The old
            // observation is never re-qualified from a refreshed engine geometry cache.
            snapshot.frameId = nextFrame
            snapshot.screenshotWidth = nextWidth
            snapshot.screenshotHeight = nextHeight
            snapshot.scaleToDisplayX = shot.optDouble("scaleToDisplayX")
                .takeIf { it.isFinite() && it > 0 } ?: snapshot.scaleToDisplayX
            snapshot.scaleToDisplayY = shot.optDouble("scaleToDisplayY")
                .takeIf { it.isFinite() && it > 0 } ?: snapshot.scaleToDisplayY
        }
        return true
    }

    private fun mutateAndInvalidate(
        backendName: String,
        backendArgs: JSONObject,
        toolName: String,
    ): AgentModelClient.ToolResult {
        controller.throwIfCancelled()
        val result = backendResult(backendName, backendArgs)
        val code = result.optString("code")
        val ok = result.optBoolean("ok", false)
        if (ok || result.optBoolean("operationMayHaveRun", false) || code in setOf("TIMEOUT", "ACTION_OUTCOME_UNKNOWN", "UNKNOWN", "APP_TAKEN_OVER") ||
            code in LOST_SESSION_CODES) {
            invalidateObservation()
        }
        return resultWithLocation(result, toolName)
    }

    private fun findNode(snapshot: Observation, args: JSONObject): Node? {
        val index = args.optInt("index", -1)
        if (index < 0) return null
        return snapshot.nodes.firstOrNull { it.index == index }
    }

    private fun editableOrdinal(snapshot: Observation, target: Node): Int =
        snapshot.nodes.filter { it.editable }.indexOfFirst { it === target }.coerceAtLeast(0)

    private fun withNodeEvidence(
        result: AgentModelClient.ToolResult,
        snapshot: Observation,
        node: Node,
    ): AgentModelClient.ToolResult {
        val json = runCatching { JSONObject(result.content) }.getOrNull() ?: return result
        json.put("observation_id", snapshot.id)
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
                ?: snapshot.nodes.mapNotNull { it.bounds?.right }.maxOrNull()
                ?: throw IllegalArgumentException("本次后台观察没有屏幕尺寸，请先重新观察")
            val height = snapshot.displayHeight
                ?: snapshot.nodes.mapNotNull { it.bounds?.bottom }.maxOrNull()
                ?: throw IllegalArgumentException("本次后台观察没有屏幕尺寸，请先重新观察")
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
        val scaleX = snapshot.scaleToDisplayX ?: (snapshot.displayWidth?.toDouble()?.div(width) ?: 1.0)
        val scaleY = snapshot.scaleToDisplayY ?: (snapshot.displayHeight?.toDouble()?.div(height) ?: 1.0)
        return Pair((x * scaleX).roundToInt(), (y * scaleY).roundToInt())
    }

    private fun backendResult(name: String, args: JSONObject): JSONObject {
        val raw = backend.execute(name, args.toString())
            ?: return JSONObject().put("ok", false)
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
        return AgentModelClient.ToolResult(copy.toString())
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
        val displayId = geometry?.optInt("displayId", -1)?.takeIf { it >= 0 }
            ?: json.optInt("displayId", -1).takeIf { it >= 0 }
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
        val LOST_SESSION_CODES = setOf("NO_SESSION", "SESSION_CLOSED", "STALE_SCREEN", "SYSTEM_RESTARTED")
    }
}
