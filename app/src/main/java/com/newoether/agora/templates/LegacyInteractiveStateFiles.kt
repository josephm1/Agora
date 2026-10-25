package com.newoether.agora.templates

import android.util.AtomicFile
import kotlinx.serialization.json.*
import java.io.File

/** Read-only compatibility for the unpublished file-based prototype; new writes are Room-only. */
internal object LegacyInteractiveStateFiles {
    fun read(directory: File, owner: String): String? {
        val folder = File(directory, InteractiveStateStore.hash(owner))
        val files = folder.listFiles().orEmpty()
        val keys = files.map { it.name.removeSuffix(".bak") }.filter { it.matches(Regex("[a-f0-9]{64}\\.json")) }.distinct()
        if (keys.isEmpty()) return null
        require(keys.size <= 32) { "Legacy conversation storage is full" }
        val records = keys.associate { name ->
            val bytes = AtomicFile(File(folder, name)).openRead().use { input ->
                val buffer = ByteArray(InteractiveStateStore.MAX_BYTES + 129)
                var count = 0
                while (count < buffer.size) {
                    val read = input.read(buffer, count, buffer.size - count)
                    if (read < 0) break
                    count += read
                }
                buffer.copyOf(count) }
            require(bytes.size <= InteractiveStateStore.MAX_BYTES + 128) { "Legacy state is too large" }
            name.removeSuffix(".json") to Json.parseToJsonElement(bytes.toString(Charsets.UTF_8))
        }
        return InteractiveStateStore.validate(JsonObject(records).toString())
    }

    fun delete(directory: File, owner: String) {
        val folder = File(directory, InteractiveStateStore.hash(owner))
        if (folder.exists()) check(folder.deleteRecursively()) { "Cannot remove legacy interface state" }
    }
}
