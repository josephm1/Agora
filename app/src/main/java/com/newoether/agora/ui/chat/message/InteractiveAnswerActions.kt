package com.newoether.agora.ui.chat.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.ui.components.Text
import com.newoether.agora.ui.components.TextButton

/** Keep both interface actions visible without spending two rows below every embedded browser. */
@Composable
internal fun InteractiveAnswerActions(saving: Boolean, onSave: () -> Unit, onExpand: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        TextButton(enabled = !saving, onClick = onSave, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.template_save))
        }
        TextButton(onClick = onExpand, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.interactive_answer_open))
        }
    }
}
