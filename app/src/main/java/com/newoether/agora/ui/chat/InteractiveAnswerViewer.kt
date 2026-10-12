package com.newoether.agora.ui.chat

import android.content.Intent
import android.net.Uri
import android.webkit.WebMessage
import android.webkit.WebMessagePort
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.newoether.agora.R
import com.newoether.agora.model.InteractiveAnswer
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream

/** Native confirmation owns sending; generated code cannot bypass a user decision. */
private data class PendingInteractivePrompt(val id: Int, val text: String, val reply: (Boolean, Boolean) -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InteractiveAnswerViewer(
    answer: InteractiveAnswer,
    onClose: () -> Unit,
    onSend: suspend (String) -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingInteractivePrompt?>(null) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val webView = remember(answer) { WebView(context) }
    // Revokes queued bridge callbacks before teardown, including callbacks from disposed viewers.
    val alive = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    val destroyed = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val ports = remember { mutableListOf<WebMessagePort>() }
    // This deadline is failure detection for a hung renderer, never a substitute for ready.
    LaunchedEffect(webView) {
        kotlinx.coroutines.delay(15000)
        if (!loaded) error = true
    }
    LaunchedEffect(error) {
        if (error) {
            alive.set(false)
            pending = null
            ports.forEach { runCatching { it.close() } }
            if (destroyed.compareAndSet(false, true)) {
                webView.stopLoading()
                webView.destroy()
            }
        }
    }
    DisposableEffect(webView) {
        onDispose {
            alive.set(false)
            ports.forEach { runCatching { it.close() } }
            if (destroyed.compareAndSet(false, true)) {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(topBar = {
            TopAppBar(title = { Text(answer.title) }, navigationIcon = {
                TextButton(onClick = onClose) { Text(stringResource(R.string.interactive_answer_close)) }
            })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).then(
                if (error) Modifier.verticalScroll(rememberScrollState()) else Modifier
            )) {
                if (!loaded && !error) {
                    CircularProgressIndicator(Modifier.padding(16.dp))
                    Text(stringResource(R.string.interactive_answer_loading), Modifier.padding(horizontal = 16.dp))
                }
                if (error) {
                    Text(stringResource(R.string.interactive_answer_error), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                    Text(answer.fallback, Modifier.padding(16.dp))
                } else {
                    AndroidView(factory = {
                        webView.apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            settings.setSupportMultipleWindows(false)
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            setDownloadListener { _, _, _, _, _ -> }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                    val url = request.url.toString()
                                    val asset = InteractiveAnswerWebPolicy.assetName(url)
                                    if (asset != null) {
                                        val mime = when {
                                            asset.endsWith(".html") -> "text/html"
                                            asset.endsWith(".css") -> "text/css"
                                            else -> "application/javascript"
                                        }
                                        return WebResourceResponse(mime, "utf-8", context.assets.open("interactive/$asset"))
                                    }
                                    if (!request.isForMainFrame && InteractiveAnswerWebPolicy.allowsNetwork(url)) return null
                                    return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                                }

                                override fun onPageFinished(view: WebView, url: String) {
                                    if (url != InteractiveAnswerWebPolicy.HOST_URL || ports.isNotEmpty() || !alive.get()) return
                                    val channel = view.createWebMessageChannel()
                                    ports.add(channel[0])
                                    channel[0].setWebMessageCallback(object : WebMessagePort.WebMessageCallback() {
                                        override fun onMessage(port: WebMessagePort, message: WebMessage?) {
                                            if (!alive.get()) return
                                            val raw = message?.data ?: return
                                            if (raw.length > 16000) return
                                            val data = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
                                            val id = (data["id"] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull
                                            val method = (data["method"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                                            val value = (data["value"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
                                            fun reply(ok: Boolean, cancelled: Boolean = false) {
                                                if (!alive.get() || id == null) return
                                                port.postMessage(WebMessage(buildJsonObject {
                                                    put("id", id)
                                                    put("ok", ok)
                                                    put("cancelled", cancelled)
                                                    if (!ok) put("error", context.getString(R.string.interactive_answer_followup_failed))
                                                }.toString()))
                                            }
                                            when (method) {
                                                "ready" -> loaded = true
                                                "error" -> { error = true; reply(false) }
                                                "sendPrompt" -> {
                                                    val text = InteractiveAnswerWebPolicy.prompt(value)
                                                    if (id == null || text == null || pending != null) reply(false)
                                                    else pending = PendingInteractivePrompt(id, text, ::reply)
                                                }
                                                "openLink" -> {
                                                    val ok = InteractiveAnswerWebPolicy.allowsLink(value) && runCatching {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value)))
                                                    }.isSuccess
                                                    reply(ok)
                                                }
                                                else -> reply(false)
                                            }
                                        }
                                    })
                                    view.postWebMessage(WebMessage(answer.encode(), arrayOf(channel[1])), Uri.parse(InteractiveAnswerWebPolicy.ORIGIN))
                                }

                                override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                                    alive.set(false)
                                    error = true
                                    if (destroyed.compareAndSet(false, true)) view.destroy()
                                    return true
                                }

                                override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: android.webkit.WebResourceError) {
                                    if (request.isForMainFrame) error = true
                                }
                            }
                            loadUrl(InteractiveAnswerWebPolicy.HOST_URL)
                        }
                    }, modifier = Modifier.fillMaxSize())
                }
            }
        }
        pending?.let { request ->
            AlertDialog(
                onDismissRequest = { if (!sending) { request.reply(false, true); pending = null } },
                title = { Text(stringResource(R.string.interactive_answer_followup)) },
                text = { Text(request.text) },
                confirmButton = {
                    Button(enabled = !sending, onClick = {
                        if (!sending) {
                            sending = true
                            scope.launch {
                                val accepted = try {
                                    onSend(request.text)
                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    false
                                }
                                request.reply(accepted, false)
                                sending = false
                                pending = null
                                if (accepted) onClose()
                            }
                        }
                    }) { Text(stringResource(R.string.interactive_answer_send)) }
                },
                dismissButton = {
                    TextButton(enabled = !sending, onClick = { request.reply(false, true); pending = null }) {
                        Text(stringResource(R.string.interactive_answer_cancel))
                    }
                },
            )
        }
    }
}
