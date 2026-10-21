package com.newoether.agora.remote.hermes

import com.newoether.agora.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.io.IOException

class HermesTransportTest {
    private class Gateway : AutoCloseable {
        val server = MockWebServer()
        val methods = CopyOnWriteArrayList<String>()
        val sockets = CopyOnWriteArrayList<WebSocket>()
        val logins = AtomicInteger()
        val tickets = AtomicInteger()
        var loseSubmit = false
        var question: JsonObject? = null
        val replies = CopyOnWriteArrayList<JsonObject>()
        init {
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.requestUrl!!.encodedPath) {
                        "/api/auth/ws-ticket" -> {
                            tickets.incrementAndGet()
                            if (request.getHeader("Cookie") == null) MockResponse().setResponseCode(401)
                            else json("""{"ticket":"single-use-ticket"}""")
                        }
                        "/auth/password-login" -> {
                            val b = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                            assertEquals("password", b.string("password"))
                            logins.incrementAndGet()
                            json("""{"ok":true}""").addHeader("Set-Cookie", "session=valid; Path=/; HttpOnly")
                        }
                        "/api/ws" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                            override fun onOpen(ws: WebSocket, response: Response) {
                                sockets += ws
                                ws.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")
                            }
                            override fun onMessage(ws: WebSocket, text: String) {
                                val f = Json.parseToJsonElement(text).jsonObject
                                val method = f.string("method")
                                if (method == null) { replies += f; return }
                                methods += method
                                if (method == "session.resume") assertEquals(true, f.obj("params")["lazy"]?.jsonPrimitive?.booleanOrNull)
                                if (method == "prompt.submit" && loseSubmit) { ws.close(1001, "Test disconnect"); return }
                                val result = when (method) {
                                    "session.resume" -> """{"session_id":"runtime","stored_session_id":"stored","running":false,"info":{"model":"model","provider":"provider"}}"""
                                    "session.create" -> """{"session_id":"runtime","stored_session_id":"stored","info":{"model":"model","provider":"provider"}}"""
                                    "session.archive" -> """{"archived":true,"session_key":"stored"}"""
                                    "prompt.submit" -> """{"status":"streaming","user_row_id":3}"""
                                    "model.options" -> """{"model":"model","provider":"provider","providers":[{"slug":"provider","models":["model"]}]}"""
                                    else -> "{}"
                                }
                                ws.send("""{"jsonrpc":"2.0","id":${f["id"]},"result":$result}""")
                            }
                        })
                        "/api/sessions" -> json("""{"sessions":[{"id":"stored","title":"A chat","started_at":1}],"total":1}""")
                        "/api/sessions/stored/messages" -> json("""{"messages":[{"row_id":1,"role":"user","text":"hello"},{"row_id":2,"role":"assistant","text":"answer"}]}""")
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            server.start()
        }
        private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
        fun backend(profile: String = "") = HermesRemoteBackend(RemoteConnection("Hermes", server.url("/").toString(),
            "password", kind = RemoteBackendKind.HERMES, username = "user", profile = profile))
        fun emit(type: String, payload: String) = sockets.last().send("""{"jsonrpc":"2.0","method":"event","params":{"session_id":"runtime","type":"$type","payload":$payload}}""")
        override fun close() { sockets.forEach { it.close(1000, "Test complete") }; server.shutdown() }
    }

    @Test fun authenticatesNegotiatesAndHydratesNativeRows() = runBlocking {
        Gateway().use { g ->
            val backend = g.backend()
            try {
                assertEquals("Hermes", backend.connect())
                assertEquals(1, g.logins.get())
                assertTrue(g.methods.contains("client.capabilities"))
                assertEquals("stored", backend.sessions().sessions.single().id)
                assertEquals("provider/model", backend.models().single().id)
                val page = backend.conversation("stored")
                assertEquals(listOf("row:1", "row:2"), page.nodes.map { it.id })
                assertEquals("answer", page.messages.last().text)
                assertEquals("idle", page.runtime?.status)
                assertFalse(backend.capabilities.activeSend)
                assertTrue(backend.capabilities.archive)
                backend.archiveSession("stored")
                assertTrue(g.methods.contains("session.archive"))
            } finally { backend.close() }
        }
    }
    @Test fun lostPromptReplyNeverResendsOnReconnect() = runBlocking {
        Gateway().use { g ->
            val backend = g.backend()
            try {
                backend.conversation("stored")
                g.loseSubmit = true
                assertThrows(Exception::class.java) { runBlocking { backend.send("stored", "request", "client") } }
                g.loseSubmit = false
                backend.connect()
                backend.conversation("stored")
                assertEquals(1, g.methods.count { it == "prompt.submit" })
                assertTrue(g.tickets.get() >= 3)
            } finally { backend.close() }
        }
    }
    @Test fun streamingAndApprovalsRemainBoundToNativeRequest() = runBlocking {
        Gateway().use { g ->
            val backend = g.backend()
            try {
                val pages = CopyOnWriteArrayList<RemoteConversationPage>()
                val job = launch { backend.events("stored").collect { pages += it } }
                withTimeout(5000) { while (pages.isEmpty()) delay(10) }
                g.emit("message.start", "{}")
                g.emit("message.delta", """{"text":"live text"}""")
                withTimeout(5000) { while (pages.none { p -> p.messages.any { it.text == "live text" } }) delay(10) }
                g.sockets.last().send("""{"jsonrpc":"2.0","id":"q1","method":"approval","params":{"session_id":"runtime","choices":["once","deny"],"command":"example"}}""")
                withTimeout(5000) { while (pages.last().runtime?.questions.isNullOrEmpty()) delay(10) }
                backend.answer("stored", "q1", buildJsonObject { put("choice", "deny") })
                withTimeout(5000) { while (g.replies.none { it.string("id") == "q1" }) delay(10) }
                assertEquals("deny", g.replies.first { it.string("id") == "q1" }.obj("result").string("choice"))
                assertThrows(FiloInputException::class.java) { runBlocking { backend.answer("other", "q1", buildJsonObject {}) } }
                job.cancelAndJoin()
            } finally { backend.close() }
        }
    }
    @Test fun nativeAckCreatesReceiptWithoutCallingItCompletion() = runBlocking {
        Gateway().use { g ->
            val backend = g.backend()
            try {
                backend.conversation("stored")
                assertEquals("row:3", backend.send("stored", "request", "client"))
                val page = withTimeout(5000) { backend.events("stored").first { it.inputReceipts.isNotEmpty() } }
                assertEquals("client", page.inputReceipts.single().clientId)
                assertNull(page.runtime?.completedTurnId)
            } finally { backend.close() }
        }
    }
}
