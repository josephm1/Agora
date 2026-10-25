package com.newoether.agora.ui.chat

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class EinkBottomPinTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pinFollowsGrowingTextAndNewItemsUntilReleased() {
        var count by mutableIntStateOf(20)
        var tailHeight by mutableIntStateOf(60)
        lateinit var list: LazyListState
        lateinit var pin: EinkBottomPin
        lateinit var scope: CoroutineScope
        compose.setContent {
            list = rememberLazyListState(); scope = rememberCoroutineScope()
            pin = rememberEinkBottomPin(true, "chat", list)
            LazyColumn(Modifier.width(360.dp).height(300.dp), state = list) {
                items(count) { Box(Modifier.height(48.dp)) }
                item("stream") { Box(Modifier.height(tailHeight.dp)) }
                item("end") { Box(Modifier.height(1.dp)) }
            }
        }
        compose.runOnIdle { pin.pin() }
        compose.waitForIdle()
        try { compose.waitUntil(5_000) { !list.canScrollForward } } catch (e: Exception) {
            throw AssertionError("pin=${pin.active}, items=${list.layoutInfo.totalItemsCount}, visible=${list.layoutInfo.visibleItemsInfo.map { Triple(it.index,it.offset,it.size) }}, end=${list.layoutInfo.viewportEndOffset}", e)
        }
        compose.runOnIdle { count++; tailHeight = 400 }
        compose.waitForIdle()
        compose.waitUntil(5_000) { !list.canScrollForward && list.layoutInfo.visibleItemsInfo.last().index == count + 1 }
        compose.runOnIdle { pin.release(); scope.launch { list.scrollToItem(0) } }
        compose.waitForIdle()
        compose.waitUntil(5_000) { list.firstVisibleItemIndex == 0 }
        compose.runOnIdle { count++; tailHeight = 700 }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(pin.active); assertEquals(0, list.firstVisibleItemIndex) }
    }

    @Test fun dragAndConversationChangeReleaseTheExplicitPin() {
        var owner by mutableStateOf("one")
        lateinit var list: LazyListState
        lateinit var pin: EinkBottomPin
        lateinit var scope: CoroutineScope
        compose.setContent {
            list = rememberLazyListState(); scope = rememberCoroutineScope()
            pin = rememberEinkBottomPin(true, owner, list)
            LazyColumn(Modifier.width(360.dp).height(300.dp), state = list) {
                items(20) { Box(Modifier.height(48.dp)) }
            }
        }
        compose.runOnIdle { pin.pin() }
        compose.waitForIdle()
        try { compose.waitUntil(5_000) { !list.canScrollForward } } catch (e: Exception) {
            throw AssertionError("pin=${pin.active}, items=${list.layoutInfo.totalItemsCount}, visible=${list.layoutInfo.visibleItemsInfo.map { Triple(it.index,it.offset,it.size) }}, end=${list.layoutInfo.viewportEndOffset}", e)
        }
        compose.runOnIdle { scope.launch { (list.interactionSource as MutableInteractionSource).emit(DragInteraction.Start()) } }
        compose.waitForIdle()
        compose.waitUntil(5_000) { !pin.active }
        compose.runOnIdle { pin.pin(); owner = "two" }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(pin.active) }
    }
}
