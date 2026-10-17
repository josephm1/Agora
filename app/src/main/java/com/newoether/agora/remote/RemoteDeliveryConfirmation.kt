package com.newoether.agora.remote

import com.newoether.agora.viewmodel.ScrollRequestCoordinator
import kotlinx.coroutines.flow.MutableStateFlow

/** Both ordinary Remote sends and artifact follow-ups reconcile one native receipt. */
internal fun confirmRemoteDelivery(
    state: MutableStateFlow<RemoteState>, attachmentStore: RemoteAttachmentStore?,
    scrollRequests: ScrollRequestCoordinator, owner: String, attempt: RemoteAttempt,
    fresh: List<RemoteMessageNode>,
) {
    val current = state.value
    if (current.attempts[owner]?.clientId != attempt.clientId || current.owner != owner ||
        current.attempts[owner]?.delivery == RemoteDelivery.DELIVERED) return
    val message = fresh.firstOrNull { it.role == "user" && it.clientId == attempt.clientId } ?: return
    if (!attempt.preserveComposer) current.attachments[owner].orEmpty().forEach { attachmentStore?.remove(it) }
    state.value = current.copy(
        attachments = if (attempt.preserveComposer) current.attachments else current.attachments - owner,
        drafts = if (!attempt.preserveComposer && current.drafts[owner] == attempt.text) current.drafts - owner else current.drafts,
        attempts = current.attempts + (owner to attempt.copy(delivery = RemoteDelivery.DELIVERED)),
    )
    scrollRequests.requestAbsoluteBottomAfter(owner, message.nativeId ?: message.id)
}
