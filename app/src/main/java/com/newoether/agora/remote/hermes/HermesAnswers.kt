package com.newoether.agora.remote.hermes

import com.newoether.agora.remote.FiloInputException
import com.newoether.agora.remote.RemoteQuestion
import kotlinx.serialization.json.*

/** Validate the exact native question before returning any permission-bearing reply. */
internal fun validateHermesAnswer(question: RemoteQuestion, result: JsonObject) {
    when (question.method) {
        "approval" -> {
            val choice = result.string("choice") ?: throw FiloInputException()
            val advertised = question.params.array("choices").map { it.jsonPrimitive.content }
            if (choice !in setOf("once", "deny") || (advertised.isNotEmpty() && choice !in advertised)) throw FiloInputException()
        }
        "clarify" -> {
            val answers = result.obj("answers")
            val questions = question.params.array("questions").map { it.jsonObject }
            if (answers.keys.any { key -> questions.none { it.string("qid") == key } }) throw FiloInputException()
            questions.forEach { q ->
                val answer = answers.string(q.string("qid").orEmpty()) ?: return@forEach
                val choices = q.array("choices").map { it.jsonPrimitive.content }
                if (choices.isNotEmpty() && answer !in choices) throw FiloInputException()
                if (answer.length > 16_384) throw FiloInputException()
            }
        }
        else -> throw FiloInputException()
    }
}
