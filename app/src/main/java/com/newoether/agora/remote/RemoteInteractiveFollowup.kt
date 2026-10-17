package com.newoether.agora.remote

import com.newoether.agora.model.InteractiveAnswer
import com.newoether.agora.model.interactiveAnswer

internal fun RemoteViewModel.hasInteractiveAnswer(
    owner: String, messageId: String, callId: String, answer: InteractiveAnswer,
): Boolean = state.value.owner == owner && cachedMessage(owner, messageId)?.segments.orEmpty().any {
    it.toolCallId == callId && it.interactiveAnswer() == answer
}

/** The existing sandbox confirms text first; revalidate its exact Remote owner and artifact. */
internal suspend fun RemoteViewModel.sendInteractiveFollowup(
    owner: String, messageId: String, callId: String, answer: InteractiveAnswer, text: String,
): Boolean {
    if (text.isBlank() || text.length > 4000 || !hasInteractiveAnswer(owner, messageId, callId, answer)) return false
    val job = sendInteractiveText(owner, text) ?: return false
    val attempt = state.value.attempts[owner]?.clientId ?: return false
    job.join()
    return state.value.attempts[owner]?.let {
        it.clientId == attempt && it.delivery in setOf(RemoteDelivery.ACCEPTED, RemoteDelivery.DELIVERED)
    } == true
}
