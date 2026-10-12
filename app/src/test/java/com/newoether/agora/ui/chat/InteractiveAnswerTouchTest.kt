package com.newoether.agora.ui.chat

import android.app.Application
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.newoether.agora.ui.motion.AgoraMotionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w600dp-h900dp")
class InteractiveAnswerTouchTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var renderer: InteractiveAnswerTouchHost
    private lateinit var drawer: ChatDrawerState
    private val actions = mutableListOf<Int>()

    private fun showRenderer(controlHandlesTouch: Boolean = true) {
        compose.setContent {
            drawer = rememberChatDrawerState()
            ChatDrawerHost(drawer, true, AgoraMotionPolicy(reduceMotion = true), {}, { _, _ -> }) {
                Column {
                    AndroidView(factory = { context ->
                        InteractiveAnswerTouchHost(context).apply {
                            renderer = this
                            // Emulate a handled browser control in the real native touch host,
                            // including Chromium yielding interception at a scroll boundary.
                            addView(android.view.View(context).apply {
                                setOnTouchListener { view, event ->
                                    actions += event.actionMasked
                                    view.parent?.requestDisallowInterceptTouchEvent(false)
                                    controlHandlesTouch
                                }
                            }, android.widget.FrameLayout.LayoutParams(-1, -1))
                        }
                    }, modifier = Modifier.fillMaxWidth().height(200.dp).testTag("renderer"))
                    Box(Modifier.fillMaxWidth().height(200.dp).testTag("outside"))
                }
            }
        }
        compose.waitForIdle()
        // Robolectric does not perform the native View layout during Compose drawing.
        val bounds = compose.onNodeWithTag("renderer").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            (renderer.parent as android.view.View).layout(0, 0, bounds.width.toInt(), bounds.height.toInt())
            renderer.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(bounds.width.toInt(), android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(bounds.height.toInt(), android.view.View.MeasureSpec.EXACTLY),
            )
            renderer.layout(0, 0, bounds.width.toInt(), bounds.height.toInt())
            assertTrue(renderer.width > 0 && renderer.height > 0)
        }
    }

    @Test fun sliderDragReachesRendererWithoutOpeningDrawerAndOutsideSwipeStillWorks() {
        showRenderer()
        compose.onNodeWithTag("renderer").performTouchInput { swipeRight() }
        compose.runOnIdle {
            assertEquals(0f, drawer.progress, 0f)
            assertTrue(actions.contains(MotionEvent.ACTION_MOVE))
            assertEquals(MotionEvent.ACTION_UP, actions.last())
        }
        compose.onNodeWithTag("outside").performTouchInput { swipeRight() }
        compose.runOnIdle { assertTrue(drawer.progress > 0f) }
    }

    @Test fun cancellationReleasesRendererGestureOwnership() {
        showRenderer()
        compose.onNodeWithTag("renderer").performTouchInput {
            down(Offset(80f, 80f))
            moveBy(Offset(100f, 0f))
            cancel()
        }
        compose.runOnIdle {
            assertEquals(0f, drawer.progress, 0f)
            assertEquals(MotionEvent.ACTION_CANCEL, actions.last())
        }
        compose.onNodeWithTag("outside").performTouchInput { swipeRight() }
        compose.runOnIdle { assertTrue(drawer.progress > 0f) }
    }
    @Test fun unhandledPageAreaDoesNotHandTheGestureToTheDrawer() {
        showRenderer(controlHandlesTouch = false)
        compose.onNodeWithTag("renderer").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(0f, drawer.progress, 0f) }
        compose.onNodeWithTag("outside").performTouchInput { swipeRight() }
        compose.runOnIdle { assertTrue(drawer.progress > 0f) }
    }

}
