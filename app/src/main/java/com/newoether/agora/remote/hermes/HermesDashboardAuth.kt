package com.newoether.agora.remote.hermes

import com.newoether.agora.remote.FiloConfigurationException
import com.newoether.agora.remote.FiloHttpException
import com.newoether.agora.remote.readRemoteResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Cookies/tickets are transient and scoped to one exact endpoint and account. */
internal class HermesDashboardAuth(address: String, private val username: String, private val password: String) {
    val endpoint: HttpUrl = try {
        address.trim().toHttpUrl().also {
            require(it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null)
            require(it.encodedPath.endsWith('/'))
            require(username.isNotBlank() && password.isNotBlank())
        }
    } catch (_: IllegalArgumentException) { throw FiloConfigurationException() }
    private val lock = Any()
    private var cookies: List<Cookie> = emptyList()
    private val login = Mutex()
    val http = OkHttpClient.Builder().retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = synchronized(lock) {
                this@HermesDashboardAuth.cookies = this@HermesDashboardAuth.cookies.filter { old ->
                    cookies.none { it.name == old.name && it.path == old.path && it.domain == old.domain }
                } + cookies
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
                cookies.filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }
            }
        }).build()
    val origin: String get() = endpoint.newBuilder().encodedPath("/").build().toString().removeSuffix("/")

    suspend fun ticket(): String = login.withLock {
        var response = execute("api/auth/ws-ticket", buildJsonObject {})
        if (response.first == 401 || response.first == 403) {
            val signedIn = execute("auth/password-login", buildJsonObject {
                put("provider", "basic"); put("username", username); put("password", password)
            })
            checked(signedIn)
            response = execute("api/auth/ws-ticket", buildJsonObject {})
        }
        checked(response).string("ticket")?.takeIf { it.isNotBlank() }
            ?: throw IOException("Hermes did not issue a WebSocket ticket")
    }
    suspend fun get(path: String): JsonObject {
        val response = execute(path, null)
        if (response.first != 401 && response.first != 403) return checked(response)
        ticket() // Safe read recovery; mutation callers never pass through here.
        return checked(execute(path, null))
    }
    private fun checked(response: Pair<Int, String>): JsonObject {
        if (response.first !in 200..299) throw FiloHttpException(response.first, null, null)
        return Json.parseToJsonElement(response.second).jsonObject
    }
    suspend fun image(path: String, persist: (java.io.InputStream, String) -> com.newoether.agora.model.ToolImageAttachment): com.newoether.agora.model.ToolImageAttachment =
        suspendCancellableCoroutine { continuation ->
            val url = endpoint.resolve("api/files/download")!!.newBuilder().addQueryParameter("path", path).build()
            val call = http.newCall(Request.Builder().url(url).header("Origin", origin).build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) { response.use {
                    try {
                        if (!it.isSuccessful) throw FiloHttpException(it.code)
                        val mime = it.header("Content-Type").orEmpty()
                        if (!mime.startsWith("image/") || it.body.contentLength() > com.newoether.agora.tool.ToolImageStore.MAX_IMAGE_BYTES)
                            throw com.newoether.agora.remote.RemoteContentLimitException()
                        val result = persist(it.body.byteStream(), mime)
                        continuation.resume(result) { _, value, _ -> java.io.File(value.path).delete() }
                    } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                } }
            })
        }

    suspend fun upload(body: RequestBody): JsonObject = checked(executeBody("api/files/upload-stream", body))
    private suspend fun execute(path: String, body: JsonObject?): Pair<Int, String> =
        executeBody(path, body?.toString()?.toRequestBody("application/json".toMediaType()))
    private suspend fun executeBody(path: String, body: RequestBody?): Pair<Int, String> {
        val url = endpoint.resolve(path) ?: throw FiloConfigurationException()
        require(url.host == endpoint.host && url.port == endpoint.port && url.scheme == endpoint.scheme)
        val request = Request.Builder().url(url).header("Origin", origin).apply {
            if (body != null) post(body)
        }.build()
        return suspendCancellableCoroutine { continuation ->
            val calls = if (body is MultipartBody) http.newBuilder()
                .callTimeout(210, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(210, java.util.concurrent.TimeUnit.SECONDS).build() else http
            val call = calls.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            val result = it.code to it.body.source().readRemoteResponse()
                            if (continuation.isActive) continuation.resume(result)
                        } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                    }
                }
            })
        }
    }
    fun close() { synchronized(lock) { cookies = emptyList() }; http.dispatcher.cancelAll() }
}

internal fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.obj(key: String): JsonObject = get(key) as? JsonObject ?: buildJsonObject {}
internal fun JsonObject.array(key: String): JsonArray = get(key) as? JsonArray ?: JsonArray(emptyList())
