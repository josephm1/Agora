package com.newoether.agora.viewmodel

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.newoether.agora.data.local.ChatDatabase
import com.newoether.agora.data.local.ChatEntity
import com.newoether.agora.data.local.RunEntity
import com.newoether.agora.data.local.MessageEntity
import com.newoether.agora.data.repository.ConversationRepository
import com.newoether.agora.model.*
import com.newoether.agora.templates.InteractiveStateStore
import com.newoether.agora.templates.InteractiveTemplate
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Room state and demand hydration with the selected topology's intentionally absent payload. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class InteractiveStateAccessTest {
    private lateinit var database: ChatDatabase
    private lateinit var repository: ConversationRepository
    private lateinit var hydration: ConversationMessagePayloadHydration
    private val owner = MutableStateFlow<String?>("chat")
    private val path = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val answer = InteractiveAnswer("Planner", "Source choices", "Open the planner", html = "<button>Go ahead</button>")
    private val template = InteractiveTemplate(name = "Planner", answer = answer)
    private lateinit var complete: ChatMessage
    private val viewModel = mockk<ChatViewModel>()
    private fun args(revision: Int = 0) = buildJsonObject {
        put("key", "food-planner.v1"); put("expectedRevision", revision); put("value", "saved")
    }
    @Before fun setup(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ChatDatabase::class.java).build()
        repository = ConversationRepository(database.chatDao(), database, scheduleMaintenance = {})
        hydration = ConversationMessagePayloadHydration(repository, context)
        database.chatDao().upsertConversation(ChatEntity("chat", "Planner", lastUpdated = 1, dataChangedAt = 1))
        database.chatDao().insertRun(RunEntity("local-run", "chat", null, RunStatus.COMPLETED,
            null, 1, 1, endedAt = 1, endReason = RunEndReason.entries.first()))
        database.chatDao().upsertMessage(MessageEntity(id = "card", conversationId = "chat", parentId = null,
            text = "", participant = Participant.MODEL, timestamp = 1, runId = "local-run",
            toolCallJson = Json.encodeToString(listOf(template.segment()))))
        complete = hydration.loadMessages("chat", listOf("card")).single()
        path.value = listOf(complete.copy(segments = null))
        every { viewModel.currentConversationId } returns owner
        every { viewModel.messages } returns path
        every { viewModel.messagePayloadHydration } returns hydration
        every { viewModel.convRepo } returns repository
    }
    @After fun cleanup() { database.close() }

    @Test fun selectedTopologyStubCanReadSaveAndReopenItsVerifiedCard() = runBlocking {
        assertNull(path.value.single().segments)
        assertTrue(viewModel.verifyInteractiveAnswer("chat", "card", template.id, answer))
        val empty = viewModel.accessInteractiveState("chat", "card", template.id, answer, "storageGet", args())
        assertEquals(JsonNull, empty.jsonObject["value"])
        val saved = viewModel.accessInteractiveState("chat", "card", template.id, answer, "storageSet", args())
        assertEquals(1, saved.jsonObject["revision"]!!.jsonPrimitive.int)
        val reopened = viewModel.accessInteractiveState("chat", "card", template.id, answer, "storageGet", args())
        assertEquals(JsonPrimitive("saved"), reopened.jsonObject["value"])
        assertNull("Access must not inflate selected topology", path.value.single().segments)
        val conversation = database.chatDao().getConversation("chat")!!
        assertEquals(1L, conversation.lastUpdated)
        assertTrue(conversation.dataChangedAt > 1)
    }
    @Test fun forgedCallOrAnswerNeverWrites() = runBlocking {
        for ((call, value) in listOf("foreign-call" to answer, template.id to answer.copy(html = "<p>Changed</p>"))) {
            val failure = runCatching { viewModel.accessInteractiveState("chat", "card", call, value, "storageSet", args()) }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertNull(database.chatDao().getInteractiveState("chat")!!.interactiveStateJson)
        }
    }
    @Test fun foreignConversationAndRemovedBranchCannotReadOrWrite() = runBlocking {
        owner.value = "other"
        assertTrue(runCatching { viewModel.accessInteractiveState("chat", "card", template.id, answer, "storageGet", args()) }.isFailure)
        owner.value = "chat"; path.value = emptyList()
        assertTrue(runCatching { viewModel.accessInteractiveState("chat", "card", template.id, answer, "storageSet", args()) }.isFailure)
        assertNull(database.chatDao().getInteractiveState("chat")!!.interactiveStateJson)
    }
    @Test fun revocationAtTheRoomCommitBoundaryRollsBackStateAndWatermark() = runBlocking {
        val proxy = mockk<ConversationRepository>()
        every { viewModel.convRepo } returns proxy
        val state = InteractiveStateStore(database)
        var checks = 0
        coEvery { proxy.accessInteractiveState(any(), any(), any(), any()) } coAnswers {
            val authorized = arg<() -> Boolean>(3)
            state.access(arg(0), arg(1), arg(2)) {
                if (++checks == 3) path.value = emptyList()
                authorized()
            }
        }
        val before = database.chatDao().getConversation("chat")
        assertTrue(runCatching { viewModel.accessInteractiveState("chat", "card", template.id, answer, "storageSet", args()) }.isFailure)
        assertEquals("Revocation must reach the post-CAS commit fence", 3, checks)
        assertEquals(before, database.chatDao().getConversation("chat"))
    }
    @Test fun aPresentDifferentPayloadStillRevokesTheViewer() = runBlocking {
        val proxy = mockk<ConversationRepository>()
        every { viewModel.convRepo } returns proxy
        var permitted: Boolean? = null
        coEvery { proxy.accessInteractiveState(any(), any(), any(), any()) } coAnswers {
            path.value = listOf(complete.copy(segments = emptyList()))
            val authorized = arg<() -> Boolean>(3)
            permitted = authorized()
            error("This interface is no longer active")
        }
        assertTrue(runCatching { viewModel.accessInteractiveState("chat", "card", template.id, answer, "storageSet", args()) }.isFailure)
        assertEquals(false, permitted)
        assertNull(database.chatDao().getInteractiveState("chat")!!.interactiveStateJson)
    }
}
