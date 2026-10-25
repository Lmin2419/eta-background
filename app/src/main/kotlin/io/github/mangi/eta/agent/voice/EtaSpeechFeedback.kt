package io.github.mangi.eta.agent.voice

import android.speech.SpeechRecognizer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class EtaSpeechPhase { IDLE, STARTING, LISTENING, RECOGNIZING }

internal fun speechIssueMessage(issue: EtaSpeechIssue): Int = when (issue.kind) {
    EtaSpeechIssueKind.NO_SERVICE -> R.string.voice_speech_no_service
    EtaSpeechIssueKind.SERVICE_TIMEOUT -> R.string.voice_speech_service_timeout
    EtaSpeechIssueKind.DOWNLOAD_AVAILABLE -> R.string.voice_model_needed
    EtaSpeechIssueKind.DOWNLOAD_PENDING -> R.string.voice_model_pending
    EtaSpeechIssueKind.RECOGNITION -> when (issue.errorCode) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_audio_permission
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.voice_no_speech
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> R.string.voice_speech_network_error
        SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> R.string.voice_speech_service_error
        SpeechRecognizer.ERROR_AUDIO -> R.string.voice_speech_audio_error
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.voice_speech_busy
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> R.string.voice_speech_rate_limited
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> R.string.voice_language_not_supported
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> R.string.voice_language_unavailable
        else -> R.string.voice_speech_unavailable
    }
}

internal data class EtaSpeechState(
    val phase: EtaSpeechPhase = EtaSpeechPhase.IDLE,
    val errorRes: Int? = null,
    val message: String? = null,
    val configureAvailable: Boolean = false,
    val downloadAvailable: Boolean = false,
    val feedbackIsError: Boolean = true,
    /** 用户正按住麦克风说话：松手才结束，界面保持输入栏而不切到语音态。 */
    val holdToTalk: Boolean = false,
) {
    val active: Boolean get() = phase != EtaSpeechPhase.IDLE
}

@Composable
internal fun EtaSpeechFeedback(speech: EtaSpeechState, onDownloadModel: () -> Unit, onOpenSpeechSettings: () -> Unit) {
    val message = speech.message ?: speech.errorRes?.let { stringResource(it) } ?: return
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = if (speech.feedbackIsError) {
                if (dark) Color(0xFFFF9E99) else Color(0xFFB42318)
            } else {
                MiuixTheme.colorScheme.onBackground
            },
            modifier = Modifier
                .weight(1f, fill = false)
                .background(
                    if (dark) Color(0xFF34363B) else Color(0xFFF2F3F5),
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        if (speech.configureAvailable) {
            TextButton(
                text = stringResource(R.string.speech_open_settings),
                onClick = onOpenSpeechSettings,
            )
        }
        if (speech.downloadAvailable) {
            TextButton(
                text = stringResource(R.string.voice_download_model),
                onClick = onDownloadModel,
            )
        }
    }
}
