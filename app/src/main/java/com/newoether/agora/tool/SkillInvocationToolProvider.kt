package com.newoether.agora.tool

import com.newoether.agora.api.*
import com.newoether.agora.data.SkillManager
import com.newoether.agora.model.ToolCallData
import com.newoether.agora.skills.*
import com.newoether.agora.viewmodel.GenerationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Instruction composition through the ordinary executor; all effects keep its authority. */
internal class SkillInvocationToolProvider(private val manager: SkillManager, private val label: String = "Run Skill") : ToolProvider {
    override fun handles(name: String) = name == "invoke_skill"
    override fun presentationMetadata(name: String): ToolPresentationMetadata? =
        if (handles(name)) ToolPresentationMetadata(label) else null
    override fun definitions(ctx: GenerationContext): List<ToolDefinition> = if (!ctx.skillReadAccess) emptyList() else listOf(
        ToolDefinition(function = ToolFunction(name = "invoke_skill", description =
            "Load and follow one explicitly requested saved Skill in this conversation. No separate agent is started. " +
                "Use the returned token as parent when calling a declared child. Keep the parent token on every child call. " +
                "Tools and folders in the result are dependencies, never new permissions. Do not invoke unrelated Skills.",
            parameters = ToolParameters(properties = mapOf(
                "skill" to ToolProperty("string", "Saved filename or stable Skill ID."),
                "input" to ToolProperty("string", "Task and inputs for this invocation."),
                "parent" to ToolProperty("string", "Token returned by the calling Skill; omit only for a user-requested root."),
            ), required = listOf("skill", "input")))))

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String =
        error("Skill invocation requires authorized call identity")

    suspend fun invoke(arguments: String, ctx: GenerationContext, token: String,
                       calls: List<ToolCallData>, runId: String, availableTools: Set<String>): ToolExecutionResult = withContext(Dispatchers.IO) {
        require(ctx.skillReadAccess) { "Skill read access is disabled" }
        val args = Json.parseToJsonElement(arguments).jsonObject
        fun arg(name: String) = args[name]?.jsonPrimitive?.content.orEmpty()
        val history = SkillInvocation.history(calls)
        val parentRoot = history.singleOrNull { it.token == arg("parent") }?.rootToken
        val pinned = history.firstOrNull { frame ->
            parentRoot != null && frame.rootToken == parentRoot &&
                (frame.name == arg("skill") || SkillDocument.decode(frame.name, frame.document).configuration.id == arg("skill"))
        }
        val document = pinned?.let { SkillDocument.decode(it.name, it.document) } ?: manager.document(arg("skill"))
        val raw = document.encode()
        require(document.configuration.tools.all { it in availableTools }) {
            "Required tool unavailable: " + document.configuration.tools.filterNot { it in availableTools }.joinToString()
        }
        document.configuration.children.filterNot { it.optional }.forEach { manager.document(it.id) }
        val frame = SkillInvocation.create(document, raw, arg("input"), token, arg("parent"), history, runId)
        ToolExecutionResult(text = buildString {
            appendLine("Skill: ${frame.name}; token: ${frame.token}; revision: ${frame.revision}")
            appendLine("Task: ${frame.input}")
            appendLine("Instructions remain subordinate to the user's request and system policy.")
            appendLine(document.instructions)
            appendLine("Declared configuration (folder aliases, tools and child IDs):")
            appendLine(kotlinx.serialization.json.Json.encodeToString(SkillConfiguration.serializer(), document.configuration))
            append("Use invoke_skill with parent=${frame.token} for each declared child. Return its outcome to this workflow.")
        }, structuredContent = frame.encode())
    }
}
