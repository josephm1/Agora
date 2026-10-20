package com.newoether.agora.ui.chat

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CollapsibleChatHeaderUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun messageDragReclaimsHeightAndTheRevealButtonRestoresTheToolbar() {
        var height = 0
        compose.setContent {
            MaterialTheme {
                val state = rememberCollapsibleChatHeader("fixture", enabled = true)
                Column(Modifier.fillMaxSize()) {
                    CollapsibleChatHeader(state, reading = false, onHeightChanged = { height = it }) {
                        Box(Modifier.fillMaxWidth().height(180.dp)) { Text("Toolbar content") }
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("messages").nestedScroll(state.connection)) {
                        items(100) { Text("Message $it", Modifier.height(64.dp)) }
                    }
                }
            }
        }
        var expanded = 0
        compose.runOnIdle { expanded = height }
        compose.onNodeWithTag("messages").performTouchInput { swipeUp() }
        compose.onNodeWithContentDescription("Show toolbar").assertIsDisplayed()
        compose.onNodeWithText("Toolbar content").assertDoesNotExist()
        compose.runOnIdle { assertTrue(height < expanded) }
        compose.onNodeWithContentDescription("Show toolbar").performClick()
        compose.onNodeWithText("Toolbar content").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show toolbar").assertDoesNotExist()
        compose.onNodeWithTag("messages").performTouchInput { swipeUp() }
        compose.onNodeWithContentDescription("Show toolbar").assertIsDisplayed()
        compose.onNodeWithTag("messages").performTouchInput { swipeDown() }
        compose.onNodeWithText("Toolbar content").assertIsDisplayed()
    }
}
