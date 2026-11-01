package io.github.mangi.eta.agent.runtime

import android.animation.ValueAnimator
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
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.eta.shadow.ShadowTools
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
    private lateinit var picture: ImageView
    private lateinit var status: TextView
    private lateinit var hint: TextView
    private lateinit var take: Button
    private lateinit var resume: Button
    private lateinit var back: Button

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
        header.addView(button("关闭") { finish() })
        root.addView(header)
        picture = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "后台应用实时预览；点击放大，接管后可操作"
            setBackgroundColor(Color.BLACK)
            setOnClickListener { if (!manual && !busy) { expanded = !expanded; resize(true) } }
            setOnTouchListener { _, event -> touch(event) }
        }
        root.addView(picture, LinearLayout.LayoutParams(-1, 0, 1f))
        hint = label("只读预览 · 点击画面放大", 12f)
        root.addView(hint, LinearLayout.LayoutParams(-1, dp(44)))
        val controls = LinearLayout(this)
        take = button("接管") { acquire() }
        resume = button("让 AI 继续") { release(resumeTask = true) }
        back = button("返回") { sendInput("key", JSONObject().put("code", 4)) }
        listOf(take, resume, back).forEach { controls.addView(it, LinearLayout.LayoutParams(0, dp(48), 1f)) }
        root.addView(controls)
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
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        down = null; gestureGeometry = null; geometry = null
        if (::picture.isInitialized) resize(animate = false)
    }

    private fun resize(animate: Boolean) {
        animation?.cancel()
        val bounds = windowManager.currentWindowMetrics.bounds
        val width = if (expanded) (bounds.width() * .94).toInt() else minOf(dp(320), (bounds.width() * .9).toInt())
        val height = if (expanded) (bounds.height() * .8).toInt() else minOf(dp(450), (bounds.height() * .65).toInt())
        val fromW = window.attributes.width.takeIf { it > 0 } ?: width
        val fromH = window.attributes.height.takeIf { it > 0 } ?: height
        if (!animate) { window.setLayout(width, height); return }
        animation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 240; interpolator = DecelerateInterpolator()
            addUpdateListener { val f = it.animatedValue as Float; window.setLayout((fromW + (width - fromW) * f).toInt(), (fromH + (height - fromH) * f).toInt()) }
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
                if (!busy && down == null) {
                    val result = rpc("preview_frame", JSONObject().put("edge", if (expanded) 720 else 480)
                        .put("since", if (geometry != null) previewKey else ""))
                    if (!visible) break
                    if (result.optBoolean("ok") && !result.optBoolean("unchanged")) {
                        val encoded = result.optString("image")
                        val bitmap = withContext(Dispatchers.Default) {
                            if (encoded.length > 550000) null else runCatching {
                                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }.getOrNull()
                        }
                        if (!visible) { bitmap?.recycle(); break }
                        if (bitmap != null) {
                            geometry = result.optJSONObject("geometry")
                            previewKey = result.optString("previewKey")
                            val old = frame; frame = bitmap; picture.setImageBitmap(bitmap); old?.recycle()
                        }
                    } else if (!result.optBoolean("ok") && result.optString("code") !in setOf("NO_FRAME", "COMMAND_BUSY", "FRAME_GEOMETRY_CHANGED")) {
                        hint.text = result.optString("message", "预览暂不可用，请返回通知查看任务")
                        geometry = null
                    }
                    AgentExecutionService.task(runId)?.let { status.text = it.status.localizedText(resources) }
                    updateControls()
                }
                delay(if (expanded) 350 else 800)
            }
        }
    }

    override fun onStop() {
        visible = false
        polling?.cancel()
        animation?.cancel()
        geometry = null; down = null
        if (::picture.isInitialized) picture.setImageDrawable(null)
        frame?.recycle(); frame = null
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
        if (busy || manual || geometry == null) return
        busy = true; updateControls(); hint.text = "正在暂停此任务并等待操作结束…"
        lifecycleScope.launch {
            controller = TaskPreviewRegistry.controller(runId)
            val drained = withContext(Dispatchers.IO) { controller?.acquireManual(owner) ?: true }
            val result = if (drained && visible) rpc("preview_acquire", JSONObject().put("requireParked", controller == null)) else JSONObject()
            manual = result.optBoolean("ok") && visible
            if (!manual) controller?.releaseManual(owner)
            busy = false
            hint.text = if (manual) "已接管 · 点击、长按或滑动；关闭后 AI 保持暂停" else "尚未取得控制权；请等待旧操作结束后重试"
            updateControls()
        }
    }

    private fun release(resumeTask: Boolean) {
        if (busy) return
        busy = true; updateControls()
        lifecycleScope.launch {
            val result = rpc("preview_release")
            if (result.optBoolean("ok")) {
                controller?.releaseManual(owner); manual = false; geometry = null
                val active = TaskPreviewRegistry.controller(runId) != null
                if (resumeTask && active) AgentRuntimeService.dispatchNotificationAction(AgentExecutionService.ACTION_RESUME_RUN, runId)
                hint.text = if (active) "已交还控制权 · AI 继续前会重新观察" else "接管结束 · 继续任务请使用通知中的“继续指令”"
            } else hint.text = "控制权释放尚未确认；未恢复 AI，请稍后再试"
            busy = false; updateControls()
        }
    }

    private fun updateControls() {
        take.isEnabled = !busy && !manual && geometry != null
        back.isEnabled = !busy && manual && geometry != null
        resume.isEnabled = !busy && (manual || TaskPreviewRegistry.controller(runId)?.isPaused == true)
        resume.text = if (TaskPreviewRegistry.controller(runId) == null) "结束接管" else "让 AI 继续"
    }

    private fun touch(event: MotionEvent): Boolean {
        if (!manual) {
            if (event.action == MotionEvent.ACTION_UP && !busy) picture.performClick()
            return true
        }
        if (busy) return true
        val g = geometry ?: return true
        fun point() = PreviewCoordinates.map(event.x, event.y, picture.width, picture.height, g.optInt("width"), g.optInt("height"))
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { down = point(); downTime = SystemClock.uptimeMillis(); gestureGeometry = JSONObject(g.toString()) }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> { down = null; gestureGeometry = null }
            MotionEvent.ACTION_UP -> {
                val start = down; val end = point(); down = null
                if (start != null && end != null) {
                    val duration = (SystemClock.uptimeMillis() - downTime).coerceIn(40, 1200)
                    val moved = kotlin.math.hypot((end.first - start.first).toDouble(), (end.second - start.second).toDouble()) > g.optInt("width") * .025
                    val args = if (moved) JSONObject().put("x1", start.first).put("y1", start.second)
                        .put("x2", end.first).put("y2", end.second).put("durationMs", duration)
                    else JSONObject().put("x", start.first).put("y", start.second).put("holdMs", duration)
                    sendInput(if (moved) "swipe" else if (duration >= 500) "press" else "tap", args, gestureGeometry)
                }
                gestureGeometry = null
            }
        }
        return true
    }

    private fun sendInput(action: String, args: JSONObject, observed: JSONObject? = geometry) {
        if (!manual || busy || observed == null || !TaskPreviewRegistry.mainDisplayAvailable(this)) return
        busy = true; updateControls()
        lifecycleScope.launch {
            val result = rpc("preview_input", args.put("action", action).put("expectedGeometry", observed))
            geometry = null // No auto-replay, including timeouts and orientation changes.
            if (!result.optBoolean("ok")) hint.text = result.optString("message", "操作未确认，请重新查看画面")
            if (result.optString("code") in setOf("MANUAL_LEASE_EXPIRED", "PREVIEW_UNAVAILABLE", "APP_TAKEN_OVER")) {
                manual = false; controller?.releaseManual(owner)
            }
            busy = false; updateControls()
        }
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
