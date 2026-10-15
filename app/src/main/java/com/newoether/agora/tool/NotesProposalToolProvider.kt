package com.newoether.agora.tool

import android.content.Context
import android.content.ComponentName
import com.newoether.agora.api.*
import com.newoether.agora.notes.*
import com.newoether.agora.viewmodel.GenerationContext
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlinx.serialization.json.*

/** Ordinary completed tool result; Notes owns the automatic validated document commit. */
class NotesProposalToolProvider(private val app: Context) : ToolProvider {
    companion object {
        const val NAME = "propose_notes_document"
        const val TRANSCRIBE_NAME = "replace_notes_handwriting"
        fun isNotesTool(name:String?) = name == NAME || name == TRANSCRIBE_NAME
    }
    private fun available() = runCatching {
        app.packageManager.getActivityInfo(ComponentName("com.kalamos.notebook", "com.kalamos.notebook.exchange.AgoraDocumentActivity"), 0)
    }.isSuccess
    override fun handles(name: String) = isNotesTool(name)
    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.notesIntegrationEnabled || !available()) return emptyList()
        if (ctx.notesMarkdownDocument != null) return listOf(ToolDefinition(function = ToolFunction(
            TRANSCRIBE_NAME, "Return notebook content only in the markdown argument. Explanations and surrounding chat text stay in the conversation; they are not notebook content.",
            ToolParameters(properties = linkedMapOf("markdown" to ToolProperty("string", "Markdown for the attached page; use task checkboxes where appropriate.")), required = listOf("markdown")))))
        val ordinary = ToolDefinition(function = ToolFunction(NAME,
            "Create or organize editable Kalamos Notes content and return it to the real Notes editor. " +
                "A successful requested transformation is applied in Notes without an extra approval step; nothing is sent automatically. For updates use documentId and baseRevisionId from an attached Notes revision; " +
                "echo targetPageIds exactly from the attachment, preserve the IDs and checked states of unchanged blocks, and return the complete working block list. " +
                "For a new document omit both identities. New blocks may omit id; the host assigns it. " +
                "Types: heading, paragraph, bullet, numbered, task. Tasks require checked. Use parentId to retain grouped/nested items. " +
                "Preserve meaning; do not invent tasks, deadlines, priorities or transcription for unreadable or untranscribed ink. For handwriting recognition call replace_notes_handwriting instead. " +
                "For recognitionProposalId, preserve every recognized block ID. Added/split elements use sourceRefs containing exactly one recognized block ID; group using parentId. The host supplies document/revision/page identity and ink bindings, so omit those fields; title is optional for this stage. Keep the payload under 80000 UTF-8 bytes.",
            ToolParameters(properties = linkedMapOf(
                "recognitionProposalId" to ToolProperty("string", "Staged recognition ID. Preserve its block IDs; added elements reference one recognized block through sourceRefs. Omit sourceInkRegionIds."),
                "documentId" to ToolProperty("string", "Existing Notes document UUID; omit to create."),
                "baseRevisionId" to ToolProperty("string", "Exact 64-character revision hash from the attachment; omit to create."),
                "targetPageIds" to ToolProperty("array", "For an edit echo the exact selected page IDs from the attached document.", items = ToolProperty("string", "Selected Notes page ID")),
                "title" to ToolProperty("string", "Document title, 1 to 200 characters."),
                "blocks" to ToolProperty("array", "Complete ordered semantic content, at most 2000 blocks.", items = ToolProperty("object", "Editable block", properties = linkedMapOf(
                    "id" to ToolProperty("string", "Preserve an existing block ID; omit for new content."),
                    "type" to ToolProperty("string", "heading, paragraph, bullet, numbered or task"),
                    "text" to ToolProperty("string", "Literal editable text, at most 20000 characters."),
                    "sourceInkRegionIds" to ToolProperty("array", "Preserve existing ink bindings; new handwriting conversion uses replace_notes_handwriting.", items = ToolProperty("string", "Ink region ID")),
                    "sourceRefs" to ToolProperty("array", "For added/split staged elements, exactly one recognized block ID supplying its content and original ink anchor.", items=ToolProperty("string","Recognized block ID")),
                    "parentId" to ToolProperty("string", "Optional ID of the parent group/item. Preserve indentation and arrow-linked subitems; do not flatten them."),
                    "styles" to ToolProperty("array", "Preserve existing inline styles; sorted nonoverlapping UTF-16 ranges.", items = ToolProperty("object", "Inline style", properties = linkedMapOf(
                        "start" to ToolProperty("integer", "Inclusive UTF-16 offset."),
                        "end" to ToolProperty("integer", "Exclusive UTF-16 offset."),
                        "bold" to ToolProperty("boolean", "Bold text."),
                        "italic" to ToolProperty("boolean", "Italic text."),
                        "strike" to ToolProperty("boolean", "Struck-through text."),
                    ), required = listOf("start", "end"))),
                    "checked" to ToolProperty("boolean", "Required for a task; omit otherwise."),
                ), required = listOf("type", "text"))),
            ), required = listOf("blocks")),
        ))
        val properties = ordinary.function.parameters.properties.toMutableMap().apply {
            remove("recognitionProposalId")
            put("deferToFormatting", ToolProperty("boolean", "For recognition followed by organization, true stages text without applying it. Then call propose_notes_document with the returned recognitionProposalId; false/default applies transcription directly."))
        }
        val blocks = properties.getValue("blocks")
        properties["blocks"] = blocks.copy(items = blocks.items?.copy(
            properties = blocks.items?.properties.orEmpty() + ("sourceInkRegionIds" to
                ToolProperty("array", "Exact ink region IDs being converted to this block.",
                    items = ToolProperty("string", "Ink region ID"))),
            required = listOf("type", "text", "sourceInkRegionIds")))
        val transcription = ordinary.copy(function = ordinary.function.copy(
            name = TRANSCRIBE_NAME,
            description = "Read the attached Kalamos page image and convert handwriting into editable elements on the same ruled page. " +
                "For literal transcription return one paragraph per line, using surrounding context for unclear words without polishing readable wording. Keep drawings as ink. " +
                "Echo documentId, baseRevisionId, targetPageIds and exact sourceInkRegionIds; a word can span several regions. The host resolves geometry, replaces referenced ink and preserves Undo. " +
                "For requested organization, set deferToFormatting=true: recognize literal text first, then call propose_notes_document with the returned recognitionProposalId and element IDs. " +
                "The notebook title is metadata; handwritten page titles remain separate text blocks. Do not make layout/style decisions during deferred recognition.",
            parameters = ordinary.function.parameters.copy(properties = properties,
                required = listOf("documentId", "baseRevisionId", "targetPageIds", "title", "blocks"))))
        return listOf(ordinary, transcription)
    }
    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String = executeOneShotResult(name, arguments, ctx).text
    override suspend fun executeOneShotResult(name: String, arguments: String, ctx: GenerationContext): ToolExecutionResult {
        require(handles(name) && ctx.notesIntegrationEnabled && available()) { "Kalamos Notes integration is unavailable" }
        NotesExchangeDiagnostics.record(app, "tool-response", buildJsonObject {
            put("arguments", arguments.take(80_000)); put("conversationId", ctx.conversationId)
        })
        ctx.notesMarkdownDocument?.let { document ->
            require(name == TRANSCRIBE_NAME) { "Use the Markdown handwriting tool for this note" }
            require(arguments.toByteArray(Charsets.UTF_8).size <= 80_000) { "Notes Markdown response is too large" }
            val input = NotesDocumentFormat.json.parseToJsonElement(arguments).jsonObject
            require(input.keys == setOf("markdown")) { "Return only Markdown; note identity is host-owned" }
            val markdown = input.getValue("markdown").jsonPrimitive.content
            val proposal = NotesProposal(proposalId = UUID.randomUUID().toString(), documentId = document.documentId,
                baseRevisionId = document.revisionId, title = document.title, targetPageIds = document.pages.map { it.id },
                operation = "replace_ink", markdown = markdown, backgroundRequestId=ctx.notesBackgroundRequestId, sourceInkRegions = document.inkRegions).validate()
            NotesExchangeDiagnostics.record(app, "validated-wire", buildJsonObject { put("proposal", Json.parseToJsonElement(proposal.encode())) })
            return ToolExecutionResult(text = "Processed Notes page. Returning to Notes for automatic application.",
                displayText = markdown, structuredContent = proposal.encode())
        }
        val input = NotesDocumentFormat.json.parseToJsonElement(arguments).jsonObject
        val defer = input["deferToFormatting"]?.jsonPrimitive?.boolean ?: false
        val recognitionId = input["recognitionProposalId"]?.jsonPrimitive?.content
        require(!defer || name == TRANSCRIBE_NAME) { "Only handwriting recognition can be staged" }
        require(recognitionId == null || name == NAME) { "Use the formatting tool for a staged recognition" }
        val stages = NotesRecognitionStore(app)
        val recognized=recognitionId?.let { stages.load(it,ctx.conversationId) }
        // Staged identity and source bindings are host-owned. Explicit identities are
        // accepted for old callers, but never allowed to retarget the saved snapshot.
        val parseInput=if(recognized==null)arguments else {
            val identities=buildJsonObject {
                put("documentId",recognized.documentId);put("baseRevisionId",recognized.baseRevisionId)
                put("targetPageIds",Json.encodeToJsonElement(recognized.targetPageIds))
            }
            identities.forEach { (key,value) -> require(input[key]==null || input[key]==value) { "Formatting does not target this recognition snapshot" } }
            JsonObject(input+identities+mapOf("operation" to JsonPrimitive("organize"),
                "title" to (input["title"]?:JsonPrimitive(recognized.title)))).toString()
        }
        val parsed = if (name == TRANSCRIBE_NAME) parseTranscription(parseInput) else parse(parseInput)
        val proposal = if (recognized==null) parsed else
            bindRecognizedFormatting(recognized, parsed).copy(proposalId=recognized.proposalId)
        if (defer) {
            stages.save(proposal, ctx.conversationId)
            NotesExchangeDiagnostics.record(app, "recognition-staged", buildJsonObject {
                put("proposalId",proposal.proposalId);put("conversationId",ctx.conversationId)
            })
            val text = buildJsonObject {
                put("recognitionProposalId", proposal.proposalId)
                put("title", proposal.title)
                put("blocks", Json.encodeToJsonElement(proposal.blocks.map { it.copy(sourceRefs=null, sourceInkRegionIds=null) }))
            }.toString()
            return ToolExecutionResult(text = "Recognition staged; the note has not changed. Format these elements with propose_notes_document using recognitionProposalId. Preserve these element IDs; added/split elements use sourceRefs:[recognizedBlockId]. Group with parentId. The host retains the notebook/revision/page identity and source bindings; send only recognitionProposalId, blocks and an optional title.\n$text",
                displayText = "Handwriting recognized; preparing editable elements.")
        }
        NotesExchangeDiagnostics.record(app, "validated-wire", buildJsonObject {
            put("proposal", Json.parseToJsonElement(proposal.encode()))
        })
        val fallback = proposal.blocks.joinToString("\n") { if (it.type == "task") "${if (it.checked == true) "[x]" else "[ ]"} ${it.text}" else it.text }
        return ToolExecutionResult(text = "Processed Notes document: ${proposal.title}\n$fallback\nReturning to Notes for automatic application.",
            displayText = fallback, structuredContent = proposal.encode())
    }
}
@Serializable
private data class ProposedBlock(val id: String? = null, val type: String, val text: String, val checked: Boolean? = null, val sourceInkRegionIds: List<String>? = null, val parentId:String?=null, val sourceRefs:List<String>?=null, val styles:List<NotesInlineStyle>?=null)
@Serializable
private data class ProposalArguments(val documentId: String? = null, val baseRevisionId: String? = null, val title: String? = null, val blocks: List<ProposedBlock>, val targetPageIds: List<String>? = null, val operation: String = "organize")
internal fun parseNotesProposal(arguments: String): NotesProposal = parse(arguments)
private fun parse(arguments: String): NotesProposal {
    require(arguments.toByteArray(Charsets.UTF_8).size <= 80_000) { "Notes proposal is too large" }
    val args = NotesDocumentFormat.json.decodeFromString<ProposalArguments>(arguments)
    return NotesProposal(proposalId = UUID.randomUUID().toString(), documentId = args.documentId,
        baseRevisionId = args.baseRevisionId, title = requireNotNull(args.title) { "A title is required outside staged formatting" }, targetPageIds = args.targetPageIds, operation = args.operation,
        blocks = args.blocks.map { NotesBlock(it.id ?: UUID.randomUUID().toString(), it.type, it.text, it.checked, sourceRefs=it.sourceRefs, sourceInkRegionIds = it.sourceInkRegionIds, parentId=it.parentId, styles=it.styles) }).validate()
}

internal fun parseTranscription(arguments:String):NotesProposal {
    val input = NotesDocumentFormat.json.parseToJsonElement(arguments).jsonObject
    val forced = JsonObject(input + ("operation" to JsonPrimitive("replace_ink")))
    return parse(forced.toString())
}
