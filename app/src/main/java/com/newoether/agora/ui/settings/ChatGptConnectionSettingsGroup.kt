package com.newoether.agora.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.newoether.agora.auth.ChatGptActivity
import com.newoether.agora.auth.ChatGptPlan
import com.newoether.agora.data.repository.SettingsRepository
import com.newoether.agora.ui.components.Icon
import com.newoether.agora.ui.components.Text

@Composable
internal fun ChatGptConnectionSettingsGroup(settings: SettingsRepository) {
    val context=LocalContext.current
    val revision by ChatGptPlan.changes.collectAsState()
    val account=remember(revision) { runCatching { ChatGptPlan.accounts().active() }.getOrNull() }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        ChatGptPlan.notifyChanged()
        val selected=runCatching { ChatGptPlan.accounts().active()?.optString("model") }.getOrNull().orEmpty()
        if(ChatGptPlan.enabled() && selected.isNotBlank()) {
            settings.addCustomModel("OpenAI",selected,"ChatGPT · $selected")
            settings.setEnabledModels(settings.enabledModels.value + "OpenAI:$selected")
            settings.setSelectedModel("OpenAI:$selected")
        }
    }
    SettingsGroup("ChatGPT account",items=listOf({
        SettingsItem(headlineContent={Text(if(account?.optBoolean("connected")==true) "Manage ChatGPT connection" else "Continue with ChatGPT")},
            supportingContent={Text(if(ChatGptPlan.enabled()) "ChatGPT plan selected · limits shared with your plan" else "Sign in and choose ChatGPT plan usage or API-key billing")},
            leadingContent={Icon(Icons.Default.AccountCircle,null)},
            modifier=Modifier.clickable { launcher.launch(Intent(context,ChatGptActivity::class.java)) })
    }))
}
