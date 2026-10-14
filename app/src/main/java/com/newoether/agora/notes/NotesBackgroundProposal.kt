package com.newoether.agora.notes

import com.newoether.agora.model.MessageSegment
import com.newoether.agora.model.ToolExecutionStates
import com.newoether.agora.tool.NotesProposalToolProvider

internal fun notesBackgroundProposal(request:NotesBackgroundRequest, segments:List<MessageSegment>):NotesProposal? =
    segments.firstNotNullOfOrNull { segment ->
        if(segment.type !in setOf("tool","notes_result") || !NotesProposalToolProvider.isNotesTool(segment.toolName) ||
            segment.toolState!=ToolExecutionStates.SUCCEEDED) null else
            segment.toolStructuredResult?.let { runCatching { NotesDocumentFormat.decodeProposal(it) }.getOrNull() }
                ?.takeIf { it.documentId==request.documentId && it.baseRevisionId==request.revisionId &&
                    it.backgroundRequestId==request.requestId }
    }
