package io.github.mangi.eta.agent.voice

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionSupport
import android.speech.SpeechRecognizer
import io.github.mangi.eta.R
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSpeechRecognizer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 34], application = Application::class)
class EtaSpeechInputTest {
    private val results = mutableListOf<String>()
    private val errors = mutableListOf<EtaSpeechIssue>()
    private val partials = mutableListOf<String>()
    private val downloads = mutableListOf<EtaSpeechDownloadStatus>()
    private lateinit var input: EtaSpeechInput

    @Before fun setup() {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        input = EtaSpeechInput(RuntimeEnvironment.getApplication(), {}, {}, {}, partials::add, results::add, errors::add, downloads::add)
    }

    @After fun close() { input.cancel() }

    private fun start(): ShadowSpeechRecognizer {
        input.start()
        shadowOf(Looper.getMainLooper()).idle()
        return shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer()).also {
            it.triggerSupportError(SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun text(value: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(value))
    }

    @Test fun finalResultIsDeliveredOnceAndReleasesRecognizer() {
        val recognizer = start()
        recognizer.triggerOnPartialResults(text("草稿"))
        assertEquals(listOf("草稿"), partials)
        recognizer.triggerOnResults(text(" 最终问题 "))
        recognizer.triggerOnResults(text("重复结果"))
        assertEquals(listOf("最终问题"), results)
        assertTrue(recognizer.isDestroyed)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31))
        assertTrue(errors.isEmpty())
    }

    @Test fun errorReturnsToOwnerAndReleasesRecognizer() {
        val recognizer = start()
        recognizer.triggerOnError(SpeechRecognizer.ERROR_NETWORK)
        assertEquals(listOf(SpeechRecognizer.ERROR_NETWORK), errors.map(EtaSpeechIssue::errorCode))
        assertTrue(recognizer.isDestroyed)
    }

    @Test fun canceledCallbacksCannotSubmitOrReplaceANewSession() {
        val old = start()
        input.cancel()
        val current = start()
        old.triggerOnResults(text("过期结果"))
        old.triggerOnPartialResults(text("过期草稿"))
        old.triggerOnError(SpeechRecognizer.ERROR_CLIENT)
        assertTrue(results.isEmpty())
        assertTrue(partials.isEmpty())
        assertTrue(errors.isEmpty())
        current.triggerOnResults(text("本次结果"))
        assertEquals(listOf("本次结果"), results)
    }

    @Test fun emptyResultsDoNotSubmit() {
        val recognizer = start()
        recognizer.triggerOnResults(Bundle())
        assertTrue(results.isEmpty())
        assertEquals(listOf(SpeechRecognizer.ERROR_NO_MATCH), errors.map(EtaSpeechIssue::errorCode))
    }

    @Test fun stalledServiceTimesOutAndRejectsLateResults() {
        val recognizer = start()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        assertEquals(listOf(SpeechRecognizer.ERROR_SPEECH_TIMEOUT), errors.map(EtaSpeechIssue::errorCode))
        assertEquals(EtaSpeechIssueKind.SERVICE_TIMEOUT, errors.single().kind)
        recognizer.triggerOnResults(text("迟到结果"))
        assertTrue(results.isEmpty())
        assertTrue(recognizer.isDestroyed)
    }

    @Test fun supportQueryCannotDelayListeningIndefinitely() {
        input.start()
        val recognizer = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(750))
        assertNotNull(recognizer.lastRecognizerIntent)
        recognizer.triggerSupportError(SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT)
        assertTrue(errors.isEmpty())
        recognizer.triggerOnResults(text("问题"))
        assertEquals(listOf("问题"), results)
    }

    @Test fun downloadableLanguageStopsRecognitionAndExposesAction() {
        input.start()
        val recognizer = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        recognizer.triggerSupportResult(
            RecognitionSupport.Builder()
                .setSupportedOnDeviceLanguages(listOf(java.util.Locale.getDefault().toLanguageTag()))
                .build(),
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(EtaSpeechIssueKind.DOWNLOAD_AVAILABLE, errors.single().kind)
        assertTrue(recognizer.isDestroyed)
        assertNull(recognizer.lastRecognizerIntent)
    }

    @Test
    @Config(sdk = [33])
    fun android13SchedulesModelDownloadAndKeepsRecognizerUntilOwnerCancels() {
        input.start()
        shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer()).triggerSupportResult(
            RecognitionSupport.Builder()
                .setSupportedOnDeviceLanguages(listOf(java.util.Locale.getDefault().toLanguageTag()))
                .build(),
        )
        shadowOf(Looper.getMainLooper()).idle()
        input.downloadModel()
        shadowOf(Looper.getMainLooper()).idle()

        val download = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        assertNotNull(download.latestModelDownloadIntent)
        assertEquals(listOf(EtaSpeechDownloadStatus.DOWNLOADING, EtaSpeechDownloadStatus.SCHEDULED), downloads)
        assertFalse(download.isDestroyed)

        input.cancel()
        assertTrue(download.isDestroyed)
        assertTrue(results.isEmpty())
    }

    @Test fun downloadableLanguageIsOfferedOnlyWhenNoInstalledOrOnlineSupport() {
        val downloadable = RecognitionSupport.Builder()
            .setSupportedOnDeviceLanguages(listOf("zh-CN"))
            .build()
        assertEquals(EtaSpeechSupportDecision.DOWNLOAD, speechSupportDecision(downloadable, "zh-CN"))
        val online = RecognitionSupport.Builder()
            .setSupportedOnDeviceLanguages(listOf("zh-CN"))
            .setOnlineLanguages(listOf("zh-CN"))
            .build()
        assertEquals(EtaSpeechSupportDecision.START, speechSupportDecision(online, "zh-CN"))
        val pending = RecognitionSupport.Builder()
            .setPendingOnDeviceLanguages(listOf("zh-CN"))
            .build()
        assertEquals(EtaSpeechSupportDecision.PENDING, speechSupportDecision(pending, "zh-CN"))
        assertEquals(EtaSpeechSupportDecision.START, speechSupportDecision(downloadable, "zh-TW"))
    }

    @Test fun recognitionFailuresUseDistinctUserMessages() {
        assertEquals(
            R.string.voice_speech_network_error,
            speechIssueMessage(EtaSpeechIssue(SpeechRecognizer.ERROR_NETWORK)),
        )
        assertEquals(
            R.string.voice_speech_service_error,
            speechIssueMessage(EtaSpeechIssue(SpeechRecognizer.ERROR_SERVER_DISCONNECTED)),
        )
        assertEquals(
            R.string.voice_language_unavailable,
            speechIssueMessage(EtaSpeechIssue(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)),
        )
    }
}
