package com.newoether.agora.notes

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class NotesBackgroundRequest(val requestId:String, val documentId:String, val revisionId:String,
                                          val title:String, val state:String="queued", val error:String?=null) {
    val conversationId get() = UUID.nameUUIDFromBytes(("notes-background:"+requestId).toByteArray()).toString()
}
internal class NotesBackgroundStore(private val context:Context) {
    private val directory=File(context.filesDir,"notes-background").apply { mkdirs() }
    fun bundle(id:String):File { NotesDocumentFormat.uuid(id);return File(directory,"$id.zip") }
    fun load(id:String):NotesBackgroundRequest? {
        NotesDocumentFormat.uuid(id);val file=File(directory,"$id.json")
        return if(file.exists())Json.decodeFromString(AtomicFile(file).openRead().bufferedReader().use { it.readText() }) else null
    }
    fun save(request:NotesBackgroundRequest) = atomic(File(directory,"${request.requestId}.json"),Json.encodeToString(request))
    fun proposal(id:String):File { NotesDocumentFormat.uuid(id);return File(context.cacheDir,"shared/notes-background-$id.json") }
    fun saveProposal(id:String, proposal:NotesProposal) = atomic(proposal(id),proposal.validate().encode())
    fun stage(id:String,uri:Uri,documentId:String,revisionId:String):NotesBackgroundRequest = synchronized(lock) {
        NotesDocumentFormat.uuid(id)
        load(id)?.let { existing ->
            require(existing.documentId==documentId && existing.revisionId==revisionId) { "Request identity mismatch" }
            return@synchronized existing
        }
        val part=File(directory,"$id.part")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                part.outputStream().use { output ->
                    val bytes=ByteArray(32768);var size=0L
                    while(true) { val n=input.read(bytes);if(n<0)break;size+=n
                        require(size<=NotesDocumentFormat.MAX_BYTES) { "Notes bundle too large" };output.write(bytes,0,n)
                    }
                }
            }
            val doc=NotesDocumentFormat.read(part)
            require(doc.documentId==documentId && doc.revisionId==revisionId && doc.isMarkdownInkPage()) { "Expected the exact single handwriting page" }
            check(part.renameTo(bundle(id)))
            NotesBackgroundRequest(id,doc.documentId,doc.revisionId,doc.title).also(::save)
        } finally { part.delete() }
    }
    private fun atomic(file:File,text:String) {
        file.parentFile?.mkdirs();val atomic=AtomicFile(file);val stream=atomic.startWrite()
        try { stream.write(text.toByteArray());atomic.finishWrite(stream) }
        catch(error:Throwable) { atomic.failWrite(stream);throw error }
    }
    companion object { private val lock=Any() }
}
