package com.newoether.agora.sharing

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.newoether.agora.R
import com.newoether.agora.model.SelectedAttachment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.util.UUID

const val EXTRA_SHARED_TEXT_REQUEST = "com.newoether.agora.extra.SHARED_TEXT_REQUEST"
const val EXTRA_EXCERPT_METADATA = "org.koreader.extra.EXCERPT_METADATA"

/** Copies untrusted incoming text into app-owned bytes before the sending Activity loses focus. */
internal class SharedTextInbox(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun directory(id: String): File {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false))
        return File(context.filesDir, "attachments/shared/$id")
    }

    fun receive(id: String, intent: Intent): SelectedAttachment {
        val folder = directory(id)
        val record = File(folder, "attachment.json")
        if (record.isFile) return load(id)
        require(intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) { "Share plain text to Agora" }
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        require(text.isNotBlank()) { "The shared content contains no text" }
        require(text.toByteArray(Charsets.UTF_8).size <= 512 * 1024) { "Shared text is too large (512 KiB maximum)" }
        val raw = intent.getStringExtra(EXTRA_EXCERPT_METADATA)
        val metadata = raw?.takeIf { it.length <= 140_000 }?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.takeIf { it.optString("schema") == "org.koreader.excerpt/v1" }
        val title = label(metadata?.optString("title")) ?: label(intent.getStringExtra(Intent.EXTRA_TITLE))
            ?: label(intent.getStringExtra(Intent.EXTRA_SUBJECT)) ?: context.getString(R.string.shared_content_name)
        val book = label(metadata?.optString("bookTitle"))
        val author = label(metadata?.optString("authors"))
        folder.mkdirs()
        val content = File(folder, "excerpt.txt").apply { writeText(text) }
        val preview = metadata?.optString("thumbnailBase64")?.takeIf { it.length in 1..120_000 }
            ?.let { encoded -> runCatching { saveThumbnail(folder, Base64.decode(encoded, Base64.DEFAULT)) }.getOrNull() }
            ?: receiveStandardThumbnail(folder, intent)
        val prepared = buildString {
            append("Excerpt: ").append(title).append('\n')
            book?.let { append("Book: ").append(it).append('\n') }
            author?.let { append("Author: ").append(it).append('\n') }
            append('\n').append(text)
        }
        val attachment = SelectedAttachment(localId = id, uri = content.toURI().toString(), type = "file",
            fileName = (book?.let { "$it — $title" } ?: title) + ".txt", mimeType = "text/plain", fileSize = content.length(),
            localPath = content.absolutePath, preparedText = prepared,
            sourceTitle = title, sourceBookTitle = book, sourceAuthors = author, sourceThumbnailPath = preview)
        val temporary = File(folder, "attachment.json.tmp")
        temporary.writeText(json.encodeToString(attachment))
        check(temporary.renameTo(record)) { "Could not save shared content" }
        return attachment
    }

    private fun label(value: String?): String? = value
        ?.map { if (it.isISOControl()) ' ' else it }
        ?.joinToString("")
        ?.trim()
        ?.take(300)
        ?.takeIf { it.isNotBlank() }

    private fun receiveStandardThumbnail(folder: File, intent: Intent): String? {
        val candidates = buildList {
            intent.clipData?.let { clip ->
                for (index in 0 until minOf(clip.itemCount, 8)) clip.getItemAt(index).uri?.let(::add)
            }
            intent.data?.let(::add)
        }.distinct()
        for (uri in candidates) {
            if (uri.scheme != "content") continue
            val thumbnail = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 90_000) { "Source cover is too large" }
                        output.write(buffer, 0, count)
                    }
                    saveThumbnail(folder, output.toByteArray())
                }
            }.getOrNull()
            if (thumbnail != null) return thumbnail
        }
        return null
    }

    private fun saveThumbnail(folder: File, bytes: ByteArray): String {
        require(bytes.size <= 90_000)
        val jpeg = bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
        val png = bytes.size >= 8 && bytes.take(8).toByteArray().contentEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        require(jpeg || png) { "Invalid source thumbnail" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..2048 && bounds.outHeight in 1..2048)
        val image = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        try {
            val file = File(folder, "cover.jpg")
            file.outputStream().use { check(image.compress(Bitmap.CompressFormat.JPEG, 85, it)) }
            return file.absolutePath
        } finally {
            image.recycle()
        }
    }

    fun load(id: String): SelectedAttachment = json.decodeFromString(File(directory(id), "attachment.json").readText())
    fun owner(id: String): String? = File(directory(id), "owner").takeIf { it.isFile }?.readText()
    fun choose(id: String, owner: String) {
        marker(id, "owner", owner)
    }
    private fun marker(id: String, name: String, value: String) {
        val folder = directory(id)
        val temporary = File(folder, "$name.tmp")
        temporary.writeText(value)
        check(temporary.renameTo(File(folder, name))) { "Could not save share receipt" }
    }
    fun consumed(id: String) = File(directory(id), "consumed").exists()
    fun consume(id: String) {
        marker(id, "consumed", "1")
    }
}
