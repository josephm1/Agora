package com.newoether.agora.viewmodel

import com.newoether.agora.model.AttachmentImportState
import com.newoether.agora.model.SelectedAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessagePayloadBuilderTest {
    private val builder = MessagePayloadBuilder()
    @Test fun cachedNotesInstructionsRefreshWithoutLeakingMetadataIntoTheProviderPrompt() {
        val payload=builder.buildComposerPayload(listOf(SelectedAttachment(
            uri="file:///ready/note.zip",type="file",localPath="/ready/note.zip",
            fileName="Private notebook title.notes.zip",mimeType=com.newoether.agora.notes.NotesDocumentFormat.MIME,
            preparedText="Private notebook title and duplicate typed text",notesMarkdownText="Old instructions and title",
            preRenderedPaths=listOf("/ready/page.jpg"))))
        val item=payload.attachmentMeta!!.items.single()
        assertEquals(com.newoether.agora.notes.NotesMarkdownPrompt.TEXT,item.textContent)
        // Presentation still retains the user's filename; only provider projection hides it.
        assertEquals("Private notebook title.notes.zip",item.fileName)
        val entity=com.newoether.agora.data.local.MessageEntity(id="user",conversationId="chat",text="Make it nicer",
            images=payload.allImages,attachmentMeta=kotlinx.serialization.json.Json.encodeToString(com.newoether.agora.model.AttachmentMeta.serializer(),payload.attachmentMeta),
            participant=com.newoether.agora.model.Participant.USER,status=com.newoether.agora.model.MessageStatus.SUCCESS,timestamp=1L,runId="run")
        val projected=projectProviderMessages(listOf(entity),false).single()
        assertEquals("Make it nicer\n\n--- File: Notes page ---\n"+com.newoether.agora.notes.NotesMarkdownPrompt.TEXT,projected.text)
        assertEquals(listOf("/ready/page.jpg"),projected.images)
    }

    @Test fun displayOnlyNotesCoverNeverBecomesAnAiImage() {
        val payload = builder.buildComposerPayload(listOf(SelectedAttachment(
            uri = "file:///ready/note.zip", type = "file", localPath = "/ready/note.zip",
            mimeType = com.newoether.agora.notes.NotesDocumentFormat.MIME, preparedText = "compact notes",
            notesThumbnailPath = "/ready/cover.jpg", preRenderedPaths = listOf("/ready/page.jpg"))))
        assertEquals(listOf("/ready/page.jpg"), payload.allImages)
        assertEquals("/ready/cover.jpg", payload.attachmentMeta!!.items.single().notesThumbnailPath)
        val textOnly = builder.buildComposerPayload(listOf(SelectedAttachment(
            uri = "file:///ready/note.zip", type = "file", localPath = "/ready/note.zip",
            mimeType = com.newoether.agora.notes.NotesDocumentFormat.MIME, preparedText = "compact notes",
            notesThumbnailPath = "/ready/cover.jpg")))
        assertEquals(emptyList<String>(), textOnly.allImages)
    }

    @Test
    fun mixedReadyArtifactsPreserveFrozenComposerOrderAndExactIndices() {
        val attachments = listOf(
            attachment("image-a", "image", localPath = "/ready/image-a.jpg"),
            attachment(
                "file",
                "file",
                localPath = "/ready/notes.txt",
                preparedText = "prepared text",
            ),
            attachment(
                "video",
                "video",
                localPath = "/ready/video.mp4",
                processedFrames = listOf("/ready/frame-1.jpg", "/ready/frame-2.jpg"),
            ),
            attachment(
                "pdf",
                "pdf",
                localPath = "/ready/document.pdf",
                preRenderedPaths = listOf("/ready/page-1.jpg", "/ready/page-2.jpg"),
            ),
            attachment("image-b", "image", localPath = "/ready/image-b.jpg"),
            attachment(
                "failed",
                "image",
                localPath = "/must-not-send.jpg",
                state = AttachmentImportState.FAILED,
            ),
            attachment(
                "unavailable",
                "image",
                localPath = "/must-not-send-either.jpg",
                unavailable = true,
            ),
        )

        val payload = builder.buildComposerPayload(attachments)
        val items = requireNotNull(payload.attachmentMeta).items

        assertEquals(
            listOf(
                "/ready/image-a.jpg",
                "/ready/frame-1.jpg",
                "/ready/frame-2.jpg",
                "/ready/page-1.jpg",
                "/ready/page-2.jpg",
                "/ready/image-b.jpg",
            ),
            payload.allImages,
        )
        assertEquals(listOf("image", "file", "video", "pdf", "image"), items.map { it.type })
        assertEquals(listOf(0, null, 1, 3, 5), items.map { it.imageIndex })
        assertEquals(listOf(null, null, 2, 2, null), items.map { it.pageCount })
        assertEquals("prepared text", items[1].textContent)
    }

    @Test
    fun duplicateArtifactPathsKeepDistinctOccurrenceIndices() {
        val payload = builder.buildComposerPayload(
            listOf(
                attachment("first", "image", localPath = "/ready/duplicate.jpg"),
                attachment("second", "image", localPath = "/ready/duplicate.jpg"),
            ),
        )
        val items = requireNotNull(payload.attachmentMeta).items

        assertEquals(
            listOf("/ready/duplicate.jpg", "/ready/duplicate.jpg"),
            payload.allImages,
        )
        assertEquals(listOf(0, 1), items.map { it.imageIndex })
    }

    @Test
    fun incompleteReadyArtifactsAreDroppedWithoutPickerUriFallback() {
        val payload = builder.buildComposerPayload(
            listOf(
                attachment("image", "image"),
                attachment("video", "video", localPath = "/ready/video.mp4"),
                attachment("pdf", "pdf", localPath = "/ready/document.pdf"),
                attachment("file", "file", localPath = "/ready/file.txt"),
            ),
        )

        assertEquals(emptyList<String>(), payload.allImages)
        assertNull(payload.attachmentMeta)
    }

    private fun attachment(
        id: String,
        type: String,
        localPath: String? = null,
        processedFrames: List<String>? = null,
        preRenderedPaths: List<String>? = null,
        preparedText: String? = null,
        state: AttachmentImportState = AttachmentImportState.READY,
        unavailable: Boolean = false,
    ) = SelectedAttachment(
        localId = id,
        uri = "content://picker/$id",
        type = type,
        fileName = "$id.bin",
        localPath = localPath,
        processedFrames = processedFrames,
        preRenderedPaths = preRenderedPaths,
        preparedText = preparedText,
        importState = state,
        unavailable = unavailable,
    )
}
