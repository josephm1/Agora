package com.newoether.agora.ui.templates

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.newoether.agora.model.InteractiveAnswer
import com.newoether.agora.templates.InteractiveTemplate
import com.newoether.agora.templates.InteractiveTemplateRepository
import com.newoether.agora.ui.chat.bottombar.ComposerTemplateMenu
import com.newoether.agora.templates.templateMenuQuery
import com.newoether.agora.templates.matchingMenuTemplates
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposerTemplateMenuTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private fun template(name: String, shortcut: String? = null) = InteractiveTemplate(name = name,
        shortcut = shortcut, answer = InteractiveAnswer("Menu", "Options", "Choose", html = "<p>Menu</p>"))

    @Test fun slashOffersMultipleTemplatesAndTypingFiltersBeforeLocalSelection() {
        val repository = InteractiveTemplateRepository(folder.newFolder())
        val osce = template("Zero to Finals OSCE") // Imported templates need no assigned shortcut.
        repository.save(osce)
        repository.save(template("Revision planner", "revise"))
        var opened: String? = null
        compose.setContent {
            var text by remember { mutableStateOf("") }
            MaterialTheme { Column {
                ComposerTemplateMenu(text, repository, enabled = true, onSelect = { opened = it.id })
                BasicTextField(text, { text = it }, Modifier.testTag("input"))
            } }
        }
        compose.onNodeWithText(osce.name).assertDoesNotExist()
        compose.onNodeWithTag("input").performTextInput("/")
        compose.waitUntil(10000) { compose.onAllNodesWithText(osce.name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Revision planner").assertExists()
        assertNull(opened)
        savePreview()
        compose.onNodeWithTag("input").performTextInput("os")
        compose.waitUntil(10000) { compose.onAllNodesWithText("Revision planner").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(osce.name).performClick()
        compose.runOnIdle { assertEquals(osce.id, opened) }
    }

    private fun savePreview() {
        compose.runOnIdle {
            val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).single()
            val view = activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            view.draw(canvas)
            val file = java.io.File("build/reports/interactive-templates/slash-menu.png")
            file.parentFile.mkdirs()
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun disabledMenuCannotOpenAndOrdinaryTextDoesNotBecomeACommand() {
        val repository = InteractiveTemplateRepository(folder.newFolder())
        val item = template("Study menu", "study")
        repository.save(item)
        var opened = false
        compose.setContent { MaterialTheme { ComposerTemplateMenu("/", repository, false, { opened = true }) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText(item.name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(item.name).performClick()
        compose.runOnIdle { assertFalse(opened) }
        assertNull(templateMenuQuery("mention /study"))
        assertNull(templateMenuQuery("/study arguments"))
        assertNull(templateMenuQuery("/study\n"))
        assertEquals("st", templateMenuQuery("/ST"))
        assertEquals(listOf(item), matchingMenuTemplates("stu", listOf(item)))
        assertTrue(matchingMenuTemplates("unknown", listOf(item)).isEmpty())
    }
}
