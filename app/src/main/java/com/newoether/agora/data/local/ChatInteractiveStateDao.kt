package com.newoether.agora.data.local

import androidx.room.Query

/** Selected-owner payload only; navigation never loads it. */
data class InteractiveStateRow(val interactiveStateJson: String?)

interface ChatInteractiveStateDao {
    @Query("SELECT id FROM conversations")
    suspend fun getAllConversationIds(): List<String>

    @Query("SELECT interactiveStateJson FROM conversations WHERE id = :owner")
    suspend fun getInteractiveState(owner: String): InteractiveStateRow?

    @Query("""
        UPDATE conversations SET interactiveStateJson = :updated,
            dataChangedAt = MAX(dataChangedAt + 1, :at)
        WHERE id = :owner AND interactiveStateJson IS :expected
    """)
    suspend fun compareAndSetInteractiveState(owner: String, expected: String?, updated: String, at: Long): Int
}
