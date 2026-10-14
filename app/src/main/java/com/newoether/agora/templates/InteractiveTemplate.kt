package com.newoether.agora.templates

import com.newoether.agora.model.InteractiveAnswer
import com.newoether.agora.model.MessageSegment
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** Portable user-owned interface. Skills remain separate resources, referenced by filename. */
@Serializable
data class InteractiveTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val shortcut: String? = null,
    val linkedSkill: String? = null,
    val resourceFiles: List<String> = emptyList(),
    val answer: InteractiveAnswer,
    val kind: String = "agora.interactive-template",
    val version: Int = 1,
) {
    fun validate(): InteractiveTemplate {
        require(kind == "agora.interactive-template" && version == 1) { "Unsupported template format" }
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) { "Invalid template ID" }
        require(name.isNotBlank() && name.length <= 200) { "Name must contain 1–200 characters" }
        require(description.length <= 2000) { "Description is too long" }
        require(shortcut == null || shortcut.matches(Regex("[a-z][a-z0-9-]{0,63}"))) { "Invalid shortcut" }
        require(linkedSkill == null || linkedSkill.matches(Regex("[^/\\\\\\r\\n]{1,200}\\.md")) && linkedSkill != "..md") {
            "Invalid skill filename"
        }
        require(resourceFiles.size <= 500 && resourceFiles.distinct().size == resourceFiles.size &&
            resourceFiles.all { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,199}\\.md")) && it != "..md" }) {
            "Invalid template resource files"
        }
        answer.validate()
        require(answer.encode().toByteArray(Charsets.UTF_8).size <= MAX_INTERFACE_BYTES) { "Interface exceeds 80,000 bytes" }
        require(encode().toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES) { "Template file is too large" }
        return this
    }

    fun encode(): String = Json.encodeToString(this)

    fun duplicate(): InteractiveTemplate = copy(id = UUID.randomUUID().toString(), shortcut = null)

    /** Immutable snapshot; no synthetic provider tool call or provider arguments. */
    fun segment(): MessageSegment = MessageSegment(
        type = SEGMENT_TYPE,
        content = encode(),
        toolCallId = id,
    )

    companion object {
        const val SEGMENT_TYPE = "interactive_template"
        const val MAX_INTERFACE_BYTES = 80000
        const val MAX_FILE_BYTES = 100000
        fun decode(text: String): InteractiveTemplate {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES) { "Template file is too large" }
            return Json.decodeFromString<InteractiveTemplate>(text).validate()
        }
    }
}

fun MessageSegment.interactiveTemplate(): InteractiveTemplate? =
    when {
        type == InteractiveTemplate.SEGMENT_TYPE -> runCatching { InteractiveTemplate.decode(content) }.getOrNull()
        type == "tool" && toolName == "open_interactive_template" && toolState == com.newoether.agora.model.ToolExecutionStates.SUCCEEDED ->
            toolStructuredResult?.let { runCatching { InteractiveTemplate.decode(it) }.getOrNull() }
        else -> null
    }

/** Exact commands only: mentions, arguments and unknown commands remain ordinary messages. */
fun resolveTemplateShortcut(text: String, templates: List<InteractiveTemplate>): InteractiveTemplate? {
    val command = text.trim()
    return templates.firstOrNull { it.shortcut != null && command == "/${it.shortcut}" }
}

fun interactiveTemplateOf(segment: MessageSegment): InteractiveTemplate? = segment.interactiveTemplate()

fun templateSkillPrefix(skill: String?): String = if (skill == null) "" else
    "Read the skill file $skill and follow its instructions for this session.\n\n"

fun isTemplateCommand(text: String): Boolean = templateMenuQuery(text.trim()) != null
