package com.newoether.agora.notes

import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** Wire data only. Notes owns edits and revisions; Agora retains immutable attachment bytes. */
@Serializable
data class NotesInlineStyle(val start: Int, val end: Int, val bold: Boolean? = null,
                            val italic: Boolean? = null, val strike: Boolean? = null)
@Serializable
data class NotesBlock(val id: String, val type: String, val text: String, val checked: Boolean? = null,
                      val sourceRefs: List<String>? = null, val sourceInkRegionIds: List<String>? = null, val parentId:String?=null,
                      val styles: List<NotesInlineStyle>? = null)
@Serializable
data class NotesPage(val id: String, val sourceFile: String, val sidecarFile: String, val previewFile: String? = null, val imageFrame:JsonObject? = null)
@Serializable
data class NotesInkRegion(val id: String, val pageId: String, val runId: String,
                          val bounds: JsonObject, val strokeIds: List<String>)
@Serializable
data class NotesDocument(
    val schema: String, val documentId: String, val revisionId: String, val title: String,
    val blocks: List<NotesBlock>, val pages: List<NotesPage>, val checksums: Map<String, String>,
    val instruction: String? = null,
    val thumbnailFile: String? = null,
    val inkRegions: List<NotesInkRegion> = emptyList(),
) {
    fun isMarkdownInkPage(): Boolean = pages.size == 1 && pages.single().previewFile != null && inkRegions.isNotEmpty()
    fun contextText(preferMarkdown: Boolean = true): String = if (preferMarkdown && isMarkdownInkPage()) buildString {
        append(NotesMarkdownPrompt.TEXT).append('\n')
        instruction?.takeIf { it.isNotBlank() }?.let { append("User request: ").append(it).append('\n') }
    } else buildString {
        append("Notes document: ").append(title).append('\n')
        append("documentId: ").append(documentId).append("\nbaseRevisionId: ").append(revisionId).append('\n')
        append("This is a semantic projection. Ink is visible only if page images are attached.\n")
        instruction?.takeIf { it.isNotBlank() }?.let { append("User request: ").append(it).append('\n') }
        append("For requested handwriting-to-text replacement use replace_notes_handwriting; for organization use propose_notes_document. Return documentId, baseRevisionId, targetPageIds and ordered blocks; preserve existing IDs and checked states. ")
        append("Supported types: heading, paragraph, bullet, numbered, task. Use propose_notes_document for semantic edits. Only replace_notes_handwriting converts handwriting; include exact sourceInkRegionIds on each recognized block. Preserve meaning; do not invent unreadable content.\n")
        append("targetPageIds: ").append(Json.encodeToString(pages.map { it.id })).append('\n')
        append("Image coordinate contract: bounds use logical page coordinates with a top-left origin. Each page imageFrame gives x/y/width/height and pixelWidth/pixelHeight; pixelX=(bounds.x-frame.x)*pixelWidth/frame.width, similarly for y. Regions are ink groupings, not necessarily words: one word may span multiple regions. Group adjacent regions belonging to the same readable line; do not split words at stroke boundaries. Use only the visible literal text; do not complete familiar phrases.\n")
        append("pageImages: ").append(Json.encodeToString(pages.filter { it.previewFile!=null }.map { page -> buildJsonObject {
            put("pageId",page.id);page.imageFrame?.let { put("imageFrame",it) }
        } })).append('\n')
        append("blocks: ").append(Json.encodeToString(blocks.map { it.copy(sourceRefs = null) })).append('\n')
        append("inkRegions: ").append(Json.encodeToString(inkRegions.map { region -> buildJsonObject {
            put("id", region.id); put("pageId", region.pageId); put("bounds", region.bounds)
        } })).append('\n')
    }
}
@Serializable
data class NotesProposal(
    val schema: String = "kalamos.notes-proposal/v1", val proposalId: String,
    val documentId: String? = null, val baseRevisionId: String? = null,
    val title: String, val blocks: List<NotesBlock> = emptyList(),
    val markdown: String? = null,
    val backgroundRequestId: String? = null,
    val sourceInkRegions: List<NotesInkRegion>? = null,
    val targetPageIds: List<String>? = null,
    val operation: String = "organize",
    @kotlinx.serialization.Transient val sourceMessageId:String?=null,
) {
    fun validate(): NotesProposal = apply {
        require(schema == "kalamos.notes-proposal/v1") { "Unsupported Notes proposal" }
        NotesDocumentFormat.uuid(proposalId)
        backgroundRequestId?.let(NotesDocumentFormat::uuid)
        require((documentId == null) == (baseRevisionId == null)) { "Both document and base revision are required for an edit" }
        documentId?.let(NotesDocumentFormat::uuid)
        baseRevisionId?.let { require(NotesDocumentFormat.hash.matches(it)) { "Invalid base revision" } }
        targetPageIds?.let { pages ->
            require(documentId != null && pages.size in 1..32 && pages.distinct().size == pages.size &&
                pages.all { Regex("[A-Za-z0-9_-]{1,120}").matches(it) }) { "Invalid target Notes pages" }
        }
        require(operation in setOf("organize", "replace_ink")) { "Unsupported Notes operation" }
        markdown?.let { text ->
            require(text.isNotBlank() && text.length <= 60000 && '\u0000' !in text && operation == "replace_ink" &&
                documentId != null && targetPageIds?.size == 1 && blocks.isEmpty() && !sourceInkRegions.isNullOrEmpty()) { "Invalid Markdown ink replacement" }
            val regions = sourceInkRegions!!
            require(regions.size <= 2000 && regions.map { it.id }.distinct().size == regions.size &&
                regions.all { it.pageId == targetPageIds.single() && Regex("[A-Za-z0-9_-]{1,120}").matches(it.id) }) { "Invalid Markdown source ink scope" }
        }
        require(markdown != null || sourceInkRegions == null) { "Markdown required for source ink region snapshot" }
        if (operation == "replace_ink" && markdown == null) {
            require(documentId != null && !targetPageIds.isNullOrEmpty() && blocks.isNotEmpty() &&
                blocks.all { !it.sourceInkRegionIds.isNullOrEmpty() }) { "Ink replacement requires an existing scoped document and source references" }
        }
        NotesDocumentFormat.validateBlocks(title, blocks)
        require(encode().toByteArray(Charsets.UTF_8).size <= 80_000) { "Notes proposal exceeds 80000 bytes" }
    }
    fun encode(): String = NotesDocumentFormat.wireJson.encodeToString(this)
}
object NotesDocumentFormat {
    const val MIME = "application/vnd.kalamos.notes-document+zip"
    const val PROPOSAL_MIME = "application/vnd.kalamos.notes-proposal+json"
    const val MAX_BYTES = 100L * 1024 * 1024
    val hash = Regex("[0-9a-f]{64}")
    private val id = Regex("[A-Za-z0-9_-]{1,120}")
    private val path = Regex("pages/[A-Za-z0-9_-]{1,120}\\.(md|json|jpg)")
    val json = Json { ignoreUnknownKeys = true }
    val wireJson = Json { encodeDefaults = true; explicitNulls = false }
    fun filename(title:String):String = java.text.Normalizer.normalize(title,java.text.Normalizer.Form.NFKC)
        .replace(Regex("[\\x00-\\x1f\\x7f/\\\\:*?\"<>|]"),"-").trim().trimEnd('.', ' ').take(120).ifBlank { "Notebook" }+".notes.zip"
    fun uuid(value: String) { require(runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)) { "Invalid document identity" } }
    fun validateBlocks(title: String, blocks: List<NotesBlock>) {
        require(title.isNotBlank() && title.length <= 200) { "Invalid Notes title" }
        require(blocks.size <= 2000 && blocks.map { it.id }.distinct().size == blocks.size) { "Invalid or duplicate Notes blocks" }
        val byId=blocks.associateBy { it.id }
        blocks.forEach { block ->
            var cursor:NotesBlock?=block;val seen=HashSet<String>();var depth=0
            while(cursor?.parentId!=null){
                require(seen.add(cursor.id) && depth++<4) { "Cyclic or overly deep Notes group" }
                cursor=requireNotNull(byId[cursor.parentId]) { "Unknown Notes parent" }
            }
        }
        blocks.forEach {
            require(id.matches(it.id) && it.type in setOf("paragraph", "heading", "bullet", "numbered", "task") && it.text.length <= 20000 && '\u0000' !in it.text) { "Invalid Notes block" }
            it.sourceRefs?.let { refs -> require(refs.size in 1..20000 && refs.distinct().size == refs.size && refs.all(id::matches)) { "Invalid source references" } }
            it.sourceInkRegionIds?.let { refs -> require(refs.size in 1..2000 && refs.distinct().size == refs.size && refs.all(id::matches)) { "Invalid source ink region references" } }
            require(if (it.type == "task") it.checked != null else it.checked == null) { "Invalid task state" }
            val text = it.text
            var previousEnd = 0
            it.styles?.let { styles ->
                require(styles.size <= 2000) { "Too many inline styles" }
                styles.forEach { style ->
                    require(style.bold == true || style.italic == true || style.strike == true) { "Inline style needs a supported mark" }
                    require(style.start >= previousEnd && style.start < style.end && style.end <= text.length) { "Invalid or overlapping inline style range" }
                    fun splitsSurrogate(index: Int) = index > 0 && index < text.length &&
                        Character.isHighSurrogate(text[index - 1]) && Character.isLowSurrogate(text[index])
                    require(!splitsSurrogate(style.start) && !splitsSurrogate(style.end)) { "Inline style splits a character" }
                    previousEnd = style.end
                }
            }
        }
    }
    fun decodeProposal(raw: String): NotesProposal {
        require(raw.toByteArray(Charsets.UTF_8).size <= 80_000)
        return json.decodeFromString<NotesProposal>(raw).validate()
    }
    fun read(file: File): NotesDocument {
        require(file.length() in 1..MAX_BYTES) { "Notes bundle is too large or empty" }
        return ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            require(entries.size in 2..130 && entries.map { it.name }.distinct().size == entries.size) { "Invalid bundle entries" }
            require(entries.all { !it.isDirectory && (it.name == "manifest.json" || path.matches(it.name)) }) { "Unsafe bundle path" }
            val manifestEntry = zip.getEntry("manifest.json") ?: error("Missing Notes manifest")
            val raw = zip.getInputStream(manifestEntry).use { readBounded(it, 1024 * 1024) }.toString(Charsets.UTF_8)
            val doc = json.decodeFromString<NotesDocument>(raw)
            require(doc.schema == "kalamos.notes-document/v1") { "Unsupported Notes document" }
            uuid(doc.documentId)
            require(hash.matches(doc.revisionId)) { "Invalid Notes revision" }
            validateBlocks(doc.title, doc.blocks)
            require((doc.instruction?.length ?: 0) <= 10000) { "Notes instruction too long" }
            require(doc.pages.size in 1..32 && doc.pages.map { it.id }.distinct().size == doc.pages.size) { "Invalid Notes pages" }
            doc.pages.forEach { page -> page.imageFrame?.let { frame ->
                require(page.previewFile!=null)
                listOf("x","y","width","height","pixelWidth","pixelHeight").forEach { k ->
                    val v=frame[k]?.jsonPrimitive?.doubleOrNull
                    require(v!=null && v.isFinite() && (k in setOf("x","y") || v>0)) { "Invalid page image frame" }
                }
            } }
            doc.thumbnailFile?.let { require(path.matches(it) && it.endsWith(".jpg")) { "Invalid Notes thumbnail" } }
            require(doc.inkRegions.size <= 2000 && doc.inkRegions.map { it.id }.distinct().size == doc.inkRegions.size) { "Invalid ink regions" }
            doc.inkRegions.forEach { region ->
                require(id.matches(region.id) && id.matches(region.runId) && doc.pages.any { it.id == region.pageId } &&
                    region.strokeIds.isNotEmpty() && region.strokeIds.size <= 20000 && region.strokeIds.distinct().size == region.strokeIds.size && region.strokeIds.all(id::matches)) { "Invalid ink region" }
                listOf("x", "y", "width", "height").forEach { key ->
                    val value = region.bounds[key]?.jsonPrimitive?.doubleOrNull
                    require(value != null && value.isFinite() && (key !in setOf("width", "height") || value > 0)) { "Invalid ink region bounds" }
                }
            }
            val referenced = doc.pages.flatMap {
                require(id.matches(it.id) && it.sourceFile.endsWith(".md") && it.sidecarFile.endsWith(".json")) { "Invalid page" }
                listOfNotNull(it.sourceFile, it.sidecarFile, it.previewFile)
            } + listOfNotNull(doc.thumbnailFile)
            require(referenced.distinct().size == referenced.size && referenced.toSet() == doc.checksums.keys) { "Invalid Notes file references" }
            require(entries.map { it.name }.toSet() == doc.checksums.keys + "manifest.json") { "Unaccounted Notes files" }
            var total = raw.toByteArray(Charsets.UTF_8).size.toLong()
            doc.checksums.forEach { (name, expected) ->
                require(path.matches(name) && hash.matches(expected)) { "Invalid Notes checksum" }
                val digest = MessageDigest.getInstance("SHA-256")
                zip.getInputStream(zip.getEntry(name)).use { input ->
                    val buffer = ByteArray(32768)
                    while (true) {
                        val size = input.read(buffer); if (size < 0) break
                        total += size; require(total <= MAX_BYTES) { "Expanded Notes bundle is too large" }
                        digest.update(buffer, 0, size)
                    }
                }
                require(digest.digest().joinToString("") { "%02x".format(it) } == expected) { "Notes checksum mismatch" }
            }
            doc
        }
    }
    private fun readBounded(input: java.io.InputStream, max: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) { val n = input.read(buffer); if (n < 0) break; require(output.size() + n <= max) { "Manifest too large" }; output.write(buffer, 0, n) }
        return output.toByteArray()
    }
}
