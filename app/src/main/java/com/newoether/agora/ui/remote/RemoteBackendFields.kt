package com.newoether.agora.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newoether.agora.remote.RemoteBackendKind
import com.newoether.agora.ui.components.Text

/** Fixed monochrome tabs remain distinguishable on an e-ink display. */
@Composable
internal fun RemoteBackendFields(
    kind: RemoteBackendKind,
    onKind: (RemoteBackendKind) -> Unit,
    enabled: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(RemoteBackendKind.FILO to "Filo", RemoteBackendKind.HERMES to "Hermes").forEach { (backend, label) ->
                val selected = kind == backend
                val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                Box(
                    Modifier.weight(1f).height(48.dp).clip(shape)
                        .background(Color.White)
                        .border(1.dp, Color.Black, shape)
                        .selectable(selected = selected, enabled = enabled, role = Role.Tab,
                            onClick = { onKind(backend) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = Color.Black,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    if (selected) {
                        Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp)
                            .fillMaxWidth().height(3.dp).background(Color.Black))
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().border(1.dp, Color.Black,
                RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}
