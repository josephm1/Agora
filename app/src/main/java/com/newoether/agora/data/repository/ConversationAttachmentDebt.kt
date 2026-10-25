package com.newoether.agora.data.repository

import com.newoether.agora.data.local.MaintenanceDebtEntity
import com.newoether.agora.data.local.MessageAttachmentReference

/** Durable attachment cleanup debt shares the graph transaction; workers perform file I/O later. */
internal suspend fun ConversationRepository.enqueueAttachmentDebt(paths: Collection<String>): Boolean =
    enqueueDebt(MaintenanceDebtEntity.KIND_ATTACHMENT_ORPHANS, paths)

internal suspend fun ConversationRepository.enqueueAttachmentReconcile(): Boolean = enqueueAttachmentDebt(
    listOf(MaintenanceDebtEntity.RECONCILE_IDENTITY),
)

internal suspend fun ConversationRepository.enqueueReconcileDebt(): Boolean {
    var scheduled = false
    for (kind in MaintenanceDebtEntity.RECONCILE_KINDS) {
        scheduled = enqueueDebt(
            kind,
            listOf(MaintenanceDebtEntity.RECONCILE_IDENTITY),
        ) or scheduled
    }
    return scheduled
}

internal suspend fun ConversationRepository.enqueueMessageAttachmentDebt(
    references: List<MessageAttachmentReference>,
): Boolean {
    if (references.any { reference ->
            val metadata = reference.attachmentMeta
            (metadata == null && reference.images.isNotEmpty()) ||
                (metadata != null && metadata.decodeAttachmentMeta() == null)
        }
    ) {
        return enqueueAttachmentReconcile()
    }
    return enqueueAttachmentDebt(references.messageReclaimablePaths())
}

internal suspend fun ConversationRepository.enqueueDebt(kind: String, identities: Collection<String>): Boolean {
    val dao = maintenanceDebtDao ?: return false
    val exact = identities.asSequence()
        .filter(String::isNotBlank)
        .distinct()
        .toList()
    if (exact.isEmpty()) return false
    val at = System.currentTimeMillis()
    exact.forEach { identity -> dao.enqueue(kind, identity, at) }
    return true
}

