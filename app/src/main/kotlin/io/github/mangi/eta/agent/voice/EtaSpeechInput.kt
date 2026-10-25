package io.github.mangi.eta.agent.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import io.github.mangi.eta.core.AndroidAgentLogger
import java.util.Locale

internal enum class EtaSpeechIssueKind { RECOGNITION, NO_SERVICE, SERVICE_TIMEOUT, DOWNLOAD_AVAILABLE, DOWNLOAD_PENDING }

internal data class EtaSpeechIssue(
    val errorCode: Int,
    val kind: EtaSpeechIssueKind = EtaSpeechIssueKind.RECOGNITION,
)

internal enum class EtaSpeechDownloadStatus { DOWNLOADING, SCHEDULED, READY, FAILED }

internal enum class EtaSpeechSupportDecision { START, DOWNLOAD, PENDING }

internal fun speechSupportDecision(support: RecognitionSupport, languageTag: String): EtaSpeechSupportDecision {
    val requested = Locale.forLanguageTag(languageTag)
    fun List<String>.containsLanguage() = any { offered ->
        val available = Locale.forLanguageTag(offered)
        available.language.equals(requested.language, ignoreCase = true) &&
            (available.country.isEmpty() || requested.country.isEmpty() ||
                available.country.equals(requested.country, ignoreCase = true)) &&
            (available.script.isEmpty() || requested.script.isEmpty() ||
                available.script.equals(requested.script, ignoreCase = true))
    }
    if (support.installedOnDeviceLanguages.containsLanguage() || support.onlineLanguages.containsLanguage()) {
        return EtaSpeechSupportDecision.START
    }
    if (support.pendingOnDeviceLanguages.containsLanguage()) return EtaSpeechSupportDecision.PENDING
    if (support.supportedOnDeviceLanguages.containsLanguage()) return EtaSpeechSupportDecision.DOWNLOAD
    // 服务给出的语言列表可能不完整；最终能否识别仍由 startListening 的回调决定。
    return EtaSpeechSupportDecision.START
}

