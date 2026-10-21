package com.newoether.agora.remote.hermes

import com.newoether.agora.model.SelectedAttachment
import com.newoether.agora.remote.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okio.BufferedSink
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** Stream original draft bytes once; only native attachment receipts enter the prompt. */
internal class HermesAttachmentUploads(private val auth: HermesDashboardAuth) {
    private val lock = Any()
    private val staged = linkedMapOf<String, RemoteUpload>()
    suspend fun upload(item: SelectedAttachment): RemoteUpload = withContext(Dispatchers.IO) {
        val file = item.localPath?.let(::File) ?: throw FiloInputException()
        if (!file.isFile || file.length() != item.fileSize || file.length() > MAX_FILE_BYTES)
            throw RemoteAttachmentException("Hermes accepts files up to 100 MiB; reselect changed files")
        val size = file.length()
        val mime = item.mimeType ?: "application/octet-stream"
        if (mime.startsWith("image/") && size > MAX_IMAGE_BYTES)
            throw RemoteAttachmentException("Hermes accepts images up to 25 MiB")
        val name = (item.fileName ?: "attachment").substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\p{Cntrl}]"), "_").take(200).trim().takeUnless { it.isBlank() || it == "." || it == ".." } ?: "attachment"
        val root = auth.get("api/files").string("path")?.trimEnd('/', '\\')
            ?: throw IOException("Hermes did not provide an upload root")
        val id = UUID.randomUUID().toString().replace("-", "")
        val path = "$root/agora-uploads/$id/$name"
        val hash = MessageDigest.getInstance("SHA-256")
        val coroutine = currentCoroutineContext()
        val payload = object : RequestBody() {
            override fun contentType() = mime.toMediaTypeOrNull()
            override fun contentLength() = size
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                var count = 0L
                file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutine.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        count += read
                        if (count > size) throw IOException("Attachment changed during upload")
                        hash.update(buffer, 0, read); sink.write(buffer, 0, read)
                    }
                }
                if (count != size) throw IOException("Attachment changed during upload")
            }
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("path", path).addFormDataPart("overwrite", "false")
            .addFormDataPart("file", name, payload).build()
        val response = auth.upload(body)
        if (response["ok"]?.jsonPrimitive?.booleanOrNull != true || response.string("path") != path ||
            response.obj("entry").string("size")?.toLongOrNull() != size) throw IOException("Hermes upload was not confirmed")
        val result = RemoteUpload(id, name, mime, size, hash.digest().joinToString("") { "%02x".format(it) }, path)
        synchronized(lock) {
            staged[id] = result
            while (staged.size > 128) staged.remove(staged.keys.first())
        }
        result
    }
    suspend fun attach(runtime: String, text: String, ids: List<String>,
        call: suspend (String, JsonObject) -> JsonObject,
    ): String {
        val items = synchronized(lock) { ids.map { staged[it] ?: throw FiloInputException() } }
        val refs = mutableListOf<String>()
        for (item in items) {
            val result = call("file.attach", buildJsonObject {
                put("session_id", runtime); put("path", item.path)
                put("name", item.name)
            })
            if (result["attached"]?.jsonPrimitive?.booleanOrNull != true) throw IOException("Hermes did not attach the selected file")
            refs += result.string("ref_text")?.takeIf { it.startsWith("@file:") }
                ?: throw IOException("Hermes did not return a document reference")
        }
        // Documents and images use native file references; Hermes chooses extraction/vision tools.
        return (listOf(text) + refs).filter { it.isNotBlank() }.joinToString("\n\n")
    }
    companion object {
        const val MAX_FILE_BYTES = 100L * 1024 * 1024
        const val MAX_IMAGE_BYTES = 25L * 1024 * 1024
    }
}
