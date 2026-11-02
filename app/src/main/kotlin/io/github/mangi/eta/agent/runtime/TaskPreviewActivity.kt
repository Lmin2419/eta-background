package io.github.mangi.eta.agent.runtime

import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.eta.shadow.ShadowTools
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import io.github.mangi.eta.agent.overlay.localizedText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/** Temporary task window opened by a notification, not a persistent overlay or a second card. */
internal class TaskPreviewActivity : ComponentActivity() {
    private val owner = UUID.randomUUID().toString()
    private val io = Mutex()
    private var runId = ""
    private var pkg = ""
    private var visible = false
    private var expanded = false
    private var manual = false
    private var busy = false
    private var resizing = false
    private var closing = false
    private var freshFrame = true
    private var inputFeedback: String? = null
    private var downInView: Pair<Float, Float>? = null
    private var controller: AgentRunController? = null
    private var frame: Bitmap? = null
    private var geometry: JSONObject? = null
    private var previewKey = ""
    private var gestureGeometry: JSONObject? = null
    private var down: Pair<Float, Float>? = null
    private var downTime = 0L
    private var polling: Job? = null
    private var animation: ValueAnimator? = null
    private lateinit var root: LinearLayout
    private lateinit var picture: TaskPreviewImageView
    private lateinit var status: TextView
    private lateinit var hint: TextView
    private lateinit var take: Button
    private lateinit var resume: Button
    private lateinit var back: Button
    private lateinit var zoom: Button
    private lateinit var openApp: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runId = intent.getStringExtra("run_id").orEmpty()
        pkg = intent.getStringExtra("package").orEmpty()
        if (runId.isBlank() || pkg.isBlank() || !TaskPreviewRegistry.mainDisplayAvailable(this)) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        window.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        window.attributes = window.attributes.apply { y = dp(56); dimAmount = .18f }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(Color.rgb(25, 27, 34)); cornerRadius = dp(26).toFloat() }
            clipToOutline = true
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        status = label("后台屏幕", 15f)
        header.addView(status, LinearLayout.LayoutParams(0, dp(42), 1f))
        zoom = button("放大") { toggleSize() }
        header.addView(zoom)
        header.addView(button("关闭") { finish() })
        root.addView(header)
        picture = TaskPreviewImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "后台应用预览；只读时点击放大，接管后使用上方放大按钮"
            setBackgroundColor(Color.rgb(30, 33, 45))
            setOnClickListener { if (!manual) toggleSize() }
            setOnTouchListener { _, event -> touch(event) }
        }
        root.addView(picture, LinearLayout.LayoutParams(-1, 0, 1f))
        hint = label("正在连接画面…", 12f)
        root.addView(hint, LinearLayout.LayoutParams(-1, dp(44)))
        val controls = LinearLayout(this)
        take = button("接管") { acquire() }
        resume = button("让 AI 继续") { release(resumeTask = true) }
        back = button("返回") { sendInput("key", JSONObject().put("code", 4)) }
        listOf(take, resume, back).forEach { controls.addView(it, LinearLayout.LayoutParams(0, dp(48), 1f)) }
        root.addView(controls)
        openApp = button("打开 App") { openCompletedApp() }
        root.addView(openApp, LinearLayout.LayoutParams(-1, dp(44)))
        setContentView(root)
        resize(animate = false)
        root.alpha = 0f
        root.translationY = -dp(16).toFloat()
        root.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        updateControls()
    }

    private fun label(text: String, size: Float) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL
    }
    private fun button(text: String, click: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 12f; isAllCaps = false; minimumWidth = 0
        setPadding(dp(4), 0, dp(4), 0); setOnClickListener { click() }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toggleSize() {
        if (busy || resizing || closing) return
        expanded = !expanded
        clearGesture()
        resize(animate = true)
    }

    private fun clearGesture() {
        down = null; downInView = null; gestureGeometry = null
        if (::picture.isInitialized) picture.clearTouch()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        clearGesture(); geometry = null; freshFrame = true
        if (::picture.isInitialized) resize(animate = false)
    }

    private fun resize(animate: Boolean) {
        animation?.cancel()
        // Current metrics may already be the small floating window on an OEM ROM.
        // Always size against the display, not against our previous panel size.
        val metrics = windowManager.maximumWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val availableWidth = (metrics.bounds.width() - insets.left - insets.right).coerceAtLeast(1)
        val availableHeight = (metrics.bounds.height() - insets.top - insets.bottom - dp(24)).coerceAtLeast(1)
        window.attributes = window.attributes.apply { y = insets.top + dp(12) }
        val width = if (expanded) (availableWidth * .96).toInt() else minOf(dp(320), (availableWidth * .92).toInt())
        val height = if (expanded) (availableHeight * .94).toInt() else minOf(dp(450), (availableHeight * .75).toInt())
        val fromW = window.attributes.width.takeIf { it > 0 } ?: width
        val fromH = window.attributes.height.takeIf { it > 0 } ?: height
        zoom.text = if (expanded) "缩小" else "放大"
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) { resizing = false; window.setLayout(width, height); updateControls(); return }
        resizing = true; updateControls()
        animation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 240; interpolator = DecelerateInterpolator()
            addUpdateListener { val f = it.animatedValue as Float; window.setLayout((fromW + (width - fromW) * f).toInt(), (fromH + (height - fromH) * f).toInt()) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { resizing = false; updateControls() }
            })
            start()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!::picture.isInitialized) return
        visible = true
        polling = lifecycleScope.launch {
            while (isActive && visible) {
                if (!TaskPreviewRegistry.mainDisplayAvailable(this@TaskPreviewActivity)) { finish(); break }
                if (!busy && !closing && down == null) {
                    val result = rpc("preview_frame", JSONObject().put("edge", if (expanded) 960 else 640)
                        .put("since", if (freshFrame) "" else previewKey))
                    if (!visible) break
                    if (busy || closing || down != null) { freshFrame = true; continue }
                    if (result.optBoolean("ok") && !result.optBoolean("unchanged")) {
                        val encoded = result.optString("image")
                        val bitmap = withContext(Dispatchers.Default) {
                            if (encoded.length > 550000) null else runCatching {
                                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }.getOrNull()
                        }
                        if (!visible) { bitmap?.recycle(); break }
                        if (busy || closing || down != null) { bitmap?.recycle(); freshFrame = true; continue }
                        displayFrame(bitmap, result.optJSONObject("geometry"), result.optString("previewKey"))
                    } else if (!result.optBoolean("ok")) {
                        geometry = null; freshFrame = true
                        val code = result.optString("code")
                        if (code !in setOf("COMMAND_BUSY", "COMMAND_DRAINING")) clearFrame()
                        hint.text = when (code) {
                            "NO_FRAME", "FRAME_GEOMETRY_CHANGED" -> "等待可用画面 · 暂以手机图标显示"
                            "COMMAND_BUSY", "COMMAND_DRAINING" -> "操作尚未结束，等待画面更新…"
                            else -> previewFailure(result)
                        }
                    }
                    updateControls()
                }
                delay(if (expanded) 350 else 800)
            }
        }
    }

    internal fun displayFrame(bitmap: Bitmap?, displayGeometry: JSONObject?, key: String) {
        previewKey = key; freshFrame = false
        if (bitmap == null || !PreviewFrameContent.usable(bitmap)) {
            bitmap?.recycle(); clearFrame()
            hint.text = "暂未取得可用画面 · 手机图标为占位，不可点击操作"
        } else {
            val wasMissing = geometry == null
            geometry = displayGeometry
            val old = frame; frame = bitmap; picture.setImageBitmap(bitmap); old?.recycle()
            if (wasMissing) hint.text = inputFeedback ?: if (manual) "可操作 · 上方可放大，关闭后 AI 保持暂停" else readOnlyHint()
        }
        updateControls()
    }

    private fun clearFrame() {
        geometry = null
        if (::picture.isInitialized) { picture.setImageDrawable(null); picture.clearTouch() }
        frame?.recycle(); frame = null
    }

    override fun finish() {
        if (!closing && visible && ::root.isInitialized && ValueAnimator.areAnimatorsEnabled()) {
            closing = true; clearGesture(); updateControls()
            root.animate().alpha(0f).translationY(-dp(8).toFloat()).setDuration(150).withEndAction { super.finish() }.start()
        } else super.finish()
    }

    override fun onStop() {
        visible = false
        polling?.cancel()
        animation?.cancel()
        clearGesture(); clearFrame()
        if (::root.isInitialized) root.animate().cancel()
        // A bounded process-owned executor completes release even if the Activity is destroyed.
        val ownedController = controller
        cleanup.execute {
            runCatching { ShadowTools.preview(runId, pkg, owner, "preview_release", "{}") }
            ownedController?.releaseManual(owner)
        }
        manual = false
        super.onStop()
        finish() // Reopening always obtains a fresh frame and a new manual lease.
    }

    private suspend fun rpc(command: String, args: JSONObject = JSONObject()): JSONObject = io.withLock {
        withContext(Dispatchers.IO) {
            runCatching { JSONObject(ShadowTools.preview(runId, pkg, owner, command, args.toString())) }
                .getOrElse { JSONObject().put("ok", false).put("code", "PREVIEW_CONNECTION_LOST")
                    .put("message", "连接中断；输入结果可能未知，请重新观察，不会自动重试输入") }
        }
    }

    private fun acquire() {
        if (busy || resizing || closing || manual || geometry == null) return
        inputFeedback = null
        busy = true; updateControls(); hint.text = "正在暂停此任务并等待操作结束…"
        lifecycleScope.launch {
            controller = TaskPreviewRegistry.controller(runId)?.takeUnless { it.isCancelled || AgentExecutionService.task(runId)?.completed == true }
            val drained = withContext(Dispatchers.IO) { controller?.acquireManual(owner) ?: true }
            val result = if (drained && visible) rpc("preview_acquire", JSONObject().put("requireParked", controller == null)) else JSONObject()
            manual = result.optBoolean("ok") && visible
            if (!manual) controller?.releaseManual(owner)
            busy = false
            if (manual) {
                geometry = null; freshFrame = true; clearGesture()
                if (!expanded) { expanded = true; resize(animate = true) }
            }
            hint.text = if (manual) "已接管，正在更新画面…" else if (!drained) "本任务已暂停，仍有操作未退出；稍后可再接管" else previewFailure(result)
            updateControls()
        }
    }

    private fun release(resumeTask: Boolean) {
        if (busy || closing) return
        // A newly opened, read-only window owns no manual lease. The old display
        // may already be gone after the user solved a prompt on the main screen.
        // Resume through the same runtime action as the notification; the run
        // controller still rejects a manual owner held by a different window.
        if (!manual) { finishRelease(resumeTask); return }
        busy = true; updateControls()
        lifecycleScope.launch {
            val result = rpc("preview_release")
            if (result.optBoolean("ok")) {
                finishRelease(resumeTask)
            } else hint.text = previewFailure(result)
            busy = false; updateControls()
        }
    }

    private fun finishRelease(resumeTask: Boolean) {
        controller?.releaseManual(owner); manual = false; geometry = null; freshFrame = true; clearGesture()
        inputFeedback = null
        val active = TaskPreviewRegistry.controller(runId)?.isCancelled == false && AgentExecutionService.task(runId)?.completed != true
        if (resumeTask && active) AgentRuntimeService.dispatchNotificationAction(AgentExecutionService.ACTION_RESUME_RUN, runId)
        hint.text = if (active) "已交还控制权 · 等待任务恢复" else "接管结束 · 继续任务请使用通知中的“继续指令”"
        updateControls()
    }

    private fun openCompletedApp() {
        if (busy || closing || AgentExecutionService.task(runId)?.completed != true) return
        busy = true; updateControls()
        lifecycleScope.launch {
            val result = rpc("preview_release")
            if (result.optBoolean("ok") || result.optString("code") in setOf("PREVIEW_UNAVAILABLE", "APP_TAKEN_OVER")) {
                controller?.releaseManual(owner); manual = false
                startActivity(Intent(this@TaskPreviewActivity, CompletedAppActivity::class.java)
                    .putExtra(CompletedAppActivity.EXTRA_RUN_ID, runId).putExtra(CompletedAppActivity.EXTRA_PACKAGE, pkg),
                    ActivityOptions.makeCustomAnimation(this@TaskPreviewActivity, android.R.anim.fade_in, android.R.anim.fade_out).toBundle())
                finish()
            } else { hint.text = previewFailure(result); busy = false; updateControls() }
        }
    }

    private fun previewFailure(result: JSONObject): String = when (result.optString("code")) {
        "TASK_NOT_SETTLED" -> "结果已返回，后台页面仍在收尾；请稍后接管"
        "INPUT_DRAIN_PENDING", "COMMAND_BUSY", "COMMAND_DRAINING" -> "上一操作尚未结束；本次未输入，请稍后再试"
        "PREVIEW_OBSERVATION_REQUIRED", "STALE_OBSERVATION" -> "正在更新画面，更新后可继续操作"
        "MANUAL_CONTROL" -> "另一个预览窗口正在控制此页面，请先结束那里的接管"
        "PREVIEW_UNAVAILABLE", "APP_TAKEN_OVER" -> if (TaskPreviewRegistry.controller(runId)?.isPaused == true)
            "应用已移至主屏或旧屏已释放 · 完成手动操作后可点“让 AI 继续”"
            else "此页面已交还或回收，可从通知打开 App 或继续指令"
        "INPUT_NOT_READY" -> "目标窗口未就绪，本次未输入；可在主屏完成操作后让 AI 继续"
        "MANUAL_LEASE_EXPIRED" -> "接管已过期，请重新点击接管"
        else -> result.optString("message").ifBlank { "暂时无法接管，请刷新画面后重试" }
    }

    private fun updateControls() {
        val card = AgentExecutionService.task(runId)
        card?.let { status.text = if (it.completed && it.status == AgentOverlayStatus.ResultReady) getString(R.string.background_task_completed) else it.status.localizedText(resources) }
        val enabled = !busy && !closing
        take.isEnabled = enabled && !resizing && !manual && geometry != null
        take.text = if (card?.completed == true) "操作页面" else "接管"
        back.isEnabled = enabled && !resizing && manual && geometry != null
        resume.isEnabled = enabled && (manual || TaskPreviewRegistry.controller(runId)?.isPaused == true)
        resume.text = if (card?.completed == true || TaskPreviewRegistry.controller(runId) == null) "结束接管" else "让 AI 继续"
        zoom.isEnabled = enabled && !resizing
        openApp.visibility = if (card?.completed == true) View.VISIBLE else View.GONE
        openApp.isEnabled = enabled
        if (!manual && !busy && geometry != null &&
            (hint.text.startsWith("只读预览") || hint.text.startsWith("AI 正在") ||
                hint.text.startsWith("任务已暂停") || hint.text.startsWith("已交还控制权"))) hint.text = readOnlyHint()
    }

    private fun readOnlyHint(): String = when {
        AgentExecutionService.task(runId)?.completed == true -> "只读预览 · 任务已结束，可操作页面或打开 App"
        TaskPreviewRegistry.controller(runId)?.isPaused == true -> "任务已暂停 · 点击“让 AI 继续”恢复"
        TaskPreviewRegistry.controller(runId) != null -> "AI 正在执行 · 预览只读，手动操作请点“接管”"
        else -> "只读预览 · 点击画面或上方按钮放大"
    }

    private fun touch(event: MotionEvent): Boolean {
        if (busy || resizing || closing) return true
        if (!manual) {
            if (event.actionMasked == MotionEvent.ACTION_UP) picture.performClick()
            return true
        }
        val g = geometry ?: return true
        fun point() = picture.displayPoint(event.x, event.y, g.optInt("width"), g.optInt("height"))
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                down = point(); downTime = SystemClock.uptimeMillis(); gestureGeometry = JSONObject(g.toString())
                downInView = event.x to event.y
                if (down != null) picture.touchStart(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> if (down != null) {
                if (point() == null) clearGesture() else picture.touchMove(event.x, event.y)
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> clearGesture()
            MotionEvent.ACTION_UP -> {
                val start = down; val end = point(); down = null
                if (start != null && end != null) {
                    val duration = (SystemClock.uptimeMillis() - downTime).coerceIn(40, 15000)
                    val startView = downInView ?: (event.x to event.y)
                    val moved = kotlin.math.hypot((event.x - startView.first).toDouble(), (event.y - startView.second).toDouble()) > ViewConfiguration.get(this).scaledTouchSlop
                    val args = if (moved) JSONObject().put("x1", start.first).put("y1", start.second)
                        .put("x2", end.first).put("y2", end.second).put("durationMs", duration)
                    else JSONObject().put("x", start.first).put("y", start.second).put("holdMs", duration)
                    picture.touchMove(event.x, event.y); picture.touchEnd()
                    sendInput(if (moved) "swipe" else if (duration >= ViewConfiguration.getLongPressTimeout()) "press" else "tap", args, gestureGeometry)
                } else {
                    picture.clearTouch()
                }
                gestureGeometry = null; downInView = null
            }
        }
        return true
    }

    private fun sendInput(action: String, args: JSONObject, observed: JSONObject? = geometry) {
        if (!manual || busy || resizing || closing || observed == null || !TaskPreviewRegistry.mainDisplayAvailable(this)) return
        busy = true; updateControls()
        lifecycleScope.launch {
            val result = rpc("preview_input", args.put("action", action).put("expectedGeometry", observed))
            acceptInputResult(result)
            busy = false; updateControls()
        }
    }

    internal fun acceptInputResult(result: JSONObject) {
        geometry = null; freshFrame = true // No replay, including unknown outcomes.
        inputFeedback = if (result.optBoolean("ok")) null else previewFailure(result)
        inputFeedback?.let { hint.text = it }
        if (result.optString("code") in setOf("MANUAL_LEASE_EXPIRED", "PREVIEW_UNAVAILABLE", "APP_TAKEN_OVER")) {
            manual = false; controller?.releaseManual(owner)
        }
        // A fresh preview is not an acknowledgement that an earlier input succeeded.
        // Keep its failure visible until the next user action or successful input.
    }

    companion object {
        private val cleanup = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "preview-release").apply { isDaemon = true } }
        fun pendingIntent(context: Context, task: TaskNotificationState, id: Int): PendingIntent = PendingIntent.getActivity(context, id,
            Intent(context, TaskPreviewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setData(Uri.Builder().scheme("eta-task").authority("preview").appendPath(task.runId).build())
                .putExtra("run_id", task.runId).putExtra("package", task.targetPackage),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