/** 单次识别由浮窗持有；关闭、切换输入方式和重新唤醒都会使旧回调失效。 */
internal class EtaSpeechInput(
    private val context: Context,
    private val onListening: () -> Unit,
    private val onRecognizing: () -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onResult: (String) -> Unit,
    private val onError: (EtaSpeechIssue) -> Unit,
    private val onDownloadStatus: (EtaSpeechDownloadStatus) -> Unit,
    /** 期望的说完判定静音时长；系统服务可以忽略该提示，结果仍以服务回调为准。 */
    private val completeSilenceMs: Int? = null,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var downloadRecognizer: SpeechRecognizer? = null
    private var downloadSource: SystemSpeechRecognizer.Source? = null
    private var generation = 0
    private var preflightTimeout: Runnable? = null
    private var recognitionTimeout: Runnable? = null
    private var listeningStarted = false
    private var startedAt = 0L
    private var startupLatencyMs: Long? = null
    private val languageTag = Locale.getDefault().toLanguageTag()

    fun start() {
        cancel()
        startedAt = SystemClock.elapsedRealtime()
        val session = generation
        val selection = try {
            SystemSpeechRecognizer.select(context)
        } catch (error: RuntimeException) {
            AndroidAgentLogger.warn("Eta speech service selection failed: type=${error.javaClass.simpleName}")
            onError(EtaSpeechIssue(
                if (error is SecurityException) SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                else SpeechRecognizer.ERROR_CLIENT,
            ))
            return
        }
        if (selection == null) {
            AndroidAgentLogger.warn("Eta speech unavailable: reason=no_service")
            onError(EtaSpeechIssue(SpeechRecognizer.ERROR_CLIENT, EtaSpeechIssueKind.NO_SERVICE))
            return
        }
        val delegate = selection.recognizer
        val source = selection.source
        val intent = recognitionIntent()
        recognizer = delegate
        AndroidAgentLogger.info("Eta speech selected: source=${source.label}")

        fun current() = generation == session && recognizer === delegate
        fun fail(issue: EtaSpeechIssue) {
            if (!current()) return
            if (issue.kind == EtaSpeechIssueKind.DOWNLOAD_AVAILABLE) downloadSource = source
            AndroidAgentLogger.warn(
                "Eta speech failed: source=${source.label} code=${issue.errorCode} kind=${issue.kind} " +
                    "startupMs=${startupLatencyMs ?: SystemClock.elapsedRealtime() - startedAt}",
            )
            releaseRecognition()
            onError(issue)
        }
        fun begin() {
            if (!current() || listeningStarted) return
            preflightTimeout?.let(handler::removeCallbacks)
            preflightTimeout = null
            listeningStarted = true
            recognitionTimeout = Runnable {
                fail(EtaSpeechIssue(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, EtaSpeechIssueKind.SERVICE_TIMEOUT))
            }.also { handler.postDelayed(it, RECOGNITION_TIMEOUT_MS) }
            try {
                delegate.startListening(intent)
            } catch (error: RuntimeException) {
                AndroidAgentLogger.warn("Eta speech start failed: source=${source.label} type=${error.javaClass.simpleName}")
                fail(EtaSpeechIssue(
                    if (error is SecurityException) SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                    else SpeechRecognizer.ERROR_CLIENT,
                ))
            }
        }

        var listenerSet = false
        try {
            delegate.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (!current()) return
                    startupLatencyMs = SystemClock.elapsedRealtime() - startedAt
                    AndroidAgentLogger.info(
                        "Eta speech ready: source=${source.label} startupMs=$startupLatencyMs",
                    )
                    onListening()
                }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) {
                    if (current()) onLevel((rmsdB / 10f).coerceIn(0f, 1f))
                }
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    if (current()) onRecognizing()
                }
                override fun onError(error: Int) = fail(EtaSpeechIssue(error))
                override fun onResults(results: Bundle?) {
                    if (!current()) return
                    val text = results.text()
                    AndroidAgentLogger.info("Eta speech completed: source=${source.label}")
                    releaseRecognition()
                    if (text.isBlank()) onError(EtaSpeechIssue(SpeechRecognizer.ERROR_NO_MATCH)) else onResult(text)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (current()) partialResults.text().takeIf(String::isNotBlank)?.let(onPartial)
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            listenerSet = true
            preflightTimeout = Runnable {
                if (current() && !listeningStarted) {
                    AndroidAgentLogger.info("Eta speech support timed out: source=${source.label}")
                    begin()
                }
            }.also { handler.postDelayed(it, SUPPORT_TIMEOUT_MS) }
            delegate.checkRecognitionSupport(intent, context.mainExecutor, object : RecognitionSupportCallback {
                override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                    if (!current() || listeningStarted) return
                    val decision = speechSupportDecision(recognitionSupport, languageTag)
                    AndroidAgentLogger.info("Eta speech support: source=${source.label} decision=$decision")
                    when (decision) {
                        EtaSpeechSupportDecision.START -> begin()
                        EtaSpeechSupportDecision.DOWNLOAD -> fail(
                            EtaSpeechIssue(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, EtaSpeechIssueKind.DOWNLOAD_AVAILABLE),
                        )
                        EtaSpeechSupportDecision.PENDING -> fail(
                            EtaSpeechIssue(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, EtaSpeechIssueKind.DOWNLOAD_PENDING),
                        )
                    }
                }
                override fun onError(error: Int) {
                    if (!current() || listeningStarted) return
                    AndroidAgentLogger.info("Eta speech support unavailable: source=${source.label} code=$error")
                    begin()
                }
            })
        } catch (error: RuntimeException) {
            AndroidAgentLogger.warn(
                "Eta speech setup failed: source=${source.label} phase=${if (listenerSet) "support" else "listener"} " +
                    "type=${error.javaClass.simpleName}",
            )
            if (listenerSet) begin() else fail(EtaSpeechIssue(SpeechRecognizer.ERROR_CLIENT))
        }
    }

    fun downloadModel() {
        val source = downloadSource ?: return
        releaseDownload()
        val delegate = try {
            SystemSpeechRecognizer.create(context, source)
        } catch (error: RuntimeException) {
            AndroidAgentLogger.warn("Eta speech model request failed: source=${source.label} type=${error.javaClass.simpleName}")
            onDownloadStatus(EtaSpeechDownloadStatus.FAILED)
            return
        }
        downloadRecognizer = delegate
        onDownloadStatus(EtaSpeechDownloadStatus.DOWNLOADING)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                delegate.triggerModelDownload(recognitionIntent())
                downloadSource = null
                // Android 13 没有下载回调，保留识别器到入口取消，避免服务连接前请求就被销毁。
                onDownloadStatus(EtaSpeechDownloadStatus.SCHEDULED)
                return
            }
            delegate.triggerModelDownload(recognitionIntent(), context.mainExecutor, object : ModelDownloadListener {
                private fun current() = downloadRecognizer === delegate
                override fun onProgress(completedPercent: Int) {
                    if (current()) onDownloadStatus(EtaSpeechDownloadStatus.DOWNLOADING)
                }
                override fun onSuccess() {
                    if (!current()) return
                    AndroidAgentLogger.info("Eta speech model ready: source=${source.label}")
                    downloadSource = null
                    releaseDownload()
                    onDownloadStatus(EtaSpeechDownloadStatus.READY)
                }
                override fun onScheduled() {
                    if (!current()) return
                    AndroidAgentLogger.info("Eta speech model scheduled: source=${source.label}")
                    downloadSource = null
                    releaseDownload()
                    onDownloadStatus(EtaSpeechDownloadStatus.SCHEDULED)
                }
                override fun onError(error: Int) {
                    if (!current()) return
                    AndroidAgentLogger.warn("Eta speech model request failed: source=${source.label} code=$error")
                    releaseDownload()
                    onDownloadStatus(EtaSpeechDownloadStatus.FAILED)
                }
            })
        } catch (error: RuntimeException) {
            AndroidAgentLogger.warn("Eta speech model request failed: source=${source.label} type=${error.javaClass.simpleName}")
            releaseDownload()
            onDownloadStatus(EtaSpeechDownloadStatus.FAILED)
        }
    }

    fun finish() {
        if (!listeningStarted) {
            cancel()
            onError(EtaSpeechIssue(SpeechRecognizer.ERROR_NO_MATCH))
            return
        }
        try {
            recognizer?.stopListening()
            onRecognizing()
        } catch (error: RuntimeException) {
            AndroidAgentLogger.warn("Eta speech stop failed: type=${error.javaClass.simpleName}")
            releaseRecognition()
            onError(EtaSpeechIssue(SpeechRecognizer.ERROR_CLIENT))
        }
    }

    fun cancel() {
        releaseRecognition()
        releaseDownload()
        downloadSource = null
    }

    private fun releaseRecognition() {
        generation++
        preflightTimeout?.let(handler::removeCallbacks)
        recognitionTimeout?.let(handler::removeCallbacks)
        preflightTimeout = null
        recognitionTimeout = null
        listeningStarted = false
        startedAt = 0L
        startupLatencyMs = null
        val previous = recognizer
        recognizer = null
        try {
            previous?.destroy()
        } catch (error: RuntimeException) {
            AndroidAgentLogger.warn("Eta speech release failed: type=${error.javaClass.simpleName}")
        }
    }

    private fun releaseDownload() {
        val previous = downloadRecognizer
        downloadRecognizer = null
        try {
            previous?.destroy()
        } catch (error: RuntimeException) {
            AndroidAgentLogger.warn("Eta speech model release failed: type=${error.javaClass.simpleName}")
        }
    }

    private fun recognitionIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        .apply {
            completeSilenceMs?.let {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, it.toLong())
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, it.toLong())
            }
        }

    private fun Bundle?.text(): String =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()

    private companion object {
        const val SUPPORT_TIMEOUT_MS = 750L
        const val RECOGNITION_TIMEOUT_MS = 30_000L
    }
}
