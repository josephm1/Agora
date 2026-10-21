package com.newoether.agora.remote

import com.newoether.agora.model.SelectedAttachment
import com.newoether.agora.model.ToolImageAttachment
import kotlinx.coroutines.flow.Flow

/** Optional transports own execution and protocol details; Remote owns presentation. */
internal interface RemoteBackend {
    val address: String
    val capabilities: RemoteCapabilities get() = RemoteCapabilities()
    suspend fun connect(): String
    suspend fun sessions(cursor: String? = null): RemoteSessionPage
    suspend fun conversation(id: String, cursor: String? = null): RemoteConversationPage
    fun events(id: String): Flow<RemoteConversationPage>
    suspend fun models(): List<RemoteModel>
    suspend fun usage(): RemoteUsage
    suspend fun create(text: String, clientId: String, attachments: List<String> = emptyList(), settings: RemoteSettings? = null): RemoteSession
    suspend fun send(id: String, text: String, clientId: String, attachments: List<String> = emptyList()): String
    suspend fun rename(id: String, name: String): RemoteSession
    suspend fun archiveSession(id: String)
    suspend fun setModel(id: String, model: String)
    suspend fun updateSettings(id: String, settings: RemoteSettings)
    suspend fun stop(id: String, turnId: String)
    suspend fun upload(item: SelectedAttachment): RemoteUpload
    suspend fun image(id: String, requested: RemotePayloadRequest, persist: (java.io.InputStream, String) -> ToolImageAttachment): ToolImageAttachment
    suspend fun answer(id: String, requestId: String, result: kotlinx.serialization.json.JsonObject) { throw FiloInputException() }
    fun close() {}
}

internal data class RemoteCapabilities(
    val label: String = "Codex Desktop", val attachments: Boolean = true,
    val archive: Boolean = true, val usage: Boolean = true, val activeSend: Boolean = true,
)

@kotlinx.serialization.Serializable
internal data class RemoteQuestion(val id: String, val method: String, val params: kotlinx.serialization.json.JsonObject)
