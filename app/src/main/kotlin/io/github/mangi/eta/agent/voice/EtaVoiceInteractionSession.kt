package io.github.mangi.eta.agent.voice

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.graphics.Bitmap
import android.service.voice.VoiceInteractionSession
import android.view.View

/**
 * 系统数字助理的入口桥接。
 *
 * 系统会为本类创建 TYPE_VOICE_INTERACTION 窗口，但 Eta 的实际界面由自己的
 * TYPE_APPLICATION_OVERLAY 窗口承载，避免把厂商助手动画和输入层级绑定到系统会话窗口。
 */
internal class EtaVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
    private var screenContext: EtaAssistantScreenContext? = null
    private var shown = false
    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_HIDE_FOR_FOREGROUND_OPERATION) {
                hide()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        setUiEnabled(false)
        context.registerReceiver(
            controlReceiver,
            IntentFilter(ACTION_HIDE_FOR_FOREGROUND_OPERATION),
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onCreateContentView(): View = View(context)

    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        super.onPrepareShow(args, showFlags)
        shown = false
        screenContext = EtaAssistantScreenContexts.begin(
            showFlags,
            args?.getParcelableArrayList(KEY_FOREGROUND_ACTIVITIES, ComponentName::class.java).orEmpty(),
        )
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        shown = true
        EtaAssistantOverlayService.show(context, screenContext?.id)
    }

    override fun onHandleAssist(state: AssistState) {
        screenContext?.acceptAssist(context.applicationContext, state)
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        val capture = screenContext
        if (capture == null) screenshot?.recycle() else capture.acceptScreenshot(screenshot)
    }

    override fun onHide() {
        EtaAssistantScreenContexts.release(screenContext?.id)
        screenContext = null
        shown = false
        super.onHide()
    }

    override fun onBackPressed() {
        EtaAssistantOverlayService.dismiss(context)
        hide()
    }

    override fun onCloseSystemDialogs() {
        EtaAssistantOverlayService.dismiss(context)
        hide()
    }

    override fun onDestroy() {
        // 浮窗拥有独立生命周期；系统会话重建不代表用户关闭了助理。
        if (!shown) EtaAssistantScreenContexts.release(screenContext?.id)
        screenContext = null
        context.unregisterReceiver(controlReceiver)
        super.onDestroy()
    }

    internal companion object {
        private const val ACTION_HIDE_FOR_FOREGROUND_OPERATION =
            "io.github.mangi.eta.agent.voice.HIDE_FOR_FOREGROUND_OPERATION"

        fun requestHideForForegroundOperation(context: Context) {
            context.sendBroadcast(
                Intent(ACTION_HIDE_FOR_FOREGROUND_OPERATION)
                    .setPackage(context.packageName),
            )
        }
    }
}
