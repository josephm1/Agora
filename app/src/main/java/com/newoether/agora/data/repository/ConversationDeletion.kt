package com.newoether.agora.data.repository

import com.newoether.agora.data.local.ChatDao
import com.newoether.agora.data.local.MessageAttachmentReference

internal suspend fun ConversationRepository.deleteConversationWithChildren(id: String) {
    prepareConversationDeletion(id)
    val removedOwners = mutableListOf<String>()
    val scheduled = withSemanticTransaction(updatedAt = System.currentTimeMillis()) {
        removedOwners += chatDao.getSubagentIds(id) + id
        chatDao.deleteOwnedConversationGraphs(id).fold(false) { scheduled, (raw, references) ->
            val draft = raw.decodeSelectedAttachments()
            scheduled or enqueueMessageAttachmentDebt(references) or when {
                raw != null && draft == null -> enqueueAttachmentReconcile()
                else -> enqueueAttachmentDebt(draft.orEmpty().reclaimablePaths())
            }
        }
    }
    if (scheduled) scheduleMaintenance()
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        legacyInteractiveStateDirectory?.let { directory ->
            removedOwners.forEach { owner ->
                runCatching { com.newoether.agora.templates.LegacyInteractiveStateFiles.delete(directory, owner) }
                    .onFailure { com.newoether.agora.util.DebugLog.e("InteractiveState", "Conversation deleted; legacy state cleanup failed", it) }
            }
        }
    }
}

/** The caller owns the semantic transaction and schedules attachment debt before committing it. */
internal suspend fun ChatDao.deleteOwnedConversationGraphs(
    id: String,
): List<Pair<String?, List<MessageAttachmentReference>>> = (getSubagentIds(id) + id).mapNotNull { ownedId ->
    val conversation = getConversation(ownedId) ?: return@mapNotNull null
    val references = mutableListOf<MessageAttachmentReference>()
    var afterId: String? = null
    while (true) {
        val page = getConversationMessageAttachmentReferencesPage(ownedId, afterId, 128)
        references += page
        afterId = page.lastOrNull()?.id
        if (page.size < 128) break
    }
    deleteEmbeddingsByConversation(ownedId)
    deleteMessagesByConversation(ownedId)
    deleteConversation(ownedId)
    conversation.draftAttachments to references
}
