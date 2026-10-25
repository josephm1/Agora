package com.newoether.agora.ui.chat

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.flow.collect

/** Explicit e-ink follow mode; screenful controls and a user drag release it. */
internal class EinkBottomPin {
    var active by mutableStateOf(false)
        private set
    fun pin() { active = true }
    fun release() { active = false }
}

internal fun Modifier.releaseEinkPinOnManualScroll(pin: EinkBottomPin): Modifier =
    onPreviewKeyEvent { event ->
        if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
            event.nativeKeyEvent.keyCode in setOf(android.view.KeyEvent.KEYCODE_PAGE_UP,
                android.view.KeyEvent.KEYCODE_PAGE_DOWN, android.view.KeyEvent.KEYCODE_DPAD_UP,
                android.view.KeyEvent.KEYCODE_DPAD_DOWN)) pin.release()
        false
    }.pointerInput(pin) {
        awaitPointerEventScope {
            while (true) if (awaitPointerEvent().type == PointerEventType.Scroll) pin.release()
        }
    }

@Composable
internal fun rememberEinkBottomPin(enabled: Boolean, owner: String?, list: LazyListState): EinkBottomPin {
    val pin = remember(enabled, owner, list) { EinkBottomPin() }
    LaunchedEffect(pin, list) {
        list.interactionSource.interactions.collect { if (it is DragInteraction.Start) pin.release() }
    }
    LaunchedEffect(pin, pin.active, enabled, list) {
        if (!enabled || !pin.active) return@LaunchedEffect
        snapshotFlow {
            val layout = list.layoutInfo
            listOf(layout.totalItemsCount, layout.viewportEndOffset, list.canScrollForward,
                layout.visibleItemsInfo.map { Triple(it.index, it.offset, it.size) })
        }.collect {
            // A fresh layout observes text growth, new items, hydration and keyboard/chrome changes.
            // Never animate an e-ink follow correction or re-run a no-op scroll.
            val last = list.layoutInfo.totalItemsCount - 1
            if (pin.active && last >= 0 && list.canScrollForward) {
                val layout = list.layoutInfo
                val tail = layout.visibleItemsInfo.lastOrNull { it.index == last }
                val viewport = layout.viewportEndOffset - layout.viewportStartOffset - layout.beforeContentPadding - layout.afterContentPadding
                list.scrollToItem(last, ((tail?.size ?: 0) - viewport).coerceAtLeast(0))
            }
        }
    }
    return pin
}
