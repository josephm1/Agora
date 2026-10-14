package com.newoether.agora.notes

import android.app.Application
import android.content.Intent
import android.os.Looper
import com.newoether.agora.MainActivity
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotesExchangeActivityTest {
    private fun request() = NotesExchangeRequest(UUID.randomUUID().toString(), "draft-owner", "new")
    private fun intent(id: String) = Intent(RuntimeEnvironment.getApplication(), NotesExchangeActivity::class.java)
        .putExtra("requestId", id).putExtra("ownerId", "draft-owner").putExtra("operation", "new")
    private fun until(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Activity did not finish its asynchronous handoff", condition())
    }
    @Test fun cancelledRequestCannotLaunchNotesAgain() {
        val store = NotesExchangeStore(RuntimeEnvironment.getApplication())
        val request = request().copy(launched = true, cancelled = true)
        store.save(request)
        val controller = Robolectric.buildActivity(NotesExchangeActivity::class.java, intent(request.requestId)).setup()
        val activity = controller.get()
        until { activity.isFinishing }
        val next = shadowOf(activity).nextStartedActivity
        assertEquals(MainActivity::class.java.name, next.component?.className)
        assertFalse(next.hasExtra(EXTRA_NOTES_REQUEST))
        val ordinaryReturn = shadowOf(activity).nextStartedActivityForResult
        assertEquals(-1, ordinaryReturn.requestCode)
        assertEquals(MainActivity::class.java.name, ordinaryReturn.intent.component?.className)
        assertNull(shadowOf(activity).nextStartedActivityForResult)
        controller.pause().stop().destroy()
    }
    @Test fun oldLaunchIntentWithoutSavedRegistryCancelsInsteadOfCreatingAnotherNote() {
        val store = NotesExchangeStore(RuntimeEnvironment.getApplication())
        val request = request().copy(launched = true)
        store.save(request)
        val controller = Robolectric.buildActivity(NotesExchangeActivity::class.java, intent(request.requestId)).setup()
        until { controller.get().isFinishing }
        assertTrue(store.load(request.requestId)!!.cancelled)
        assertEquals(MainActivity::class.java.name, shadowOf(controller.get()).nextStartedActivity.component?.className)
        controller.pause().stop().destroy()
    }
    @Test fun freshLaunchIsDurableBeforeOpeningAndCancellationReturnsToAgora() {
        val store = NotesExchangeStore(RuntimeEnvironment.getApplication())
        val request = request()
        val controller = Robolectric.buildActivity(NotesExchangeActivity::class.java, intent(request.requestId)).setup()
        val activity = controller.get()
        var launch: org.robolectric.shadows.ShadowActivity.IntentForResult? = null
        until { launch = launch ?: shadowOf(activity).nextStartedActivityForResult; launch != null }
        assertEquals("com.kalamos.notebook.exchange.AgoraDocumentActivity", launch!!.intent.component?.className)
        assertEquals(request.requestId, launch!!.intent.getStringExtra("requestId"))
        assertTrue(store.load(request.requestId)!!.launched)
        activity.activityResultRegistry.dispatchResult(launch!!.requestCode, android.app.Activity.RESULT_CANCELED, null)
        until { activity.isFinishing }
        assertTrue(store.load(request.requestId)!!.cancelled)
        assertEquals(MainActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        controller.pause().stop().destroy()
    }
    @Test fun anotherDraftResumesTheExactUnfinishedRequestInsteadOfReplacingItsOwner() {
        val store = NotesExchangeStore(RuntimeEnvironment.getApplication())
        val original = request()
        store.reserve(original)
        val controller = Robolectric.buildActivity(NotesExchangeActivity::class.java, intent(original.requestId)).setup()
        var launched = false
        until { launched = launched || shadowOf(controller.get()).nextStartedActivityForResult != null; launched }
        val another = request().copy(ownerId = "another-draft")
        assertEquals(original.requestId, store.reserve(another).requestId)
        assertEquals("draft-owner", store.load(original.requestId)!!.ownerId)
        assertNull(store.load(another.requestId))
        assertEquals(original.requestId, NotesExchangeActivity.resumeIntent(original.requestId)!!.getStringExtra("requestId"))
        controller.pause().stop().destroy()
    }
    @Test fun invalidResultRetainsAnActionableErrorSurface() {
        val request = request()
        val controller = Robolectric.buildActivity(NotesExchangeActivity::class.java, intent(request.requestId)).setup()
        val activity = controller.get()
        var launch: org.robolectric.shadows.ShadowActivity.IntentForResult? = null
        until { launch = launch ?: shadowOf(activity).nextStartedActivityForResult; launch != null }
        activity.activityResultRegistry.dispatchResult(launch!!.requestCode, android.app.Activity.RESULT_OK,
            Intent().putExtra("requestId", "wrong-request"))
        until { org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
        assertFalse(activity.isFinishing)
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertEquals("Retry", dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).text.toString())
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick()
        until { activity.isFinishing }
        controller.pause().stop().destroy()
    }
    @Test fun receivedRequestReturnsToItsExactInboxWithoutLaunchingAnotherEditor() {
        val store = NotesExchangeStore(RuntimeEnvironment.getApplication())
        val request = request().copy(launched = true, ready = true)
        store.save(request)
        val controller = Robolectric.buildActivity(NotesExchangeActivity::class.java, intent(request.requestId)).setup()
        until { controller.get().isFinishing }
        val next = shadowOf(controller.get()).nextStartedActivity
        assertEquals(request.requestId, next.getStringExtra(EXTRA_NOTES_REQUEST))
        assertTrue(next.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals("draft-owner", store.load(request.requestId)!!.ownerId)
        controller.pause().stop().destroy()
    }
}
