package com.newoether.agora.ui.chat

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
import androidx.compose.ui.res.stringResource
import com.newoether.agora.R
import com.newoether.agora.ui.components.DialogWindowEinkPresentation
import com.newoether.agora.ui.motion.LocalAgoraMotionPolicy
import androidx.compose.ui.unit.dp
import com.newoether.agora.model.SelectedAttachment
import com.newoether.agora.sharing.SharedTextInbox
import com.newoether.agora.viewmodel.ChatViewModel
import com.newoether.agora.viewmodel.NEW_CHAT_WORKSPACE_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Uses the same draft destination pattern as Notes imports. Nothing sends automatically. */
@Composable
internal fun SharedTextIncoming(viewModel: ChatViewModel, requestId: String?, onConsumed: (String) -> Unit) {
    if (requestId == null) return
    val context = LocalContext.current
    val store = remember(context) { SharedTextInbox(context) }
    val scope = rememberCoroutineScope()
    var attachment by remember(requestId) { mutableStateOf<SelectedAttachment?>(null) }
    var owner by remember(requestId) { mutableStateOf<String?>(null) }
    var error by remember(requestId) { mutableStateOf<String?>(null) }
    var retry by remember(requestId) { mutableIntStateOf(0) }
    var choosing by remember(requestId) { mutableStateOf(false) }
    LaunchedEffect(requestId, retry) {
        try {
            val loaded = withContext(Dispatchers.IO) {
                if (store.consumed(requestId)) null else store.load(requestId) to store.owner(requestId)
            }
            if (loaded == null) { onConsumed(requestId); return@LaunchedEffect }
            attachment = loaded.first
            owner = loaded.second
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: context.getString(R.string.shared_content_load_failed) }
    }
    LaunchedEffect(owner, attachment, retry) {
        val destination = owner ?: return@LaunchedEffect
        val incoming = attachment ?: return@LaunchedEffect
        try {
            if (destination != NEW_CHAT_WORKSPACE_ID) withContext(Dispatchers.IO) {
                val repository = (context.applicationContext as com.newoether.agora.AgoraApplication).requireContainer().conversationRepository
                check(repository.getConversation(destination) != null) { "This chat was deleted. Share the excerpt again to choose another draft." }
            }
            viewModel.conversationComposer.load(destination)
            try {
                withContext(NonCancellable) {
                    check(viewModel.conversationComposer.attachPreparedMany(destination, listOf(incoming))) {
                        "Draft is busy; retry when sending finishes"
                    }
                    withContext(Dispatchers.IO) { store.consume(requestId) }
                }
            } finally { withContext(NonCancellable) { viewModel.conversationComposer.release(destination) } }
            if (destination == NEW_CHAT_WORKSPACE_ID) viewModel.createNewChat() else viewModel.selectConversation(destination)
            viewModel.emitSnackbar(context.getString(R.string.shared_content_attached))
            onConsumed(requestId)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: context.getString(R.string.shared_content_attach_failed) }
    }
    if (attachment != null && owner == null && error == null) {
        val conversations by viewModel.conversations.collectAsState()
        fun choose(destination: String) {
            if (choosing) return
            choosing = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { store.choose(requestId, destination) }
                    owner = destination
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: context.getString(R.string.shared_content_choose_failed) }
                finally { choosing = false }
            }
        }
        AlertDialog(onDismissRequest = { if (!choosing) onConsumed(requestId) },
            title = { DialogWindowEinkPresentation(LocalAgoraMotionPolicy.current.eInkMode); Text(stringResource(R.string.shared_content_title)) },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(attachment?.sourceTitle ?: stringResource(R.string.shared_content_name))
                attachment?.sourceBookTitle?.let { Text(it) }
                Text(stringResource(R.string.shared_content_destination))
                TextButton(enabled = !choosing, onClick = { choose(NEW_CHAT_WORKSPACE_ID) }) { Text(stringResource(R.string.shared_content_new_draft)) }
                conversations.orEmpty().forEach { conversation ->
                    TextButton(enabled = !choosing, onClick = { choose(conversation.id) }) { Text(conversation.title) }
                }
            } }, confirmButton = {}, dismissButton = {
                TextButton(enabled = !choosing, onClick = { onConsumed(requestId) }) { Text(stringResource(R.string.cancel)) }
            })
    }
    error?.let { message ->
        AlertDialog(onDismissRequest = { onConsumed(requestId) }, title = { DialogWindowEinkPresentation(LocalAgoraMotionPolicy.current.eInkMode); Text(stringResource(R.string.shared_content_title)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null; retry++ }) { Text(stringResource(R.string.retry)) } },
            dismissButton = { TextButton(onClick = { onConsumed(requestId) }) { Text(stringResource(R.string.cancel)) } })
    }
}
