package com.newoether.agora.notes

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/** Private, bounded uncommitted recognition. Never a saved notebook or an apply receipt. */
internal class NotesRecognitionStore(context: Context) {
    private val directory = File(context.filesDir, "notes-recognition")
    @Serializable private data class Record(val ownerId: String, val createdAt: Long, val proposal: NotesProposal)

    fun save(proposal: NotesProposal, ownerId: String?) = synchronized(lock) {
        require(!ownerId.isNullOrBlank()) { "Staged recognition requires a conversation" }
        proposal.validate()
        require(proposal.operation == "replace_ink")
        directory.mkdirs()
        val now = System.currentTimeMillis()
        directory.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }
            ?.forEachIndexed { index, file -> if (index >= 23 || now-file.lastModified() > retentionMs) file.delete() }
        val atomic = AtomicFile(File(directory, "${proposal.proposalId}.json"))
        val stream = atomic.startWrite()
        try {
            stream.write(NotesDocumentFormat.wireJson.encodeToString(Record(ownerId, now, proposal)).toByteArray())
            atomic.finishWrite(stream)
        } catch (error: Throwable) { atomic.failWrite(stream); throw error }
    }

    fun load(id: String, ownerId: String?): NotesProposal = synchronized(lock) {
        NotesDocumentFormat.uuid(id)
        require(!ownerId.isNullOrBlank()) { "Staged recognition requires a conversation" }
        val file = File(directory, "$id.json")
        require(file.exists() && file.length() in 1..100_000) { "Recognition is unavailable; attach the note again" }
        val record = NotesDocumentFormat.json.decodeFromString<Record>(AtomicFile(file).openRead().bufferedReader().use { it.readText() })
        require(record.ownerId == ownerId && System.currentTimeMillis()-record.createdAt in 0..retentionMs) {
            "Recognition does not belong to this conversation or has expired"
        }
        require(record.proposal.proposalId == id)
        record.proposal.validate()
    }
    companion object {
        private val lock = Any()
        private const val retentionMs = 7L*24*60*60*1000
    }
}
