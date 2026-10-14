package com.newoether.agora.notes

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotesExchangeStoreTest {
    @Test fun automaticReturnReceiptSurvivesStoreRecreationAndKeepsOwner() {
        val context = RuntimeEnvironment.getApplication()
        val id = UUID.randomUUID().toString()
        assertTrue(NotesExchangeStore(context).claimAutomaticReturn("original-draft", id))
        assertFalse(NotesExchangeStore(context).claimAutomaticReturn("original-draft", id))
        assertTrue(NotesExchangeStore(context).claimAutomaticReturn("different-draft", id))
    }
    @Test fun displayCoverIsIndependentOfAiPageImagesAndIsChecksumValidated() {
        val context = RuntimeEnvironment.getApplication()
        val source = bundle(cover = true)
        val store = NotesExchangeStore(context)
        val doc = NotesDocumentFormat.read(source)
        val path = store.extractThumbnail(source, doc, UUID.randomUUID().toString())
        assertEquals("display-thumbnail", File(requireNotNull(path)).readText())
        assertTrue(doc.pages.all { it.previewFile == null })
        assertFalse(doc.contextText().contains("display-thumbnail"))
    }

    class Provider : ContentProvider() {
        val files = mutableMapOf<String, File>()
        override fun onCreate() = true
        override fun openFile(uri: Uri, mode: String) = ParcelFileDescriptor.open(
            files[uri.path] ?: throw java.io.FileNotFoundException(), ParcelFileDescriptor.MODE_READ_ONLY)
        override fun getType(uri: Uri) = NotesDocumentFormat.MIME
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    }
    private fun bundle(documentId: String = UUID.randomUUID().toString(), cover: Boolean = false): File {
        val context = RuntimeEnvironment.getApplication()
        val file = File.createTempFile("selected-", ".zip", context.cacheDir)
        val files = linkedMapOf("pages/first.md" to "typed", "pages/first.json" to "{\"inkRuns\":[]}")
        if (cover) files["pages/cover.jpg"] = "display-thumbnail"
        val checksums = files.mapValues { (_, value) -> MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) } }
        val document = NotesDocument("kalamos.notes-document/v1", documentId, "a".repeat(64), "Tasks",
            emptyList(), listOf(NotesPage("first", "pages/first.md", "pages/first.json")), checksums, thumbnailFile = if (cover) "pages/cover.jpg" else null)
        ZipOutputStream(file.outputStream()).use { zip ->
            (files + ("manifest.json" to Json.encodeToString(document))).forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
            }
        }
        return file
    }
    private fun provider() = Provider().also {
        it.attachInfo(RuntimeEnvironment.getApplication(), android.content.pm.ProviderInfo().apply { authority = "selected-notes" })
        ShadowContentResolver.registerProviderInternal("selected-notes", it)
    }
    @Test fun failedGroupStaysUnpublishedAndRetryRetainsAttachmentIdentitiesAndOwner() {
        val store = NotesExchangeStore(RuntimeEnvironment.getApplication())
        val request = NotesExchangeRequest(UUID.randomUUID().toString(), "captured-draft", "pick")
        store.save(request)
        val provider = provider()
        provider.files["/first"] = bundle()
        val uris = listOf(Uri.parse("content://selected-notes/first"), Uri.parse("content://selected-notes/second"))
        assertThrows(java.io.FileNotFoundException::class.java) { store.receiveMany(request, uris) }
        assertFalse(store.load(request.requestId)!!.ready)
        provider.files["/second"] = bundle()
        val received = store.receiveMany(request, uris)
        assertTrue(received.ready)
        assertEquals("captured-draft", received.ownerId)
        assertEquals(2, store.attachments(received).size)
        assertEquals(received.documentRequests, store.receiveMany(request, uris).documentRequests)
        assertEquals(2, store.attachments(received).map { it.notesDocumentId }.distinct().size)
    }
    @Test fun duplicateDocumentsAndCancelledGroupsAreRefused() {
        val store = NotesExchangeStore(RuntimeEnvironment.getApplication())
        val request = NotesExchangeRequest(UUID.randomUUID().toString(), "draft", "pick")
        store.save(request)
        val provider = provider()
        val duplicate = bundle()
        provider.files["/first"] = duplicate; provider.files["/second"] = duplicate
        val uris = listOf(Uri.parse("content://selected-notes/first"), Uri.parse("content://selected-notes/second"))
        assertThrows(IllegalArgumentException::class.java) { store.receiveMany(request, uris) }
        assertFalse(store.load(request.requestId)!!.ready)
        assertThrows(IllegalStateException::class.java) { store.receiveMany(request.copy(cancelled = true), uris) }
    }
}
