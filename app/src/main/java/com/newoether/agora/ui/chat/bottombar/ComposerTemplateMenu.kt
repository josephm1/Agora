package com.newoether.agora.ui.chat.bottombar

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.newoether.agora.R
import com.newoether.agora.data.SkillManager
import com.newoether.agora.skills.SkillAuthoring
import com.newoether.agora.templates.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One local slash picker. Metadata only; the selected Skill is loaded on invocation. */
@Composable
internal fun ComposerTemplateMenu(text: String, repository: InteractiveTemplateRepository, enabled: Boolean,
    onSelect: (InteractiveTemplate) -> Unit, skillManager: SkillManager? = null,
    skillsEnabled: Boolean = enabled, onSkillSelect: (String) -> Unit = {}) {
    val explicitSkills = text == "/skills" || text.startsWith("/skills ")
    val query = if (explicitSkills) text.removePrefix("/skills").trim() else templateMenuQuery(text) ?: return
    val revision = skillManager?.catalogRevision?.collectAsState()?.value
    val entries by produceState<Pair<List<InteractiveTemplate>, List<ComposerCommand>>?>(null, repository, skillManager, query, revision, explicitSkills) {
        value = null
        try { value = withContext(Dispatchers.IO) {
            val templates = if (explicitSkills) emptyList() else matchingMenuTemplates(query, repository.list())
            val skills = try { skillManager?.listFiles().orEmpty().toMutableList() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableListOf<SkillManager.SkillFileInfo>() }
            if (skillManager != null && skills.none { it.name == SkillAuthoring.NAME }) skills += SkillManager.SkillFileInfo(
                SkillAuthoring.NAME, SkillAuthoring.document.description)
            templates to localComposerCommands(query, templates, skills)
        } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = emptyList<InteractiveTemplate>() to emptyList() }
    }
    val (templates, loaded) = entries ?: return
    ComposerCommandMenu(loaded, enabled = enabled || skillsEnabled,
        emptyLabel = stringResource(if (query.isEmpty()) R.string.template_empty else R.string.search_no_results),
        canSelect = { if (it.key.startsWith("template:")) enabled else skillsEnabled }, onSelect = { entry ->
            if (entry.key.startsWith("template:")) {
                if (enabled) templates.firstOrNull { "template:${it.id}" == entry.key }?.let(onSelect)
            } else if (skillsEnabled) onSkillSelect(entry.input)
        })
}

internal fun localComposerCommands(query: String, templates: List<InteractiveTemplate>, skills: List<SkillManager.SkillFileInfo>): List<ComposerCommand> {
    val occupied = templates.mapNotNull { it.shortcut }.map(String::lowercase).toMutableSet()
    val names = templates.map { it.name.lowercase() }.toMutableSet()
    return templates.map { ComposerCommand("template:${it.id}", it.name, it.description, "") } +
        skills.sortedBy { it.name.lowercase() }.mapNotNull { skill ->
            val stem = skill.name.removeSuffix(".md")
            if (query.isNotEmpty() && !stem.contains(query, true) && !skill.shortcut.startsWith(query, true)) return@mapNotNull null
            val command = skill.shortcut.ifBlank { stem }
            if (!occupied.add(command.lowercase()) || !names.add(stem.lowercase())) return@mapNotNull null
            ComposerCommand("skill:${skill.name}", "/$command", skill.description, "/skill ${Json.encodeToString(skill.name)} ")
        }
}
