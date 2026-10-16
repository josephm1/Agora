package com.newoether.agora.viewmodel

import android.content.Context
import com.newoether.agora.data.SkillManager
import com.newoether.agora.data.repository.SettingsRepository
import com.newoether.agora.model.*
import com.newoether.agora.skills.*
import com.newoether.agora.templates.*
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class SkillWorkflowSubmissionTest {
    @get:Rule val folder = TemporaryFolder()
    private fun template() = InteractiveTemplate(name = "Setup", description = "Collect inputs for this workflow",
        answer = InteractiveAnswer("Setup", "Collect inputs", "Choose", html = "<button>Start</button>"))
    private inner class Fixture(val card: InteractiveTemplate) {
        val skills = SkillManager(mockk<Context> { every { filesDir } returns folder.newFolder() })
        val templates = InteractiveTemplateRepository(folder.newFolder())
        val message = ChatMessage(id = "m", participant = Participant.MODEL, text = "", runId = "local",
            segments = listOf(card.segment()))
        val path = MutableStateFlow(listOf(message.copy(segments = null)))
        val current = MutableStateFlow<String?>("c")
        val hydration = mockk<ConversationMessagePayloadHydration>()
        val vm = mockk<ChatViewModel>(relaxed = true)
        val generation = mockk<MessageGenerationController>(relaxed = true)
        val origin = mockk<ChatClient>(relaxed = true)
        val target = ForegroundSendTarget("c", "c", "fresh", false, null, "model")
        val admission = mockk<ForegroundSendAdmission>()
        init {
            val settings = mockk<SettingsRepository>()
            every { settings.accessSkills } returns MutableStateFlow(true)
            every { settings.sandboxEnabled } returns MutableStateFlow(false)
            every { settings.sandboxSharedStorageEnabled } returns MutableStateFlow(false)
            every { vm.settings } returns settings
            every { vm.skillManager } returns skills
            every { vm.interactiveTemplates } returns templates
            every { vm.sandboxManager } returns null
            every { vm.currentConversationId } returns current
            every { vm.messages } returns path
            every { vm.messagePayloadHydration } returns hydration
            every { vm.isNewChatMode } returns MutableStateFlow(false)
            every { vm.newChatEntryId } returns MutableStateFlow(1L)
            every { vm.currentActiveModel } returns MutableStateFlow("model")
            coEvery { hydration.loadMessages("c", listOf("m"), any()) } returns listOf(message)
            every { generation.captureForegroundSendTarget(any(), any(), any(), any(), any(), any()) } returns target
            coEvery { generation.prepareForegroundSend(any(), any(), any()) } returns admission
            coEvery { generation.sendMessage(any(), any(), any(), any(), any()) } coAnswers {
                path.value += ChatMessage(id = "accepted", participant = Participant.USER, text = secondArg(), runId = "fresh")
                SendAcceptance.Direct("accepted", "c")
            }
        }
        suspend fun send(text: String, open: suspend (InteractiveTemplate, ForegroundSendTarget) -> LocalComposerResult = { _, _ -> error("Unexpected local open") }) =
            vm.submitTemplateFollowup("c", "m", card.id, card.answer, text, emptyList(), generation, origin,
                { error("Must not capture another workspace") }, open)
    }
    @Test fun beforeTemplateOpensLocallyAndStartEntersTheExistingSendPathOnce() = runTest {
        val setup = template()
        val card = bindSkillTemplate(setup, SkillTemplateFlow("examiner.md", "before", "Cardiology"))
        val f = Fixture(card)
        f.templates.save(setup)
        f.skills.saveDocument(SkillDocument("examiner.md", "Ask one question", SkillConfiguration("osce", beforeTemplate = setup.id)), "")
        var opened: InteractiveTemplate? = null
        val result = f.vm.localComposerSubmit(f.target, ConversationComposerSnapshot(text = "/osce", loaded = true), null) { t, _ ->
            opened = t; LocalComposerResult(SendAcceptance.Direct("setup", "c"))
        }
        assertNotNull(result?.acceptance)
        assertEquals("before", opened?.skillFlow?.phase)
        coVerify(exactly = 0) { f.generation.prepareForegroundSend(any(), any(), any()) }
        assertTrue(f.send("Questions: 12"))
        coVerify(exactly = 1) { f.generation.sendMessage(f.admission, match { it.contains("examiner.md") && it.contains("Questions: 12") }, emptyList(), any(), f.origin) }
        assertFalse(f.send("Questions: 12"))
        verify(exactly = 0) { f.vm.conversationComposer }
    }
    @Test fun afterContinueRunsTheConfiguredNextSkillOrOpensItsSetupWithoutCallingAi() = runTest {
        val setup = template()
        val card = bindSkillTemplate(template(), SkillTemplateFlow("examiner.md", "after", "Assessment gaps", "revision.md", "root-token", listOf("examiner.md")))
        val f = Fixture(card)
        f.templates.save(setup)
        f.skills.saveDocument(SkillDocument("revision.md", "Revise", SkillConfiguration(beforeTemplate = setup.id)), "")
        var next: InteractiveTemplate? = null
        assertTrue(f.send("Focus on examination") { t, _ -> next = t; LocalComposerResult(SendAcceptance.Direct("next-setup", "c")) })
        assertEquals("revision.md", next?.skillFlow?.skill)
        assertEquals("root-token", next?.skillFlow?.parent)
        assertTrue(next?.skillFlow?.input.orEmpty().contains("Assessment gaps"))
        coVerify(exactly = 0) { f.generation.prepareForegroundSend(any(), any(), any()) }
    }
    @Test fun afterContinuePassesCollectedDataAndActualParentToTheConfiguredSkill() = runTest {
        val card = bindSkillTemplate(template(), SkillTemplateFlow("examiner.md", "after", "Assessment gaps", "revision.md", "root-token", listOf("examiner.md")))
        val f = Fixture(card)
        f.skills.saveDocument(SkillDocument("revision.md", "Revise the selected gaps"), "")
        assertTrue(f.send("Focus on examination"))
        coVerify(exactly = 1) { f.generation.sendMessage(f.admission, match {
            it.contains("revision.md") && it.contains("root-token") && it.contains("Assessment gaps") && it.contains("Focus on examination")
        }, emptyList(), any(), f.origin) }
        assertFalse(f.send("Replay"))
    }
    @Test fun pendingDuplicateGestureAndSelectionChangeCannotSendOrRetarget() = runTest {
        val card = bindSkillTemplate(template(), SkillTemplateFlow("examiner.md", "before"))
        val f = Fixture(card)
        f.skills.saveDocument(SkillDocument("examiner.md", "Ask"), "")
        val gate = CompletableDeferred<List<ChatMessage>>()
        coEvery { f.hydration.loadMessages("c", listOf("m"), any()) } coAnswers { gate.await() }
        val pending = async { f.send("Start") }
        testScheduler.runCurrent()
        assertFalse(f.send("Start"))
        f.current.value = "other"
        gate.complete(listOf(f.message))
        assertFalse(pending.await())
        coVerify(exactly = 0) { f.generation.sendMessage(any(), any(), any(), any(), any()) }
    }
    @Test fun missingNextSkillNeverFallsBackToAnOrdinaryPrompt() = runTest {
        val card = bindSkillTemplate(template(), SkillTemplateFlow("examiner.md", "after", "Finished", "missing.md", "root", listOf("examiner.md")))
        val f = Fixture(card)
        assertFalse(f.send("Continue"))
        coVerify(exactly = 0) { f.generation.prepareForegroundSend(any(), any(), any()) }
        coVerify(exactly = 0) { f.generation.sendMessage(any(), any(), any(), any(), any()) }
    }
}
