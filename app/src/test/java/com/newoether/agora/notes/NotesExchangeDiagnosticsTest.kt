package com.newoether.agora.notes
import com.newoether.agora.model.*
import kotlinx.serialization.json.*
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
class NotesExchangeDiagnosticsTest {
 @Test fun selectedResultIncludesExposedThoughtsAndToolsWithoutOpaqueStateOrOtherMessages() {
  val p=NotesProposal(proposalId=UUID.randomUUID().toString(),title="Note",blocks=listOf(NotesBlock("a","paragraph","Hello")))
  val selected=ChatMessage(text="Finished",participant=Participant.MODEL,thoughts="Need the matching region",
   segments=listOf(MessageSegment(type="thought",content="Check image coordinates",signature="opaque-state"),
    MessageSegment(type="tool",toolName="replace_notes_handwriting",toolArgs="arguments",toolStructuredResult=p.encode())))
  val other=ChatMessage(text="Unrelated conversation text",participant=Participant.MODEL)
  val trace=NotesExchangeDiagnostics.resultTrace(listOf(other,selected),p.proposalId)!!
  assertTrue(trace.contains("Need the matching region"));assertTrue(trace.contains("Check image coordinates"));assertTrue(trace.contains("arguments"))
  assertFalse(trace.contains("opaque-state"));assertFalse(trace.contains("Unrelated conversation text"))
  assertNull(NotesExchangeDiagnostics.resultTrace(listOf(other),p.proposalId))
 }
 @Test fun overLimitResultDoesNotCreateAnUnboundedDiagnosticTransfer() {
  val p=NotesProposal(proposalId=UUID.randomUUID().toString(),title="Note",blocks=listOf(NotesBlock("a","paragraph","Hello")))
  val selected=ChatMessage(text="x".repeat(128_001),participant=Participant.MODEL,
   segments=listOf(MessageSegment(type="tool",toolStructuredResult=p.encode())))
  assertNull(NotesExchangeDiagnostics.resultTrace(listOf(selected),p.proposalId))
 }
}
