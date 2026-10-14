package com.newoether.agora.ui.chat

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newoether.agora.notes.*
import com.newoether.agora.viewmodel.toUiChatMessage
import com.newoether.agora.viewmodel.ChatViewModel
import com.newoether.agora.viewmodel.NEW_CHAT_WORKSPACE_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import java.util.UUID

internal val LocalNotesEnabled = staticCompositionLocalOf { true }
internal val LocalNotesOpen = staticCompositionLocalOf<(String, NotesProposal?, String?, String?, String?) -> Unit> { { _, _, _, _, _ -> } }

/** Launches the full Notes editor with the exact draft owner, after flushing its text. */
@Composable
internal fun NotesDocumentHost(viewModel: ChatViewModel, ownerId: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launchGate = remember(ownerId) { NotesLaunchGate() }
    val integrationEnabled by viewModel.settings.notesIntegrationEnabled.collectAsState()
    val pageImagesEnabled by viewModel.settings.notesPageImagesEnabled.collectAsState()
    CompositionLocalProvider(LocalNotesEnabled provides integrationEnabled, LocalNotesOpen provides { operation, proposal, documentId, revisionId, bundlePath ->
        scope.launch {
        launchGate.run {
            try {
                com.newoether.agora.util.DebugLog.d("NotesExchange", "launch accepted operation=$operation")
                check(integrationEnabled) { "Enable Kalamos Notes in Interaction settings" }
                viewModel.conversationComposer.load(ownerId)
                try {
                    val snapshot = viewModel.conversationComposer.state(ownerId).value
                    check(viewModel.conversationComposer.persistText(ownerId, snapshot.text)) { "Could not save this draft" }
                    val automatic = operation == "apply"
                    val request = NotesExchangeRequest(UUID.randomUUID().toString(), ownerId, operation,
                        proposal?.documentId ?: documentId, proposal?.baseRevisionId ?: revisionId)
                    val reserved = withContext(Dispatchers.IO) { NotesExchangeStore(context).reserve(request) }
                    if (reserved.requestId != request.requestId) {
                        check(!automatic) { "Another Notes session is still open. Return to this result and choose Open in Notes when it closes." }
                        val resume = NotesExchangeActivity.resumeIntent(reserved.requestId)
                        check(resume != null) { "The previous Notes session just closed. Please try again." }
                        context.startActivity(resume)
                        viewModel.emitSnackbar("Resuming your unfinished note. It remains linked to its original draft.")
                    } else {
                    val trace=proposal?.let { result ->
                        withContext(Dispatchers.IO) {
                            val repo=(context.applicationContext as com.newoether.agora.AgoraApplication).requireContainer().conversationRepository
                            result.sourceMessageId?.let { id ->
                                repo.getMessage(id)?.takeIf { it.conversationId==ownerId }?.toUiChatMessage(context)?.let { message ->
                                    NotesExchangeDiagnostics.traceMessage(message,result.proposalId)
                                }
                            } ?: NotesExchangeDiagnostics.resultTrace(viewModel.messages.value,result.proposalId)
                        }
                    }
                    context.startActivity(Intent(context, NotesExchangeActivity::class.java).apply {
                        // Admission above permits one live session. A fresh task avoids Android
                        // silently reusing a completed/stale editor task with a different owner.
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                        putExtra("ownerId", ownerId); putExtra("operation", operation);putExtra("includeImages",pageImagesEnabled)
                        putExtra("requestId", request.requestId)
                        proposal?.let { putExtra("proposal", it.validate().encode()) }
                        trace?.let { putExtra("notesResultTrace",it) }
                        documentId?.let { putExtra("documentId", it) }
                        revisionId?.let { putExtra("baseRevisionId", it) }
                        bundlePath?.let { putExtra("bundlePath", it) }
                    })
                    }
                } finally { withContext(NonCancellable) { viewModel.conversationComposer.release(ownerId) } }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) {
                com.newoether.agora.util.DebugLog.w("NotesExchange", "launch failed", error)
                viewModel.emitSnackbar(error.message ?: "Could not open Notes")
            }
        }
        }
    }) {
        NotesAutomaticReturnEffect(viewModel, ownerId, integrationEnabled)
        content()
    }
}

