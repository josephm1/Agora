package com.newoether.agora.skills

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID

/** Configuration travels inside Markdown, so existing import/export has one atomic resource. */
@Serializable
data class SkillConfiguration(
    val id: String = UUID.randomUUID().toString(),
    val version: Int = 1,
    val shortcut: String = "",
    val tools: List<String> = emptyList(),
    val folders: List<SkillFolder> = emptyList(),
    val children: List<SkillDependency> = emptyList(),
    val callExamples: String = "",
) {
    fun validate(): SkillConfiguration {
        require(version == 1 && UUID.fromString(id).toString() == id) { "Unsupported Skill configuration" }
        require(shortcut.isEmpty() || shortcut.matches(Regex("[a-z][a-z0-9-]{0,63}"))) { "Invalid shortcut" }
        require(shortcut !in setOf("skills", "skill")) { "Reserved shortcut" }
        require(tools.size <= 32 && tools.distinct().size == tools.size && tools.all {
            it.matches(Regex("[A-Za-z0-9_.:-]{1,128}"))
        }) { "Invalid tool names" }
        require(folders.size <= 16 && folders.map { it.alias }.distinct().size == folders.size) { "Duplicate folders" }
        folders.forEach { it.validate() }
        require(children.size <= 32 && children.map { it.id }.distinct().size == children.size) { "Duplicate child Skills" }
        children.forEach { require(it.id.isNotBlank() && it.id.length <= 200) { "Invalid child Skill" } }
        require(callExamples.length <= 8000) { "Call examples are too large" }
        return this
    }
}

@Serializable
data class SkillFolder(val alias: String, val location: String, val server: String = "Local Sandbox") {
    fun validate() {
        require(alias.matches(Regex("[a-z][a-z0-9-]{0,63}"))) { "Invalid folder alias" }
        require(location.isNotBlank() && location.length <= 4096 && !location.any { it == '\u0000' }) { "Invalid folder location" }
        require(location.startsWith('/') || location.startsWith("content://")) { "Use an absolute directory path or chosen Android URI" }
        require(server.isNotBlank() && server.length <= 200) { "Invalid folder server" }
    }
    val isDocumentTree: Boolean get() = location.startsWith("content://")
}

@Serializable
data class SkillDependency(val id: String, val optional: Boolean = false)

data class SkillDocument(val name: String, val instructions: String, val configuration: SkillConfiguration) {
    fun encode(): String {
        configuration.validate()
        require(instructions.toByteArray().size <= MAX_BYTES) { "Skill is too large" }
        val header = Json.encodeToString(configuration).replace("<", "\\u003c").replace(">", "\\u003e")
        return "$PREFIX$header\n-->\n$instructions".also {
            require(it.toByteArray().size <= MAX_BYTES) { "Skill is too large" }
        }
    }
    companion object {
        const val MAX_BYTES = 1_048_576
        private const val PREFIX = "<!-- agora-skill/v1\n"
        fun decode(name: String, text: String): SkillDocument {
            require(text.toByteArray().size <= MAX_BYTES) { "Skill is too large" }
            if (!text.startsWith(PREFIX)) return SkillDocument(name, text,
                SkillConfiguration(id = UUID.nameUUIDFromBytes(name.toByteArray()).toString()))
            val end = text.indexOf("\n-->\n", PREFIX.length)
            require(end > 0) { "Incomplete Skill configuration" }
            val config = Json.decodeFromString<SkillConfiguration>(text.substring(PREFIX.length, end)).validate()
            return SkillDocument(name, text.substring(end + 5), config)
        }
        fun revision(text: String): String = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/** Only explicit Skill commands enter this path. Templates retain their existing shortcuts. */
fun skillCommand(text: String): Pair<String, String>? {
    val value = text.trim()
    if (!value.startsWith("/skill ")) return null
    val rest = value.removePrefix("/skill ").trim()
    require(rest.isNotEmpty()) { "Choose a Skill" }
    return rest.substringBefore(' ') to rest.substringAfter(' ', "")
}
