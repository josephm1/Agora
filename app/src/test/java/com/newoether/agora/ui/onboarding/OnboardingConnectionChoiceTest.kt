package com.newoether.agora.ui.onboarding

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.newoether.agora.data.CustomEndpointProtocol
import com.newoether.agora.util.Constants
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en")
class OnboardingConnectionChoiceTest {
    @get:Rule val compose = createComposeRule()

    private fun show(provider: String, preferPlan: Boolean = false, maxHeight: Int = 600) {
        compose.setContent {
            var plan by remember { mutableStateOf(preferPlan) }
            var key by remember { mutableStateOf("") }
            MaterialTheme {
                ApiKeyPage(provider, false, key, { key = it }, "", {}, CustomEndpointProtocol.OPENAI, {},
                    false, {}, false, {}, Modifier.heightIn(max = maxHeight.dp), useChatGpt = plan, onUseChatGpt = { plan = it },
                    chatGptConnection = { Text("Existing account connection") })
            }
        }
    }

    @Test fun openAiOffersBothChoicesAndSwitchingPreservesTheApiDraft() {
        show(Constants.PROVIDER_OPENAI)
        compose.onNodeWithText("Use an API Key").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("test-draft")
        compose.onNodeWithText("Continue with ChatGPT").performClick()
        compose.onNodeWithText("Existing account connection").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("Use an API Key").performClick()
        compose.onNodeWithText("Existing account connection").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertTextContains("test-draft")
    }

    @Test fun existingPlanChoiceStartsWithAccountConnectionInsteadOfAnApiKeyField() {
        show(Constants.PROVIDER_OPENAI, preferPlan = true)
        compose.onNodeWithText("Existing account connection").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test fun otherProvidersKeepTheirApiFormWithoutChatGptControls() {
        show(Constants.PROVIDER_GOOGLE)
        compose.onNodeWithText("Continue with ChatGPT").assertDoesNotExist()
        compose.onNodeWithText("Use an API Key").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertExists()
    }

    @Test fun accountControlsRemainReachableOnAShortScreen() {
        show(Constants.PROVIDER_OPENAI, preferPlan = true, maxHeight = 200)
        compose.onNodeWithText("Existing account connection").performScrollTo().assertIsDisplayed()
    }

    @Test fun planSelectionNeverFallsBackToTheApiCatalogueAndOtherProvidersRemainIndependent() {
        val catalogue = mapOf("OpenAI" to listOf("OpenAI:api-model"), "Google" to listOf("Google:gemini"))
        assertEquals(listOf("OpenAI:plan-model"), onboardingProviderModels("OpenAI", true, "plan-model", catalogue, emptyList()))
        assertEquals(emptyList<String>(), onboardingProviderModels("OpenAI", true, null, catalogue, emptyList()))
        assertEquals(emptyList<String>(), onboardingProviderModels("OpenAI", true, " ", catalogue, emptyList()))
        assertEquals(catalogue["OpenAI"], onboardingProviderModels("OpenAI", false, "plan-model", catalogue, emptyList()))
        assertEquals(catalogue["Google"], onboardingProviderModels("Google", true, "plan-model", catalogue, emptyList()))
        assertEquals(listOf("Local:gguf"), onboardingProviderModels(Constants.PROVIDER_LOCAL, true, "plan-model", catalogue, listOf("Local:gguf")))
    }
}
