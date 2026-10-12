package com.newoether.agora.ui.chat.message

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.model.ChatMessage
import com.newoether.agora.model.InteractiveAnswer
import com.newoether.agora.model.interactiveAnswer

internal val LocalOpenInteractiveAnswer = staticCompositionLocalOf<(String, String, InteractiveAnswer) -> Unit> {
    { _, _, _ -> }
}

@Composable
internal fun InteractiveAnswerCards(message: ChatMessage) {
    val open = LocalOpenInteractiveAnswer.current
    message.segments.orEmpty().forEach { segment ->
        val callId = segment.toolCallId ?: return@forEach
        if (segment.type != "tool" || segment.toolName != InteractiveAnswer.TOOL_NAME ||
            segment.toolState != com.newoether.agora.model.ToolExecutionStates.SUCCEEDED) return@forEach
        key(message.id, callId) {
            // Growing answer text must not repeatedly decode an unchanged executable payload.
            val answer = remember(segment.toolStructuredResult) { segment.interactiveAnswer() }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(answer?.title ?: stringResource(R.string.interactive_answers), style = MaterialTheme.typography.titleMedium)
                    Text(answer?.description ?: stringResource(R.string.interactive_answer_unavailable), style = MaterialTheme.typography.bodyMedium)
                    if (answer != null) {
                        Button(onClick = { open(message.id, callId, answer) }) {
                            Text(stringResource(R.string.interactive_answer_open))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
