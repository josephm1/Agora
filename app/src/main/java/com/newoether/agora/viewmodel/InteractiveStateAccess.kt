package com.newoether.agora.viewmodel

import com.newoether.agora.model.InteractiveAnswer
import com.newoether.agora.model.interactiveAnswer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement

/** The native card supplies the conversation identity; scripts cannot choose another owner. */
internal suspend fun ChatViewModel.accessInteractiveState(conversation: String, message: String, call: String,
    answer: InteractiveAnswer, method: String, args: JsonElement?): JsonElement {
    check(verifyInteractiveAnswer(conversation, message, call, answer)) { "This interface is no longer active" }
    val result = withContext(Dispatchers.IO) {
        convRepo.accessInteractiveState(conversation, method, args) {
            hasInteractiveAnswer(conversation, message) && messages.value.any { row ->
                // The persisted artifact was verified above. Selected topology may omit its
                // heavy payload; when present, a different payload still revokes this viewer.
                row.id == message && (row.segments == null || row.segments.any {
                    it.toolCallId == call && it.interactiveAnswer() == answer
                })
            }
        }
    }
    check(verifyInteractiveAnswer(conversation, message, call, answer)) { "This interface is no longer active" }
    return result
}
