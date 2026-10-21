package com.newoether.agora.remote.hermes

import com.newoether.agora.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import com.newoether.agora.model.SelectedAttachment
import java.nio.file.Files
import java.util.Base64
import kotlinx.serialization.json.put
import java.util.UUID

/** Explicit opt-in only; creates one native test chat and one provider turn. Never runs in CI. */
class HermesLiveTest {
    @Test fun uploadsAndResumesNativeConversation() = runBlocking {
        val envFile = System.getenv("AGORA_HERMES_LIVE_ENV")
        assumeTrue(!envFile.isNullOrEmpty())
        val config = File(requireNotNull(envFile)).readLines().associate { it.substringBefore('=') to it.substringAfter('=') }
        val connection = RemoteConnection("Live verification", requireNotNull(System.getenv("AGORA_HERMES_LIVE_URL")),
            requireNotNull(config["HERMES_DASHBOARD_BASIC_AUTH_PASSWORD"]), kind = RemoteBackendKind.HERMES,
            username = requireNotNull(config["HERMES_DASHBOARD_BASIC_AUTH_USERNAME"]))
        var backend = HermesRemoteBackend(connection)
        val files = Files.createTempDirectory("hermes-attachment-fixture").toFile()
        try {
            backend.connect()
            val models = backend.models()
            val model = models.firstOrNull { it.isDefault } ?: error("Native default model was not found")
            suspend fun upload(name: String, mime: String, bytes: ByteArray): RemoteUpload {
                val file = File(files, name).apply { writeBytes(bytes) }
                return backend.upload(SelectedAttachment(uri = "fixture:$name", type = if (mime.startsWith("image/")) "image" else "file",
                    fileName = name, mimeType = mime, fileSize = file.length(), localPath = file.path))
            }
            val document = upload("agora-verification.txt", "text/plain", "AGORA_DOCUMENT_VERIFIED".toByteArray())
            val image = upload("agora-verification.png", "image/png", Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jhCEAAAAASUVORK5CYII="))
            val client = UUID.randomUUID().toString()
            val session = backend.create("Reply with exactly AGORA_HERMES_OK. Do not use tools.", client, attachments = listOf(document.id, image.id), settings = RemoteSettings(model.id))
            val result = withTimeout(120_000) {
                backend.events(session.id).first { page -> page.runtime?.isRunning == false && (page.runtime?.completedTurnId != null || page.messages.any { it.error }) }
            }
            backend.rename(session.id, "Agora gateway verification " + client.take(8))
            assertTrue("Native turn failed", result.messages.none { it.error })
            assertTrue(result.messages.any { it.role == "assistant" && it.text.contains("AGORA_HERMES_OK") })
            assertNotNull(result.runtime?.completedTurnId)
            assertTrue(result.inputReceipts.any { it.clientId == client })
            backend.close()
            // Retire only this finished fixture runtime, then exercise a genuinely cold watch attach.
            val transport = HermesRpcTransport(HermesDashboardAuth(connection.address, connection.username, connection.token), "")
            try {
                val resumed = transport.call("session.resume", kotlinx.serialization.json.buildJsonObject {
                    put("session_id", session.id); put("lazy", true); put("omit_messages", true)
                })
                transport.call("session.close", kotlinx.serialization.json.buildJsonObject {
                    put("session_id", requireNotNull(resumed.string("session_id")))
                })
            } finally { transport.close() }
            backend = HermesRemoteBackend(connection)
            val restored = backend.conversation(session.id)
            assertTrue(restored.messages.any { it.text.contains("AGORA_HERMES_OK") })
            val continued = UUID.randomUUID().toString()
            backend.send(session.id, "Reply exactly AGORA_CONTINUE_OK. Do not use tools.", continued)
            val settled = withTimeout(120_000) { backend.events(session.id).first { page ->
                page.runtime?.isRunning == false && (page.runtime?.completedTurnId != null || page.messages.any { it.error })
            } }
            assertTrue(settled.messages.any { it.role == "assistant" && it.text.contains("AGORA_CONTINUE_OK") })
            assertTrue(settled.inputReceipts.any { it.clientId == continued })
            backend.archiveSession(session.id)
        } finally { backend.close(); files.deleteRecursively() }
    }
}
