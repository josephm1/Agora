package com.newoether.agora.viewmodel

import com.newoether.agora.model.SelectedAttachment
import com.newoether.agora.util.AttachmentFiles
import java.io.File
import java.util.UUID

/** Selected source snapshots use the same owned files and message metadata as ordinary attachments. */
internal class TemplateReferenceAttachments(private val filesDirectory: () -> File) : AutoCloseable {
    private val owner = Any()
    private val paths = mutableListOf<String>()
    private val staged = mutableListOf<SelectedAttachment>()
    private var sendAttempted = false
    val attachments: List<SelectedAttachment> get() = staged.toList()

    /** Called on IO, after the caller has validated the source path and combined byte limit. */
    fun add(path: String, content: String) {
        val name = AttachmentFiles.sanitizeFileName(path.substringAfterLast('/'))
        val file = File(filesDirectory(), "att_${UUID.randomUUID()}_$name")
        AttachmentFiles.retainLivePath(owner, file.absolutePath)
        paths += file.absolutePath
        file.writeText(content, Charsets.UTF_8)
        staged += SelectedAttachment(uri = file.toURI().toString(), type = "file", fileName = name,
            mimeType = "text/markdown", fileSize = file.length(), localPath = file.absolutePath, preparedText = content)
    }

    fun handOffForSend() { sendAttempted = true }

    override fun close() {
        AttachmentFiles.releaseLivePaths(owner)
        // A Send can commit before cancellation is observed. Once attempted, leave ambiguous
        // files to the existing reference-aware orphan sweeper instead of risking a durable file.
        if (!sendAttempted) AttachmentFiles.deleteUnownedPaths(paths)
    }
}
