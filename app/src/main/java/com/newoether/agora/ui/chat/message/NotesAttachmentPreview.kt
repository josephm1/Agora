package com.newoether.agora.ui.chat.message

import com.newoether.agora.model.AttachmentItem
import com.newoether.agora.notes.NotesDocumentFormat

/** Display cover is independent of provider images; old records use their first saved page. */
internal fun notesAttachmentPreviewPath(item: AttachmentItem?, images: List<String>): String? {
    if (item?.mimeType != NotesDocumentFormat.MIME) return null
    return item.notesThumbnailPath?.takeIf { it.isNotBlank() }
        ?: item.imageIndex?.let(images::getOrNull)?.takeIf { it.isNotBlank() }
}
