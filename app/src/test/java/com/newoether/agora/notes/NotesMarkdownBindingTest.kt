package com.newoether.agora.notes

import android.app.Application
import android.content.pm.*
import com.newoether.agora.model.SelectedAttachment
import com.newoether.agora.tool.NotesProposalToolProvider
import com.newoether.agora.viewmodel.GenerationContext
import com.newoether.agora.viewmodel.captureNotesMarkdownDocument
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class NotesMarkdownBindingTest {
    private val app get() = RuntimeEnvironment.getApplication()
    @Before fun installed() {
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName="com.kalamos.notebook"
            applicationInfo=ApplicationInfo().apply { packageName="com.kalamos.notebook" }
            activities=arrayOf(ActivityInfo().apply { packageName="com.kalamos.notebook";name="com.kalamos.notebook.exchange.AgoraDocumentActivity";applicationInfo=ApplicationInfo().apply { packageName="com.kalamos.notebook" } })
        })
    }
    private fun fixture(): Pair<SelectedAttachment,NotesDocument> {
        val files=mapOf("pages/first.md" to "", "pages/first.json" to "{}", "pages/first.jpg" to "image")
        val checksums=files.mapValues { MessageDigest.getInstance("SHA-256").digest(it.value.toByteArray()).joinToString("") { byte -> "%02x".format(byte) } }
        val region=NotesInkRegion("ink-one","first","run-one",buildJsonObject { put("x",0);put("y",0);put("width",40);put("height",20) },listOf("stroke-one"))
        val doc=NotesDocument("kalamos.notes-document/v1",UUID.randomUUID().toString(),"a".repeat(64),"Tasks",emptyList(),
            listOf(NotesPage("first","pages/first.md","pages/first.json","pages/first.jpg")),checksums,inkRegions=listOf(region))
        val file=File.createTempFile("markdown-note-",".zip",app.cacheDir)
        ZipOutputStream(file.outputStream()).use { zip -> (files+mapOf("manifest.json" to Json.encodeToString(doc))).forEach { (name,text) ->
            zip.putNextEntry(ZipEntry(name));zip.write(text.toByteArray());zip.closeEntry()
        } }
        return SelectedAttachment(uri=file.toURI().toString(),type="file",mimeType=NotesDocumentFormat.MIME,
            localPath=file.absolutePath,preRenderedPaths=listOf("page-image.jpg"),notesDocumentId=doc.documentId,notesRevisionId=doc.revisionId) to doc
    }
    @Test fun exactSubmittedAttachmentIsFrozenAndMultipleNotesCannotGrantAmbiguousEdit() {
        val (first,doc)=fixture();val (second,_)=fixture()
        val captured=captureNotesMarkdownDocument(listOf(first))!!
        assertEquals(doc,captured)
        assertNull(captureNotesMarkdownDocument(listOf(first,second)))
        assertNull(captureNotesMarkdownDocument(listOf(first.copy(notesRevisionId="b".repeat(64)))))
        File(first.localPath!!).writeText("changed after submission")
        assertEquals(doc,captured)
        assertNull(captureNotesMarkdownDocument(listOf(first)))
    }
    @Test fun realToolAcceptsOnlyMarkdownAndOwnsIdentityAndInkScope():Unit=runBlocking {
        val (attachment,doc)=fixture()
        val context=GenerationContext(conversationId="chat",notesMarkdownDocument=captureNotesMarkdownDocument(listOf(attachment)))
        val provider=NotesProposalToolProvider(app)
        val definition=provider.definitions(context).single().function
        assertEquals(NotesProposalToolProvider.TRANSCRIBE_NAME,definition.name)
        assertEquals(setOf("markdown"),definition.parameters.properties.keys)
        val result=provider.executeOneShotResult(definition.name,"""{"markdown":"# Tasks\n- [ ] Milk"}""",context)
        val proposal=NotesDocumentFormat.decodeProposal(result.structuredContent!!)
        assertEquals(doc.documentId,proposal.documentId);assertEquals(doc.revisionId,proposal.baseRevisionId)
        assertEquals(listOf("first"),proposal.targetPageIds);assertEquals(doc.inkRegions,proposal.sourceInkRegions)
        assertEquals("# Tasks\n- [ ] Milk",proposal.markdown);assertTrue(proposal.blocks.isEmpty())
        assertEquals("kalamos.notes-proposal/v1",Json.parseToJsonElement(proposal.encode()).jsonObject.getValue("schema").jsonPrimitive.content)
        var rejected=false
        try { provider.executeOneShotResult(definition.name,"""{"markdown":"Milk","documentId":"invented"}""",context) }
        catch (_:IllegalArgumentException) { rejected=true }
        assertTrue(rejected)
        val typed=doc.copy(title="Metadata title not on the page",blocks=listOf(NotesBlock("typed","paragraph","Duplicate typed content")))
        val visible=typed.contextText()
        assertEquals(NotesMarkdownPrompt.TEXT+"\n",visible)
        assertFalse(visible.contains(typed.title));assertFalse(visible.contains("Duplicate typed content"))
        assertFalse(visible.contains(doc.documentId));assertFalse(visible.contains("ink-one"));assertFalse(visible.contains("bounds"))
        assertTrue(doc.contextText(preferMarkdown=false).contains(doc.documentId))
    }
}
