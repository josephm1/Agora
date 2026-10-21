package com.newoether.agora.ui.remote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.newoether.agora.R
import com.newoether.agora.remote.RemoteQuestion
import com.newoether.agora.ui.components.Text
import com.newoether.agora.ui.components.TextButton
import kotlinx.serialization.json.*

/** Responses are explicit user gestures bound to the native request id. */
@Composable
internal fun RemoteQuestions(questions: List<RemoteQuestion>, enabled: Boolean,
    answer: (String, JsonObject) -> Unit,
) {
    if (questions.isEmpty()) return
    Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())
        .semantics { liveRegion = LiveRegionMode.Polite }) {
    questions.forEach { request ->
        key(request.id) {
            var sent by remember { mutableStateOf(false) }
            val canAnswer = enabled && !sent
            fun submit(result: JsonObject) { sent = true; answer(request.id, result) }
            Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (request.method == "approval") {
                    Text(request.params["description"]?.jsonPrimitive?.contentOrNull.orEmpty())
                    Text(request.params["command"]?.jsonPrimitive?.contentOrNull.orEmpty())
                    val choices = (request.params["choices"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
                    Row {
                        if (choices.isEmpty() || "once" in choices) TextButton(enabled = canAnswer,
                            onClick = { submit(buildJsonObject { put("choice", "once") }) }) {
                            Text(stringResource(R.string.remote_hermes_approve_once))
                        }
                        TextButton(enabled = canAnswer, onClick = { submit(buildJsonObject { put("choice", "deny") }) }) {
                            Text(stringResource(R.string.remote_hermes_deny))
                        }
                    }
                } else {
                    val questionsArray = request.params["questions"] as? JsonArray ?: JsonArray(emptyList())
                    val answers = remember { mutableStateMapOf<String, String>() }
                    questionsArray.forEach { raw ->
                        val question = raw.jsonObject
                        val id = question["qid"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                        Text(question["question"]?.jsonPrimitive?.contentOrNull.orEmpty())
                        val choices = (question["choices"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
                        if (choices.isEmpty()) OutlinedTextField(value = answers[id].orEmpty(),
                            onValueChange = { if (it.length <= 16_384) answers[id] = it }, enabled = canAnswer)
                        else choices.forEach { choice ->
                            androidx.compose.material3.FilterChip(selected = answers[id] == choice,
                                enabled = canAnswer, onClick = { answers[id] = choice }, label = { Text(choice) })
                        }
                    }
                    Row {
                        TextButton(enabled = canAnswer && answers.isNotEmpty(), onClick = {
                            submit(buildJsonObject { put("answers", buildJsonObject { answers.forEach { (k, v) -> put(k, v) } }) })
                        }) { Text(stringResource(R.string.send)) }
                        TextButton(enabled = canAnswer, onClick = { submit(buildJsonObject {}) }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
            }
        }
    }
    }
}
