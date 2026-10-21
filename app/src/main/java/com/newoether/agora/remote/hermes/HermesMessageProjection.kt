package com.newoether.agora.remote.hermes

import com.newoether.agora.remote.*
import kotlinx.serialization.json.*
import java.security.MessageDigest

/** Native row identities are preserved; no prompt-text matching is a delivery receipt. */
internal fun hermesHistory(rows: JsonArray, clients: Map<String, String>): List<RemoteMessage> {
    var turn = "history"
    return rows.mapNotNull { raw ->
        val row = raw as? JsonObject ?: return@mapNotNull null
        if (row.string("display_kind") == "hidden") return@mapNotNull null
        val native = row.string("row_id") ?: row.string("_row_id") ?: row.string("id") ?: return@mapNotNull null
        val role = row.string("role") ?: return@mapNotNull null
        if (role == "user") turn = "row:$native"
        val text = row.string("text") ?: row.string("content") ?: row["content"]?.toString().orEmpty()
        val timestamp = ((row.string("timestamp")?.toDoubleOrNull() ?: 0.0) * 1000).toLong()
        when (role) {
            "user", "assistant" -> {
                val display = if (role == "user") hermesHistoryImages(text) else text to emptyList()
                RemoteMessage("row:$native", turn, clients[native], role, display.first, timestamp, nativeId = "row:$native", imageLinks = display.second)
            }
            "tool" -> RemoteMessage("row:$native", turn, null, "assistant", "", timestamp,
                activity = RemoteActivity("tool", row.string("name") ?: "Tool", row["args"]?.toString(), text, "succeeded"))
            else -> null
        }
    }
}

internal fun hermesPage(messages: List<RemoteMessage>, runtime: RemoteRuntime?, cursor: String? = null,
    pageCursor: String? = null, receipts: List<RemoteInputReceipt> = emptyList()): RemoteConversationPage = RemoteConversationPage(
    messages, cursor, emptyList(), runtime, pageCursor = pageCursor, inputReceipts = receipts,
    nodes = messages.map { m -> RemoteMessageNode(
        m.id, m.turnId, m.clientId, m.role, m.timestamp,
        MessageDigest.getInstance("SHA-256").digest((m.text + m.activity.toString()).toByteArray())
            .joinToString("") { "%02x".format(it) }, m.text.length,
        nativeId = m.nativeId, activity = m.activity?.let { RemoteNodeActivity(it.type, it.state, it.durationMs) },
        error = m.error, imageCount = m.imageLinks.size,
    ) },
)
