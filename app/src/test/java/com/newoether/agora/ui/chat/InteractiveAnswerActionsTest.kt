package com.newoether.agora.ui.chat

import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.newoether.agora.ui.chat.message.InteractiveAnswerActions
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class InteractiveAnswerActionsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun actionsShareOneRowAtPhoneWidthAndKeepTheirOwnActions() {
        var saved = 0
        var expanded = 0
        compose.setContent { MaterialTheme { Box(Modifier.width(328.dp)) {
            InteractiveAnswerActions(false, { saved++ }, { expanded++ })
        } } }
        val save = compose.onNodeWithText("Save as template")
        val expand = compose.onNodeWithText("Expand interactive view")
        val left = save.fetchSemanticsNode().boundsInRoot
        val right = expand.fetchSemanticsNode().boundsInRoot
        assertTrue(left.right <= right.left)
        assertTrue(kotlin.math.abs(left.center.y - right.center.y) < 2f)
        save.performClick()
        expand.performClick()
        compose.runOnIdle { assertEquals(1, saved); assertEquals(1, expanded) }
    }
}
