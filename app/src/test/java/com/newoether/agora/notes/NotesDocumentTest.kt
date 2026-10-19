package com.newoether.agora.notes

import com.newoether.agora.data.NativeBackupMediaPolicy
import com.newoether.agora.model.AttachmentItem
import com.newoether.agora.model.AttachmentMeta
import com.newoether.agora.tool.parseNotesProposal
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NotesDocumentTest {
    @Test fun `inline styles preserve UTF16 positions and combined formatting through tool and wire`() {
        val proposal = parseNotesProposal("""{"title":"Styled","blocks":[{"id":"one","type":"paragraph","text":"😀 bold italic","styles":[{"start":3,"end":7,"bold":true,"strike":true},{"start":8,"end":14,"italic":true}]}]}""")
        val styles = proposal.blocks.single().styles!!
        assertEquals(listOf(NotesInlineStyle(3,7,bold=true,strike=true), NotesInlineStyle(8,14,italic=true)), styles)
        assertEquals(styles, NotesDocumentFormat.decodeProposal(proposal.encode()).blocks.single().styles)
        val doc = manifest(emptyMap()).copy(blocks = proposal.blocks)
        assertTrue(doc.contextText().contains("\"styles\""))
        assertTrue(doc.contextText().contains("\"strike\":true"))
    }
    @Test fun `inline styles reject invalid overlapping unsorted and split character ranges`() {
        val text = "😀 abcd"
        val invalid = listOf(
            listOf(NotesInlineStyle(-1,2,bold=true)),
            listOf(NotesInlineStyle(2,2,bold=true)),
            listOf(NotesInlineStyle(3,20,bold=true)),
            listOf(NotesInlineStyle(3,6,bold=true),NotesInlineStyle(5,7,italic=true)),
            listOf(NotesInlineStyle(5,7,bold=true),NotesInlineStyle(3,4,italic=true)),
            listOf(NotesInlineStyle(1,2,bold=true)),
        )
        invalid.forEach { styles ->
            assertThrows(IllegalArgumentException::class.java) {
                NotesDocumentFormat.validateBlocks("Styled", listOf(NotesBlock("one","paragraph",text,styles=styles)))
            }
        }
        NotesDocumentFormat.validateBlocks("Plain", listOf(NotesBlock("one","paragraph",text)))
        NotesDocumentFormat.validateBlocks("Styled", listOf(NotesBlock("one","paragraph",text,
            styles=listOf(NotesInlineStyle(0,2,bold=true),NotesInlineStyle(3,7,italic=true)))))
    }
    @org.junit.Test fun readableExportFilenamesArePortableAndMatchNotebookNames(){
        org.junit.Assert.assertEquals("To do.notes.zip",NotesDocumentFormat.filename("To do"))
        org.junit.Assert.assertEquals("Notebook.notes.zip",NotesDocumentFormat.filename(".."))
        org.junit.Assert.assertEquals("Café.notes.zip",NotesDocumentFormat.filename("Cafe\u0301"))
        org.junit.Assert.assertFalse(NotesDocumentFormat.filename("a/b\\c\u0000").contains('/'))
    }

    @get:Rule val temp = TemporaryFolder()
    private val docId = UUID.randomUUID().toString()
    private fun manifest(checksums: Map<String, String>) = NotesDocument("kalamos.notes-document/v1", docId,
        "a".repeat(64), "Tasks", listOf(NotesBlock("task-1", "task", "Milk", false)),
        listOf(NotesPage("first", "pages/first.md", "pages/first.json")), checksums)
    private fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun zip(extra: String? = null, corrupt: Boolean = false): File {
        val files = linkedMapOf("pages/first.md" to "typed", "pages/first.json" to "{\"inkRuns\":[]}")
        val document = manifest(files.mapValues { hash(it.value) })
        val file = temp.newFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            (files + ("manifest.json" to Json.encodeToString(document)) + extra?.let { mapOf(it to "unsafe") }.orEmpty()).forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name)); zip.write((if (corrupt && name.endsWith(".md")) "tampered" else text).toByteArray()); zip.closeEntry()
            }
        }
        return file
    }
    @Test fun `bundle validates vectors and semantic projection without model exposure to internal files`() {
        val document = NotesDocumentFormat.read(zip())
        assertEquals(docId, document.documentId)
        assertTrue(document.contextText().contains("\"type\":\"task\""))
        assertTrue(document.contextText().contains("targetPageIds: [\"first\"]"))
        assertFalse(document.contextText().contains("inkRuns"))
    }
    @Test fun `unsafe zip path and checksum mismatch are refused`() {
        assertThrows(IllegalArgumentException::class.java) { NotesDocumentFormat.read(zip("../outside")) }
        assertThrows(IllegalArgumentException::class.java) { NotesDocumentFormat.read(zip(corrupt = true)) }
    }
    @Test fun `new model proposal gets host identity and updates require exact base identity`() {
        val proposal = parseNotesProposal("""{"title":"Tasks","blocks":[{"type":"task","text":"Milk","checked":false}]}""")
        assertNotNull(UUID.fromString(proposal.proposalId))
        assertNotNull(UUID.fromString(proposal.blocks.single().id))
        assertThrows(IllegalArgumentException::class.java) {
            proposal.copy(documentId = docId).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            proposal.copy(blocks = listOf(NotesBlock("one", "task", "Milk"))).validate()
        }
    }
    @Test fun `actual wire always includes schema and unchecked state without null new note identities`() {
        val proposal = parseNotesProposal("""{"title":"Tasks","blocks":[{"type":"task","text":"Milk","checked":false}]}""")
        val wire = Json.parseToJsonElement(proposal.encode()).jsonObject
        assertEquals("kalamos.notes-proposal/v1", wire.getValue("schema").jsonPrimitive.content)
        assertEquals("organize", wire.getValue("operation").jsonPrimitive.content)
        assertFalse(wire.containsKey("documentId"))
        assertFalse(wire.containsKey("baseRevisionId"))
        assertFalse(wire.getValue("blocks").jsonArray.single().jsonObject.getValue("checked").jsonPrimitive.boolean)
    }
    @Test fun `ink replacement wire preserves exact scope and region identities`() {
        val proposal = parseNotesProposal("""{"operation":"replace_ink","documentId":"$docId","baseRevisionId":"${"a".repeat(64)}","targetPageIds":["first"],"title":"Tasks","blocks":[{"type":"task","text":"Milk","checked":false,"sourceInkRegionIds":["ink-region1"]}]}""")
        val wire = Json.parseToJsonElement(proposal.encode()).jsonObject
        assertEquals(docId, wire.getValue("documentId").jsonPrimitive.content)
        assertEquals("replace_ink", wire.getValue("operation").jsonPrimitive.content)
        assertEquals("ink-region1", wire.getValue("blocks").jsonArray.single().jsonObject.getValue("sourceInkRegionIds").jsonArray.single().jsonPrimitive.content)
        assertThrows(IllegalArgumentException::class.java) { proposal.copy(targetPageIds = null).validate() }
        assertThrows(IllegalArgumentException::class.java) { proposal.copy(blocks = proposal.blocks.map { it.copy(sourceInkRegionIds = null) }).validate() }
    }
    @Test fun `completed non-tool structured response retains document base and host-assigned proposal identity`() {
        val proposal=com.newoether.agora.ui.chat.message.responseNotesProposal("```json\n"+"""{"title":"Tasks","documentId":"$docId","baseRevisionId":"${"a".repeat(64)}","blocks":[{"id":"task-1","type":"task","text":"Milk","checked":true}]}"""+"\n```")
        assertEquals(docId,proposal.documentId);assertEquals("a".repeat(64),proposal.baseRevisionId)
        assertTrue(proposal.blocks.single().checked==true);assertNotNull(UUID.fromString(proposal.proposalId))
    }
    @Test fun `selected page scope survives model parsing and rejects invalid scope`() {
        val proposal = parseNotesProposal("""{"title":"Tasks","documentId":"$docId","baseRevisionId":"${"a".repeat(64)}","targetPageIds":["first"],"blocks":[]}""")
        assertEquals(listOf("first"), proposal.targetPageIds)
        assertEquals(listOf("first"), NotesDocumentFormat.decodeProposal(proposal.encode()).targetPageIds)
        assertThrows(IllegalArgumentException::class.java) { proposal.copy(targetPageIds = listOf("first", "first")).validate() }
        assertThrows(IllegalArgumentException::class.java) { proposal.copy(targetPageIds = emptyList()).validate() }
        assertThrows(IllegalArgumentException::class.java) { proposal.copy(documentId = null, baseRevisionId = null).validate() }
    }
    @Test fun `native archive retains original document bytes alongside readable projection`() {
        val raw = Json.encodeToString(AttachmentMeta(listOf(AttachmentItem(type = "file", mimeType = NotesDocumentFormat.MIME,
            originalUri = "file:///notes.zip", textContent = "Tasks", notesDocumentId = docId, notesRevisionId = "a".repeat(64)))))
        val exported = NativeBackupMediaPolicy.rewriteAttachmentMetaForExport(raw, oldToNewImageIndex = emptyMap(), archiveEntryForSource = { "media/videos/document.zip" })
        val item = Json.decodeFromString<AttachmentMeta>(exported!!).items.single()
        assertEquals("media/videos/document.zip", item.originalUri)
        assertEquals(docId, item.notesDocumentId)
        assertFalse(item.unavailable)
    }
    @Test fun dedicatedHandwritingToolForcesReplacementAndRequiresSourceProvenance() {
        val args="""{"documentId":"$docId","baseRevisionId":"${"a".repeat(64)}","targetPageIds":["first"],"title":"Transcribed","operation":"organize","blocks":[{"type":"paragraph","text":"Hello","sourceInkRegionIds":["ink-region"]}]}"""
        val proposal=com.newoether.agora.tool.parseTranscription(args)
        assertEquals("replace_ink",proposal.operation)
        assertEquals(listOf("ink-region"),proposal.blocks.single().sourceInkRegionIds)
        assertTrue(com.newoether.agora.tool.NotesProposalToolProvider.isNotesTool("replace_notes_handwriting"))
        assertThrows(IllegalArgumentException::class.java){com.newoether.agora.tool.parseTranscription(args.replace(",\"sourceInkRegionIds\":[\"ink-region\"]",""))}
    }

    @Test fun nestedElementsRetainRelationshipsAndRejectUnknownParentsOrCycles(){
        val proposal=parseNotesProposal("""{"title":"Tasks","blocks":[{"id":"buy","type":"task","text":"Buy milk","checked":false},{"id":"eggs","type":"bullet","text":"eggs","parentId":"buy"}]}""")
        assertEquals("buy",NotesDocumentFormat.decodeProposal(proposal.encode()).blocks[1].parentId)
        assertThrows(IllegalArgumentException::class.java){proposal.copy(blocks=proposal.blocks.map{it.copy(parentId="missing")}).validate()}
        assertThrows(IllegalArgumentException::class.java){proposal.copy(blocks=listOf(NotesBlock("a","paragraph","A",parentId="b"),NotesBlock("b","paragraph","B",parentId="a"))).validate()}
    }

    @Test fun formattingRecognitionUsesHostBindingsAndRejectsAnotherSnapshotOrMissingElement(){
        val original=NotesProposal(proposalId=UUID.randomUUID().toString(),documentId=docId,baseRevisionId="a".repeat(64),title="To do",targetPageIds=listOf("first"),operation="replace_ink",blocks=listOf(NotesBlock("buy","paragraph","Buy milk",sourceInkRegionIds=listOf("ink-buy")),NotesBlock("eggs","bullet","eggs",sourceInkRegionIds=listOf("ink-eggs"))))
        val formatted=original.copy(operation="organize",blocks=listOf(NotesBlock("buy","task","Buy milk",false),NotesBlock("eggs","bullet","eggs",parentId="buy")))
        val bound=bindRecognizedFormatting(original,formatted)
        assertEquals("replace_ink",bound.operation);assertEquals("buy",bound.blocks[1].parentId)
        assertEquals(listOf("ink-eggs"),bound.blocks[1].sourceInkRegionIds)
        assertThrows(IllegalArgumentException::class.java){bindRecognizedFormatting(original,formatted.copy(baseRevisionId="b".repeat(64)))}
        assertThrows(IllegalArgumentException::class.java){bindRecognizedFormatting(original,formatted.copy(blocks=formatted.blocks.take(1)))}
        assertThrows(IllegalArgumentException::class.java){bindRecognizedFormatting(original,formatted.copy(blocks=formatted.blocks.map{it.copy(sourceInkRegionIds=listOf("other"))}))}
    }

    @Test fun addedFormattingElementsBindOneRecognizedSourceAndRejectUnknownOrUnboundContent(){
        val original=NotesProposal(proposalId=UUID.randomUUID().toString(),documentId=docId,baseRevisionId="a".repeat(64),title="To do",targetPageIds=listOf("first"),operation="replace_ink",blocks=listOf(NotesBlock("buy","paragraph","Buy milk",sourceInkRegionIds=listOf("ink-buy"))))
        val formatted=original.copy(operation="organize",blocks=listOf(NotesBlock("buy","heading","Buy"),NotesBlock("milk","task","milk",false,parentId="buy",sourceRefs=listOf("buy"))))
        val bound=bindRecognizedFormatting(original,formatted)
        assertEquals(listOf("ink-buy"),bound.blocks[0].sourceInkRegionIds)
        assertEquals(bound.blocks[0].sourceInkRegionIds,bound.blocks[1].sourceInkRegionIds)
        assertThrows(IllegalArgumentException::class.java){bindRecognizedFormatting(original,formatted.copy(blocks=formatted.blocks.map{if(it.id=="milk")it.copy(sourceRefs=null) else it}))}
        assertThrows(IllegalArgumentException::class.java){bindRecognizedFormatting(original,formatted.copy(blocks=formatted.blocks.map{if(it.id=="milk")it.copy(sourceRefs=listOf("other")) else it}))}
    }

}
