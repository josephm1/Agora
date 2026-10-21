package com.newoether.agora.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
                        .drawBehind {
                            val radius = 12.dp.toPx()
                            val strokeWidth = 1.5.dp.toPx()
                            val edge = strokeWidth / 2
                            val outline = Path().apply {
                                moveTo(edge, size.height)
                                lineTo(edge, radius)
                                arcTo(Rect(edge, edge, 2 * radius - edge, 2 * radius - edge), 180f, 90f, false)
                                lineTo(size.width - radius, edge)
                                arcTo(Rect(size.width - 2 * radius + edge, edge, size.width - edge, 2 * radius - edge), 270f, 90f, false)
                                lineTo(size.width - edge, size.height)
                            }
                            drawPath(outline, Color.Black, style = Stroke(strokeWidth))
                        }
                        .selectable(selected = selected, enabled = enabled, role = Role.Tab,
                            onClick = { onKind(backend) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = Color.Black,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().drawBehind {
                val strokeWidth = 1.5.dp.toPx()
                val edge = strokeWidth / 2
                val radius = 16.dp.toPx()
                val tabWidth = (size.width - 6.dp.toPx()) / 2
                val outline = Path().apply {
                    moveTo(edge, 0f)
                    lineTo(edge, size.height - radius)
                    arcTo(Rect(edge, size.height - 2 * radius + edge, 2 * radius - edge, size.height - edge), 180f, -90f, false)
                    lineTo(size.width - radius, size.height - edge)
                    arcTo(Rect(size.width - 2 * radius + edge, size.height - 2 * radius + edge, size.width - edge, size.height - edge), 90f, -90f, false)
                    lineTo(size.width - edge, 0f)
                }
                drawPath(outline, Color.Black, style = Stroke(strokeWidth))
                // Only the other tab and the gap have a divider; the selected tab opens into the form.
                if (kind == RemoteBackendKind.FILO) {
                    drawLine(Color.Black, Offset(tabWidth, edge), Offset(size.width - edge, edge), strokeWidth)
                } else {
                    drawLine(Color.Black, Offset(edge, edge), Offset(size.width - tabWidth, edge), strokeWidth)
                }
            }.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}
