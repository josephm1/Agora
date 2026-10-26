package com.newoether.agora.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.*
import com.newoether.agora.R
import com.newoether.agora.data.SettingsManager
import com.newoether.agora.ui.auth.ChatGptConnectionScreen
import com.newoether.agora.ui.motion.ProvideAgoraMotionPolicy
import com.newoether.agora.ui.theme.AgoraTheme
import com.newoether.agora.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Presentation only: the existing app-scoped account owner retains OAuth, tokens and cancellation. */
class ChatGptActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SETUP = "chatgpt.setup"
        const val EXTRA_START = "chatgpt.start"
    }
    private var accounts: ChatGptAccounts? = null
    private val http = OpenAiHttp()
    private var account by mutableStateOf<JSONObject?>(null)
    private var saved by mutableStateOf(JSONArray())
    private var signingIn by mutableStateOf(false)
    private var working by mutableStateOf(true)
    private var error by mutableStateOf<String?>(null)
    private var managing by mutableStateOf(false)
    private var activate = false
    private var autoStarted = false
    private val accountChanged = Runnable { refresh() }
    private var refreshRequest = 0
    private var attemptedModelLoad: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        ChatGptPlan.initialize(this)
        activate = intent.getBooleanExtra(EXTRA_START, false) || savedInstanceState?.getBoolean("activate") == true
        autoStarted = savedInstanceState?.getBoolean("started") == true
        val settings = SettingsManager(this)
        setContent {
            val eInk by settings.eInkMode.collectAsState(initial = false)
            val reduceMotion by settings.reduceMotion.collectAsState(initial = false)
            val theme by settings.themeMode.collectAsState(initial = "FOLLOW_DEVICE")
            val font by settings.fontPreference.collectAsState(initial = "app_default")
            ProvideAgoraMotionPolicy(reduceMotion, eInk) {
                AgoraTheme(themeMode = runCatching { ThemeMode.valueOf(theme) }.getOrDefault(ThemeMode.FOLLOW_DEVICE), eInkMode = eInk, fontPreference = font) {
                    ChatGptConnectionScreen(account, saved, signingIn, working, error,
                        setup = intent.getBooleanExtra(EXTRA_SETUP, false), managing = managing,
                        onBack = { if (managing) { managing = false; error = null } else finish() },
                        onSignIn = { signIn() }, onCancel = { activate = false; accounts?.cancelSignIn(); refresh() },
                        onModel = { model -> val id = account?.optString("client_id"); if (id != null) perform { accounts!!.model(id, model) } },
                        onRetry = { val id = account?.optString("client_id"); if (id != null) { attemptedModelLoad = id; perform { accounts!!.refreshModels(id, http) } } },
                        onContinue = { finishConnection() }, onManage = { managing = true },
                        onAddAccount = { signIn(newAccount = true) },
                        onSelectAccount = { id -> activate = false; attemptedModelLoad = null; perform { accounts!!.select(id) } },
                        onSignOut = { val id = account?.optString("client_id"); if (id != null) perform { accounts!!.signOut(id, http) } },
                        onUsage = { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(OpenAiHttp.USAGE))) },
                        onDiagnostics = { lifecycleScope.launch { error = withContext(Dispatchers.IO) { accounts?.diagnosticSummary() } } },
                        onUseApiKey = { ChatGptPlan.setEnabled(false); setResult(RESULT_CANCELED); finish() })
                }
            }
        }
        loadAccounts()
    }

    private fun loadAccounts(startAfterLoad: Boolean = false) {
        working = true; error = null
        lifecycleScope.launch {
            try {
                accounts = withContext(Dispatchers.IO) { ChatGptPlan.accounts() }
                working = false
                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) accounts!!.observe(accountChanged)
                if (startAfterLoad) signIn() else refresh()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { working = false; error = ChatGptAccounts.safeError(failure) }
        }
    }

    override fun onResume() { super.onResume(); accounts?.observe(accountChanged); if (accounts != null) refresh() }
    override fun onPause() { accounts?.removeObserver(accountChanged); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("started", autoStarted); outState.putBoolean("activate", activate)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { accounts?.removeObserver(accountChanged); http.cancel(); super.onDestroy() }

    private fun refresh() {
        val owner = accounts ?: return
        val request = ++refreshRequest
        lifecycleScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { owner.active() to owner.accounts() }
                if (request != refreshRequest || isFinishing) return@launch
                account = snapshot.first; saved = snapshot.second; signingIn = owner.isSigningIn
                val current = account
                val notice = owner.takeNotice()
                if (notice.isNotBlank() && (current?.optBoolean("connected") != true || !current.optBoolean("plan") || current.optJSONArray("models").let { it == null || it.length() == 0 })) error = notice
                if (activate && !signingIn && !ChatGptPlan.enabled() && current?.optBoolean("connected") == true && current.optBoolean("plan")) {
                    withContext(Dispatchers.IO) { ChatGptPlan.useConnectedAccount(current.getString("client_id")) }
                    if (request != refreshRequest || isFinishing) return@launch
                }
                if (intent.getBooleanExtra(EXTRA_START, false) && !autoStarted) {
                    autoStarted = true
                    if (!signingIn && (current?.optBoolean("connected") != true || !current.optBoolean("plan"))) {
                        signIn(); return@launch
                    }
                }
                if (!signingIn && current?.optBoolean("connected") == true && current.optBoolean("plan") && current.optJSONArray("models").let { it == null || it.length() == 0 }) {
                    val id = current.getString("client_id")
                    if (attemptedModelLoad != id) {
                        attemptedModelLoad = id
                        perform { owner.refreshModels(id, http) }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { working = false; error = ChatGptAccounts.safeError(failure) }
        }
    }

    private fun signIn(newAccount: Boolean = false) {
        activate = true; autoStarted = true; error = null
        val owner = accounts ?: run { loadAccounts(startAfterLoad = true); return }
        lifecycleScope.launch {
            working = true
            try {
                val url = withContext(Dispatchers.IO) {
                    val id = if (newAccount) "" else owner.active()?.optString("client_id") ?: owner.interruptedClient()
                    owner.startSignIn(id, true)
                }
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { owner.cancelSignIn(); error = ChatGptAccounts.safeError(failure) }
            finally { working = false; refresh() }
        }
    }

    private fun perform(work: () -> Unit) {
        if (working || signingIn) return
        working = true; error = null
        lifecycleScope.launch {
            try { withContext(Dispatchers.IO) { work() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = ChatGptAccounts.safeError(failure) }
            finally { working = false; refresh() }
        }
    }

    private fun finishConnection() {
        val selected = account ?: return
        if (working || signingIn || selected.optString("model").isBlank()) return
        working = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { ChatGptPlan.useConnectedAccount(selected.getString("client_id")) }
                setResult(RESULT_OK, Intent().putExtra("model", selected.getString("model")))
                finish()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { working = false; error = ChatGptAccounts.safeError(failure) }
        }
    }
}
