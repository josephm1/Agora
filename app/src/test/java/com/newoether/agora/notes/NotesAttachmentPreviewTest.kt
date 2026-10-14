package com.newoether.agora.notes

import com.newoether.agora.model.AttachmentItem
import com.newoether.agora.ui.chat.message.notesAttachmentPreviewPath
import org.junit.Assert.*
import org.junit.Test

class NotesAttachmentPreviewTest {
    @Test fun notesCoverReusesThumbnailAndOldRecordsFallBackToRealPage() {
        val note=AttachmentItem(type="file",mimeType=NotesDocumentFormat.MIME,imageIndex=0,notesThumbnailPath="cover.jpg")
        assertEquals("cover.jpg",notesAttachmentPreviewPath(note,listOf("page.jpg")))
        assertEquals("page.jpg",notesAttachmentPreviewPath(note.copy(notesThumbnailPath=null),listOf("page.jpg")))
        assertNull(notesAttachmentPreviewPath(note.copy(mimeType="application/zip"),listOf("page.jpg")))
        assertNull(notesAttachmentPreviewPath(note.copy(notesThumbnailPath=null,imageIndex=8),listOf("page.jpg")))
    }
}
