package com.newoether.agora.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.*
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ChatGptPlanChoiceTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val owner = mockk<ChatGptAccounts>()
    @Before fun setup() {
        ChatGptPlan.initialize(app)
        ChatGptPlan.setEnabled(false)
        mockkStatic(ChatGptAccounts::class)
        every { ChatGptAccounts.get(any()) } returns owner
    }
    @After fun cleanup() { ChatGptPlan.setEnabled(false); unmockkStatic(ChatGptAccounts::class) }
    private fun active(connected: Boolean, permission: Boolean) = JSONObject()
        .put("client_id", "chosen-account").put("connected", connected).put("plan", permission)

    @Test fun choosingAValidatedAccountEnablesItWithoutASecondPlanToggle() {
        every { owner.active() } returns active(true, true)
        ChatGptPlan.useConnectedAccount("chosen-account")
        assertTrue(ChatGptPlan.enabled())
    }
    @Test fun missingConsentOrAStaleAccountNeverActivatesPlanUsage() {
        for (value in listOf(active(false, true), active(true, false), active(true, true).put("client_id", "other"))) {
            every { owner.active() } returns value
            assertThrows(IllegalStateException::class.java) { ChatGptPlan.useConnectedAccount("chosen-account") }
            assertFalse(ChatGptPlan.enabled())
        }
    }
}
