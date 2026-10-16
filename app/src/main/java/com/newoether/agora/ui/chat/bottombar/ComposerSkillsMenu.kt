package com.newoether.agora.ui.chat.bottombar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.data.SkillManager
import com.newoether.agora.skills.SkillDocument
import com.newoether.agora.ui.components.Text
import kotlinx.coroutines.*

/** Discovery is an explicit local read; choosing a row prepares input without sending it. */
@Composable
internal fun ComposerSkillsMenu(text: String, manager: SkillManager, enabled: Boolean, onSelect: (String) -> Unit) {
    if (text != "/skills" && !text.startsWith("/skills ")) return
    val query = text.removePrefix("/skills").trim()
    val revision by manager.catalogRevision.collectAsState()
    val entries by produceState<List<SkillDocument>>(emptyList(), manager, query, revision) {
        try { value = withContext(Dispatchers.IO) { manager.documents() }.filter {
            it.name.contains(query, true) || it.configuration.shortcut.contains(query, true)
        } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = emptyList() }
    }
    Surface(shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(stringResource(R.string.skill_run_hint), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.heightIn(max = 240.dp)) {
                items(entries, key = { it.name }) { skill ->
                    Column(Modifier.fillMaxWidth().clickable(enabled = enabled) {
                        onSelect("/skill ${skill.configuration.id} ")
                    }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(skill.name.removeSuffix(".md"), style = MaterialTheme.typography.bodyMedium)
                        if (skill.configuration.shortcut.isNotBlank()) Text("/${skill.configuration.shortcut}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
