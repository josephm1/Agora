package com.newoether.agora.ui.chat.message

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w536dp-h724dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkdownShareDialogTest {
    @get:Rule val compose=createComposeRule()
    private val source="Here is the draft.\n\n# Plan\n\nKeep **this** paragraph.\n\nWould you like more?"

    @Test fun defaultsToAllThenRemovesPrefaceAndCommentaryAcrossPages() {
        var shared:String?=null
        compose.setContent { MaterialTheme { MarkdownShareDialog(source,{}, {shared=it}) } }
        try { compose.waitUntil(10_000){compose.onAllNodesWithText("4/4").fetchSemanticsNodes().isNotEmpty()} }
        catch(e:Exception){throw AssertionError(compose.onNode(isDialog()).printToString(),e)}
        compose.onNodeWithText("Share Markdown").assertIsDisplayed()
        compose.onNodeWithText("Here is the draft.").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Would you like more?").performClick()
        compose.onNodeWithText("Share Markdown").performClick()
        compose.waitUntil(10_000){shared!=null}
        assertEquals("# Plan\n\nKeep **this** paragraph.",shared)
        assertTrue(source.startsWith("Here is"))
    }
    @Test fun emptySelectionCannotShareAndCancelDoesNotSubmit() {
        var dismissed=false;var shared=false
        compose.setContent { MaterialTheme { MarkdownShareDialog(source,{dismissed=true},{shared=true}) } }
        compose.waitUntil(10_000){compose.onAllNodesWithText("None").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("None").performClick()
        compose.onNodeWithText("Share Markdown").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed);assertFalse(shared)
    }
}
