package com.newoether.agora.ui.chat.bottombar

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w411dp-h914dp")
class ComposerSettingsAccessTest {
    @get:Rule val compose = createComposeRule()
    @Test fun settingsAndThinkingRemainAvailableFromTheComposerWithoutTheToolbar() {
        var opened = 0
        val menu = mutableStateOf<String?>("tools")
        val thinking = mutableStateOf(false)
        compose.setContent { MaterialTheme { Column {
            ComposerToolsMenuContent(
                activeMenuState = menu, showThinkingSheetState = thinking,
                showOpenAiServiceTierSheetState = mutableStateOf(false),
                showLowContextMode = false, lowContextModeEnabled = false, onLowContextModeToggle = {},
                thinkingEnabled = true, thinkingLevel = "medium", thinkingBudgetEnabled = false,
                thinkingBudgetTokens = 4096, onThinkingToggle = {}, thinkingCanDisable = false,
                selectedProvider = "OpenAI", isModelValid = true,
                codeExecutionEnabled = false, onCodeExecutionToggle = {}, googleSearchEnabled = false,
                onGoogleSearchToggle = {}, capabilityControlsEnabled = true,
                openAiServiceTierAvailable = false, openAiServiceTierEnabled = false,
                openAiServiceTier = "auto", onOpenAiServiceTierToggle = {},
                openAiWebSearchAvailable = false, openAiWebSearchEnabled = false, onOpenAiWebSearchToggle = {},
                showWebSearch = false, webSearchEnabled = false, onWebSearchToggle = {},
                showShell = false, shellEnabled = false, onShellToggle = {},
                canCompact = false, isCompacting = false, onCompactClick = {}, onAdvancedClick = {},
                onOpenSettings = { opened++ },
            )
        } } }
        compose.onNodeWithText("Settings").assertIsDisplayed().performClick()
        assertEquals(1, opened)
        assertNull(menu.value)
        compose.onNodeWithText("Always on").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Thinking").performClick()
        assertTrue(thinking.value)
    }
}
