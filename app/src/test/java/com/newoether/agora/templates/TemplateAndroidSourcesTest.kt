package com.newoether.agora.templates

import android.app.Application
import android.content.ContentResolver
import android.content.ContextWrapper
import android.content.Intent
import android.content.UriPermission
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import com.newoether.agora.model.InteractiveAnswer
import com.newoether.agora.sandbox.SandboxManager
import com.newoether.agora.viewmodel.GenerationContext
import io.mockk.*
import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TemplateAndroidSourcesTest {
    private val root = Uri.parse("content://references.test/tree/root")
    private val resolver = mockk<ContentResolver>()
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val context = object : ContextWrapper(application) { override fun getContentResolver() = resolver }
    private val access = TemplateAndroidSources(context)
    private val tree = mutableMapOf(
        "root" to mutableListOf(TemplateAndroidSources.Entry("medicine", "Medicine", true)),
        "medicine" to mutableListOf(TemplateAndroidSources.Entry("first", "01_heart-failure.MD", false)),
    )
    private val contents = mutableMapOf("first" to "# Heart failure\n\n**Read this article.**".toByteArray())
    private fun connected(): String {
        val permission = mockk<UriPermission>()
        every { permission.uri } returns root
        every { permission.isReadPermission } returns true
        every { resolver.persistedUriPermissions } returns listOf(permission)
        every { resolver.takePersistableUriPermission(root, Intent.FLAG_GRANT_READ_URI_PERMISSION) } just Runs
        every { resolver.query(any(), any(), any(), any(), any()) } answers {
            val uri = firstArg<Uri>()
            val id = DocumentsContract.getDocumentId(uri)
            val columns = secondArg<Array<String>>()
            MatrixCursor(columns).also { cursor ->
                if (uri.lastPathSegment == "children") tree[id].orEmpty().forEach { entry ->
                    cursor.addRow(arrayOf(entry.id, entry.name, if (entry.directory) DocumentsContract.Document.MIME_TYPE_DIR else "text/markdown"))
                } else cursor.addRow(arrayOf("Clinical references"))
            }
        }
        every { resolver.openInputStream(any()) } answers {
            contents[DocumentsContract.getDocumentId(firstArg())]?.let(::ByteArrayInputStream)
        }
        return access.connect(root)
    }
    private fun template(folder: String) = InteractiveTemplate(name = "OSCE", sourceFolderId = folder,
        answer = InteractiveAnswer("OSCE", "Choose a reference", "Choose a condition", html = "<p>Choose</p>"))

    @Test fun opensWithShellDisabledAndListsOnlyMetadataThenReadsSelectedFile() = runTest {
        val folder = connected()
        val sandbox = mockk<SandboxManager>()
        val sources = TemplateSources(sandbox, GenerationContext(shellEnabled = false, sandboxEnabled = false), access)
        val opened = sources.prepare(template(folder))
        assertEquals(listOf("Medicine/01_heart-failure.MD"), opened.resourceFiles)
        assertEquals("01 heart failure", templateReferenceSnapshot(opened.answer).single().label)
        verify(exactly = 0) { resolver.openInputStream(any()) }
        assertEquals(contents["first"]!!.toString(Charsets.UTF_8), sources.read(opened, opened.resourceFiles.single()))
        verify(exactly = 1) { resolver.openInputStream(any()) }
        coVerify { sandbox wasNot Called }
    }
    @Test fun nextOpenFindsNewFilesAndOldSnapshotCannotSelectThem() = runTest {
        val sources = TemplateSources(null, GenerationContext(), access)
        val initial = template(connected())
        val first = sources.prepare(initial)
        tree["medicine"]!!.add(TemplateAndroidSources.Entry("second", "New_condition.md", false))
        contents["second"] = "New article".toByteArray()
        val next = sources.prepare(initial)
        assertEquals(2, next.resourceFiles.size)
        assertEquals(1, first.resourceFiles.size)
        try { sources.read(first, "Medicine/New_condition.md"); fail() } catch (_: IllegalArgumentException) { }
        assertEquals("New article", sources.read(next, "Medicine/New_condition.md"))
    }
    @Test fun missingGrantMovedFileTraversalAndOversizedFileFailBeforeOpeningAnythingElse() = runTest {
        val folder = connected()
        val sources = TemplateSources(null, GenerationContext(), access)
        val opened = sources.prepare(template(folder))
        try { sources.read(opened, "../secret.md"); fail() } catch (_: IllegalArgumentException) { }
        contents["first"] = ByteArray(128001) { 65 }
        try { sources.read(opened, opened.resourceFiles.single()); fail() } catch (_: IllegalArgumentException) { }
        tree["medicine"]!!.clear()
        try { sources.read(opened, opened.resourceFiles.single()); fail() } catch (_: IllegalStateException) { }
        every { resolver.persistedUriPermissions } returns emptyList()
        assertFalse(access.connected(folder))
        try { sources.prepare(template(folder)); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun ambiguousNamesAndCyclesAreRejectedRatherThanSelectingAnotherDocument() {
        val folder = connected()
        tree["medicine"]!!.add(TemplateAndroidSources.Entry("other", "01_heart-failure.MD", false))
        assertThrows(IllegalArgumentException::class.java) { access.list(folder) }
        tree["medicine"]!!.clear()
        tree["medicine"]!!.add(TemplateAndroidSources.Entry("root", "Again", true))
        assertThrows(IllegalArgumentException::class.java) { access.list(folder) }
    }
    @Test fun manualCatalogueRemainsFixedAndCaseSensitivePathsRemainExact() = runTest {
        val folder = connected()
        val sources = TemplateSources(null, GenerationContext(), access)
        val configured = template(folder).copy(resourceFiles = listOf("Medicine/01_heart-failure.MD"), sourceFilesAuto = false)
        tree["medicine"]!!.add(TemplateAndroidSources.Entry("extra", "Extra.md", false))
        assertEquals(configured.resourceFiles, sources.prepare(configured).resourceFiles)
        try { sources.read(configured, "medicine/01_heart-failure.MD"); fail() } catch (_: IllegalArgumentException) { }
        assertTrue(isTemplateSourcePath("Medicine/01_heart-failure.MD", true))
    }
}
