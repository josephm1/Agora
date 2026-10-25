package com.newoether.agora.api.openai

import com.newoether.agora.api.*
import com.newoether.agora.api.util.resolvedThinking
import com.newoether.agora.model.ThinkingProviderFamily
import com.newoether.agora.util.Constants
import com.newoether.agora.auth.ChatGptPlan
import com.newoether.agora.auth.OpenAiHttp
import com.newoether.agora.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn

class OpenAiProvider : BaseOpenAiProvider() {
    override val name: String = Constants.PROVIDER_OPENAI
    override val defaultBaseUrl: String = "https://api.openai.com/v1"

    override fun generateResponse(messages: List<ChatMessage>, config: ProviderConfig): Flow<StreamEvent> = flow {
        val account = config.chatGptAccountId ?: if (ChatGptPlan.enabled()) {
            checkNotNull(ChatGptPlan.activeId()) { "Reconnect ChatGPT in OpenAI settings. No API-key fallback was used." }
        } else null
        val route = if (account != null) {
            val active = ChatGptPlan.accounts().accounts()
            check((0 until active.length()).any { active.getJSONObject(it).optString("client_id") == account && active.getJSONObject(it).optBoolean("plan") }) { "ChatGPT plan permission is unavailable. Reconnect this account in OpenAI settings." }
            config.copy(chatGptAccountId = account, apiKey = ChatGptPlan.token(account), baseUrl = OpenAiHttp.API,
                responsesApiEnabled = true, maxTokens = null, temperature = null, topP = null,
                frequencyPenalty = null, presencePenalty = null, openAiServiceTier = null)
        } else config
        emitAll(super.generateResponse(messages, route))
    }.flowOn(Dispatchers.IO)

    /**
     * Chat Completions `reasoning_effort` accepts none/minimal/low/medium/high/xhigh/max. "none" is
     * how the API expresses thinking off, and a model documented without an effort selector simply
     * omits the field. The value comes from the model's capability entry, not from a model-name test.
     */
    override fun customizeRequest(request: OpenAiChatRequest, config: ProviderConfig): OpenAiChatRequest {
        val resolved = config.resolvedThinking(ThinkingProviderFamily.OPENAI)
        val effort = if (resolved.disabled) {
            "none".takeIf { resolved.capability.supportsEffort }
        } else {
            resolved.effort
        }
        return if (effort == null) request else request.copy(reasoningEffort = effort)
    }
    // Reasoning/content parsing uses BaseOpenAiProvider's default (reasoning_content + content).
}
