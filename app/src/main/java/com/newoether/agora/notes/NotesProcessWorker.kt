package com.newoether.agora.notes

import android.content.Context
import androidx.work.*
import com.newoether.agora.AgoraApplication
import com.newoether.agora.automation.TaskExecutionEngine
import com.newoether.agora.data.local.ChatEntity
import com.newoether.agora.model.*
import com.newoether.agora.service.AutomationForegroundInfo
import com.newoether.agora.tool.NotesProposalToolProvider
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** Durable one-shot execution through Agora's ordinary generation engine and chat repository. */
class NotesProcessWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    // Used automatically by WorkManager for expedited work on pre-Android-12
    // devices. Modern Android runs this as a job without an app-started FGS.
    override suspend fun getForegroundInfo():ForegroundInfo {
        val requestId=requireNotNull(inputData.getString("requestId"))
        val conversationId=NotesBackgroundStore(applicationContext).load(requestId)?.conversationId
        return AutomationForegroundInfo.create(applicationContext,"Formatting Notes page",
            AutomationForegroundInfo.taskNotificationId(requestId,id),conversationId)
    }
    override suspend fun doWork():Result {
        val id=inputData.getString("requestId")?:return Result.failure()
        val store=NotesBackgroundStore(applicationContext)
        var request=runCatching { store.load(id) }.getOrNull()?:return Result.failure()
        val container=(applicationContext as AgoraApplication).awaitContainer()?:run {
            sendNotesProcessResult(applicationContext,id,error="Agora database is unavailable")
            return Result.failure()
        }
        suspend fun recovered():NotesProposal? {
            val models=container.conversationRepository.getMessageTopologySnapshot(request.conversationId)
                .filter { it.participant==Participant.MODEL && it.status==MessageStatus.SUCCESS }.sortedByDescending { it.timestamp }
            for(model in models) {
                val raw=container.conversationRepository.getMessage(model.id)?.toolCallJson?:continue
                val segments=runCatching { Json.decodeFromString<List<MessageSegment>>(raw) }.getOrDefault(emptyList())
                notesBackgroundProposal(request,segments)?.let { return it }
            }
            return null
        }
        try {
            if(request.state=="done" && store.proposal(id).exists()) {
                sendNotesProcessResult(applicationContext,id,store.proposal(id),conversationId=request.conversationId);return Result.success()
            }
            recovered()?.let { proposal ->
                store.saveProposal(id,proposal);store.save(request.copy(state="done"))
                sendNotesProcessResult(applicationContext,id,store.proposal(id),conversationId=request.conversationId);return Result.success()
            }
            check(request.state=="queued") { request.error?:"Processing was interrupted. Start a new request to avoid repeating a model charge." }
            container.settingsRepository.awaitInitialLoad()
            check(container.settingsRepository.notesIntegrationEnabled.value) { "Enable Kalamos Notes integration in Agora" }
            val model=container.settingsRepository.selectedModel.value
            check(model.isNotBlank()) { "Select a configured model in Agora" }
            if(container.conversationRepository.getConversation(request.conversationId)==null)
                container.conversationRepository.upsertConversation(ChatEntity(id=request.conversationId,title="Format with AI · ${request.title}",modelId=model))
            val exchange=NotesExchangeStore(applicationContext)
            store.bundle(id).copyTo(exchange.file(id),overwrite=true)
            val attachment=exchange.attachment(NotesExchangeRequest(id,request.conversationId,"background",ready=true,documentId=request.documentId))
            request=request.copy(state="running");store.save(request)
            when(val result=container.taskExecutionEngine.runOnce(request.conversationId,"Format this note.",model,
                foregroundServiceManagedExternally=true,requestKind="notes",attachments=listOf(attachment))) {
                is TaskExecutionEngine.Result.Success -> {
                    val proposal=requireNotNull(recovered()) { "The model returned no valid Notes result" }
                    store.saveProposal(id,proposal);store.save(request.copy(state="done"))
                    sendNotesProcessResult(applicationContext,id,store.proposal(id),conversationId=request.conversationId)
                    return Result.success()
                }
                is TaskExecutionEngine.Result.Busy -> error(result.reason)
                is TaskExecutionEngine.Result.Failure -> error(result.reason)
            }
        } catch(cancelled:CancellationException) { throw cancelled }
        catch(error:Exception) {
            val reason=error.message?:"Notes processing failed"
            store.save(request.copy(state="error",error=reason.take(500)))
            sendNotesProcessResult(applicationContext,id,error=reason,conversationId=request.conversationId)
            return Result.failure(workDataOf("error" to reason.take(500)))
        }
    }
}
