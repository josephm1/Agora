package com.newoether.agora.ui.settings

import androidx.compose.runtime.*
import com.newoether.agora.auth.ChatGptPlan
import org.json.JSONObject

/** Existing account-owner revision drives Settings; no network call or credential projection. */
@Composable
internal fun rememberChatGptSettingsAccount(): JSONObject? {
    val revision by ChatGptPlan.changes.collectAsState()
    return remember(revision) { runCatching { ChatGptPlan.accounts().active() }.getOrNull() }
}

internal fun chatGptCatalogModels(account: JSONObject?): List<String> {
    if (account?.optBoolean("connected") != true || !account.optBoolean("plan")) return emptyList()
    val models = account.optJSONArray("models") ?: return emptyList()
    return (0 until models.length()).mapNotNull { index ->
        models.optJSONObject(index)?.optString("slug")?.takeIf { it.isNotBlank() }?.let { "OpenAI:$it" }
    }.distinct()
}
