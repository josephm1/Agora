package com.newoether.agora.notes

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.newoether.agora.model.SelectedAttachment
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Durable handoff receipts, not document state. A request always returns to its captured draft. */
@Serializable
data class NotesExchangeRequest(
    val requestId: String, val ownerId: String?, val operation: String,
    val documentId: String? = null, val baseRevisionId: String? = null,
    val linkedOwnerId: String? = null, val ready: Boolean = false, val consumed: Boolean = false,
    val launched: Boolean = false, val cancelled: Boolean = false,
    val documentRequests: List<String> = emptyList(),
)
class NotesExchangeStore(private val context: Context) {
    /** At most one automatic navigation per proposal, including activity/process recreation.
     * A failed launch remains available through the explicit Open in Notes recovery action. */
    fun claimAutomaticReturn(ownerId: String, proposalId: String): Boolean = synchronized(launchLock) {
        NotesDocumentFormat.uuid(proposalId)
        val receipts = context.getSharedPreferences("notes-automatic-return", Context.MODE_PRIVATE)
        val key = "$ownerId:$proposalId"
        if (receipts.contains(key)) false else {
            check(receipts.edit().putBoolean(key, true).commit()) { "Could not save Notes return receipt" }
            true
        }
    }
    private val directory get() = File(context.filesDir, "notes-exchange").apply { mkdirs() }
    private val activeFile get() = AtomicFile(File(directory, "active-request"))
    /** Called only after explicit Notes entry. Never scans requests/owners on Agora startup. */
    fun reserve(request: NotesExchangeRequest): NotesExchangeRequest = synchronized(launchLock) {
        val active = runCatching { activeFile.openRead().bufferedReader().use { load(it.readText()) } }.getOrNull()
        if (active != null && !active.cancelled && !active.ready && !active.consumed) {
            if (NotesExchangeActivity.isLive(active.requestId) || !active.launched && reservedInProcess == active.requestId) return@synchronized active
            // No result registry/editor task remains in this process. Retire this abandoned
            // receipt; the saved Notes document is retained and may be attached explicitly.
            save(active.copy(cancelled = true))
        }
        save(request)
        reservedInProcess = request.requestId
        val stream = activeFile.startWrite()
        try { stream.write(request.requestId.toByteArray()); activeFile.finishWrite(stream) }
        catch (error: Throwable) { activeFile.failWrite(stream); throw error }
        request
    }
    fun file(id: String): File { NotesDocumentFormat.uuid(id); return File(directory, "$id.zip") }
    fun load(id: String): NotesExchangeRequest? {
        NotesDocumentFormat.uuid(id)
        val source = File(directory, "$id.json")
        if (!source.exists()) return null
        return Json.decodeFromString<NotesExchangeRequest>(AtomicFile(source).openRead().bufferedReader().use { it.readText() })
    }
    fun save(request: NotesExchangeRequest) {
        NotesDocumentFormat.uuid(request.requestId)
        val atomic = AtomicFile(File(directory, "${request.requestId}.json"))
        val stream = atomic.startWrite()
        try { stream.write(Json.encodeToString(request).toByteArray()); atomic.finishWrite(stream) }
        catch (error: Throwable) { atomic.failWrite(stream); throw error }
    }
    fun receive(request: NotesExchangeRequest, uri: Uri): NotesExchangeRequest {
        check(!request.cancelled) { "This Notes request was cancelled" }
        require(uri.scheme == "content") { "Notes must return a content URI" }
        load(request.requestId)?.takeIf { it.ready }?.let { return it }
        val target = file(request.requestId)
        val part = File(directory, "${request.requestId}.part")
        try {
            context.contentResolver.openInputStream(uri).use { source ->
                requireNotNull(source) { "Notes document is unavailable" }
                part.outputStream().use { output ->
                    val buffer = ByteArray(32768); var size = 0L
                    while (true) { val n = source.read(buffer); if (n < 0) break; size += n
                        require(size <= NotesDocumentFormat.MAX_BYTES) { "Notes document exceeds 100 MiB" }; output.write(buffer, 0, n) }
                }
            }
            val document = NotesDocumentFormat.read(part)
            require(request.documentId == null || request.documentId == document.documentId) { "Notes returned a different document" }
            check(part.renameTo(target)) { "Could not save Notes document" }
            return request.copy(ready = true, documentId = document.documentId).also(::save)
        } finally { part.delete() }
    }
    /** Stage/validate all immutable documents before publishing the parent as ready. Child
     * receipts are deterministic, so a retry does not create new attachment identities. */
    fun receiveMany(request:NotesExchangeRequest, uris:List<Uri>):NotesExchangeRequest {
        require(request.operation=="pick" && uris.size in 1..32)
        check(!request.cancelled)
        load(request.requestId)?.takeIf { it.ready }?.let { return it }
        require(uris.distinct().size==uris.size)
        val children=uris.map { uri ->
            val id=java.util.UUID.nameUUIDFromBytes((request.requestId+":selected:"+uri.toString()).toByteArray()).toString()
            val child=receive(NotesExchangeRequest(id,request.ownerId,"pick"),uri)
            child
        }
        require(children.map { it.documentId }.distinct().size==children.size) { "Notes returned a duplicate document" }
        require(children.sumOf { file(it.requestId).length() }<=NotesDocumentFormat.MAX_BYTES) { "Selected notes exceed 100 MiB" }
        return request.copy(ready=true,documentRequests=children.map { it.requestId }).also(::save)
    }
    fun attachments(request:NotesExchangeRequest):List<SelectedAttachment> {
        check(request.ready && !request.consumed)
        return if(request.documentRequests.isEmpty())listOf(attachment(request)) else request.documentRequests.map { id ->
            val child=requireNotNull(load(id));require(child.ownerId==request.ownerId)
            attachment(child, preferMarkdown = false)
        }
    }
    companion object { private val launchLock = Any(); private var reservedInProcess: String? = null }
    /** Extract a checksum-validated cover for presentation, independently of model page images. */
    fun extractThumbnail(source: File, document: NotesDocument, attachmentId: String): String? {
        val name = document.thumbnailFile ?: return null
        NotesDocumentFormat.uuid(attachmentId)
        val output = File(directory, "$attachmentId-cover.jpg")
        val atomic = AtomicFile(output)
        val stream = atomic.startWrite()
        try {
            java.util.zip.ZipFile(source).use { zip ->
                zip.getInputStream(requireNotNull(zip.getEntry(name))).use { it.copyTo(stream) }
            }
            atomic.finishWrite(stream)
        } catch (error: Throwable) { atomic.failWrite(stream); throw error }
        return output.absolutePath
    }
    fun attachment(request: NotesExchangeRequest, preferMarkdown: Boolean = true): SelectedAttachment {
        check(request.ready && !request.consumed)
        val source = file(request.requestId)
        val document = NotesDocumentFormat.read(source)
        NotesExchangeDiagnostics.imported(context, document)
        val previews = java.util.zip.ZipFile(source).use { zip -> document.pages.mapNotNull { page ->
            page.previewFile?.let { name ->
                require(name.endsWith(".jpg")) { "Unsupported Notes preview" }
                val output = File(directory, "${request.requestId}-${page.id}.jpg")
                if (!output.exists()) {
                    val atomic = AtomicFile(output)
                    val stream = atomic.startWrite()
                    try { zip.getInputStream(zip.getEntry(name)).use { it.copyTo(stream) }; atomic.finishWrite(stream) }
                    catch (error: Throwable) { atomic.failWrite(stream); throw error }
                }
                output.absolutePath
            }
        } }
        return SelectedAttachment(localId = request.requestId, uri = Uri.fromFile(source).toString(), type = "file",
            fileName = NotesDocumentFormat.filename(document.title), mimeType = NotesDocumentFormat.MIME,
            notesDocumentId = document.documentId, notesRevisionId = document.revisionId,
            notesThumbnailPath = extractThumbnail(source, document, request.requestId),
            preRenderedPaths = previews.takeIf { it.isNotEmpty() },
            fileSize = source.length(), localPath = source.absolutePath, preparedText = document.contextText(preferMarkdown = false),
            notesMarkdownText = document.contextText().takeIf { preferMarkdown && document.isMarkdownInkPage() })
    }
}
