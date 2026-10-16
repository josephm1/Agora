package com.newoether.agora.skills

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract as Docs
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** SAF access stays in the selected document tree; filesystem paths use existing shell tools. */
internal class SkillFolderAccess(private val context: Context) {
    fun list(folder: SkillFolder, path: String, cursor: Int): String {
        require(cursor in 0..10000) { "Invalid folder cursor" }
        val uri = resolve(folder, path)
        val rows = if (Docs.isTreeUri(Uri.parse(folder.location))) children(uri) else listOf(
            Entry(uri.toString(), com.newoether.agora.util.FileValidator.resolveFileName(context, uri) ?: "source",
                false, com.newoether.agora.util.FileValidator.resolveFileSize(context, uri) ?: 0L))
        return buildJsonObject {
            put("folder", folder.alias)
            putJsonArray("files") { rows.drop(cursor).take(50).forEach { add(buildJsonObject {
                put("name", it.name); put("directory", it.directory); put("size", it.size)
            }) } }
            if (rows.size > cursor + 50) put("next_cursor", cursor + 50)
        }.toString()
    }

    fun read(folder: SkillFolder, path: String, limit: Int): String {
        require(limit in 1..64000) { "Read limit must be 1–64000 bytes" }
        val uri = resolve(folder, path)
        val mime = context.contentResolver.getType(uri).orEmpty()
        require(mime.startsWith("text/") || path.substringAfterLast('.', "") in setOf("md", "txt", "json", "csv", "tsv")) {
            "This file requires document extraction; choose a text/Markdown file"
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (output.size() <= limit) {
                val n = input.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
                if (n < 0) break
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        } ?: error("Unable to read selected file")
        val truncated = bytes.size > limit
        var end = minOf(bytes.size, limit)
        if (truncated) while (end > 0 && (bytes[end].toInt() and 0xc0) == 0x80) end--
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, 0, end)).toString()
        return buildJsonObject {
            put("folder", folder.alias); put("path", path); put("content", text); put("truncated", truncated)
        }.toString().also { require(it.toByteArray().size <= 80000) { "Encoded result too large; request a smaller limit" } }
    }

    private data class Entry(val id: String, val name: String, val directory: Boolean, val size: Long)
    private fun children(uri: Uri): List<Entry> {
        val result = mutableListOf<Entry>()
        val childrenUri = Docs.buildChildDocumentsUriUsingTree(uri, Docs.getDocumentId(uri))
        context.contentResolver.query(childrenUri, arrayOf(Docs.Document.COLUMN_DOCUMENT_ID,
            Docs.Document.COLUMN_DISPLAY_NAME, Docs.Document.COLUMN_MIME_TYPE, Docs.Document.COLUMN_SIZE),
            null as android.os.Bundle?, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                require(result.size < 10000) { "Folder contains too many entries; choose a subfolder" }
                result += Entry(cursor.getString(0), cursor.getString(1),
                    cursor.getString(2) == Docs.Document.MIME_TYPE_DIR, cursor.getLong(3))
            }
        } ?: error("Folder is unavailable; reconnect it in Skill settings")
        return result.sortedBy { it.name }
    }

    private fun resolve(folder: SkillFolder, path: String): Uri {
        require(folder.isDocumentTree) { "Not a document tree" }
        val tree = Uri.parse(folder.location)
        if (!Docs.isTreeUri(tree)) {
            val name = com.newoether.agora.util.FileValidator.resolveFileName(context, tree) ?: "source"
            require(safeSkillRelativePath(path).let { it.isEmpty() || it == name }) { "Choose the linked source file" }
            return tree
        }
        var uri = Docs.buildDocumentUriUsingTree(tree, Docs.getTreeDocumentId(tree))
        safeSkillRelativePath(path).split('/').filter { it.isNotEmpty() }.forEach { name ->
            val entry = children(uri).singleOrNull { it.name == name } ?: error("Missing or ambiguous file: $name")
            uri = Docs.buildDocumentUriUsingTree(tree, entry.id)
        }
        return uri
    }
}

internal fun safeSkillRelativePath(path: String): String {
    require(path.length <= 4096 && !path.startsWith('/') && !path.contains('\\') && !path.contains('\u0000') &&
        path.split('/').none { it == ".." || it == "." }) { "Choose a relative path inside the linked folder" }
    return path
}

internal fun skillFolderPath(folder: SkillFolder, path: String): String {
    val relative = safeSkillRelativePath(path)
    require(!folder.isDocumentTree && folder.location.startsWith('/')) { "Use an absolute folder path" }
    val root = folder.location.trimEnd('/').ifEmpty { "/" }
    return if (relative.isEmpty()) root else root.trimEnd('/') + "/$relative"
}

/** Normalize existing filesystem-tool output to paths relative to the declared folder. */
internal fun skillFileListing(folder: SkillFolder, path: String, cursor: Int, result: String): String {
    require(cursor in 0..10000) { "Invalid folder cursor" }
    val objectValue = Json.parseToJsonElement(result).jsonObject
    if (objectValue.containsKey("error")) return result
    val root = skillFolderPath(folder, "").trimEnd('/') + "/"
    val files = objectValue["files"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty().map { file ->
        require(file.startsWith(root)) { "File listing returned an unexpected root" }
        safeSkillRelativePath(file.removePrefix(root))
    }.sorted()
    return buildJsonObject {
        put("folder", folder.alias); put("path", path); put("resolved_directory", skillFolderPath(folder, path))
        putJsonArray("files") { files.drop(cursor).take(50).forEach { add(it) } }
        if (files.size > cursor + 50) put("next_cursor", cursor + 50)
        put("truncated", objectValue["truncated"]?.jsonPrimitive?.booleanOrNull ?: false)
        put("depth", 4)
    }.toString().also { require(it.toByteArray().size <= 80000) { "File list is too large; choose a smaller subfolder" } }
}

/** Translate a pasted Android path only when its existing sandbox mount is actually authorized. */
internal fun mapSkillSharedPath(folder: SkillFolder, sharedRoot: String, mounted: Boolean): SkillFolder {
    if (folder.isDocumentTree || folder.server != "Local Sandbox") return folder
    val root = listOf(sharedRoot.trimEnd('/'), "/sdcard").firstOrNull {
        it.isNotEmpty() && (folder.location == it || folder.location.startsWith("$it/"))
    } ?: return folder
    require(mounted) { "Enable sandbox shared storage and grant Android file access before using this path" }
    return folder.copy(location = "/mnt/shared" + folder.location.removePrefix(root))
}
