package com.newoether.agora.ui.templates

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.newoether.agora.R
import com.newoether.agora.templates.InteractiveTemplate
import com.newoether.agora.templates.readBytesBounded
import com.newoether.agora.ui.components.Text
import com.newoether.agora.ui.components.AlertDialog
import com.newoether.agora.ui.components.AgoraDropdownMenu
import com.newoether.agora.ui.components.AgoraDropdownMenuItem
import com.newoether.agora.ui.components.TextButton
import com.newoether.agora.ui.motion.LocalAgoraMotionPolicy
import com.newoether.agora.ui.components.DialogWindowEinkPresentation
import com.newoether.agora.templates.InteractiveTemplateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** User-initiated library; imports never execute their interfaces. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TemplatesScreen(repository: InteractiveTemplateRepository, onOpen: suspend (String) -> Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var templates by remember { mutableStateOf<List<InteractiveTemplate>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<InteractiveTemplate?>(null) }
    var deleting by remember { mutableStateOf<InteractiveTemplate?>(null) }
    var replacing by remember { mutableStateOf<InteractiveTemplate?>(null) }
    var exporting by remember { mutableStateOf<InteractiveTemplate?>(null) }

    fun runOperation(operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                operation()
                templates = withContext(Dispatchers.IO) { repository.list() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: context.getString(R.string.template_operation_failed) }
            finally { busy = false }
        }
    }

    LaunchedEffect(repository) {
        try { templates = withContext(Dispatchers.IO) { repository.list() } }
        catch (failure: Exception) { error = failure.message }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = replacing
        replacing = null
        if (uri != null) runOperation {
            withContext(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytesBounded(InteractiveTemplate.MAX_FILE_BYTES)
                } ?: error(context.getString(R.string.template_read_failed))
                val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                val imported = InteractiveTemplate.decode(text)
                repository.save(if (target == null) imported else target.copy(answer = imported.answer), replace = target != null, expected = target)
            }
        }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val selected = exporting
        exporting = null
        if (uri != null && selected != null) runOperation {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(selected.encode().toByteArray(Charsets.UTF_8)) }
                    ?: error(context.getString(R.string.template_write_failed))
            }
        }
    }

    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !busy, dismissOnClickOutside = !busy)) {
        DialogWindowEinkPresentation(LocalAgoraMotionPolicy.current.eInkMode)
        Scaffold(topBar = {
            TopAppBar(title = { Text(stringResource(R.string.templates)) }, navigationIcon = {
                TextButton(enabled = !busy, onClick = onClose) { Text(stringResource(R.string.interactive_answer_close)) }
            }, actions = {
                TextButton(enabled = !busy, onClick = { replacing = null; importFile.launch(arrayOf("application/json", "text/plain")) }) {
                    Text(stringResource(R.string.template_import))
                }
            })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(stringResource(R.string.template_search)) }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (templates.isEmpty()) Text(stringResource(R.string.template_empty))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(templates.filter { it.name.contains(query, true) || it.description.contains(query, true) }, key = { it.id }) { template ->
                        var menu by remember(template.id) { mutableStateOf(false) }
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp)) {
                                Column(Modifier.weight(1f).clickable(enabled = !busy) {
                                    runOperation { check(onOpen(template.id)) { context.getString(R.string.template_open_failed) }; onClose() }
                                }) {
                                    Text(template.name, style = MaterialTheme.typography.titleMedium)
                                    if (template.description.isNotBlank()) Text(template.description, style = MaterialTheme.typography.bodySmall)
                                    template.shortcut?.let { Text("/$it", style = MaterialTheme.typography.labelMedium) }
                                }
                                Box {
                                    TextButton(enabled = !busy, onClick = { menu = true }) { Text(stringResource(R.string.template_actions)) }
                                    AgoraDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        TemplateMenuAction(R.string.template_edit, { menu = false }) { editing = template }
                                        TemplateMenuAction(R.string.template_duplicate, { menu = false }) { runOperation { withContext(Dispatchers.IO) { repository.save(template.duplicate()) } } }
                                        TemplateMenuAction(R.string.template_replace, { menu = false }) { replacing = template; importFile.launch(arrayOf("application/json", "text/plain")) }
                                        TemplateMenuAction(R.string.template_export, { menu = false }) { exporting = template; exportFile.launch("${template.shortcut ?: template.id}.agora-template.json") }
                                        TemplateMenuAction(R.string.template_delete, { menu = false }) { deleting = template }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        editing?.let { template ->
            TemplateMetadataDialog(template, busy, error, onDismiss = { editing = null }) { updated ->
                runOperation { withContext(Dispatchers.IO) { repository.save(updated, replace = true, expected = template) }; editing = null }
            }
        }
        deleting?.let { template ->
            AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text(stringResource(R.string.template_delete)) },
                text = { Text(template.name) }, confirmButton = {
                    TextButton(enabled = !busy, onClick = { runOperation { withContext(Dispatchers.IO) { repository.delete(template.id) }; deleting = null } }) {
                        Text(stringResource(R.string.template_delete), color = MaterialTheme.colorScheme.error)
                    }
                }, dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } })
        }
    }
}

@Composable
internal fun TemplateMetadataDialog(template: InteractiveTemplate, busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (InteractiveTemplate) -> Unit) {
    var name by remember(template.id) { mutableStateOf(template.name) }
    var description by remember(template.id) { mutableStateOf(template.description) }
    var shortcut by remember(template.id) { mutableStateOf(template.shortcut.orEmpty()) }
    var directory by remember(template.id) { mutableStateOf(template.sourceDirectory.orEmpty()) }
    val updated = template.copy(name = name.trim(), description = description.trim(), shortcut = shortcut.trim().ifBlank { null }, sourceDirectory = directory.trim().ifBlank { null }, linkedSkill = null)
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.template_edit)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.template_name)) })
            OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.template_description)) })
            OutlinedTextField(shortcut, { shortcut = it }, label = { Text(stringResource(R.string.template_shortcut)) }, supportingText = { Text(stringResource(R.string.template_shortcut_help)) })
            OutlinedTextField(directory, { directory = it }, label = { Text(stringResource(R.string.template_source_directory)) })
        }
    }, confirmButton = {
        TextButton(enabled = !busy && runCatching { updated.validate(); require(description.trim().split(Regex("\\s+")).filter(String::isNotEmpty).size <= 10) }.isSuccess, onClick = { onSave(updated) }) { Text(stringResource(R.string.save)) }
    }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun TemplateMenuAction(label: Int, onDismiss: () -> Unit, onAction: () -> Unit) {
    AgoraDropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
        onDismiss()
        onAction()
    })
}
