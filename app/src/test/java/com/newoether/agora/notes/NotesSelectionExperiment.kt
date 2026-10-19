package com.newoether.agora.notes

import com.newoether.agora.api.ToolDefinition
import com.newoether.agora.api.ToolProperty
import com.newoether.agora.tool.NotesProposalToolProvider
import com.newoether.agora.tool.ToolExecutionResult
import com.newoether.agora.viewmodel.GenerationContext
import kotlinx.serialization.json.*

/** Test-only interface experiment. Expands trusted line bindings before the real Notes tool. */
internal class NotesSelectionExperiment(
    private val notes: NotesProposalToolProvider,
    private val binding: JsonObject,
) {
    private val selections = binding.getValue("selections").jsonArray.map { it.jsonObject }
    init {
        require(selections.map { it.getValue("id").jsonPrimitive.content }.distinct().size == selections.size)
    }

    fun context(document: NotesDocument): String {
        require(document.documentId == binding.getValue("documentId").jsonPrimitive.content)
        require(document.revisionId == binding.getValue("baseRevisionId").jsonPrimitive.content)
        require(document.pages.map { it.id } == binding.getValue("targetPageIds").jsonArray.map { it.jsonPrimitive.content })
        val known = document.inkRegions.associateBy { it.id }
        val consumed = mutableSetOf<String>()
        selections.forEach { selection ->
            val pageId = selection.getValue("pageId").jsonPrimitive.content
            selection.getValue("sourceInkRegionIds").jsonArray.forEach { ref ->
                val id = ref.jsonPrimitive.content
                require(consumed.add(id) && known[id]?.pageId == pageId) { "Invalid selection provenance" }
            }
        }
        require(consumed == known.keys) { "Selection catalog omits exported ink" }
        return buildJsonObject {
            put("documentId", document.documentId)
            put("baseRevisionId", document.revisionId)
            put("title", document.title)
            put("targetPageIds", binding.getValue("targetPageIds"))
            put("blocks", NotesDocumentFormat.json.encodeToJsonElement(document.blocks))
            put("pageImages", NotesDocumentFormat.json.encodeToJsonElement(document.pages.map { page ->
                buildJsonObject { put("pageId", page.id); page.imageFrame?.let { put("imageFrame", it) } }
            }))
            putJsonArray("selections") {
                selections.forEach { add(JsonObject(it.filterKeys { key -> key != "sourceInkRegionIds" })) }
            }
        }.toString()
    }

    fun definitions(ctx: GenerationContext): List<ToolDefinition> = notes.definitions(ctx).map { definition ->
        if (definition.function.name != NotesProposalToolProvider.TRANSCRIBE_NAME) definition else {
            val parameters = definition.function.parameters
            val blocks = parameters.properties.getValue("blocks")
            val items = requireNotNull(blocks.items)
            val properties = requireNotNull(items.properties).toMutableMap().apply {
                remove("sourceInkRegionIds")
                put("sourceSelectionIds", ToolProperty("array", "Exact selection labels from the attached snapshot.",
                    items = ToolProperty("string", "Selection label")))
            }
            definition.copy(function = definition.function.copy(
                description = definition.function.description.replace("sourceInkRegionIds", "sourceSelectionIds"),
                parameters = parameters.copy(properties = parameters.properties + ("blocks" to blocks.copy(
                    items = items.copy(properties = properties, required = listOf("type", "text", "sourceSelectionIds")))))
            ))
        }
    }

    suspend fun execute(name: String, arguments: String, ctx: GenerationContext): ToolExecutionResult {
        if (name != NotesProposalToolProvider.TRANSCRIBE_NAME) return notes.executeOneShotResult(name, arguments, ctx)
        val input = Json.parseToJsonElement(arguments).jsonObject
        require(input.getValue("documentId") == binding.getValue("documentId") &&
            input.getValue("baseRevisionId") == binding.getValue("baseRevisionId") &&
            input.getValue("targetPageIds") == binding.getValue("targetPageIds")) { "Selection belongs to another snapshot" }
        val byId = selections.associateBy { it.getValue("id").jsonPrimitive.content }
        val consumed = mutableSetOf<String>()
        val blocks = input.getValue("blocks").jsonArray.map { value ->
            val block = value.jsonObject
            require(!block.containsKey("sourceInkRegionIds")) { "Use selection labels in this experiment" }
            val refs = block.getValue("sourceSelectionIds").jsonArray
            require(refs.isNotEmpty())
            val selected = refs.map { ref ->
                val id = ref.jsonPrimitive.content
                require(consumed.add(id)) { "Selection referenced more than once" }
                requireNotNull(byId[id]) { "Unknown selection" }
            }
            require(selected.map { it.getValue("pageId") }.distinct().size == 1) { "Mixed selection pages" }
            JsonObject(block.filterKeys { it != "sourceSelectionIds" } +
                ("sourceInkRegionIds" to JsonArray(selected.flatMap { it.getValue("sourceInkRegionIds").jsonArray })))
        }
        val expanded = JsonObject(input + ("blocks" to JsonArray(blocks)))
        return notes.executeOneShotResult(name, expanded.toString(), ctx)
    }
}
