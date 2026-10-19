package com.newoether.agora.notes

/** Compile semantic formatting of an uncommitted recognition snapshot into an ink edit.
 * The host owns provenance; models need only retain recognized element identities.
 * This is not a rebase onto a newer saved document.
 */
internal fun bindRecognizedFormatting(recognized: NotesProposal, formatted: NotesProposal): NotesProposal {
    recognized.validate(); formatted.validate()
    require(recognized.operation == "replace_ink" && recognized.documentId == formatted.documentId &&
        recognized.baseRevisionId == formatted.baseRevisionId && recognized.targetPageIds == formatted.targetPageIds) {
        "Formatting does not target this recognition snapshot"
    }
    val sources = recognized.blocks.associateBy { it.id }
    require(formatted.blocks.map { it.id }.toSet().containsAll(sources.keys)) {
        "Formatting must preserve recognized elements before their ink is replaced"
    }
    val bound = formatted.blocks.map { block ->
        val sourceId=if(block.id in sources)block.id else requireNotNull(block.sourceRefs?.singleOrNull()) { "Added elements need one recognized source block" }
        require(block.sourceRefs==null || block.sourceRefs==listOf(sourceId)) { "Formatting changed element provenance" }
        val refs = requireNotNull(sources[sourceId]?.sourceInkRegionIds) { "Unknown recognized source block" }
        require(block.sourceInkRegionIds == null || block.sourceInkRegionIds == refs) {
            "Formatting changed source bindings"
        }
        block.copy(sourceInkRegionIds = refs)
    }
    return formatted.copy(operation = "replace_ink", blocks = bound).validate()
}
