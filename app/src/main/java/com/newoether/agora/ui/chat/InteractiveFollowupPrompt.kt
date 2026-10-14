package com.newoether.agora.ui.chat

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.newoether.agora.R
import com.newoether.agora.ui.components.AlertDialog
import com.newoether.agora.ui.components.Button
import com.newoether.agora.ui.components.Text
import com.newoether.agora.ui.components.TextButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class PendingInteractivePrompt(val id: Int, val text: String, val reply: (Boolean, Boolean) -> Unit)

/** A saved interface's explicit Run gesture is sufficient; generated answers retain confirmation. */
@Composable
internal fun InteractiveFollowupPrompt(
    request: PendingInteractivePrompt,
    confirmPrompt: Boolean,
    onSend: suspend (String) -> Boolean,
    onFinished: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var sending by remember(request) { mutableStateOf(false) }
    val latestSend by rememberUpdatedState(onSend)
    val latestFinished by rememberUpdatedState(onFinished)
    suspend fun dispatch() {
        if (sending) return
        sending = true
        val accepted = try { latestSend(request.text) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
        request.reply(accepted, false)
        latestFinished(accepted)
    }
    LaunchedEffect(request, confirmPrompt) { if (!confirmPrompt) dispatch() }
    if (confirmPrompt) AlertDialog(
        onDismissRequest = { if (!sending) { request.reply(false, true); latestFinished(false) } },
        title = { Text(stringResource(R.string.interactive_answer_followup)) },
        text = { Text(request.text) },
        confirmButton = {
            Button(enabled = !sending, onClick = { scope.launch { dispatch() } }) {
                Text(stringResource(R.string.interactive_answer_send))
            }
        },
        dismissButton = {
            TextButton(enabled = !sending, onClick = { request.reply(false, true); latestFinished(false) }) {
                Text(stringResource(R.string.interactive_answer_cancel))
            }
        },
    )
}
