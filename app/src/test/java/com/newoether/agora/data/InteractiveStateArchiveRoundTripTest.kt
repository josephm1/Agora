package com.newoether.agora.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.newoether.agora.data.local.ChatDatabase
import com.newoether.agora.data.local.ChatEntity
import com.newoether.agora.data.local.RunEntity
import com.newoether.agora.model.ConversationTodo
import com.newoether.agora.model.ConversationTodoItem
import com.newoether.agora.model.RunStatus
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InteractiveStateArchiveRoundTripTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: ChatDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(ChatDatabase.DB_NAME)
        database = ChatDatabase.build(context)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(ChatDatabase.DB_NAME)
    }

    @Test
    fun realExporterAndIncrementalBackupRestoreCurrentStateIncludingClear() = runBlocking {
        withContext(Dispatchers.IO) {
            val dao = database.chatDao()
            dao.upsertConversation(ChatEntity("c", "State", lastUpdated = 1L, dataChangedAt = 1L))
            val store = com.newoether.agora.templates.InteractiveStateStore(database)
            suspend fun save(revision: Int, value: String) {
                store.access("c", "storageSet", kotlinx.serialization.json.buildJsonObject {
                    put("key", kotlinx.serialization.json.JsonPrimitive("food-planner.v1"))
                    put("expectedRevision", kotlinx.serialization.json.JsonPrimitive(revision))
                    put("value", kotlinx.serialization.json.JsonPrimitive(value))
                }) { true }
            }
            save(0, "Before")
            val initial = dao.getConversation("c")!!.interactiveStateJson
            val settings = mockk<SettingsManager> {
                every { conversationSettings } returns flowOf(emptyMap())
            }
            val exporter = DataExporter(context, settings, mockk(), mockk())
            suspend fun export(name: String, baseline: File? = null): File {
                val file = File(temporaryFolder.root, name)
                exporter.export(Uri.fromFile(file), setOf(DataExporter.ExportCategory.CONVERSATIONS), false, baseline)
                assertTrue(file.length() > 0L)
                return file
            }
            val first = export("first.agora")
            assertEquals(initial, restoredState(first))
            save(1, "After")
            val changed = dao.getConversation("c")!!.interactiveStateJson
            val incremental = export("incremental.agora", first)
            assertEquals(changed, restoredState(incremental))
            save(2, "")
            assertEquals(dao.getConversation("c")!!.interactiveStateJson, restoredState(export("cleared.agora", incremental)))
        }
    }

    private suspend fun restoredState(file: File): String? =
        NativeBackupArchive.open(FileInputStream(file).channel).use { archive ->
            NativeConversationGraphSource.open(archive, NativeBackupFormat.CURRENT_VERSION, temporaryFolder.root).use { source ->
                val importer = NativeConversationGraphImporter(
                    database = database, chatDao = database.chatDao(), importJson = Json { ignoreUnknownKeys = true },
                    mediaRestorer = NativeConversationMediaRestorer(context, Json), scheduleMaintenance = {},
                )
                source.open().use { stream ->
                    importer.readConversationGraphHeaders(
                        stream, DataImporter.ImportStrategy.REPLACE,
                        NativeConversationMediaRestorer.RestoredMedia(emptyMap(), emptyMap(), emptyMap(), emptyList()),
                    ) { it }.conversations.single().interactiveStateJson
                }
            }
        }
}