@Composable
private fun NotesAutomaticReturnEffect(viewModel: ChatViewModel, ownerId: String, enabled: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val open by rememberUpdatedState(LocalNotesOpen.current)
    LaunchedEffect(viewModel, ownerId, enabled, lifecycle) {
        if (!enabled) return@LaunchedEffect
        val tracker = NotesAutomaticReturn()
        combine(viewModel.messages, viewModel.generatingInConversationId) { messages, generating ->
            messages to (generating == ownerId)
        }.collect { (messages, generating) ->
            if (viewModel.currentConversationId.value != ownerId) return@collect
            val proposals = tracker.update(messages, generating)
            // One editor session at a time; keep additional results available as explicit cards.
            proposals.firstOrNull()?.let { proposal ->
                // Do not steal focus or request a background Activity launch. The result card
                // remains available if the user left Agora before processing completed.
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@let
                try {
                    val claimed = withContext(Dispatchers.IO) {
                        NotesExchangeStore(context).claimAutomaticReturn(ownerId, proposal.proposalId)
                    }
                    if (claimed && viewModel.currentConversationId.value == ownerId)
                        open("apply", proposal, null, null, null)
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (failure: Exception) { viewModel.emitSnackbar(failure.message ?: "Could not return to Notes") }
            }
        }
    }
}

/** Only a user-directed incoming request loads its chosen draft; startup never scans inbox owners. */
@Composable
internal fun NotesIncomingDocument(viewModel: ChatViewModel, requestId: String?, onConsumed: (String) -> Unit) {
    if (requestId == null) return
    val context = LocalContext.current
    val store = remember(context) { NotesExchangeStore(context) }
    val scope = rememberCoroutineScope()
    var request by remember(requestId) { mutableStateOf<NotesExchangeRequest?>(null) }
    var error by remember(requestId) { mutableStateOf<String?>(null) }
    var retry by remember(requestId) { mutableIntStateOf(0) }
    LaunchedEffect(requestId, retry) {
        val id = requestId ?: return@LaunchedEffect
        try {
            val loaded = withContext(Dispatchers.IO) { store.load(id) } ?: error("Notes request is unavailable")
            if (loaded.consumed) { onConsumed(id); return@LaunchedEffect }
            check(loaded.ready) { "Notes document has not arrived" }
            request = loaded
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Notes import failed" }
    }
    LaunchedEffect(request?.ownerId, request?.requestId, retry) {
        val incoming = request ?: return@LaunchedEffect
        val owner = incoming.ownerId ?: return@LaunchedEffect
        try {
            val attachments = withContext(Dispatchers.IO) {
                if (owner != NEW_CHAT_WORKSPACE_ID) {
                    val repo = (context.applicationContext as com.newoether.agora.AgoraApplication).requireContainer().conversationRepository
                    check(repo.getConversation(owner) != null) { "The original chat was deleted; this document remains in Notes" }
                }
                store.attachments(incoming)
            }
            viewModel.conversationComposer.load(owner)
            try {
                withContext(NonCancellable) {
                    check(viewModel.conversationComposer.attachPreparedMany(owner, attachments)) { "Draft is busy; retry when sending finishes" }
                    withContext(Dispatchers.IO) { store.save(incoming.copy(consumed = true)) }
                }
            } finally { withContext(NonCancellable) { viewModel.conversationComposer.release(owner) } }
            if (owner == NEW_CHAT_WORKSPACE_ID) viewModel.createNewChat() else viewModel.selectConversation(owner)
            viewModel.emitSnackbar("${attachments.size} Notes ${if(attachments.size==1)"document" else "documents"} attached. Review the draft before sending.")
            onConsumed(incoming.requestId); request = null
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Could not attach Notes document" }
    }
    if (request?.ownerId == null && request != null && error == null) {
        val conversations by viewModel.conversations.collectAsState()
        val incoming = request!!
        fun choose(owner: String) { scope.launch {
            val chosen = incoming.copy(ownerId = owner)
            withContext(Dispatchers.IO) { store.save(chosen) }
            request = chosen
        } }
        AlertDialog(onDismissRequest = { onConsumed(incoming.requestId); request = null },
            title = { com.newoether.agora.ui.components.DialogWindowEinkPresentation(com.newoether.agora.ui.motion.LocalAgoraMotionPolicy.current.eInkMode); Text("Attach Notes document") },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Choose a draft. Nothing is sent automatically.")
                TextButton(onClick = { choose(NEW_CHAT_WORKSPACE_ID) }) { Text("New chat draft") }
                conversations.orEmpty().forEach { conversation ->
                    TextButton(onClick = { choose(conversation.id) }) {
                        Text(conversation.title + if (conversation.id == incoming.linkedOwnerId) " · Linked chat" else "")
                    }
                }
            } }, confirmButton = {}, dismissButton = {
                TextButton(onClick = { onConsumed(incoming.requestId); request = null }) { Text("Cancel") }
            })
    }
    error?.let { message ->
        AlertDialog(onDismissRequest = { requestId?.let(onConsumed); error = null }, title = { com.newoether.agora.ui.components.DialogWindowEinkPresentation(com.newoether.agora.ui.motion.LocalAgoraMotionPolicy.current.eInkMode); Text("Notes document") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null; retry++ }) { Text("Retry") } },
            dismissButton = { TextButton(onClick = { requestId?.let(onConsumed); error = null; request = null }) { Text("Close") } })
    }
}
