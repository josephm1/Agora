package com.newoether.agora.ui.auth

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.*
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.newoether.agora.ui.motion.ProvideAgoraMotionPolicy
import com.newoether.agora.ui.theme.AgoraTheme
import com.newoether.agora.ui.theme.ThemeMode
import java.io.File
import org.json.JSONArray
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
class ChatGptConnectionScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var root: View
    private var continued = 0
    private var signIns = 0
    private var selected = ""
    private fun account(model: String = "test-model") = JSONObject().put("client_id", "local-test")
        .put("label", "Alex · abc123").put("connected", true).put("plan", true).put("model", model)
        .put("models", JSONArray().put(JSONObject().put("slug", "test-model").put("display_name", "Everyday")))

    private fun show(value: JSONObject?, setup: Boolean = true, signingIn: Boolean = false, working: Boolean = false) {
        compose.setContent {
            root = LocalView.current.rootView
            var managing by remember { mutableStateOf(false) }
            ProvideAgoraMotionPolicy(true, true) {
                AgoraTheme(themeMode = ThemeMode.LIGHT, eInkMode = true, fontPreference = "system") {
                    ChatGptConnectionScreen(value, JSONArray().put(account()), signingIn, working, null,
                        setup, managing, {}, { signIns++ }, {}, { selected = it }, {}, { continued++ },
                        { managing = true }, {}, {}, {}, {}, {})
                }
            }
        }
    }

    @Test fun firstConnectionHasOneSignInActionWithoutMaintenanceOrBillingControls() {
        show(null)
        compose.onNodeWithText("Continue with ChatGPT").assertIsDisplayed().performClick()
        assertEquals(1, signIns)
        for (title in listOf("Add an Account", "Reload Models", "Sign Out", "Manage Usage", "Connection Details", "Manage Account", "Use ChatGPT plan", "Use API-key billing", "Use an API Key")) {
            compose.onNodeWithText(title).assertDoesNotExist()
        }
    }

    @Test fun connectedSetupShowsOnlyAccountModelAndContinueAndCapturesTheLayout() {
        show(account())
        compose.onNodeWithText("Alex").assertIsDisplayed()
        compose.onNodeWithText("Everyday").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsDisplayed().performClick()
        assertEquals(1, continued)
        compose.onNodeWithText("Manage Account").assertDoesNotExist()
        compose.onNodeWithText("abc123", substring = true).assertDoesNotExist()
        System.getenv("AGORA_UI_CAPTURE_DIR")?.let { directory ->
            File(directory, "agora-chatgpt-after-jvm.png").outputStream().use {
                compose.runOnUiThread {
                    val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                    root.draw(Canvas(bitmap))
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    bitmap.recycle()
                }
            }
        }
    }

    @Test fun modelCanBeChosenWithoutARefreshOrEnablePlanStep() {
        show(account(""))
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithText("Choose a Model").performClick()
        compose.onNodeWithText("Everyday").performClick()
        assertEquals("test-model", selected)
        compose.onNodeWithText("Use ChatGPT plan").assertDoesNotExist()
    }

    @Test fun managementIsDisclosedOnlyWhenRequestedOutsideFirstRun() {
        show(account(), setup = false)
        compose.onNodeWithText("Sign Out").assertDoesNotExist()
        compose.onNodeWithText("Manage Account").performClick()
        compose.onNodeWithText("Sign Out").assertExists()
        compose.onNodeWithText("Connection Details").assertExists()
        compose.onNodeWithText("Use an API Key").assertExists()
        compose.onNodeWithText("Continue").assertDoesNotExist()
    }

    @Test fun browserWaitOffersCancellationWithoutStartingAnotherSignIn() {
        show(null, signingIn = true)
        compose.onNodeWithText("Cancel Sign-In").assertIsDisplayed()
        compose.onNodeWithText("Continue with ChatGPT").assertDoesNotExist()
    }
    @Test fun loadingStateDisablesThePrimaryAction() {
        show(null, working = true)
        compose.onNodeWithText("Connecting…").assertIsNotEnabled()
    }

}
