package com.newoether.agora.ui.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.newoether.agora.ui.theme.AgoraTheme
import com.newoether.agora.ui.theme.ThemeMode
import com.newoether.agora.ui.motion.ProvideAgoraMotionPolicy
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w411dp-h914dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountModelPresentationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun cachedCatalogueIncludesSelectedSolWithoutNetworkOrManualDuplication() {
        val account = JSONObject("""{"connected":true,"plan":true,"model":"gpt-6.1-sol","models":[{"slug":"gpt-6-astra"},{"slug":"gpt-6.1-sol"},{"slug":"gpt-6.1-sol"},{}]}""")
        val cached = chatGptCatalogModels(account)
        assertEquals(listOf("OpenAI:gpt-6-astra", "OpenAI:gpt-6.1-sol"), cached)
        val custom = setOf("OpenAI:gpt-6.1-sol", "Relay:mine") - cached.toSet()
        val groups = fetchedModelGroups(mapOf("OpenAI" to cached), custom, emptyMap(), "6.1")
        assertEquals(listOf("OpenAI:gpt-6.1-sol"), groups.single().models)
        assertEquals(setOf("Relay:mine"), custom)
        assertEquals("gpt-6.1-sol", account.getString("model"))
        assertTrue(chatGptCatalogModels(account.put("plan", false)).isEmpty())
        assertTrue(chatGptCatalogModels(account.put("connected", false).put("plan", true)).isEmpty())
        assertTrue(chatGptCatalogModels(null).isEmpty())
    }

    @Test fun catalogueRowSeparatesVisibilityFromRenameAndRenders() {
        var renamed = ""
        var root: android.view.View? = null
        var enabled by mutableStateOf(setOf("OpenAI:gpt-6.1-sol"))
        compose.setContent {
            root = LocalView.current.rootView
            ProvideAgoraMotionPolicy(true, true) {
                AgoraTheme(themeMode = ThemeMode.LIGHT, eInkMode = true) {
                    val expanded = remember { mutableStateMapOf("auto:OpenAI" to MutableTransitionState(true)) }
                    val heights = remember { mutableStateMapOf<String, Float>() }
                    LazyColumn {
                        modelProviderGroups("auto", listOf(ModelProviderGroup("OpenAI", listOf("OpenAI:gpt-6.1-sol"))),
                            true, true, false, false, enabled, emptyMap(), emptyList(),
                            expanded, heights, { renamed = it }, null,
                            { model, checked -> enabled = if (checked) enabled + model else enabled - model })
                    }
                }
            }
        }
        compose.onNodeWithText("Show in chat").assertIsDisplayed()
        compose.onNodeWithText("Rename Model").assertIsDisplayed().performClick()
        assertEquals("OpenAI:gpt-6.1-sol", renamed)
        assertTrue("Rename must not disable the model", enabled.isNotEmpty())
        compose.onNodeWithText("Show in chat").performClick()
        assertTrue(enabled.isEmpty())
        System.getenv("AGORA_UI_CAPTURE_DIR")?.let { directory ->
            compose.runOnUiThread {
                val view = root!!
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                File(directory, "agora-model-row-after.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

    @Test fun providerNameEditorLabelHasDarkTextOnALightSurface() {
        var root: android.view.View? = null
        compose.setContent {
            root = LocalView.current.rootView
            ProvideAgoraMotionPolicy(true, true) {
                AgoraTheme(themeMode = ThemeMode.LIGHT, eInkMode = true) {
                    androidx.compose.material3.Surface(color = Color.White) {
                        ModelProviderNameSwitch(true) {}
                    }
                }
            }
        }
        val bounds = compose.onNodeWithText("Show Provider name").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.runOnUiThread {
            val view = root!!
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            var darkPixels = 0
            for (x in bounds.left.toInt().coerceAtLeast(0) until bounds.right.toInt().coerceAtMost(bitmap.width)) {
                for (y in bounds.top.toInt().coerceAtLeast(0) until bounds.bottom.toInt().coerceAtMost(bitmap.height)) {
                    if (bitmap.getPixel(x, y) == Color.Black.toArgb()) darkPixels++
                }
            }
            assertTrue("The editor label must draw readable black glyphs", darkPixels > 20)
            bitmap.recycle()
        }
    }
}
