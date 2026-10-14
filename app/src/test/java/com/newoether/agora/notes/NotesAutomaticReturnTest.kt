package com.newoether.agora.notes

import com.newoether.agora.model.*
import com.newoether.agora.tool.NotesProposalToolProvider
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class NotesAutomaticReturnTest {
    private fun proposal() = NotesProposal(proposalId = UUID.randomUUID().toString(), title = "Tasks",
        blocks = listOf(NotesBlock("one", "task", "Milk", false)))
    private fun message(proposal: NotesProposal, status: MessageStatus = MessageStatus.SUCCESS,
                        toolState: String = ToolExecutionStates.SUCCEEDED) =
        ChatMessage(id = "answer", text = "", participant = Participant.MODEL, status = status,
            segments = listOf(MessageSegment(type = "tool", toolName = NotesProposalToolProvider.NAME,
                toolState = toolState, toolStructuredResult = proposal.encode())))

    @Test fun `old results and recomposition do not launch`() {
        val tracker = NotesAutomaticReturn()
        val old = message(proposal())
        assertTrue(tracker.update(listOf(old), false).isEmpty())
        assertTrue(tracker.update(listOf(old), true).isEmpty())
        assertTrue(tracker.update(listOf(old), false).isEmpty())
    }
    @Test fun `live result waits for successful terminal message and returns once`() {
        val tracker = NotesAutomaticReturn()
        val proposal = proposal()
        tracker.update(emptyList(), false)
        assertTrue(tracker.update(listOf(message(proposal, MessageStatus.TOOL_CALLING)), true).isEmpty())
        assertEquals(listOf(proposal.copy(sourceMessageId="answer")), tracker.update(listOf(message(proposal)), false))
        assertTrue(tracker.update(listOf(message(proposal)), false).isEmpty())
    }
    @Test fun `result first published in terminal snapshot is not missed`() {
        val tracker = NotesAutomaticReturn()
        val proposal = proposal()
        tracker.update(emptyList(), false)
        tracker.update(emptyList(), true)
        assertEquals(listOf(proposal.copy(sourceMessageId="answer")), tracker.update(listOf(message(proposal)), false))
    }
    @Test fun `failed stopped and non Notes tools do not return`() {
        listOf(MessageStatus.ERROR, MessageStatus.STOPPED).forEach { status ->
            val tracker = NotesAutomaticReturn()
            val proposal = proposal()
            tracker.update(emptyList(), false)
            tracker.update(listOf(message(proposal, MessageStatus.TOOL_CALLING)), true)
            assertTrue(tracker.update(listOf(message(proposal, status)), false).isEmpty())
        }
        val tracker = NotesAutomaticReturn()
        tracker.update(emptyList(), false)
        tracker.update(emptyList(), true)
        val unrelated = message(proposal()).let { it.copy(segments = it.segments!!.map { segment -> segment.copy(toolName = "other") }) }
        assertTrue(tracker.update(listOf(unrelated), false).isEmpty())
    }
    @Test fun `new tracker after activity recreation ignores completed receipt`() {
        val completed = message(proposal())
        assertTrue(NotesAutomaticReturn().update(listOf(completed), false).isEmpty())
    }
    @Test fun `surrounding chat Markdown never becomes notebook content`() {
        val documentId=UUID.randomUUID().toString()
        val returned=NotesProposal(proposalId=UUID.randomUUID().toString(),documentId=documentId,
            baseRevisionId="a".repeat(64),title="Notebook",targetPageIds=listOf("first"),operation="replace_ink",
            markdown="- [ ] Milk",sourceInkRegions=listOf(NotesInkRegion("ink-one","first","run-one",
                kotlinx.serialization.json.buildJsonObject { put("x",kotlinx.serialization.json.JsonPrimitive(0));put("y",kotlinx.serialization.json.JsonPrimitive(0));put("width",kotlinx.serialization.json.JsonPrimitive(20));put("height",kotlinx.serialization.json.JsonPrimitive(20)) },listOf("stroke-one")))).validate()
        val answer=message(returned).copy(text="# Explanation\nI formatted your list.\n- This is chat commentary.")
        val tracker=NotesAutomaticReturn();tracker.update(emptyList(),false);tracker.update(emptyList(),true)
        assertEquals("- [ ] Milk",tracker.update(listOf(answer),false).single().markdown)
        val chatOnly=answer.copy(segments=emptyList())
        val other=NotesAutomaticReturn();other.update(emptyList(),false);other.update(emptyList(),true)
        assertTrue(other.update(listOf(chatOnly),false).isEmpty())
    }

}
