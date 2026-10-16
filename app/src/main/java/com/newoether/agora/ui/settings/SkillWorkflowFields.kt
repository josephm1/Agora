package com.newoether.agora.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.newoether.agora.R
import com.newoether.agora.skills.SkillConfiguration
import com.newoether.agora.templates.InteractiveTemplate
import com.newoether.agora.ui.components.*
import com.newoether.agora.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SkillWorkflowFields(vm: ChatViewModel, filename: String, configuration: SkillConfiguration,
    onChange: (SkillConfiguration) -> Unit) {
    var templates by remember { mutableStateOf<List<InteractiveTemplate>>(emptyList()) }
    var skills by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(vm) {
        try { withContext(Dispatchers.IO) { vm.interactiveTemplates.list() to vm.skillManager.listFiles().map { it.name } }.let {
            templates = it.first; skills = it.second.filterNot { name -> name == filename }
        } }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { vm.emitSnackbar(failure.message.orEmpty()) }
    }
    SettingsGroup(title = stringResource(R.string.skill_workflow), items = buildList { add {
        WorkflowChoice(stringResource(R.string.skill_before), stringResource(R.string.skill_before_help),
            configuration.beforeTemplate, templates.map { it.id to it.name }) { onChange(configuration.copy(beforeTemplate = it)) }
    }; add {
        WorkflowChoice(stringResource(R.string.skill_after), stringResource(R.string.skill_after_help),
            configuration.afterTemplate, templates.map { it.id to it.name }) {
            onChange(configuration.copy(afterTemplate = it, nextSkill = configuration.nextSkill.takeIf { _ -> it != null }))
        }
    }; if (configuration.afterTemplate != null) add {
        WorkflowChoice(stringResource(R.string.skill_next), stringResource(R.string.skill_next_help),
            configuration.nextSkill, skills.map { it to it.removeSuffix(".md") }) { onChange(configuration.copy(nextSkill = it)) }
    } })
}

@Composable
private fun WorkflowChoice(title: String, help: String, selected: String?, choices: List<Pair<String, String>>, onChoose: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val none = stringResource(R.string.skill_workflow_none)
    androidx.compose.foundation.layout.Box {
        SettingsItem(headlineContent = { Text(title) }, supportingContent = { Text(help) },
            leadingContent = { Icon(Icons.Default.Extension, null) }, trailingContent = {
                TextButton(onClick = { expanded = true }, modifier = Modifier.semantics {
                    contentDescription = "$title: ${choices.firstOrNull { it.first == selected }?.second ?: selected ?: none}"
                }) {
                    Text(choices.firstOrNull { it.first == selected }?.second ?: selected ?: none, style = MaterialTheme.typography.labelLarge)
                }
            })
        AgoraDropdownMenu(expanded, { expanded = false }) {
            AgoraDropdownMenuItem(text = { Text(none) }, onClick = { expanded = false; onChoose(null) })
            choices.forEach { (id, label) -> AgoraDropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; onChoose(id) }) }
        }
    }
}
