package com.newoether.agora.ui.chat.bottombar

import androidx.compose.foundation.BorderStroke
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
import com.newoether.agora.templates.InteractiveTemplate
import com.newoether.agora.templates.InteractiveTemplateRepository
import com.newoether.agora.ui.components.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Only an otherwise empty slash command offers local interfaces; ordinary prose stays untouched. */
internal fun templateMenuQuery(text: String): String? =
    text.takeIf { it.matches(Regex("/[a-zA-Z0-9-]{0,64}")) }?.drop(1)?.lowercase()

internal fun matchingMenuTemplates(query: String, templates: List<InteractiveTemplate>) =
    templates.filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) ||
        it.shortcut?.startsWith(query, ignoreCase = true) == true }.sortedBy { it.name.lowercase() }

/** Inline above the input: keeps keyboard focus and routes taps through local composer admission. */
@Composable
internal fun ComposerTemplateMenu(
    text: String,
    repository: InteractiveTemplateRepository,
    enabled: Boolean,
    onSelect: (InteractiveTemplate) -> Unit,
) {
    val query = templateMenuQuery(text) ?: return
    val templates by produceState<List<InteractiveTemplate>?>(null, repository, query) {
        value = null
        try { value = withContext(Dispatchers.IO) { matchingMenuTemplates(query, repository.list()) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = emptyList() }
    }
    val entries = templates ?: return
    Surface(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.small) {
        if (entries.isEmpty()) {
            Text(stringResource(if (query.isEmpty()) R.string.template_empty else R.string.search_no_results), modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall)
        } else LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
            items(entries, key = { it.id }) { template ->
                Row(modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onSelect(template) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("/", modifier = Modifier.padding(end = 12.dp), color = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(template.name, style = MaterialTheme.typography.bodyMedium)
                        val detail = template.shortcut?.let { "/$it" } ?: template.description
                        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
