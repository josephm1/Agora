package com.newoether.agora.ui.chat.message

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.newoether.agora.model.ChatMessage
import com.newoether.agora.model.ToolExecutionStates
import com.newoether.agora.notes.*
import com.newoether.agora.tool.NotesProposalToolProvider
import com.newoether.agora.ui.chat.LocalNotesOpen
import java.util.UUID

@Composable
internal fun NotesDocumentCards(message: ChatMessage) {
    if(!com.newoether.agora.ui.chat.LocalNotesEnabled.current)return
    val open = LocalNotesOpen.current
    val hasNotesResult = message.segments.orEmpty().any { segment ->
        segment.type in setOf("tool", "notes_result") && NotesProposalToolProvider.isNotesTool(segment.toolName) &&
            segment.toolState == ToolExecutionStates.SUCCEEDED && segment.toolStructuredResult != null
    }
    message.segments.orEmpty().forEach { segment ->
        if (segment.type !in setOf("tool", "notes_result") || !NotesProposalToolProvider.isNotesTool(segment.toolName) || segment.toolState != ToolExecutionStates.SUCCEEDED) return@forEach
        val proposal = remember(segment.toolStructuredResult) { segment.toolStructuredResult?.let { runCatching { NotesDocumentFormat.decodeProposal(it) }.getOrNull() } }
        if (proposal != null) OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(proposal.title, style = MaterialTheme.typography.titleMedium)
                Text(if(proposal.markdown != null) "Processed Markdown ready for Notes." else
                    "Processed Notes document · ${proposal.blocks.size} editable blocks.")
                TextButton(onClick = { open("apply", proposal.copy(sourceMessageId=message.id), null, null, null) }) { Text("Open in Notes") }
            }
        }
    }
    if (!hasNotesResult && message.status == com.newoether.agora.model.MessageStatus.SUCCESS && message.text.isNotBlank()) {
        val proposal = remember(message.text) { runCatching { responseNotesProposal(message.text) }.getOrNull() }
        if (proposal != null) TextButton(onClick = { open("apply", proposal.copy(sourceMessageId=message.id), null, null, null) }) { Text(if(proposal.documentId==null)"Create note in Notes" else "Open in Notes") }
    }
}
internal fun responseNotesProposal(text:String):NotesProposal {
    val trimmed=text.trim()
    val json=if(trimmed.startsWith("```json\n")&&trimmed.endsWith("```"))trimmed.substringAfter('\n').removeSuffix("```").trim() else trimmed.takeIf{it.startsWith("{")&&it.endsWith("}")}
    if(json!=null)return com.newoether.agora.tool.parseNotesProposal(json)
    return plainNotesProposal(text)
}
internal fun plainNotesProposal(text: String): NotesProposal {
    require(text.length <= 60000) { "Select a shorter answer to create a note" }
    val blocks = text.lines().filter { it.isNotBlank() }.map { line ->
        val task = Regex("^\\s*[-*] \\[([ xX])\\] (.*)$").matchEntire(line)
        val heading = Regex("^#{1,6} (.*)$").matchEntire(line)
        val bullet = Regex("^\\s*[-*+] (.*)$").matchEntire(line)
        val numbered = Regex("^\\s*\\d+[.)] (.*)$").matchEntire(line)
        NotesBlock(UUID.randomUUID().toString(), when { task != null -> "task"; heading != null -> "heading"; bullet != null -> "bullet"; numbered != null -> "numbered"; else -> "paragraph" },
            task?.groupValues?.get(2) ?: heading?.groupValues?.get(1) ?: bullet?.groupValues?.get(1) ?: numbered?.groupValues?.get(1) ?: line,
            task?.groupValues?.get(1)?.let { it != " " })
    }
    return NotesProposal(proposalId = UUID.randomUUID().toString(), title = blocks.firstOrNull()?.text?.take(200) ?: "New note", blocks = blocks).validate()
}
