package io.github.mangi.eta.agent.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

internal class SpeechRecorder {
    val frames = Channel<ByteArray>(10)
    private val finishing = AtomicBoolean(false)
    @Volatile private var recorder: AudioRecord? = null

    @SuppressLint("MissingPermission")
    suspend fun record(onReady: () -> Unit, onLevel: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) throw SpeechFailure(SpeechErrorCode.AUDIO, "设备不支持语音录音格式")
        val audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, FRAME_BYTES * 2))
        recorder = audio
        try {
            if (audio.state != AudioRecord.STATE_INITIALIZED) throw SpeechFailure(SpeechErrorCode.AUDIO, "无法初始化麦克风")
            if (finishing.get()) return@withContext
            audio.startRecording()
            if (audio.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw SpeechFailure(SpeechErrorCode.AUDIO, "麦克风不可用，可能正被其他应用占用")
            }
            onReady()
            var total = 0
            while (!finishing.get() && total < MAX_BYTES) {
                val frame = ByteArray(minOf(FRAME_BYTES, MAX_BYTES - total))
                val count = audio.read(frame, 0, frame.size)
                if (count <= 0 && finishing.get()) break
                if (count <= 0 || count % 2 != 0) throw SpeechFailure(SpeechErrorCode.AUDIO, "麦克风读取失败")
                val bytes = if (count == frame.size) frame else frame.copyOf(count)
                val sent = frames.trySend(bytes)
                if (sent.isFailure) {
                    if (finishing.get() && sent.isClosed) break
                    throw SpeechFailure(SpeechErrorCode.BACKPRESSURE, "网络上传不及时，请重试")
                }
                total += count
                var energy = 0.0
                for (index in bytes.indices step 2) {
                    val sample = ((bytes[index].toInt() and 255) or (bytes[index + 1].toInt() shl 8)).toShort().toDouble()
                    energy += sample * sample
                }
                onLevel((sqrt(energy / (count / 2)) / 4000).toFloat().coerceIn(0f, 1f))
            }
        } finally {
            finishing.set(true)
            recorder = null
            audio.release()
            frames.close()
        }
    }

    fun finish() {
        finishing.set(true)
        // stop 解除阻塞 read；由录音协程在 finally 中唯一释放实例。
        try { recorder?.stop() } catch (_: IllegalStateException) { /* 初始化尚未完成或已经停止。 */ }
    }

    fun cancel() { finish(); frames.cancel() }

    companion object {
        const val RATE = 16_000
        const val FRAME_BYTES = 6_400
        const val MAX_BYTES = RATE * 2 * 60
    }
}

internal fun pcmToWav(pcm: ByteArray): ByteArray = ByteBuffer.allocate(44 + pcm.size)
    .order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVEfmt ".toByteArray())
        putInt(16); putShort(1); putShort(1); putInt(SpeechRecorder.RATE)
        putInt(SpeechRecorder.RATE * 2); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(pcm.size); put(pcm)
    }.array()

internal suspend fun collectSpeechAudio(frames: kotlinx.coroutines.channels.ReceiveChannel<ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    for (frame in frames) {
        if (out.size() + frame.size > SpeechRecorder.MAX_BYTES) {
            throw SpeechFailure(SpeechErrorCode.AUDIO, "录音超过长度限制")
        }
        out.write(frame)
    }
    if (out.size() == 0) throw SpeechFailure(SpeechErrorCode.NO_SPEECH, "没有录到语音，请重试")
    return pcmToWav(out.toByteArray())
}
