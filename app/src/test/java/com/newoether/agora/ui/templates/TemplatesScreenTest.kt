package com.newoether.agora.ui.templates

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.newoether.agora.model.InteractiveAnswer
import com.newoether.agora.templates.InteractiveTemplate
import com.newoether.agora.templates.InteractiveTemplateRepository
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TemplatesScreenTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()

    @Test fun libraryOpensStoredTemplateWithoutModelOrGenerationDependencies() {
        val repository = InteractiveTemplateRepository(folder.newFolder())
        val template = InteractiveTemplate(name = "Reusable menu", shortcut = "custom",
            answer = InteractiveAnswer("Menu", "Options", "Select an option", html = "<p>Menu</p>"))
        repository.save(template)
        var opened: String? = null
        var closed = false
        compose.setContent { MaterialTheme { TemplatesScreen(repository, { opened = it; true }, { closed = true }) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Reusable menu").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("/custom").assertDoesNotExist()
        assertNull(opened)
        compose.onNodeWithText("Reusable menu").performClick()
        compose.waitUntil(10000) { closed }
        assertEquals(template.id, opened)
        assertEquals(listOf(template), repository.list())
    }

    @Test fun metadataEditingChangesTheSourceFolderWithoutOfferingASlashShortcut() {
        val repository = InteractiveTemplateRepository(folder.newFolder())
        val template = InteractiveTemplate(name = "Menu", answer = InteractiveAnswer("Menu", "Options", "Select", html = "<p>Menu</p>"))
        repository.save(template)
        compose.setContent { MaterialTheme { TemplatesScreen(repository, { true }, {}) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Actions").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Actions").performClick()
        compose.onNodeWithText("Edit template").performClick()
        compose.onNodeWithText("Shortcut (optional)").assertDoesNotExist()
        compose.onNodeWithText("Alpine source folder (optional)").performTextInput("/mnt/shared/References")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10000) { repository.get(template.id).sourceDirectory == "/mnt/shared/References" }
        assertNull(repository.get(template.id).shortcut)
    }
}
