package com.newoether.agora.api.openai

import android.content.Context
import android.content.pm.ApplicationInfo
import com.newoether.agora.api.*
import com.newoether.agora.model.*
import com.newoether.agora.util.DebugLog
import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/** Captures the production Responses serializer's final HTTP request without live credentials. */
class ChatGptResponsesRequestTest {
    @Before fun logging() {
        val context = mockk<Context>()
        every { context.applicationInfo } returns ApplicationInfo().apply { flags = 0 }
        DebugLog.forceEnabled = false
        DebugLog.init(context)
    }

    @Test fun planPreservesInstructionsOutsideInputAndCompletesStreaming() = Server().use { server ->
        val messages = listOf(ChatMessage(text = "hello", participant = Participant.USER))
        val body = server.capture(config(server).copy(systemPrompt = "Keep my instructions intact."), messages)
        assertEquals("Keep my instructions intact.", body["instructions"]!!.jsonPrimitive.content)
        assertEquals(listOf("user"), body["input"]!!.jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content })
        assertEquals("hello", body["input"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content)
        assertFalse(body["store"]!!.jsonPrimitive.boolean)
        assertTrue(body["stream"]!!.jsonPrimitive.boolean)
        assertEquals("hello", messages.single().text)
    }

    @Test fun ordinaryApiResponsesKeepTheirExistingSystemRole() = Server().use { server ->
        val body = server.capture(config(server).copy(chatGptAccountId = null, systemPrompt = "API instructions"))
        assertFalse(body.containsKey("instructions"))
        assertEquals("system", body["input"]!!.jsonArray.first().jsonObject["role"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("store"))
    }

    @Test fun everySolEffortReachesTheFinalRequest() {
        for (effort in listOf("low", "medium", "high", "xhigh", "max")) Server().use { server ->
            val body = server.capture(config(server).copy(thinkingLevel = effort))
            assertEquals(effort, body["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
            assertEquals("auto", body["reasoning"]!!.jsonObject["summary"]!!.jsonPrimitive.content)
        }
    }

    @Test fun legacyOffAndMinimalResolveToTheLowestValidEffort() {
        for (enabled in listOf(false, true)) Server().use { server ->
            val config = config(server).copy(thinkingEnabled = enabled, thinkingLevel = "minimal")
            val body = server.capture(config)
            assertEquals("low", body["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
            assertEquals("minimal", config.thinkingLevel)
        }
    }

    @Test fun selectedModelCapabilityOffersExactlyTheSupportedThinkingChoices() {
        val capability = com.newoether.agora.data.thinkingCapabilityForSelectedModel("OpenAI:gpt-6.1-sol", emptyList())
        assertEquals(listOf("low", "medium", "high", "xhigh", "max"), capability.supportedEfforts)
        assertFalse(capability.canDisableThinking)
        assertFalse(capability.supportsThinkingBudget)
    }

    @Test fun discoveredLevelsOverrideDefaultsAndStayBoundToTheirModel() = Server().use { server ->
        val discovered = ModelThinkingCapability(canDisableThinking = false, supportedEfforts = listOf("high", "max"), supportsThinkingBudget = false)
        val body = server.capture(config(server).copy(thinkingLevel = "low", chatGptThinkingCapabilities = mapOf("gpt-6.1-sol" to discovered)))
        assertEquals("high", body["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        Server().use { other ->
            val fallback = other.capture(config(other).copy(thinkingLevel = "low", chatGptThinkingCapabilities = mapOf("different-model" to discovered)))
            assertEquals("low", fallback["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        }
    }
    @Test fun catalogueOffIsExplicitAndDoesNotRequestASummary() = Server().use { server ->
        val discovered = ModelThinkingCapability(canDisableThinking = true, supportedEfforts = listOf("low", "high"), supportsThinkingBudget = false)
        val body = server.capture(config(server).copy(thinkingEnabled = false, chatGptThinkingCapabilities = mapOf("gpt-6.1-sol" to discovered)))
        assertEquals("none", body["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertFalse(body["reasoning"]!!.jsonObject.containsKey("summary"))
    }
    private fun config(server: Server) = ProviderConfig(
        apiKey = "fixture-token", modelId = "gpt-6.1-sol", baseUrl = server.url,
        chatGptAccountId = "fixture-account", responsesApiEnabled = true,
        thinkingEnabled = true, thinkingLevel = "medium",
    )
    private class Server : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val url = "http://127.0.0.1:${server.address.port}/v1"
        private val bodies = CopyOnWriteArrayList<String>()
        init {
            server.createContext("/v1/responses") { exchange ->
                bodies += exchange.requestBody.bufferedReader().use { it.readText() }
                val bytes = ("data: {\"type\":\"response.output_text.delta\",\"sequence_number\":1,\"delta\":\"Hello\"}\n\n" +
                    "data: {\"type\":\"response.completed\",\"sequence_number\":2,\"response\":{\"status\":\"completed\"}}\n\n").toByteArray()
                exchange.responseHeaders.add("Content-Type", "text/event-stream")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }
        fun capture(config: ProviderConfig, messages: List<ChatMessage> = listOf(ChatMessage(text = "hello", participant = Participant.USER))): JsonObject {
            val provider = object : BaseOpenAiProvider() {
                override val name = "OpenAI"
                override val defaultBaseUrl = url
                override fun retryDelayMillis(attempt: Int) = 0L
            }
            val events = runBlocking { withTimeout(5_000) { provider.generateResponse(messages, config).toList() } }
            assertTrue("$events", events.none { it is StreamEvent.Error })
            assertEquals("Hello", events.filterIsInstance<StreamEvent.TextChunk>().joinToString("") { it.text })
            return Json.parseToJsonElement(bodies.single()).jsonObject
        }
        override fun close() = server.stop(0)
    }
}
