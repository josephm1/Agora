package com.newoether.agora.viewmodel

import com.newoether.agora.model.SelectedAttachment
import com.newoether.agora.notes.NotesDocument

internal data class HeadlessAttachmentInput(val payload:MessagePayloadBuilder.MessagePayload, val notesDocument:NotesDocument?)
/** The headless engine stores and sends the same settled attachment artifacts as the composer. */
internal fun headlessAttachmentInput(attachments:List<SelectedAttachment>) = HeadlessAttachmentInput(
    MessagePayloadBuilder().buildComposerPayload(attachments),captureNotesMarkdownDocument(attachments),
)
