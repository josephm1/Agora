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
        val doc = SkillDocument("ui-examiner.md", "Original examiner instructions", SkillConfiguration(folders = listOf(SkillFolder("clinical-notes", "/home/agora/cases"))))
        manager.saveDocument(doc, "Teaching workflow")
        val vm = vm(manager)
        compose.setContent { MaterialTheme { SettingsSkillsPage(vm, {}) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("ui-examiner").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("ui-examiner").performScrollTo().performClick()
        try { compose.waitUntil(10000) { compose.onAllNodesWithText("Original examiner instructions").fetchSemanticsNodes().isNotEmpty() } }
        catch (error: Throwable) { throw AssertionError(compose.onRoot().printToString(), error) }
        compose.onNodeWithText("Original examiner instructions").performScrollTo().performTextReplacement("Changed instructions")
        compose.onNodeWithContentDescription("Save").performClick()
        compose.waitUntil(10000) { manager.document(doc.configuration.id).instructions == "Changed instructions" }
        compose.waitUntil(10000) { compose.onAllNodesWithText("ui-examiner").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("ui-examiner").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Changed instructions").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Changed instructions").performScrollTo().performTextReplacement("Unsaved draft")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertEquals("Changed instructions", manager.document(doc.configuration.id).instructions)
        compose.onNodeWithText("ui-examiner").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("clinical-notes").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("clinical-notes").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Save").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("ui-examiner").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(doc.configuration.folders, manager.document(doc.configuration.id).configuration.folders)
        verify(exactly = 0) { vm.emitSnackbar(match { it.contains("sent", true) }, any(), any()) }
    }
}
