package com.newoether.agora.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.ui.components.*
import com.newoether.agora.ui.settings.CollapsingSettingsScaffold
import com.newoether.agora.ui.settings.SettingsGroup
import com.newoether.agora.ui.settings.SettingsItem
import org.json.JSONArray
import org.json.JSONObject

/** Setup presents account → model → continue. Maintenance is disclosed on a separate screen. */
@Composable
internal fun ChatGptConnectionScreen(
    account: JSONObject?, saved: JSONArray, signingIn: Boolean, working: Boolean, error: String?,
    setup: Boolean, managing: Boolean, onBack: () -> Unit, onSignIn: () -> Unit,
    onCancel: () -> Unit, onModel: (String) -> Unit, onRetry: () -> Unit,
    onContinue: () -> Unit, onManage: () -> Unit, onAddAccount: () -> Unit,
    onSelectAccount: (String) -> Unit, onSignOut: () -> Unit, onUsage: () -> Unit,
    onDiagnostics: () -> Unit, onUseApiKey: () -> Unit = {},
) {
    val connected = account?.optBoolean("connected") == true && account.optBoolean("plan")
    val models = account?.optJSONArray("models") ?: JSONArray()
    val selected = account?.optString("model").orEmpty()
    val email = account?.optString("label").orEmpty().substringBeforeLast(" · ")
    var choosingModel by remember(account?.optString("client_id")) { mutableStateOf(false) }
    var choosingAccount by remember(account?.optString("client_id")) { mutableStateOf(false) }
    var confirmSignOut by remember(account?.optString("client_id")) { mutableStateOf(false) }
    val enabled = !working && !signingIn
    val selectedName = (0 until models.length()).map { models.getJSONObject(it) }
        .firstOrNull { it.optString("slug") == selected }?.optString("display_name") ?: selected

    CollapsingSettingsScaffold(
        title = stringResource(if (managing) R.string.chatgpt_manage_account else R.string.chatgpt_title),
        onBack = onBack,
    ) {
        if (managing) {
            SettingsGroup(stringResource(R.string.chatgpt_account), items = listOf({
                ConnectionRow(R.string.chatgpt_switch_account, email, Icons.Default.AccountCircle,
                    enabled && saved.length() > 1) { choosingAccount = true }
            }, {
                ConnectionRow(R.string.chatgpt_add_account, stringResource(R.string.chatgpt_add_account_help), Icons.Default.PersonAdd, enabled, onAddAccount)
            }, {
                ConnectionRow(R.string.chatgpt_sign_out, email, Icons.Default.Logout, enabled && account?.optBoolean("connected") == true) { confirmSignOut = true }
            }, {
                ConnectionRow(R.string.onboarding_use_api_key, stringResource(R.string.chatgpt_api_help), Icons.Default.Key, enabled, onUseApiKey)
            }))
            SettingsGroup(stringResource(R.string.chatgpt_support), items = listOf({
                ConnectionRow(R.string.chatgpt_refresh_models, stringResource(R.string.chatgpt_refresh_help), Icons.Default.Refresh, enabled && connected, onRetry)
            }, {
                ConnectionRow(R.string.chatgpt_usage, stringResource(R.string.chatgpt_usage_help), Icons.Default.OpenInNew, true, onUsage)
            }, {
                ConnectionRow(R.string.chatgpt_diagnostics, stringResource(R.string.chatgpt_diagnostics_help), Icons.Default.Info, enabled, onDiagnostics)
            }))
            if (error != null) Text(error, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(stringResource(when {
                signingIn -> R.string.chatgpt_browser_help
                connected -> R.string.chatgpt_connected_help
                else -> R.string.chatgpt_signin_help
            }), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            if (connected && !signingIn) {
                SettingsGroup(stringResource(R.string.chatgpt_account), items = listOf({
                    SettingsItem(headlineContent = { Text(email) },
                        supportingContent = { Text(stringResource(R.string.chatgpt_connected)) },
                        leadingContent = { Icon(Icons.Default.AccountCircle, null) })
                }, {
                    ConnectionRow(R.string.chatgpt_model,
                        selectedName.ifBlank { stringResource(R.string.chatgpt_choose_model) },
                        Icons.Default.AutoAwesome, enabled && models.length() > 0) { choosingModel = true }
                }))
            }
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            val label = when {
                working -> R.string.chatgpt_loading
                signingIn -> R.string.chatgpt_cancel_signin
                !connected -> if (error == null) R.string.onboarding_use_chatgpt_action else R.string.chatgpt_retry
                models.length() == 0 -> R.string.chatgpt_retry
                else -> R.string.onboarding_continue
            }
            Button(onClick = when {
                signingIn -> onCancel
                !connected -> onSignIn
                models.length() == 0 -> onRetry
                else -> onContinue
            }, enabled = !working && (signingIn || !connected || models.length() == 0 || selected.isNotBlank()),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp)) {
                Text(stringResource(label))
            }
            if (connected && !setup) TextButton(onClick = onManage, modifier = Modifier.padding(8.dp), enabled = enabled) {
                Text(stringResource(R.string.chatgpt_manage_account))
            }
        }
    }
    if (choosingModel) AlertDialog(onDismissRequest = { choosingModel = false },
        title = { Text(stringResource(R.string.chatgpt_choose_model)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            for (index in 0 until models.length()) {
                val item = models.getJSONObject(index)
                Row(Modifier.fillMaxWidth().clickable { onModel(item.getString("slug")); choosingModel = false }) {
                    RadioButton(selected = item.optString("slug") == selected, onClick = { onModel(item.getString("slug")); choosingModel = false })
                    Text(item.optString("display_name", item.getString("slug")), Modifier.padding(vertical = 12.dp))
                }
            }
        } }, confirmButton = { TextButton(onClick = { choosingModel = false }) { Text(stringResource(R.string.cancel)) } })
    if (choosingAccount) AlertDialog(onDismissRequest = { choosingAccount = false },
        title = { Text(stringResource(R.string.chatgpt_switch_account)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            for (index in 0 until saved.length()) {
                val item = saved.getJSONObject(index)
                TextButton(onClick = { onSelectAccount(item.getString("client_id")); choosingAccount = false }) {
                    Text(item.optString("label").substringBeforeLast(" · "))
                }
            }
        } }, confirmButton = { TextButton(onClick = { choosingAccount = false }) { Text(stringResource(R.string.cancel)) } })
    if (confirmSignOut) AlertDialog(onDismissRequest = { confirmSignOut = false },
        title = { Text(stringResource(R.string.chatgpt_sign_out)) },
        text = { Text(stringResource(R.string.chatgpt_sign_out_help)) },
        confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text(stringResource(R.string.chatgpt_sign_out)) } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun ConnectionRow(title: Int, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean, onClick: () -> Unit) {
    SettingsItem(modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        headlineContent = { Text(stringResource(title)) }, supportingContent = { Text(description) },
        leadingContent = { Icon(icon, null) })
}
