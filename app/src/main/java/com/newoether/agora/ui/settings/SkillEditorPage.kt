package com.newoether.agora.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.skills.*
import com.newoether.agora.ui.components.Text
import com.newoether.agora.ui.components.Icon
import com.newoether.agora.ui.components.IconButton
import com.newoether.agora.ui.components.TextButton
import com.newoether.agora.viewmodel.ChatViewModel
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/** Owner-approved System Prompt editor structure with saved folders/tools/child references. */
@Composable
internal fun SkillEditorPage(viewModel: ChatViewModel, original: SkillDocument, initialDescription: String,
    busy: Boolean, onBack: () -> Unit, onSave: (SkillDocument, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val docs by viewModel.settings.showDocumentationFab.collectAsState()
    var name by remember(original) { mutableStateOf(original.name) }
    var description by remember(original) { mutableStateOf(initialDescription) }
    var instructions by remember(original) { mutableStateOf(original.instructions) }
    var shortcut by remember(original) { mutableStateOf(original.configuration.shortcut) }
    var tools by remember(original) { mutableStateOf(original.configuration.tools.joinToString(", ")) }
    var examples by remember(original) { mutableStateOf(original.configuration.callExamples) }
    var folders by remember(original) { mutableStateOf(original.configuration.folders) }
    var children by remember(original) { mutableStateOf(original.configuration.children) }
    var candidates by remember { mutableStateOf<List<SkillDocument>>(emptyList()) }
    var childPicker by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var folderAlias by remember { mutableStateOf("") }
    var folderPath by remember { mutableStateOf("") }
    var folderServer by remember { mutableStateOf("Local Sandbox") }
    var editingFolder by remember { mutableStateOf<String?>(null) }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                folderPath = uri.toString()
            } catch (failure: Exception) { viewModel.emitSnackbar(failure.message.orEmpty()) }
        }
    }
    val sourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                folderPath = uri.toString()
            } catch (failure: Exception) { viewModel.emitSnackbar(failure.message.orEmpty()) }
        }
    }
    LaunchedEffect(original) {
        try { candidates = withContext(Dispatchers.IO) { viewModel.skillManager.documents() }.filter {
            it.configuration.id != original.configuration.id
        } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { viewModel.emitSnackbar(failure.message.orEmpty()) }
    }
    fun document(): SkillDocument = SkillDocument(name.trim(), instructions, original.configuration.copy(
        shortcut = shortcut.trim().removePrefix("/"), tools = tools.split(',').map(String::trim).filter(String::isNotEmpty),
        folders = if (editingFolder == null) folders else folders.map {
            if (it.alias == editingFolder) SkillFolder(folderAlias.trim(), folderPath.trim(), folderServer.trim()) else it
        }, children = children, callExamples = examples))
    val valid = name.isNotBlank() && runCatching { document().encode() }.isSuccess
    fun addFolder() {
        try {
            val f = SkillFolder(folderAlias.trim(), folderPath.trim(), folderServer.trim())
            f.validate()
            check(folders.none { it.alias == f.alias && it.alias != editingFolder }) { context.getString(R.string.skill_folder_duplicate) }
            folders = if (editingFolder == null) folders + f else folders.map { if (it.alias == editingFolder) f else it }
            editingFolder = null; folderAlias = ""; folderPath = ""
        } catch (failure: Exception) { viewModel.emitSnackbar(failure.message.orEmpty()) }
    }
    CollapsingSettingsScaffold(title = stringResource(R.string.skills_edit), onBack = { if (!busy) onBack() },
        floatingActionButton = { if (docs) DocumentationFab("skills.md") },
        actions = { IconButton(onClick = { onSave(document(), description) }, enabled = valid && !busy && !checking) {
            Icon(Icons.Default.Save, stringResource(R.string.save))
        } }) {
        SettingsGroupColumn {
            SettingsGroup(title = stringResource(R.string.skill_identity), items = listOf({
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkillField(name, { name = it }, R.string.skills_name)
                    SkillField(description, { description = it }, R.string.skills_description)
                    SkillField(shortcut, { shortcut = it }, R.string.skill_shortcut)
                }
            }))
            SettingsGroup(title = stringResource(R.string.skills_content), items = listOf({
                OutlinedTextField(instructions, { instructions = it }, modifier = Modifier.fillMaxWidth().padding(16.dp)
                    .heightIn(min = 240.dp, max = 480.dp), label = { Text(stringResource(R.string.skills_content)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
            }))
            SettingsGroup(title = stringResource(R.string.skill_tools), items = listOf({
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.skill_tools_help), style = MaterialTheme.typography.bodySmall)
                    SkillField(tools, { tools = it }, R.string.skill_tool_names)
                    OutlinedTextField(examples, { examples = it }, label = { Text(stringResource(R.string.skill_call_examples)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 240.dp))
                }
            }))
            SettingsGroup(title = stringResource(R.string.skill_folders), items = buildList {
                folders.forEach { folder -> add {
                    SettingsItem(modifier = Modifier.clickable(enabled = !checking) {
                        folderAlias = folder.alias; folderPath = folder.location; folderServer = folder.server
                        editingFolder = folder.alias
                    }, headlineContent = { Text(folder.alias) },
                        supportingContent = { Text("${folder.server}\n${folder.location}") },
                        leadingContent = { Icon(Icons.Default.Folder, null) }, trailingContent = {
                            Row {
                                TextButton(enabled = !checking, onClick = {
                                    checking = true
                                    scope.launch {
                                        try { preview = checkSkillFolder(viewModel, context, folder) }
                                        catch (cancelled: CancellationException) { throw cancelled }
                                        catch (failure: Exception) { preview = failure.message.orEmpty() }
                                        finally { checking = false }
                                    }
                                }) { Text(stringResource(R.string.skill_check)) }
                                IconButton(onClick = { folders = folders - folder
                                    if (editingFolder == folder.alias) { editingFolder = null; folderAlias = ""; folderPath = "" }
                                }, enabled = !checking) {
                                    Icon(Icons.Default.Close, stringResource(R.string.delete))
                                }
                            }
                        })
                } }
                add {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.skill_folder_help), style = MaterialTheme.typography.bodySmall)
                        SkillField(folderAlias, { folderAlias = it }, R.string.skill_folder_alias)
                        SkillField(folderPath, { folderPath = it }, R.string.skill_folder_path)
                        SkillField(folderServer, { folderServer = it }, R.string.skill_folder_server)
                        Row {
                            TextButton(onClick = { treePicker.launch(null) }) { Text(stringResource(R.string.skill_choose_folder)) }
                            TextButton(onClick = { sourcePicker.launch(arrayOf("text/*", "application/json")) }) {
                                Text(stringResource(R.string.skill_choose_file))
                            }
                            TextButton(onClick = ::addFolder) { Text(stringResource(if (editingFolder == null) R.string.add else R.string.save)) }
                        }
                        if (preview.isNotEmpty()) Text(preview.take(4000), style = MaterialTheme.typography.bodySmall,
                            maxLines = 12, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            })
            SettingsGroup(title = stringResource(R.string.skill_children), items = buildList {
                children.forEach { child -> add {
                    val label = candidates.firstOrNull { it.configuration.id == child.id }?.name ?: child.id
                    SettingsItem(headlineContent = { Text(label) }, supportingContent = {
                        Text(stringResource(if (child.optional) R.string.skill_optional else R.string.skill_required))
                    }, leadingContent = { Icon(Icons.Default.Extension, null) }, trailingContent = {
                        Row {
                            Checkbox(child.optional, { value -> children = children.map { if (it.id == child.id) it.copy(optional = value) else it } })
                            IconButton(onClick = { children = children - child }) { Icon(Icons.Default.Close, stringResource(R.string.delete)) }
                        }
                    })
                } }
                add { Box {
                    SettingsAddItem(stringResource(R.string.skill_add_child), { childPicker = true })
                    com.newoether.agora.ui.components.AgoraDropdownMenu(childPicker, { childPicker = false }) {
                        candidates.filter { d -> children.none { it.id == d.configuration.id } }.forEach { child ->
                            com.newoether.agora.ui.components.AgoraDropdownMenuItem(text = { Text(child.name) }, onClick = {
                                childPicker = false
                                children = children + SkillDependency(child.configuration.id)
                            })
                        }
                    }
                } }
            })
            SettingsGroup(title = stringResource(R.string.skill_test), items = listOf({
                SettingsItem(headlineContent = { Text(stringResource(R.string.skill_test)) },
                    supportingContent = { Text(stringResource(R.string.skill_test_help)) },
                    leadingContent = { Icon(Icons.Default.PlayArrow, null) }, trailingContent = {
                        TextButton(enabled = valid && !busy, onClick = {
                            try {
                                document().encode()
                                preview = buildString {
                                    appendLine(context.getString(R.string.skill_test_summary))
                                    appendLine("/skill ${document().configuration.id}")
                                    appendLine("Tools: ${document().configuration.tools.joinToString()}")
                                    appendLine("Folders: ${folders.map { it.alias }.joinToString()}")
                                    appendLine("Child Skills: ${children.size}")
                                }
                            } catch (failure: Exception) { viewModel.emitSnackbar(failure.message.orEmpty()) }
                        }) { Text(stringResource(R.string.skill_check)) }
                    })
            }))
            if (docs) Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SkillField(value: String, onChange: (String) -> Unit, label: Int) {
    OutlinedTextField(value, onChange, label = { Text(stringResource(label)) }, modifier = Modifier.fillMaxWidth())
}

private suspend fun checkSkillFolder(vm: ChatViewModel, context: android.content.Context, folder: SkillFolder): String =
    withContext(Dispatchers.IO) {
        val resolved = mapSkillSharedPath(folder, android.os.Environment.getExternalStorageDirectory().absolutePath,
            vm.settings.sandboxEnabled.value && vm.settings.sandboxSharedStorageEnabled.value &&
                com.newoether.agora.sandbox.SandboxSharedStorageAccess.isGranted(context))
        if (folder.isDocumentTree) return@withContext SkillFolderAccess(context).list(folder, "", 0)
        val factory = object : com.newoether.agora.sandbox.SandboxManagerFactory {
            override fun create() = checkNotNull(vm.sandboxManager) { "Sandbox unavailable in this build" }
            override fun isAvailable() = vm.isSandboxFlavor
        }
        val ctx = com.newoether.agora.viewmodel.GenerationContext(shellEnabled = vm.settings.shellEnabled.value,
            shellDevices = vm.settings.shellDevices.value, sandboxEnabled = vm.settings.sandboxEnabled.value,
            sandboxSharedStorageEnabled = vm.settings.sandboxSharedStorageEnabled.value)
        val result = com.newoether.agora.tool.ShellToolProvider(factory.takeIf { vm.isSandboxFlavor }).executeOneShotResult(
            "file_glob", buildJsonObject { put("server", folder.server); put("path", skillFolderPath(resolved, ""));
                put("pattern", "*"); put("depth", 4) }.toString(), ctx).text
        skillFileListing(resolved, "", 0, result)
    }
