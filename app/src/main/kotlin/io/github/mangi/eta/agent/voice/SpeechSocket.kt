package io.github.mangi.eta.agent.voice

import java.io.Closeable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

internal class SpeechSocket(request: Request) : Closeable {
    private val ready = CompletableDeferred<Unit>()
    val messages = Channel<ByteArray>(32)
    private val socket = SpeechHttp.client.newWebSocket(request, object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) { ready.complete(Unit) }
        override fun onMessage(webSocket: WebSocket, text: String) = receive(webSocket, text.toByteArray(Charsets.UTF_8))
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = receive(webSocket, bytes.toByteArray())
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val failure = response?.let {
                try { SpeechHttp.checkStatus(it.code); null } catch (error: SpeechFailure) { error }
            } ?: t.speechFailure()
            ready.completeExceptionally(failure)
            messages.close(failure)
            response?.close()
        }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
            messages.close()
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { messages.close() }
    })

    private fun receive(socket: WebSocket, bytes: ByteArray) {
        if (bytes.size > SpeechHttp.MAX_JSON_BYTES || !messages.trySend(bytes).isSuccess) {
            messages.close(SpeechFailure(SpeechErrorCode.BACKPRESSURE, "语音数据处理不及时，请重试"))
            socket.cancel()
        }
    }

    suspend fun awaitReady() = withTimeout(15_000) { ready.await() }
    fun text(value: String) = sent(socket.send(value))
    fun binary(value: ByteArray) = sent(socket.send(value.toByteString()))
    private fun sent(accepted: Boolean) {
        if (!accepted || socket.queueSize() > 256 * 1024) {
            throw SpeechFailure(SpeechErrorCode.BACKPRESSURE, "网络上传不及时，请检查网络后重试")
        }
    }
    override fun close() {
        socket.cancel()
        ready.cancel()
        messages.cancel()
    }
}
