package com.newoether.agora.templates

import com.newoether.agora.model.InteractiveAnswer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TemplateReference(val path: String, val label: String, val categories: List<String>)

/** Display-only normalization. The original case-sensitive path always opens the document. */
fun templateReferenceLabel(name: String, prefix: String = ""): String = name
    .replace(Regex("(?i)\\.md$"), "")
    .let { if (prefix.isNotEmpty() && it.startsWith(prefix)) it.removePrefix(prefix) else it }
    .replace(Regex("[-_]+"), " ").replace(Regex("\\s+"), " ").trim().ifBlank { name }

fun templateReferenceCatalogue(paths: List<String>, prefix: String = ""): List<TemplateReference> = paths.map { path ->
    require(isTemplateSourcePath(path, true)) { "Invalid reference path" }
    val parts = path.split('/')
    TemplateReference(path, templateReferenceLabel(parts.last(), prefix), parts.dropLast(1).map { templateReferenceLabel(it) })
}.sortedWith(compareBy({ it.categories.joinToString("/").lowercase() }, { it.label.lowercase() }, { it.path }))

internal fun injectTemplateSources(template: InteractiveTemplate): InteractiveTemplate {
    val references = templateReferenceCatalogue(template.resourceFiles, template.referencePrefix)
    fun safe(text: String) = text.replace("<", "\\u003c").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
    val expressions = listOf(
        "globalThis.agoraReferences = ${safe(Json.encodeToString(references))};",
        "globalThis.agoraSourceFiles = globalThis.agoraReferences.map(reference => reference.path);",
    )
    return template.copy(answer = template.answer.copy(jsExpressions = expressions + template.answer.jsExpressions.filterNot {
        it.startsWith("globalThis.agoraSourceFiles =") || it.startsWith("globalThis.agoraReferences =")
    })).validate()
}

@Serializable
internal data class TemplateEditCode(val answer: InteractiveAnswer, val resourceFiles: List<String> = emptyList(),
    val referencePrefix: String = "", val sourceFilesAuto: Boolean = true, val kind: String = "agora.template-edit/v1")

internal fun templateEditCode(template: InteractiveTemplate): String = Json { prettyPrint = true; encodeDefaults = true }.encodeToString(
    TemplateEditCode(template.answer, template.resourceFiles, template.referencePrefix, template.sourceFilesAuto))

internal fun applyTemplateEditCode(template: InteractiveTemplate, code: String): InteractiveTemplate {
    require(code.toByteArray().size <= InteractiveTemplate.MAX_FILE_BYTES) { "Template source exceeds 100,000 bytes" }
    val root = Json.parseToJsonElement(code) as? kotlinx.serialization.json.JsonObject ?: error("Paste a JSON object")
    return if (root["kind"] == kotlinx.serialization.json.JsonPrimitive("agora.template-edit/v1")) {
        val edit = Json.decodeFromString<TemplateEditCode>(code)
        template.copy(answer = edit.answer, resourceFiles = edit.resourceFiles, referencePrefix = edit.referencePrefix,
            sourceFilesAuto = edit.sourceFilesAuto).validate()
    } else template.copy(answer = InteractiveAnswer.decode(code)).validate()
}

internal fun templateAiEditingBrief(template: InteractiveTemplate): String = """
Edit this Agora template. Return one complete agora.template-edit/v1 JSON object, without Markdown fences.
Keep its existing purpose and e-ink accessibility. Its Skill owns the examiner instructions; do not duplicate them.
The Android app supplies window.agoraReferences: [{path,label,categories}] and window.agoraSourceFiles: [relativePath].
You can also await Websandbox.connection.remote.listReferenceFiles() for that same native catalogue snapshot.
Build categories/buttons from that data; do not hardcode the current filenames. Paths stay exact; labels are display only.
No filesystem, shell or network access is needed in JavaScript. Start submits exactly one selected path through:
await Websandbox.connection.remote.sendPrompt({text: JSON.stringify({condition: selected.label, questions: 12}), sourceFiles: [selected.path]})
Do not submit while browsing. The host reads the file before sending.
Preview must remain local. No article text, folder URI, credentials or Skill instructions are included here.

${templateEditCode(template)}
""".trimIndent()

/** The bridge returns only the host's immutable catalogue snapshot, never arbitrary file access. */
internal fun templateReferenceSnapshot(answer: InteractiveAnswer): List<TemplateReference> {
    val prefix = "globalThis.agoraReferences = "
    val encoded = answer.jsExpressions.singleOrNull { it.startsWith(prefix) } ?: return emptyList()
    val references = Json.decodeFromString<List<TemplateReference>>(encoded.removePrefix(prefix).removeSuffix(";"))
    require(references.size <= 500 && references.map { it.path }.distinct().size == references.size) { "Invalid reference catalogue" }
    references.forEach { require(isTemplateSourcePath(it.path, true)) { "Invalid reference path" } }
    return references
}
