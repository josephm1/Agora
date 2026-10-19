package com.newoether.agora.viewmodel

import com.newoether.agora.model.AttachmentImportState
import com.newoether.agora.model.SelectedAttachment
import com.newoether.agora.notes.NotesDocument
import com.newoether.agora.notes.NotesDocumentFormat
import java.io.File
import kotlinx.serialization.encodeToString

/** Only the exact submitted attachment can grant a one-page Markdown edit target. */
internal fun captureNotesMarkdownDocument(attachments: List<SelectedAttachment>): NotesDocument? {
    val notes = attachments.filter { it.mimeType == NotesDocumentFormat.MIME }
    if (notes.size != 1) return null
    val attachment = notes.single()
    if (attachment.unavailable || attachment.importState != AttachmentImportState.READY || attachment.preRenderedPaths.isNullOrEmpty()) return null
    val path = attachment.localPath ?: return null
    val document = runCatching { NotesDocumentFormat.read(File(path)) }.getOrNull() ?: return null
    if (!document.isMarkdownInkPage() || attachment.notesDocumentId != document.documentId ||
        attachment.notesRevisionId != document.revisionId) return null
    // Serialization copies all nested collections; no draft-owned mutable lists survive admission.
    return NotesDocumentFormat.json.decodeFromString(NotesDocumentFormat.json.encodeToString(document))
}
