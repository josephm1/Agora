package com.newoether.agora.templates

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.newoether.agora.data.local.ChatDatabase
import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest

/** Conversation metadata owns mutable interface state; Room also owns backup, fork and deletion. */
internal class InteractiveStateStore(
    private val database: ChatDatabase,
    private val legacyDirectory: File? = null,
) {
    companion object {
        const val MAX_BYTES = 64000
        private const val OWNER_BYTES = 1_048_576
        internal fun hash(value: String) = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

        /** Archive staging and runtime reads share the same strict bounded format. */
        fun validate(raw: String?): String? {
            if (raw == null) return null
            require(raw.toByteArray().size <= OWNER_BYTES) { "Conversation storage is full" }
            val entries = Json.parseToJsonElement(raw) as? JsonObject ?: error("Invalid interface state")
            require(entries.size <= 32) { "Conversation storage is full" }
            entries.forEach { (key, value) ->
                require(key.matches(Regex("[a-f0-9]{64}"))) { "Invalid saved state key" }
                val record = value as? JsonObject ?: error("Invalid saved state record")
                val revision = record["revision"] as? JsonPrimitive
                require(revision != null && !revision.isString && revision.longOrNull?.let { it >= 0 } == true && "value" in record) {
                    "Saved state is invalid; it has not been replaced"
                }
                require(record.getValue("value").toString().toByteArray().size <= MAX_BYTES) { "State exceeds 64,000 bytes" }
            }
            return entries.toString()
        }
    }

    /** Only explicit card access, fork or backup invokes migration; never a startup owner scan. */
    suspend fun migrateLegacy(owner: String) = withContext(Dispatchers.IO) {
        val files = legacyDirectory ?: return@withContext
        if (database.chatDao().getInteractiveState(owner)?.interactiveStateJson != null) {
            LegacyInteractiveStateFiles.delete(files, owner)
            return@withContext
        }
        val legacy = LegacyInteractiveStateFiles.read(files, owner) ?: return@withContext
        database.withTransaction {
            val row = database.chatDao().getInteractiveState(owner) ?: return@withTransaction
            if (row.interactiveStateJson == null) {
                check(database.chatDao().compareAndSetInteractiveState(owner, null, validate(legacy)!!, System.currentTimeMillis()) == 1)
            }
        }
        LegacyInteractiveStateFiles.delete(files, owner)
    }

    suspend fun access(owner: String, method: String, argument: JsonElement?, authorized: () -> Boolean): JsonElement {
        require(owner.isNotBlank()) { "No conversation is open" }
        val args = argument as? JsonObject ?: error("Pass a storage request object")
        val key = (args["key"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
        require(key.matches(Regex("[a-z][a-z0-9_.-]{0,63}"))) { "Invalid storage key" }
        require(method in setOf("storageGet", "storageSet")) { "Unknown storage operation" }
        check(authorized()) { "This interface is no longer active" }
        migrateLegacy(owner)
        return database.withTransaction {
            val dao = database.chatDao()
            val row = dao.getInteractiveState(owner) ?: error("Conversation no longer exists")
            val entries = validate(row.interactiveStateJson)?.let { Json.parseToJsonElement(it).jsonObject }.orEmpty()
            val current = entries[hash(key)] ?: buildJsonObject { put("revision", 0); put("value", JsonNull) }
            check(authorized()) { "This interface is no longer active" }
            if (method == "storageGet") return@withTransaction current
            val revision = current.jsonObject.getValue("revision").jsonPrimitive.long
            val expected = (args["expectedRevision"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
            check(expected == revision) { "Saved data changed in another view. Reload before saving." }
            check(revision < Long.MAX_VALUE) { "Storage revision exhausted" }
            val value = args["value"] ?: error("Missing storage value")
            val replacement = buildJsonObject { put("revision", revision + 1); put("value", value) }
            val updated = JsonObject(entries + (hash(key) to replacement)).toString()
            validate(updated)
            check(dao.compareAndSetInteractiveState(owner, row.interactiveStateJson, updated, System.currentTimeMillis()) == 1) {
                "Saved data changed in another view. Reload before saving."
            }
            // Throwing revocation or cancellation before commit rolls the Room transaction back.
            check(authorized()) { "This interface is no longer active" }
            replacement
        }
    }
}
