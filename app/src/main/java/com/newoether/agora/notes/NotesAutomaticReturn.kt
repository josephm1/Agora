package com.newoether.agora.notes

import com.newoether.agora.model.ChatMessage
import com.newoether.agora.model.MessageStatus
import com.newoether.agora.model.Participant
import com.newoether.agora.model.ToolExecutionStates
import com.newoether.agora.tool.NotesProposalToolProvider

/** Watches only the live, visible conversation. Historical successes are never launch events. */
internal class NotesAutomaticReturn {
    private val seen = mutableSetOf<String>()
    private val pending = linkedMapOf<String, NotesProposal>()
    private var initialized = false
    private var liveRunObserved = false

    fun update(messages: List<ChatMessage>, generating: Boolean): List<NotesProposal> {
        val results = messages.filter { it.participant == Participant.MODEL }.flatMap { message ->
            message.segments.orEmpty().mapNotNull { segment ->
                if (segment.type !in setOf("tool", "notes_result") || !NotesProposalToolProvider.isNotesTool(segment.toolName) ||
                    segment.toolState != ToolExecutionStates.SUCCEEDED) return@mapNotNull null
                segment.toolStructuredResult?.let { raw ->
                    runCatching { NotesDocumentFormat.decodeProposal(raw) }.getOrNull()?.takeIf { it.backgroundRequestId==null }
                }?.let { message to it }
            }
        }
        if (!initialized) {
            initialized = true
            // An already-completed answer is history even when a different run is active.
            results.filter { it.first.status == MessageStatus.SUCCESS }.forEach { seen += it.second.proposalId }
        }
        if (generating) liveRunObserved = true
        results.forEach { (message, proposal) ->
            if (proposal.proposalId !in seen && liveRunObserved) {
                if (message.status != MessageStatus.ERROR && message.status != MessageStatus.STOPPED)
                    pending[proposal.proposalId] = proposal
            }
        }
        if (generating) return emptyList()
        val completed = results.filter { it.first.status == MessageStatus.SUCCESS && it.second.proposalId in pending }
            .map { it.second.copy(sourceMessageId=it.first.id) }
        seen += pending.keys
        pending.clear()
        liveRunObserved = false
        return completed
    }
}
