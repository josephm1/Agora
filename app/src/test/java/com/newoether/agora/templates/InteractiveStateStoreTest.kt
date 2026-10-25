package com.newoether.agora.templates

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.newoether.agora.data.local.ChatDatabase
import com.newoether.agora.data.local.ChatEntity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class InteractiveStateStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var database: ChatDatabase
    private fun args(revision: Int = 0, value: JsonElement = JsonPrimitive("saved")) = buildJsonObject {
        put("key", "food-planner.v1"); put("expectedRevision", revision); put("value", value)
    }
    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ChatDatabase::class.java).build()
        database.chatDao().upsertConversation(ChatEntity("one", "One", lastUpdated = 1, dataChangedAt = 1))
        database.chatDao().upsertConversation(ChatEntity("two", "Two", lastUpdated = 1, dataChangedAt = 1))
    }
    @After fun cleanup() { database.close() }

    @Test fun survivesRecreationSeparatesOwnersAndAdvancesBackupWatermarkWithoutReorderingChat() = runBlocking {
        val store = InteractiveStateStore(database)
        assertEquals(JsonNull, store.access("one", "storageGet", args()) { true }.jsonObject["value"])
        store.access("one", "storageSet", args()) { true }
        assertEquals(JsonPrimitive("saved"), InteractiveStateStore(database).access("one", "storageGet", args()) { true }.jsonObject["value"])
        assertEquals(JsonNull, store.access("two", "storageGet", args()) { true }.jsonObject["value"])
        val row = database.chatDao().getConversation("one")!!
        assertTrue(row.dataChangedAt > 1); assertEquals(1L, row.lastUpdated)
    }
    @Test fun concurrentViewsCannotOverwriteOneAnother() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            val writes = (1..2).map { pool.submit<Boolean> { runCatching { runBlocking { InteractiveStateStore(database).access("one", "storageSet", args()) { true } } }.isSuccess } }
            assertEquals(1, writes.count { it.get() })
            runBlocking { assertEquals(1, InteractiveStateStore(database).access("one", "storageGet", args()) { true }.jsonObject.getValue("revision").jsonPrimitive.int) }
        } finally { pool.shutdownNow() }
    }
    @Test fun ownerRevocationBeforeCommitRollsBackStateAndWatermark() = runBlocking {
        val store = InteractiveStateStore(database)
        store.access("one", "storageSet", args()) { true }
        val before = database.chatDao().getConversation("one")
        var checks = 0
        try { store.access("one", "storageSet", args(1)) { ++checks < 3 }; fail("revocation must reject") }
        catch (_: IllegalStateException) { }
        assertEquals(before, database.chatDao().getConversation("one"))
    }
    @Test fun invalidKeysStaleRevisionsAndOversizeUtf8NeverMutateState() = runBlocking {
        val store = InteractiveStateStore(database); store.access("one", "storageSet", args()) { true }
        val before = database.chatDao().getConversation("one")
        for ((method, arguments) in listOf("storageSet" to args(), "storageGet" to buildJsonObject { put("key", "../other") }, "storageSet" to args(1, JsonPrimitive("é".repeat(33000))))) {
            try { store.access("one", method, arguments) { true }; fail("must reject") } catch (_: IllegalStateException) { } catch (_: IllegalArgumentException) { }
        }
        assertEquals(before, database.chatDao().getConversation("one"))
    }
    @Test fun deletedConversationCannotBeRecreatedByAStaleCard() = runBlocking {
        database.chatDao().deleteConversation("one")
        try { InteractiveStateStore(database).access("one", "storageSet", args()) { true }; fail("deleted owner") } catch (_: IllegalStateException) { }
        assertNull(database.chatDao().getConversation("one"))
    }
    @Test fun legacyMigrationPreservesRevisionAndDoesNotReadUnrelatedOwners() = runBlocking {
        val directory = temporary.newFolder(); val owner = File(directory, InteractiveStateStore.hash("one")).apply { mkdirs() }
        val legacy = buildJsonObject { put("revision", 7); put("value", "legacy") }
        File(owner, InteractiveStateStore.hash("food-planner.v1") + ".json").writeText(legacy.toString())
        val unrelated = File(directory, InteractiveStateStore.hash("two")).apply { mkdirs() }
        File(unrelated, InteractiveStateStore.hash("broken") + ".json").writeText("corrupt")
        val store = InteractiveStateStore(database, directory)
        assertEquals(legacy, store.access("one", "storageGet", args()) { true })
        assertFalse(owner.exists()); assertTrue(unrelated.exists())
        assertEquals(legacy, InteractiveStateStore(database).access("one", "storageGet", args()) { true })
    }
    @Test fun ownerQuotaRejectsTheThirtyThirdKeyWithoutLosingExistingValues() = runBlocking {
        val store = InteractiveStateStore(database)
        for (index in 0 until 32) store.access("one", "storageSet", buildJsonObject {
            put("key", "entry-$index"); put("expectedRevision", 0); put("value", index)
        }) { true }
        val before = database.chatDao().getConversation("one")
        try { store.access("one", "storageSet", args()) { true }; fail("Owner quota must reject") }
        catch (_: IllegalArgumentException) { }
        assertEquals(before, database.chatDao().getConversation("one"))
    }

    @Test fun cancellationBeforeCommitRollsBackTheWrite() = runBlocking {
        val store = InteractiveStateStore(database)
        val before = database.chatDao().getConversation("one")
        var checks = 0
        try {
            store.access("one", "storageSet", args()) {
                if (++checks == 3) throw kotlinx.coroutines.CancellationException("Card disposed")
                true
            }
            fail("Cancellation must propagate")
        } catch (_: kotlinx.coroutines.CancellationException) { }
        assertEquals(before, database.chatDao().getConversation("one"))
    }

}
