package com.newoether.agora.remote.hermes

import com.newoether.agora.remote.FiloInputException
import com.newoether.agora.remote.MAX_REMOTE_RESPONSE_BYTES
import com.newoether.agora.remote.RemoteContentLimitException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** No outbound queue: a lost reply is an unknown outcome, never permission to replay a write. */
internal class HermesRpcTransport(private val auth: HermesDashboardAuth, private val profile: String) {
    private val mutex = Mutex()
    private val ids = AtomicLong()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var socket: WebSocket? = null
    @Volatile private var generation = 0L
    val events = MutableSharedFlow<JsonObject>(extraBufferCapacity = 128)
    var onFrame: (JsonObject) -> Unit = {}
    var onDisconnect: () -> Unit = {}
    @Volatile private var ready: CompletableDeferred<Unit>? = null
    @Volatile private var closed = false
    @Volatile private var supportsDeclines = false

    suspend fun connect() = mutex.withLock {
        check(!closed)
        if (socket != null && ready?.isCompleted == true && ready?.isCancelled == false) return@withLock
        val ticket = auth.ticket()
        val epoch = ++generation
        val gate = CompletableDeferred<Unit>()
        ready = gate
        val url = auth.endpoint.resolve("api/ws")!!.newBuilder().addQueryParameter("ticket", ticket).build()
        socket = auth.http.newWebSocket(Request.Builder().url(url).header("Origin", auth.origin).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (epoch != generation) return
                    try {
                        if (text.toByteArray().size > MAX_REMOTE_RESPONSE_BYTES) throw RemoteContentLimitException()
                        val frame = Json.parseToJsonElement(text).jsonObject
                        if (frame.string("method") == null && frame.string("id") != null) {
                            pending.remove(frame.string("id"))?.complete(frame)
                            return
                        }
                        if (frame.obj("params").string("type") == "gateway.ready") gate.complete(Unit)
                        onFrame(frame)
                        if (!events.tryEmit(frame)) throw IOException("Hermes event backlog exceeded")
                    } catch (error: Exception) { disconnect(epoch, error); webSocket.cancel() }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    disconnect(epoch, IOException("Hermes connection closed", t))
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    disconnect(epoch, IOException("Hermes connection closed"))
                    webSocket.close(code, null)
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    disconnect(epoch, IOException("Hermes connection closed"))
                }
            })
        try {
            withTimeout(20_000) { gate.await() }
            supportsDeclines = callConnected("client.capabilities", buildJsonObject { put("server_requests", true) },
                includeProfile = false)["declines_not_shown"]?.jsonPrimitive?.booleanOrNull == true
            scope.launch {
                while (isActive && epoch == generation) {
                    delay(20_000)
                    try { callConnected("ping", buildJsonObject {}, includeProfile = false) }
                    catch (error: Exception) {
                        if (error is CancellationException) throw error
                        val old = socket; disconnect(epoch, error); old?.cancel()
                    }
                }
            }
        } catch (error: Exception) {
            socket?.cancel(); disconnect(epoch, error); throw error
        }
    }
    @Synchronized private fun disconnect(epoch: Long, error: Exception) {
        if (epoch != generation) return
        generation++
        socket = null
        ready?.completeExceptionally(error)
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
        onDisconnect()
    }
    suspend fun call(method: String, params: JsonObject = buildJsonObject {}): JsonObject {
        connect()
        return callConnected(method, params)
    }
    private suspend fun callConnected(method: String, params: JsonObject, includeProfile: Boolean = true): JsonObject {
        val id = ids.incrementAndGet().toString()
        val result = CompletableDeferred<JsonObject>()
        pending[id] = result
        try {
            val body = buildJsonObject {
                put("jsonrpc", "2.0"); put("id", id); put("method", method)
                put("params", JsonObject(params + if (includeProfile && profile.isNotBlank()) mapOf("profile" to JsonPrimitive(profile)) else emptyMap()))
            }
            if (body.toString().toByteArray().size > MAX_REMOTE_RESPONSE_BYTES) throw FiloInputException()
            if (socket?.send(body.toString()) != true) throw IOException("Hermes request was not sent")
            val frame = withTimeout(30_000) { result.await() }
            if (frame["error"] != null) throw HermesRpcException(frame.obj("error").string("code"), method)
            return frame.obj("result")
        } finally { pending.remove(id) }
    }
    fun respond(id: String, result: JsonObject? = null, notShown: Boolean = false): Boolean {
        if (notShown && !supportsDeclines) return false // An older server might settle another viewer's request.
        return socket?.send(buildJsonObject {
        put("jsonrpc", "2.0"); put("id", id)
        if (result != null) put("result", result) else put("error", buildJsonObject {
            put("code", if (notShown) 4404 else -32601); put("message", "This client cannot answer this request")
        })
    }.toString()) == true
    }

    fun close() {
        closed = true
        val old = socket
        disconnect(generation, IOException("Hermes connection closed"))
        old?.cancel(); scope.cancel(); auth.close()
    }
}

internal class HermesRpcException(val code: String?, operation: String) : IOException("Hermes refused $operation (code $code)")
