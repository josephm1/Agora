package com.newoether.agora.skills

import android.app.Application
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract as Docs
import android.provider.DocumentsProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import kotlinx.serialization.json.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SkillDocumentAccessTest {
    @get:Rule val temp = TemporaryFolder()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private fun access(content: ByteArray): SkillFolderAccess {
        val file = temp.newFile().also { it.writeBytes(content) }
        val provider = object : DocumentsProvider() {
            private val columns = arrayOf(Docs.Document.COLUMN_DOCUMENT_ID, Docs.Document.COLUMN_DISPLAY_NAME,
                Docs.Document.COLUMN_MIME_TYPE, Docs.Document.COLUMN_SIZE)
            override fun onCreate() = true
            override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(arrayOf(Docs.Root.COLUMN_ROOT_ID))
            override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor = MatrixCursor(columns).apply {
                addRow(arrayOf<Any>(documentId, if (documentId == "root") "References" else "source.md",
                    if (documentId == "root") Docs.Document.MIME_TYPE_DIR else "text/markdown", file.length()))
            }
            override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
                MatrixCursor(columns).apply { repeat(51) { index -> addRow(arrayOf<Any>("file-$index",
                    if (index == 0) "source.md" else "source-$index.md", "text/markdown", file.length())) } }
            override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
                parentDocumentId == "root" && documentId.startsWith("file-")
            override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
                require(mode == "r")
                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        }
        val info = android.content.pm.ProviderInfo().apply { authority = "skill.test"; exported = true; grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"; writePermission = "android.permission.MANAGE_DOCUMENTS" }
        provider.attachInfo(context, info)
        ShadowContentResolver.registerProviderInternal("skill.test", provider)
        return SkillFolderAccess(context)
    }
    private val folder = SkillFolder("references", "content://skill.test/tree/root")

    @Test fun listsMetadataWithPaginationAndReadsOnlyTheSelectedFile() {
        val access = access("Selected source".toByteArray())
        val list = Json.parseToJsonElement(access.list(folder, "", 0)).jsonObject
        assertEquals(50, list.getValue("files").jsonArray.size)
        assertEquals(50, list.getValue("next_cursor").jsonPrimitive.int)
        assertFalse(list.toString().contains("Selected source"))
        assertEquals(1, Json.parseToJsonElement(access.list(folder, "", 50)).jsonObject.getValue("files").jsonArray.size)
        val read = Json.parseToJsonElement(access.read(folder, "source.md", 100)).jsonObject
        assertEquals("Selected source", read.getValue("content").jsonPrimitive.content)
        assertFalse(read.getValue("truncated").jsonPrimitive.boolean)
    }
    @Test fun truncatedUtf8DoesNotInventAReplacementCharacter() {
        val access = access("abcéfg".toByteArray())
        val result = Json.parseToJsonElement(access.read(folder, "source.md", 4)).jsonObject
        assertEquals("abc", result.getValue("content").jsonPrimitive.content)
        assertTrue(result.getValue("truncated").jsonPrimitive.boolean)
    }
    @Test fun unreadableOrMalformedSourcesAreNotSilentlyAccepted() {
        val access = access(byteArrayOf(0xc3.toByte(), 0x28))
        assertThrows(Exception::class.java) { access.read(folder, "source.md", 100) }
        assertThrows(IllegalArgumentException::class.java) { access.read(folder, "../private.md", 100) }
        assertThrows(IllegalArgumentException::class.java) { access.list(folder, "", -1) }
        assertThrows(IllegalStateException::class.java) { access.read(folder, "missing.md", 100) }
    }
    @Test fun folderToolsRequireACompletedInvocationFromTheSelectedPath() = kotlinx.coroutines.runBlocking {
        val provider = com.newoether.agora.tool.SkillFolderToolProvider(context, null)
        assertTrue(provider.definitions(com.newoether.agora.viewmodel.GenerationContext(skillReadAccess = false)).isEmpty())
        try {
            provider.executeWithHistory("skill_read_file", "{\"invocation\":\"missing\",\"folder\":\"references\",\"path\":\"source.md\"}",
                com.newoether.agora.viewmodel.GenerationContext(), emptyList())
            fail("A guessed token must not authorize a read")
        } catch (expected: IllegalStateException) { assertTrue(expected.message.orEmpty().contains("unavailable")) }
    }

}
