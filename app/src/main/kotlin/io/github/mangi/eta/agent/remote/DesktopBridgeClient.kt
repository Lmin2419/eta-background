package io.github.mangi.eta.agent.remote

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

internal class DesktopBridgeException(val code: String, message: String) : Exception(message)

/**
 * 到 dsh-bridge 的单条 WebSocket 长连接。
 *
 * - 所有请求复用这一条连接，避免每次操作重新握手 TLS；
 * - 断线后指数退避重连（0.3s 起，最长 8s），网络切换时可由 [reconnectNow] 立刻重连；
 * - 15 秒一次 WebSocket ping，死链在 30 秒内被发现；
 * - 推送按到达顺序发到 [pushes]，每次握手成功后在 [connections] 发出 `hello` 结果。
 */
internal class DesktopBridgeClient(
    baseClient: OkHttpClient,
    private val scope: CoroutineScope,
) {
    sealed interface State {
        data object Idle : State
        data class Connecting(val attempt: Int, val lastError: String?) : State
        data class Connected(val host: String, val version: String, val latencyMs: Long) : State
        data class Unauthorized(val message: String) : State
    }

    private val baseHttp: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _pushes = MutableSharedFlow<JsonObject>(extraBufferCapacity = PUSH_BUFFER)
    val pushes: SharedFlow<JsonObject> = _pushes.asSharedFlow()

    private val _connections = MutableSharedFlow<JsonObject>(extraBufferCapacity = 4)
    val connections: SharedFlow<JsonObject> = _connections.asSharedFlow()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var ready = false
    @Volatile private var fatal: String? = null
    private var loopJob: Job? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private val ids = AtomicLong()

    val isConnected: Boolean get() = ready && socket != null

    @Synchronized
    fun start(config: DesktopBridgeConfig) {
        stop()
        val url = config.webSocketUrl ?: run {
            _state.value = State.Unauthorized("电脑地址无效")
            return
        }
        val http = baseHttp.newBuilder().pinnedTo(config.pin).build()
        loopJob = scope.launch { runLoop(http, url, config.token) }
    }

    @Synchronized
    fun stop() {
        loopJob?.cancel()
        loopJob = null
        closeSocket(1000, "client stop")
        _state.value = State.Idle
    }

    /** 跳过当前退避等待；已连接时主动断开重连（用于网络切换，旧连接多半已失效）。 */
    fun reconnectNow(dropCurrent: Boolean) {
        if (dropCurrent && socket != null) closeSocket(4000, "network changed")
        wake.trySend(Unit)
    }

    private suspend fun runLoop(http: OkHttpClient, url: String, token: String) {
        var attempt = 0
        var lastError: String? = null
        while (scope.isActive) {
            _state.value = State.Connecting(attempt, lastError)
            val startedAt = System.currentTimeMillis()
            fatal = null
            when (val outcome = connectOnce(http, url, token)) {
                is Outcome.Unauthorized -> {
                    _state.value = State.Unauthorized(outcome.message)
                    return
                }
                is Outcome.Closed -> lastError = outcome.reason
            }
            fatal?.let {
                _state.value = State.Unauthorized(it)
                return
            }
            // 稳定连接过一段时间后断开（典型是切网），从最短退避重新开始。
            attempt = if (System.currentTimeMillis() - startedAt > STABLE_CONNECTION_MS) 0 else attempt + 1
            val backoff = BACKOFF_MS[minOf(attempt, BACKOFF_MS.lastIndex)]
            withTimeoutOrNull(backoff) { wake.receive() }
        }
    }

    private sealed interface Outcome {
        data class Closed(val reason: String?) : Outcome
        data class Unauthorized(val message: String) : Outcome
    }

    private suspend fun connectOnce(http: OkHttpClient, url: String, token: String): Outcome = suspendCancellableCoroutine { cont ->
        val finished = AtomicBoolean(false)
        fun finish(outcome: Outcome) {
            if (!finished.compareAndSet(false, true)) return
            ready = false
            socket = null
            failPending("与电脑的连接已断开")
            if (cont.isActive) cont.resume(outcome)
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        val ws = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socket = webSocket
                scope.launch { handshake(webSocket) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleText(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                finish(Outcome.Closed(reason.ifBlank { "closed $code" }))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                finish(Outcome.Closed(reason.ifBlank { "closed $code" }))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                val mismatch = t.findPinMismatch()
                when {
                    mismatch != null -> finish(Outcome.Unauthorized(mismatch.message ?: "服务器证书指纹不符"))
                    code == 401 || code == 403 -> finish(Outcome.Unauthorized("Token 不正确（HTTP $code）"))
                    // nginx 连不上 frps 映射的端口：电脑上的 frpc 没运行，或电脑离线/休眠。
                    code == 502 || code == 504 -> finish(Outcome.Closed("电脑不在线（frpc 未连接到中转服务器）"))
                    else -> finish(Outcome.Closed(code?.let { "HTTP $it" } ?: t.message ?: t.javaClass.simpleName))
                }
            }
        })
        cont.invokeOnCancellation {
            ws.cancel()
            finish(Outcome.Closed("cancelled"))
        }
    }

    private suspend fun handshake(webSocket: WebSocket) {
        val startedAt = System.nanoTime()
        try {
            val hello = request(webSocket, "hello", mapOf("client" to JsonPrimitive("pangxiaopang")), HANDSHAKE_TIMEOUT_MS)
                .jsonObject
            val latency = (System.nanoTime() - startedAt) / 1_000_000
            val protocol = hello.int("protocol") ?: 0
            if (protocol != DesktopBridgeProtocol.VERSION) {
                fatal = "电脑端插件协议版本 $protocol 与 App（${DesktopBridgeProtocol.VERSION}）不一致，请同时升级"
                webSocket.close(1000, "protocol mismatch")
                return
            }
            ready = true
            _state.value = State.Connected(hello.string("host").orEmpty(), hello.string("version").orEmpty(), latency)
            _connections.emit(hello)
        } catch (error: Exception) {
            webSocket.cancel()
        }
    }

    private fun handleText(text: String) {
        val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        if (json.string("t") == "res") {
            val id = json.string("id") ?: return
            pending.remove(id)?.complete(json)
            return
        }
        if (!_pushes.tryEmit(json)) {
            // 消费方异常缓慢时宁可重连拿快照，也不悄悄丢推送导致界面错乱。
            closeSocket(4001, "client overflow")
        }
    }

    /** 发起一个请求并等待结果；未连接或超时会抛 [DesktopBridgeException]。 */
    suspend fun call(op: String, params: Map<String, JsonElement> = emptyMap(), timeoutMs: Long = CALL_TIMEOUT_MS): JsonElement {
        val ws = socket?.takeIf { ready } ?: throw DesktopBridgeException("offline", "未连接到电脑")
        return request(ws, op, params, timeoutMs)
    }

    private suspend fun request(ws: WebSocket, op: String, params: Map<String, JsonElement>, timeoutMs: Long): JsonElement {
        val id = ids.incrementAndGet().toString()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        try {
            val payload = JsonObject(params + mapOf("id" to JsonPrimitive(id), "op" to JsonPrimitive(op)))
            if (!ws.send(payload.toString())) throw DesktopBridgeException("offline", "未连接到电脑")
            val response = try {
                withTimeout(timeoutMs) { deferred.await() }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                throw DesktopBridgeException("timeout", "电脑响应超时")
            }
            if (response.bool("ok") == true) return response["data"] ?: JsonNull
            val error = response["error"] as? JsonObject
            throw DesktopBridgeException(error?.string("code") ?: "error", error?.string("message") ?: "请求失败")
        } finally {
            pending.remove(id)
        }
    }

    private fun failPending(message: String) {
        val waiting = pending.values.toList()
        pending.clear()
        waiting.forEach { it.completeExceptionally(DesktopBridgeException("offline", message)) }
    }

    private fun closeSocket(code: Int, reason: String) {
        val current = socket ?: return
        ready = false
        if (!current.close(code, reason)) current.cancel()
    }

    /** 等待连接就绪，最长 [timeoutMs]。 */
    suspend fun awaitConnected(timeoutMs: Long): Boolean = withTimeoutOrNull(timeoutMs) {
        while (!isConnected) delay(50)
        true
    } ?: false

    private companion object {
        val BACKOFF_MS = longArrayOf(300, 1_000, 2_000, 4_000, 8_000)
        const val STABLE_CONNECTION_MS = 10_000L
        const val CALL_TIMEOUT_MS = 20_000L
        const val HANDSHAKE_TIMEOUT_MS = 10_000L
        const val PUSH_BUFFER = 8_192
    }
}
