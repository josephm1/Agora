package com.newoether.agora.skills

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.newoether.agora.data.SkillManager
import com.newoether.agora.data.repository.SettingsRepository
import com.newoether.agora.ui.settings.SettingsSkillsPage
import com.newoether.agora.viewmodel.ChatViewModel
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SkillEditorUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private fun vm(manager: SkillManager): ChatViewModel {
        val vm = mockk<ChatViewModel>(relaxed = true)
        val settings = mockk<SettingsRepository>(relaxed = true)
        every { vm.skillManager } returns manager
        every { vm.settings } returns settings
        every { vm.emitSnackbar(any(), any(), any()) } answers { System.err.println("SKILL UI ERROR: " + firstArg<String>()) }
        every { settings.accessSkills } returns MutableStateFlow(true)
        every { settings.showDocumentationFab } returns MutableStateFlow(false)
        every { vm.interactiveTemplates } returns com.newoether.agora.templates.InteractiveTemplateRepository(context)
        return vm
    }
    @Test fun editSaveReopenAndCancelUseTheSavedDocument() {
        val manager = SkillManager(context)
        manager.listFiles().forEach { manager.deleteFile(it.name) }
        val doc = SkillDocument("ui-examiner.md", "Original examiner instructions", SkillConfiguration())
        manager.saveDocument(doc, "Teaching workflow")
        val vm = vm(manager)
        compose.setContent { MaterialTheme { SettingsSkillsPage(vm, {}) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("ui-examiner").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("ui-examiner").performScrollTo().performClick()
        try { compose.waitUntil(10000) { compose.onAllNodesWithText("Original examiner instructions").fetchSemanticsNodes().isNotEmpty() } }
        catch (error: Throwable) { throw AssertionError(compose.onRoot().printToString(), error) }
        compose.onNodeWithText("Original examiner instructions").performScrollTo().performTextReplacement("Changed instructions")
        compose.onNodeWithContentDescription("Save").performClick()
        compose.waitUntil(10000) { manager.document(doc.name).instructions == "Changed instructions" }
        compose.waitUntil(10000) { compose.onAllNodesWithText("ui-examiner").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("ui-examiner").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Changed instructions").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Changed instructions").performScrollTo().performTextReplacement("Unsaved draft")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertEquals("Changed instructions", manager.document(doc.name).instructions)
        compose.onAllNodesWithText("Import Markdown").assertCountEquals(0)
        compose.onAllNodesWithText("Required tools").assertCountEquals(0)
        verify(exactly = 0) { vm.emitSnackbar(match { it.contains("sent", true) }, any(), any()) }
    }
    @Test fun reviewedChatDraftUsesTheSameEditorAndSavesOnlyOnExplicitSave() {
        val manager = SkillManager(context)
        manager.listFiles().forEach { manager.deleteFile(it.name) }
        val vm = vm(manager)
        val draft = SkillDocument("discussion-workflow.md", "Proposed instructions", description = "Reuse the useful workflow from this discussion")
        compose.setContent { MaterialTheme {
            com.newoether.agora.ui.settings.SkillEditorPage(vm, draft, draft.description, false, {}, { document, description ->
                manager.saveDocument(document, description)
            })
        } }
        compose.onNodeWithText("Proposed instructions").assertExists()
        assertTrue(manager.listFiles().isEmpty())
        compose.onNodeWithText("Proposed instructions").performScrollTo().performTextReplacement("Reviewed instructions")
        assertTrue(manager.listFiles().isEmpty())
        compose.onNodeWithContentDescription("Save").performClick()
        compose.waitUntil(10000) { manager.listFiles().isNotEmpty() }
        assertEquals("Reviewed instructions", manager.document(draft.name).instructions)
        assertEquals(draft.description, manager.getDescription(draft.name))
    }

}
