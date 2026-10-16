package com.newoether.agora.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

class SkillManager(context: Context) {
    private val skillDir = File(context.filesDir, "skill_db").also { it.mkdirs() }
    private val metaFile = File(skillDir, "skill_meta.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val metadata = DescriptionMetadataStore(metaFile, json)
    private val _catalogRevision = MutableStateFlow(0L)
    val catalogRevision = _catalogRevision.asStateFlow()

    data class SkillFileInfo(
        val name: String,
        val description: String = "",
    )

    @Synchronized
    fun listFiles(): List<SkillFileInfo> {
        val values = metadata.read()
        skillDir.listFiles()?.filter { it.name.endsWith(".md.bak") }?.forEach {
            recoverBody(File(it.path.removeSuffix(".bak")))
        }
        return skillDir.listFiles()
            ?.filter { it.extension == "md" }
            ?.map { SkillFileInfo(it.name, values[it.name].orEmpty()) }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    @Synchronized
    fun catalog(): String {
        val files = listFiles()
        if (files.isEmpty()) return ""
        return buildString {
            appendLine("<available_skills>")
            appendLine(
                "Use the skill file tools to read a relevant skill before following it. " +
                    "Only names and descriptions are preloaded.",
            )
            files.forEach { file ->
                append("- ")
                append(file.name)
                if (file.description.isNotBlank()) {
                    append(": ")
                    append(file.description.replace('\n', ' ').trim())
                }
                appendLine()
            }
            append("</available_skills>")
        }
    }

    @Synchronized
    fun getDescription(name: String): String {
        val file = resolveFile(name)
        if (!file.exists()) return ""
        return metadata.read()[file.name].orEmpty()
    }

    @Synchronized
    fun getMetaJson(): String = metadata.readJson()

    @Synchronized
    fun saveMetaJson(jsonString: String) {
        metadata.replaceJson(jsonString)
        _catalogRevision.value += 1
    }

    @Synchronized
    fun readFile(name: String): String {
        val file = resolveFile(name)
        recoverBody(file)
        require(file.isFile) { "File not found: $name" }
        require(file.length() <= com.newoether.agora.skills.SkillDocument.MAX_BYTES) { "Skill is too large" }
        return file.readText()
    }

    @Synchronized
    fun createFile(name: String, content: String, description: String = ""): String {
        val file = resolveFile(name)
        require(!file.exists()) { "File already exists: ${file.name}" }
        if (content.startsWith("<!-- agora-skill/v1\n")) {
            val incoming = com.newoether.agora.skills.SkillDocument.decode(file.name, content)
            require(documents().none { it.configuration.id == incoming.configuration.id }) { "Skill identity already exists" }
        }
        val values = description.takeIf(String::isNotBlank)?.let { metadata.read() }
        try {
            writeBody(file, content)
            if (values != null) {
                values[file.name] = description
                metadata.write(values)
            }
        } catch (error: Exception) {
            if (file.exists() && !file.delete()) {
                error.addSuppressed(IOException("Unable to roll back ${file.name}"))
            }
            throw error
        }
        _catalogRevision.value += 1
        return "Created ${file.name}"
    }

    @Synchronized
    fun editFile(
        name: String,
        content: String? = null,
        newName: String? = null,
        description: String? = null,
        oldString: String? = null,
        newString: String? = null,
    ): String {
        val file = resolveFile(name)
        require(file.exists()) { "File not found: $name" }
        require(content == null || oldString == null) {
            "content and old_string are mutually exclusive"
        }
        require(content != null || oldString != null || newName != null || description != null) {
            "At least one edit must be provided"
        }

        val renameTarget = newName
            ?.let(::resolveFile)
            ?.takeIf { it.name != file.name }
        if (renameTarget != null) {
            require(!renameTarget.exists()) {
                "Target file already exists: ${renameTarget.name}"
            }
        }

        val originalContent = if (content != null || oldString != null) file.readBytes() else null
        val replacementContent = when {
            oldString != null -> {
                require(oldString.isNotEmpty()) { "old_string must not be empty" }
                val existing = String(requireNotNull(originalContent), Charsets.UTF_8)
                val matches = existing.countOccurrences(oldString)
                require(matches == 1) {
                    if (matches == 0) "old_string not found in ${file.name}"
                    else "old_string matches $matches times in ${file.name}; it must be unique"
                }
                existing.replace(oldString, newString.orEmpty())
            }
            content != null -> {
                val old = String(requireNotNull(originalContent), Charsets.UTF_8)
                if (old.startsWith("<!-- agora-skill/v1\n") && !content.startsWith("<!-- agora-skill/v1\n")) {
                    com.newoether.agora.skills.SkillDocument.decode(file.name, old).copy(instructions = content).encode()
                } else content
            }
            else -> null
        }

        if (replacementContent?.startsWith("<!-- agora-skill/v1\n") == true) {
            com.newoether.agora.skills.SkillDocument.decode((renameTarget ?: file).name, replacementContent)
        }
        val values = if (renameTarget != null || description != null) metadata.read() else null
        val originalValues = values?.toMap()
        val targetName = renameTarget?.name ?: file.name
        if (renameTarget != null) {
            values?.remove(file.name)?.let { values[renameTarget.name] = it }
            values?.let { map ->
                map.keys.filter { it.startsWith("@skill-id:") && map[it] == file.name }.forEach {
                    map[it] = renameTarget.name
                }
            }
        }
        if (description != null) {
            updateDescription(requireNotNull(values), targetName, description)
        }
        val metadataChanged = values != null && values != originalValues

        var target = file
        var contentTouched = false
        var renamed = false
        try {
            if (replacementContent != null) {
                contentTouched = true
                writeBody(file, replacementContent)
            }
            if (renameTarget != null) {
                if (!file.renameTo(renameTarget)) {
                    throw IOException("Unable to rename ${file.name}")
                }
                target = renameTarget
                renamed = true
            }
            if (metadataChanged) {
                metadata.write(requireNotNull(values))
            }
        } catch (error: Exception) {
            val rollbackFile = if (renamed) target else file
            if (contentTouched) {
                try {
                    writeBody(rollbackFile, String(requireNotNull(originalContent), Charsets.UTF_8))
                } catch (restoreError: Exception) {
                    error.addSuppressed(restoreError)
                }
            }
            if (renamed && !target.renameTo(file)) {
                error.addSuppressed(IOException("Unable to roll back rename to ${file.name}"))
            }
            throw error
        }

        if (replacementContent != null || renamed || metadataChanged) {
            _catalogRevision.value += 1
        }
        return if (replacementContent != null || renamed || metadataChanged) {
            "Updated ${target.name}"
        } else {
            "No changes made."
        }
    }

    @Synchronized
    fun deleteFile(name: String): String {
        val file = resolveFile(name)
        require(file.exists()) { "File not found: $name" }
        val values = metadata.read()
        val originalValues = values.toMap()
        val content = file.readBytes()
        require(file.delete()) { "Unable to delete ${file.name}" }
        values.remove(file.name)
        values.keys.filter { it.startsWith("@skill-id:") && values[it] == file.name }.forEach(values::remove)
        if (values != originalValues) {
            try {
                metadata.write(values)
            } catch (error: Exception) {
                try {
                    writeBody(file, String(content, Charsets.UTF_8))
                } catch (restoreError: Exception) {
                    error.addSuppressed(restoreError)
                }
                throw error
            }
        }
        _catalogRevision.value += 1
        return "Deleted ${file.name}"
    }

    /** Explicit settings/invocation reads only; never called during application startup. */
    @Synchronized
    fun documents(): List<com.newoether.agora.skills.SkillDocument> = listFiles().map { document(it.name) }

    @Synchronized
    fun document(reference: String): com.newoether.agora.skills.SkillDocument {
        if (resolveFile(reference).exists()) return decodeStoredDocument(resolveFile(reference).name, readFile(reference))
        val values = metadata.read()
        values["@skill-id:$reference"]?.let { indexed ->
            if (resolveFile(indexed).isFile) {
                val candidate = decodeStoredDocument(indexed, readFile(indexed))
                if (candidate.configuration.id == reference) return candidate
            }
        }
        val found = listFiles().asSequence().map {
            decodeStoredDocument(it.name, readFile(it.name))
        }.firstOrNull { it.configuration.id == reference } ?: error("Skill not found: $reference")
        values["@skill-id:$reference"] = found.name
        metadata.write(values)
        return found

    }

    @Synchronized
    fun saveDocument(document: com.newoether.agora.skills.SkillDocument, description: String,
                     originalName: String? = null, expected: String? = null) {
        val encoded = document.encode()
        val existing = documents().filter { it.name != originalName }
        require(document.configuration.shortcut.isEmpty() || existing.none {
            it.configuration.shortcut == document.configuration.shortcut
        }) { "Skill shortcut already exists" }
        require(existing.none { it.configuration.id == document.configuration.id }) { "Skill identity already exists" }
        if (originalName == null) createFile(document.name, encoded, description) else {
            require(expected != null && readFile(originalName) == expected) { "Skill changed; reopen before saving" }
            editFile(originalName, content = encoded, newName = document.name, description = description)
        }
        val values = metadata.read()
        values["@skill-id:${document.configuration.id}"] = resolveFile(document.name).name
        metadata.write(values)
    }

    private fun decodeStoredDocument(name: String, raw: String): com.newoether.agora.skills.SkillDocument {
        val document = com.newoether.agora.skills.SkillDocument.decode(name, raw)
        if (raw.startsWith("<!-- agora-skill/v1\n")) return document
        val aliases = metadata.read().filter { it.key.startsWith("@skill-id:") && it.value == name }.keys
        require(aliases.size <= 1) { "Ambiguous legacy Skill identity" }
        val id = aliases.singleOrNull()?.removePrefix("@skill-id:") ?: return document
        return document.copy(configuration = document.configuration.copy(id = id).validate())
    }

    private fun recoverBody(file: File) {
        val backup = File(file.path + ".bak")
        if (backup.exists()) {
            if (file.exists()) check(backup.delete()) { "Unable to clear Skill backup" }
            else check(backup.renameTo(file)) { "Unable to recover Skill" }
        }
    }

    private fun writeBody(file: File, content: String) {
        require(!file.exists() || file.isFile) { "Skill path is not a regular file" }
        require(content.toByteArray().size <= com.newoether.agora.skills.SkillDocument.MAX_BYTES) { "Skill is too large" }
        recoverBody(file)
        val pending = File(file.path + ".tmp")
        val backup = File(file.path + ".bak")
        pending.outputStream().use { it.write(content.toByteArray()); it.flush(); it.fd.sync() }
        val moved = file.exists()
        if (moved) check(file.renameTo(backup)) { "Unable to stage Skill" }
        try {
            check(pending.renameTo(file)) { "Unable to save Skill" }
            if (backup.exists()) check(backup.delete()) { "Unable to clear Skill backup" }
        } catch (failure: Exception) {
            if (!file.exists() && backup.exists()) backup.renameTo(file)
            throw failure
        } finally { pending.delete() }
    }

    private fun updateDescription(
        values: MutableMap<String, String>,
        name: String,
        description: String,
    ) {
        if (description.isBlank()) values.remove(name) else values[name] = description
    }

    private fun String.countOccurrences(value: String): Int {
        require(value.isNotEmpty()) { "old_string must not be empty" }
        var count = 0
        var start = 0
        while (true) {
            val match = indexOf(value, start)
            if (match < 0) return count
            count += 1
            start = match + value.length
        }
    }

    private fun resolveFile(name: String): File {
        val sanitized = name.replace(Regex("""[/\\]"""), "_")
        val file = File(skillDir, if (sanitized.endsWith(".md")) sanitized else "$sanitized.md")
        val canonicalDirectory = skillDir.canonicalFile
        val canonicalFile = file.canonicalFile
        require(canonicalFile.parentFile == canonicalDirectory) { "Invalid file name: $name" }
        return canonicalFile
    }
}
