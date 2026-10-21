package com.newoether.agora.remote.hermes

import com.newoether.agora.model.SelectedAttachment
import com.newoether.agora.remote.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class HermesAttachmentUploadsTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun auth(server: MockWebServer) = HermesDashboardAuth(server.url("/").toString(), "user", "password")

    @Test fun originalDocumentAndImageBytesStreamOnceThenAttachToExactRuntimeInOrder() = runBlocking<Unit> {
        val requests = mutableListOf<Pair<String, JsonObject>>()
        val uploads = AtomicInteger()
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                    "/api/files" -> MockResponse().setBody("""{"path":"/managed"}""")
                    "/api/files/upload-stream" -> {
                        uploads.incrementAndGet()
                        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data"))
                        val wire = request.body.readUtf8()
                        assertTrue(wire.contains("ORIGINAL_BYTES"))
                        val path = Regex("name=\"path\"\\r\\n\\r\\n([^\\r]+)").find(wire)?.groupValues?.get(1) ?: error("missing path")
                        MockResponse().setBody(buildJsonObject {
                            put("ok", true); put("path", path); put("entry", buildJsonObject { put("size", 14) })
                        }.toString())
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
            server.start()
            val a = auth(server)
            try {
                val stage = HermesAttachmentUploads(a)
                suspend fun item(name: String, mime: String): RemoteUpload {
                    val file = temporary.newFile(name).apply { writeText("ORIGINAL_BYTES") }
                    return stage.upload(SelectedAttachment(uri = "content://fixture/$name", type = "file", fileName = name,
                        mimeType = mime, fileSize = file.length(), localPath = file.path))
                }
                val doc = item("document.txt", "text/plain")
                val image = item("photo.png", "image/png")
                val text = stage.attach("exact-runtime", "Question", listOf(doc.id, image.id)) { method, params ->
                    requests += method to params
                    buildJsonObject { put("attached", true); put("ref_text", if (params.string("path") == doc.path) "@file:document.txt" else "@file:photo.png") }
                }
                assertEquals("Question\n\n@file:document.txt\n\n@file:photo.png", text)
                assertEquals(listOf("file.attach", "file.attach"), requests.map { it.first })
                assertTrue(requests.all { it.second.string("session_id") == "exact-runtime" })
                assertEquals(doc.path, requests.first().second.string("path"))
                assertEquals(image.path, requests.last().second.string("path"))
                assertEquals(2, uploads.get())
                assertEquals(64, doc.sha256.length)
            } finally { a.close() }
        }
    }
    @Test fun failedOrChangedUploadCannotProduceASendableReference() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"path":"/managed"}"""))
            server.enqueue(MockResponse().setResponseCode(413))
            server.start()
            val a = auth(server)
            try {
                val file = temporary.newFile("test").apply { writeText("bytes") }
                val item = SelectedAttachment(uri = "fixture", type = "file", fileSize = file.length(), localPath = file.path)
                val stage = HermesAttachmentUploads(a)
                assertThrows(FiloHttpException::class.java) { runBlocking { stage.upload(item) } }
                assertThrows(FiloInputException::class.java) { runBlocking { stage.attach("runtime", "", listOf("unknown")) { _, _ -> error("must not call") } } }
                assertThrows(RemoteAttachmentException::class.java) { runBlocking { stage.upload(item.copy(fileSize = 999)) } }
                assertEquals(2, server.requestCount)
            } finally { a.close() }
        }
    }
    @Test fun imageOnlyInputNeedsNativeAttachmentConfirmationAndMissingDocumentReferenceFails() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"path":"/managed"}"""))
            // Failed server receipt must not be admitted, even with HTTP 200.
            server.enqueue(MockResponse().setBody("""{"ok":true,"path":"/foreign","entry":{"size":3}}"""))
            server.start()
            val a = auth(server)
            try {
                val f = temporary.newFile("a.png").apply { writeText("png") }
                assertThrows(IOException::class.java) { runBlocking { HermesAttachmentUploads(a).upload(
                    SelectedAttachment(uri = "fixture", type = "image", fileSize = 3, localPath = f.path)) } }
            } finally { a.close() }
        }
    }
}
