package com.newoether.agora.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.skills.SkillConfiguration
import com.newoether.agora.templates.InteractiveTemplate
import com.newoether.agora.ui.components.*
import com.newoether.agora.ui.templates.TemplateMetadataDialog
import com.newoether.agora.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SkillWorkflowFields(vm: ChatViewModel, filename: String, configuration: SkillConfiguration,
    onChange: (SkillConfiguration) -> Unit) {
    var templates by remember { mutableStateOf<List<InteractiveTemplate>>(emptyList()) }
    var skills by remember { mutableStateOf<List<String>>(emptyList()) }
    var editing by remember { mutableStateOf<InteractiveTemplate?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(vm, filename) {
        try { withContext(Dispatchers.IO) { vm.interactiveTemplates.list() to vm.skillManager.listFiles().map { it.name } }.let {
            templates = it.first; skills = it.second.filterNot { name -> name == filename }
        } }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { vm.emitSnackbar(failure.message.orEmpty()) }
    }
    val templateChoices = templates.map { it.id to it.name }
    SettingsGroup(title = stringResource(R.string.skill_workflow), items = buildList {
        listOf(true, false).forEach { before ->
            val steps = if (before) configuration.beforeSteps else configuration.afterSteps
            val title = if (before) R.string.skill_before else R.string.skill_after
            fun update(values: List<String>) = onChange(if (before)
                configuration.withTemplates(values, configuration.afterSteps)
                else configuration.withTemplates(configuration.beforeSteps, values))
            steps.forEachIndexed { index, id -> add {
                Column {
                    WorkflowChoice(stringResource(title) + if (steps.size > 1) " ${index + 1}" else "",
                        stringResource(if (before) R.string.skill_before_help else R.string.skill_after_help), id, templateChoices,
                        onEdit = templates.firstOrNull { it.id == id }?.let { { editing = it } },
                        onChoose = { replacement -> update(steps.toMutableList().apply {
                            if (replacement == null) removeAt(index) else set(index, replacement)
                        }) })
                    Row(Modifier.padding(horizontal = 16.dp)) {
                        IconButton(enabled = index > 0, onClick = { update(steps.toMutableList().apply {
                            add(index - 1, removeAt(index))
                        }) }) { Icon(Icons.Default.ArrowUpward, stringResource(R.string.skill_step_up)) }
                        IconButton(enabled = index < steps.lastIndex, onClick = { update(steps.toMutableList().apply {
                            add(index + 1, removeAt(index))
                        }) }) { Icon(Icons.Default.ArrowDownward, stringResource(R.string.skill_step_down)) }
                        TextButton(onClick = {
                            val remaining = steps.filterIndexed { position, _ -> position != index }
                            onChange(if (before) configuration.withTemplates(remaining, configuration.afterSteps + id)
                                else configuration.withTemplates(configuration.beforeSteps + id, remaining))
                        }) { Text(stringResource(if (before) R.string.skill_move_after else R.string.skill_move_before)) }
                    }
                }
            } }
            add {
                WorkflowChoice(stringResource(title), stringResource(R.string.skill_add_step_help), null, templateChoices,
                    enabled = configuration.beforeSteps.size + configuration.afterSteps.size < 16,
                    onChoose = { id -> if (id != null) update(steps + id) })
            }
        }
        add {
            WorkflowChoice(stringResource(R.string.skill_next),
                stringResource(if (configuration.afterSteps.isEmpty()) R.string.skill_next_requires_after else R.string.skill_next_help),
                configuration.nextSkill, skills.map { it to it.removeSuffix(".md") },
                enabled = configuration.afterSteps.isNotEmpty(),
                onChoose = { onChange(configuration.copy(nextSkill = it)) })
        }
        add { Text(stringResource(R.string.skill_workflow_save_help), modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall) }
    })
    editing?.let { template ->
        TemplateMetadataDialog(template, saving, error, onDismiss = { editing = null; error = null }) { updated ->
            saving = true
            error = null
            scope.launch {
                try {
                    templates = withContext(Dispatchers.IO) {
                        vm.interactiveTemplates.save(updated, replace = true, expected = template)
                        vm.interactiveTemplates.list()
                    }
                    editing = null
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message.orEmpty() }
                finally { saving = false }
            }
        }
    }
}

@Composable
private fun WorkflowChoice(title: String, help: String, selected: String?, choices: List<Pair<String, String>>,
    enabled: Boolean = true, onEdit: (() -> Unit)? = null, onChoose: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val none = stringResource(R.string.skill_workflow_none)
    val label = choices.firstOrNull { it.first == selected }?.second ?: selected ?: none
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(help, style = MaterialTheme.typography.bodySmall)
        TextButton(enabled = enabled, onClick = { query = ""; expanded = true },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = if (selected == null && choices.isNotEmpty() && onEdit == null) "$title: $none" else "$title: $label" }) {
            if (selected == null) Icon(Icons.Default.Add, null)
            Text(if (selected == null) stringResource(R.string.skill_workflow_add) else label)
        }
        if (selected != null) Row {
            onEdit?.let { edit -> TextButton(onClick = edit) { Text(stringResource(R.string.template_edit)) } }
            TextButton(onClick = { onChoose(null) }, modifier = Modifier.semantics {
                contentDescription = "Remove $title"
            }) { Icon(Icons.Default.Remove, null); Text(stringResource(R.string.remove)) }
        }
    }
    if (expanded) AlertDialog(onDismissRequest = { expanded = false }, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.skill_workflow_search)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                item { TextButton(onClick = { expanded = false; onChoose(null) }) { Text(none) } }
                val matches = choices.filter { it.second.contains(query, ignoreCase = true) }
                items(matches, key = { it.first }) { (id, name) ->
                    TextButton(onClick = { expanded = false; onChoose(id) }, modifier = Modifier.fillMaxWidth()) { Text(name) }
                }
                if (matches.isEmpty()) item { Text(stringResource(R.string.skill_workflow_empty)) }
            }
        }
    }, confirmButton = {}, dismissButton = {
        TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.cancel)) }
    })
}
