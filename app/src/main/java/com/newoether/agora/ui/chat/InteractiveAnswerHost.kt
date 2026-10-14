package com.newoether.agora.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.newoether.agora.model.InteractiveAnswer
import com.newoether.agora.ui.chat.message.LocalInteractiveConversationId
import com.newoether.agora.ui.chat.message.LocalSendInteractiveAnswer
import com.newoether.agora.ui.chat.message.LocalOpenInteractiveAnswer
import com.newoether.agora.viewmodel.ChatViewModel
import com.newoether.agora.viewmodel.hasInteractiveAnswer
import com.newoether.agora.viewmodel.verifyInteractiveAnswer

private data class OpenInteractiveAnswer(val conversationId: String, val messageId: String, val callId: String, val answer: InteractiveAnswer, val linkedSkill: String?, val confirmPrompt: Boolean, val resourceFiles: List<String>)

/** One viewer for the selected chat; branch/selection changes revoke its originating artifact. */
@Composable
internal fun InteractiveAnswerHost(viewModel: ChatViewModel, content: @Composable () -> Unit) {
    var selected by remember { mutableStateOf<OpenInteractiveAnswer?>(null) }
    val conversationId by viewModel.currentConversationId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    LaunchedEffect(conversationId, messages, selected) {
        selected?.let {
            if (!viewModel.hasInteractiveAnswer(it.conversationId, it.messageId)) selected = null
        }
    }
    val originatingConversationId = conversationId
    CompositionLocalProvider(
        LocalInteractiveConversationId provides originatingConversationId,
        LocalSendInteractiveAnswer provides { messageId, callId, answer, text ->
            originatingConversationId?.let { id -> viewModel.sendInteractiveFollowup(id, messageId, callId, answer, text.text, text.sourceFiles) } ?: false
        },
        LocalOpenInteractiveAnswer provides { messageId, callId, answer, linkedSkill ->
            conversationId?.let { id ->
                if (viewModel.hasInteractiveAnswer(id, messageId)) {
                    val template = viewModel.messages.value.firstOrNull { it.id == messageId }?.segments
                        ?.firstOrNull { it.toolCallId == callId }?.let { com.newoether.agora.templates.interactiveTemplateOf(it) }
                    selected = OpenInteractiveAnswer(id, messageId, callId, answer, linkedSkill,
                        confirmPrompt = template == null, resourceFiles = template?.resourceFiles.orEmpty())
                }
            }
        },
    ) { content() }
    selected?.let { request ->
        InteractiveAnswerViewer(request.answer, onClose = { selected = null }, onSave = { saveExpandedTemplate(viewModel, request) }, confirmPrompt = request.confirmPrompt, promptPrefix = com.newoether.agora.templates.templateSkillPrefix(request.linkedSkill), onSend = { text ->
            viewModel.sendInteractiveFollowup(request.conversationId, request.messageId, request.callId, request.answer, text.text, text.sourceFiles)
        })
    }
}

private suspend fun saveExpandedTemplate(viewModel: ChatViewModel, request: OpenInteractiveAnswer): Boolean {
    if (!viewModel.verifyInteractiveAnswer(request.conversationId, request.messageId, request.callId, request.answer)) return false
    return try {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            viewModel.interactiveTemplates.save(com.newoether.agora.templates.InteractiveTemplate(
                name = request.answer.title, description = request.answer.description, answer = request.answer,
                linkedSkill = request.linkedSkill, resourceFiles = request.resourceFiles,
            ))
        }
        viewModel.emitSnackbar(viewModel.getApplication<android.app.Application>().getString(com.newoether.agora.R.string.template_saved))
        true
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (failure: Exception) { viewModel.emitSnackbar(failure.message.orEmpty()); false }
}
