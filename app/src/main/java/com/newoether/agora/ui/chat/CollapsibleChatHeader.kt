package com.newoether.agora.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.ui.components.Icon
import com.newoether.agora.ui.components.IconButton
import com.newoether.agora.ui.components.Surface

/** Only deliberate user drags change chrome; streaming/IME/programmatic scrolling cannot hide it. */
internal class CollapsibleChatHeader(private val hideDistance: Float, private val revealDistance: Float) {
    var collapsed by mutableStateOf(false)
        private set
    private var travel = 0f
    fun show() { collapsed = false; travel = 0f }
    fun drag(delta: Float, enabled: Boolean) {
        if (!enabled) { show(); return }
        if (!delta.isFinite() || delta == 0f) return
        if (travel * delta < 0) travel = 0f
        travel += delta
        if (!collapsed && travel <= -hideDistance) { collapsed = true; travel = 0f }
        else if (collapsed && travel >= revealDistance) show()
    }
    var connection: NestedScrollConnection = object : NestedScrollConnection {}
        internal set
}

@Composable
internal fun rememberCollapsibleChatHeader(owner: String?, enabled: Boolean): CollapsibleChatHeader {
    val density = LocalDensity.current
    val state = remember(owner, density) {
        CollapsibleChatHeader(with(density) { 48.dp.toPx() }, with(density) { 12.dp.toPx() })
    }
    val currentEnabled by rememberUpdatedState(enabled)
    state.connection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) state.drag(available.y, currentEnabled)
                return Offset.Zero // Chrome never consumes movement intended for the message list.
            }
        }
    }
    LaunchedEffect(enabled) { if (!enabled) state.show() }
    return state
}

@Composable
internal fun CollapsibleChatHeader(
    state: CollapsibleChatHeader,
    reading: Boolean,
    onHeightChanged: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxWidth().onSizeChanged { onHeightChanged(it.height) }) {
        Box(Modifier.readingChrome(state.collapsed && !reading)) { content() }
        if (state.collapsed && !reading) {
            Surface(modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()) {
                IconButton(onClick = state::show, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.chat_show_toolbar))
                }
            }
        }
    }
}
