package com.newoether.agora.ui.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.newoether.agora.R
import com.newoether.agora.auth.ChatGptActivity
import com.newoether.agora.auth.ChatGptPlan
import com.newoether.agora.data.repository.SettingsRepository
import com.newoether.agora.ui.components.Icon
import com.newoether.agora.ui.components.Text

/** Both setup and Settings register the selected account model through the same result boundary. */
@Composable
internal fun rememberChatGptConnectionLauncher(settings: SettingsRepository, onConnected: () -> Unit = {}) =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        ChatGptPlan.notifyChanged()
        val selected = runCatching { ChatGptPlan.accounts().active()?.optString("model") }.getOrNull().orEmpty()
        if (result.resultCode == Activity.RESULT_OK && ChatGptPlan.enabled() && selected.isNotBlank()) {
            settings.addCustomModel("OpenAI", selected, "ChatGPT · $selected")
            settings.setEnabledModels(settings.enabledModels.value + "OpenAI:$selected")
            settings.setSelectedModel("OpenAI:$selected")
            onConnected()
        }
    }

@Composable
internal fun ChatGptConnectionSettingsGroup(settings: SettingsRepository) {
    val context = LocalContext.current
    val revision by ChatGptPlan.changes.collectAsState()
    val account = remember(revision) { runCatching { ChatGptPlan.accounts().active() }.getOrNull() }
    val launcher = rememberChatGptConnectionLauncher(settings)
    val connected = account?.optBoolean("connected") == true && ChatGptPlan.enabled()
    SettingsGroup(stringResource(R.string.chatgpt_title), items = listOf({
        SettingsItem(headlineContent = { Text(stringResource(if (connected) R.string.chatgpt_account else R.string.onboarding_use_chatgpt_action)) },
            supportingContent = { Text(if (connected) account!!.optString("label").substringBeforeLast(" · ") else stringResource(R.string.chatgpt_signin_help)) },
            leadingContent = { Icon(Icons.Default.AccountCircle, null) },
            modifier = Modifier.clickable { launcher.launch(Intent(context, ChatGptActivity::class.java)
                .putExtra(ChatGptActivity.EXTRA_START, !connected)) })
    }))
}
