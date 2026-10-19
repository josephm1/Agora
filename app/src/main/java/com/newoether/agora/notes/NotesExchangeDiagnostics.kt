package com.newoether.agora.notes

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.serialization.json.*

/** Private, bounded Notes-only evidence. Never records provider credentials or image bytes. */
internal object NotesExchangeDiagnostics {
    private const val MAX_RECORD_BYTES = 180_000
    private const val MAX_RECORDS = 12
    @Synchronized fun record(context: Context, stage: String, content: JsonObject) {
        runCatching {
            val directory = File(context.filesDir, "notes-diagnostics").apply { mkdirs() }
            val raw = buildJsonObject { put("stage", stage); put("content", content) }.toString().toByteArray()
            if (raw.size > MAX_RECORD_BYTES) return
            val file = AtomicFile(File(directory, "${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}.json"))
            val output = file.startWrite()
            try { output.write(raw); file.finishWrite(output) }
            catch (error: Throwable) { file.failWrite(output); throw error }
            directory.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_RECORDS)?.forEach { it.delete() }
        }
    }
    /** Only the requested Notes result's public assistant output, never opaque provider state. */
    fun resultTrace(messages:List<com.newoether.agora.model.ChatMessage>,proposalId:String):String? {
        val message=messages.lastOrNull { m -> m.segments.orEmpty().any {
            it.type=="tool" && it.toolStructuredResult?.let { raw ->
                runCatching { NotesDocumentFormat.decodeProposal(raw).proposalId==proposalId }.getOrDefault(false)
            }==true
        } } ?: return null
        return traceMessage(message,proposalId)
    }
    fun traceMessage(message:com.newoether.agora.model.ChatMessage,proposalId:String):String? {
        val raw=buildJsonObject {
            put("messageId",message.id);put("model",message.modelName);put("proposalId",proposalId)
            put("answer",message.text);put("exposedThinking",message.thoughts)
            putJsonArray("segments") { message.segments.orEmpty().forEach { segment ->
                add(buildJsonObject {
                    put("type",segment.type);put("content",segment.content)
                    put("toolName",segment.toolName);put("arguments",segment.toolArgs)
                    put("result",segment.toolResult);put("structuredResult",segment.toolStructuredResult)
                })
            } }
        }.toString()
        return raw.takeIf { it.toByteArray().size<=128_000 }
    }
    fun imported(context: Context, document: NotesDocument) = record(context, "input", buildJsonObject {
        put("projection", document.contextText())
        putJsonArray("pageImages") { document.pages.filter { it.previewFile != null }.forEach { page ->
            add(buildJsonObject { put("pageId", page.id); put("checksum", document.checksums[page.previewFile]) })
        } }
        put("hasDisplayThumbnail", document.thumbnailFile != null)
    })
}
