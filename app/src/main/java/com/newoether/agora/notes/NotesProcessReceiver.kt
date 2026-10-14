package com.newoether.agora.notes

import android.content.*
import androidx.core.content.FileProvider
import androidx.work.*
import kotlinx.coroutines.*

const val ACTION_PROCESS_DOCUMENT="com.kalamos.notes.action.PROCESS_DOCUMENT"
const val ACTION_PROCESS_RESULT="com.kalamos.notes.action.PROCESS_RESULT"
const val ACTION_REPLAY_PROCESS_RESULT="com.kalamos.notes.action.REPLAY_PROCESS_RESULT"
private const val NOTES_PACKAGE="com.kalamos.notebook"

/** Only a URI granted by the installed Notes-owned provider is admitted. Never opens an Activity. */
class NotesProcessReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action !in setOf(ACTION_PROCESS_DOCUMENT,ACTION_REPLAY_PROCESS_RESULT))return
        val uri=intent.data?:return
        if(uri.scheme!="content" || uri.authority!="com.kalamos.notebook.agora-documents")return
        val provider=context.packageManager.resolveContentProvider(uri.authority!!,0)?:return
        if(provider.packageName!=NOTES_PACKAGE)return
        val id=intent.getStringExtra("requestId")?:return
        if(runCatching { NotesDocumentFormat.uuid(id) }.isFailure)return
        val pending=goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                require(intent.type==NotesDocumentFormat.MIME && intent.getStringExtra("returnPackage")==NOTES_PACKAGE)
                if(intent.action==ACTION_REPLAY_PROCESS_RESULT) {
                    replayNotesProcessResult(context,id,uri,
                        requireNotNull(intent.getStringExtra("documentId")),requireNotNull(intent.getStringExtra("baseRevisionId")),
                        diagnosticOnly=intent.getBooleanExtra("diagnosticOnly",false))
                    return@launch
                }
                val request=NotesBackgroundStore(context).stage(id,uri,
                    requireNotNull(intent.getStringExtra("documentId")),requireNotNull(intent.getStringExtra("baseRevisionId")))
                val work=notesProcessWorkRequest(request.requestId)
                WorkManager.getInstance(context).enqueueUniqueWork("notes-process-$id",ExistingWorkPolicy.KEEP,work)
            } catch(error:Exception) {
                if(intent.action==ACTION_REPLAY_PROCESS_RESULT && intent.getBooleanExtra("diagnosticOnly",false))
                    com.newoether.agora.util.DebugLog.w("NotesExchange","Saved-result diagnostic grant failed: ${error.javaClass.simpleName}")
                else sendNotesProcessResult(context,id,error=error.message?:"Could not process Notes page")
            }
            finally { pending.finish() }
        }
    }
}
internal fun sendNotesProcessResult(context:Context,id:String,proposal:java.io.File?=null,error:String?=null,conversationId:String?=null) {
    context.sendBroadcast(Intent(ACTION_PROCESS_RESULT).apply {
        component=ComponentName(NOTES_PACKAGE,"com.kalamos.notebook.exchange.NotesProcessResultReceiver")
        putExtra("requestId",id);conversationId?.let { putExtra("chatConversationId",it) }
        error?.let { putExtra("error",it.take(500)) }
        proposal?.let { file ->
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
            // Broadcast flag grants are not sufficient on all vendor builds. Make the exact
            // Notes package's read grant explicit before its asynchronous receiver runs.
            context.grantUriPermission(NOTES_PACKAGE,uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(uri,NotesDocumentFormat.PROPOSAL_MIME);clipData=ClipData.newRawUri("Processed Notes page",uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    })
}

/** Delivery recovery only: never queues work or contacts a provider. */
internal fun replayNotesProcessResult(context:Context,id:String,sourceUri:android.net.Uri,documentId:String,revisionId:String,diagnosticOnly:Boolean=false) {
    val store=NotesBackgroundStore(context)
    val request=requireNotNull(store.load(id)) { "This processing request is unavailable" }
    require(request.documentId==documentId && request.revisionId==revisionId) { "Request identity mismatch" }
    // Recheck a live grant from the Notes-owned provider, rather than trusting intent metadata.
    context.contentResolver.openInputStream(sourceUri).use { requireNotNull(it) { "Notes source is unavailable" } }
    // Sparse delivery polls must not turn a still-running provider request into an error.
    if(request.state in setOf("queued","running"))return
    check(request.state=="done") { request.error?:"This processing result is not complete" }
    val proposal=store.proposal(id)
    check(proposal.exists()) { "The saved result is unavailable; no model request was repeated" }
    val decoded=NotesDocumentFormat.decodeProposal(proposal.readText())
    require(decoded.documentId==request.documentId && decoded.baseRevisionId==request.revisionId && decoded.backgroundRequestId==id) { "Saved result identity mismatch" }
    if(diagnosticOnly) {
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",proposal)
        context.grantUriPermission(NOTES_PACKAGE,uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return
    }
    sendNotesProcessResult(context,id,proposal,conversationId=request.conversationId)
}

/** User-triggered short work: JobScheduler owns execution on Android 12+.
 * WorkManager supplies its compatibility foreground service on older Android.
 */
internal fun notesProcessWorkRequest(requestId:String):OneTimeWorkRequest {
    NotesDocumentFormat.uuid(requestId)
    return OneTimeWorkRequestBuilder<NotesProcessWorker>()
        .setInputData(workDataOf("requestId" to requestId))
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
}
