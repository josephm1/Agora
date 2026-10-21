package com.newoether.agora.ui.remote

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsNotEnabled
import com.newoether.agora.remote.RemoteQuestion
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemoteQuestionsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun approvalRequiresGestureAndDoesNotOfferPermanentAccess() {
        val replies = mutableListOf<Pair<String, JsonObject>>()
        val q = RemoteQuestion("exact-request", "approval", Json.parseToJsonElement("""{"description":"Needs approval","choices":["once","deny"]}""").jsonObject)
        compose.setContent { MaterialTheme { RemoteQuestions(listOf(q), true) { id, result -> replies += id to result } } }
        compose.onNodeWithText("Needs approval").assertExists()
        assertTrue(replies.isEmpty())
        compose.onNodeWithText("Always").assertDoesNotExist()
        compose.onNodeWithText("Approve once").performClick()
        assertEquals("exact-request", replies.single().first)
        assertEquals("once", replies.single().second["choice"]?.jsonPrimitive?.content)
        compose.onNodeWithText("Deny").assertIsNotEnabled()
    }
    @Test fun clarifyChoiceUsesNativeQuestionKeyAndLabel() {
        var reply: JsonObject? = null
        val q = RemoteQuestion("q", "clarify", Json.parseToJsonElement("""{"questions":[{"qid":"native-qid","question":"Choose","choices":["Alpha","Beta"]}]}""").jsonObject)
        compose.setContent { MaterialTheme { RemoteQuestions(listOf(q), true) { _, result -> reply = result } } }
        compose.onNodeWithText("Beta").performClick()
        assertNull(reply)
        compose.onNodeWithText("Send").performClick()
        assertEquals("Beta", reply?.get("answers")?.jsonObject?.get("native-qid")?.jsonPrimitive?.content)
    }
}
