package com.newoether.agora.ui.remote

import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.newoether.agora.R
import com.newoether.agora.remote.RemoteBackendKind
import com.newoether.agora.ui.components.Text
import com.newoether.agora.ui.settings.McpLabeledField

@Composable
internal fun RemoteBackendFields(kind: RemoteBackendKind, onKind: (RemoteBackendKind) -> Unit,
    username: String, onUsername: (String) -> Unit, profile: String, onProfile: (String) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = kind == RemoteBackendKind.FILO, onClick = { onKind(RemoteBackendKind.FILO) },
                enabled = enabled, label = { Text("Filo") })
            FilterChip(selected = kind == RemoteBackendKind.HERMES, onClick = { onKind(RemoteBackendKind.HERMES) },
                enabled = enabled, label = { Text("Hermes") })
        }
        if (kind == RemoteBackendKind.HERMES) {
            McpLabeledField(label = stringResource(R.string.remote_hermes_username), value = username,
                onValueChange = onUsername, keyboardType = KeyboardType.Text)
            McpLabeledField(label = stringResource(R.string.remote_hermes_profile), value = profile,
                onValueChange = onProfile, keyboardType = KeyboardType.Text,
                supportingText = stringResource(R.string.remote_hermes_profile_hint))
        }
    }
}
