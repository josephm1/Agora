// Adapted from josephm1/LearnHarness-Mobile Android account connection code.
package com.newoether.agora.auth

import android.content.Context
import com.newoether.agora.model.ModelThinkingCapability
import com.newoether.agora.model.ThinkingLevels
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** App-local account owner. Billing is explicit; plan failures never select an API key. */
object ChatGptPlan {
    @Volatile private var app: Context? = null
    val changes = MutableStateFlow(0L)
    @JvmStatic fun initialize(context: Context) { app = context.applicationContext }
    private fun context() = checkNotNull(app) { "Open ChatGPT connection settings first" }
    @JvmStatic fun accounts(): ChatGptAccounts = ChatGptAccounts.get(context())
    @JvmStatic fun enabled(): Boolean = app?.getSharedPreferences("chatgpt-billing",0)?.getBoolean("usePlan",false) == true
    @JvmStatic fun activeId(): String? = runCatching { accounts().active()?.optString("client_id")?.takeIf { it.isNotBlank() } }.getOrNull()
    @JvmStatic fun configured(): Boolean = runCatching { val account=accounts().active(); account!=null && account.optBoolean("connected") && account.optBoolean("plan") }.getOrDefault(false)
    @JvmStatic fun setEnabled(enabled: Boolean) {
        check(!enabled || configured()) { "Connect ChatGPT and grant plan permission first" }
        check(context().getSharedPreferences("chatgpt-billing",0).edit().putBoolean("usePlan",enabled).commit())
        notifyChanged()
    }
    /** Selecting ChatGPT is sufficient; enable it only for the exact validated active account. */
    fun useConnectedAccount(expectedId: String) {
        val owner = accounts()
        synchronized(owner) {
            val current = owner.active()
            check(current != null && current.optString("client_id") == expectedId &&
                current.optBoolean("connected") && current.optBoolean("plan")) {
                "Reconnect your ChatGPT account to continue."
            }
            setEnabled(true)
        }
    }
    /** Immutable per-account projection; callers freeze it with their existing request snapshot. */
    fun thinkingCapabilities(accountId: String): Map<String, ModelThinkingCapability> {
        val models = accounts().models(accountId)
        return buildMap {
            for (index in 0 until models.length()) {
                val entry = models.getJSONObject(index)
                catalogThinkingCapability(entry)?.let { put(entry.getString("slug"), it) }
            }
        }
    }
    internal fun catalogThinkingCapability(entry: JSONObject): ModelThinkingCapability? {
        val levels = entry.optJSONArray("supported_reasoning_levels")
            ?: entry.optJSONArray("supported_reasoning_efforts") ?: return null
        val recognized = (ThinkingLevels.effortValues + "none").toSet()
        val advertised = (0 until levels.length()).mapNotNull { index ->
            val item = levels.opt(index)
            val effort = if (item is JSONObject) item.optString("effort") else item as? String
            effort?.takeIf { it in recognized }
        }.toSet()
        if (advertised.isEmpty()) return null
        return ModelThinkingCapability(
            canDisableThinking = "none" in advertised,
            supportedEfforts = ThinkingLevels.effortValues.filter { it in advertised },
            supportsThinkingBudget = false,
        )
    }
    @JvmStatic fun token(account: String): String = accounts().token(account,OpenAiHttp())
    @JvmStatic fun notifyChanged() { changes.update { it+1 } }
}
