package com.newoether.agora.skills

import com.newoether.agora.model.ToolCallData
import org.junit.Assert.*
import org.junit.Test

class SkillInvocationTest {
    private fun doc(name: String, children: List<SkillDependency> = emptyList()) = SkillDocument(name, "Instructions for $name",
        SkillConfiguration(children = children))
    private fun invoke(d: SkillDocument, token: String, parent: String? = null, history: List<SkillInvocation> = emptyList(), run: String = "run") =
        SkillInvocation.create(d, d.encode(), "task", token, parent, history, run)

    @Test fun declaredChildSharesItsRootAndRetainsAnExactSnapshot() {
        val child = doc("child.md")
        val root = doc("root.md", listOf(SkillDependency(child.configuration.id)))
        val a = invoke(root, "a")
        val b = invoke(child, "b", "a", listOf(a))
        assertEquals(listOf(root.configuration.id, child.configuration.id), b.chain)
        assertEquals("a", b.rootToken)
        assertEquals(child, SkillDocument.decode(b.name, b.document))
        assertEquals(SkillDocument.revision(b.document), b.revision)
    }
    @Test fun cyclesUndeclaredChildrenAndUnknownParentsFailBeforeContinuation() {
        val root = doc("root.md")
        val a = invoke(root, "a")
        assertThrows(IllegalArgumentException::class.java) { invoke(root, "b", "a", listOf(a)) }
        assertThrows(IllegalArgumentException::class.java) { invoke(doc("child.md"), "b", "a", listOf(a)) }
        assertThrows(IllegalStateException::class.java) { invoke(doc("child.md"), "b", "missing", listOf(a)) }
    }
    @Test fun repeatedSequentialChildCallsAreAllowed() {
        val child = doc("child.md")
        val root = doc("root.md", listOf(SkillDependency(child.configuration.id)))
        val a = invoke(root, "a")
        val b = invoke(child, "b", "a", listOf(a))
        assertEquals(b.chain, invoke(child, "c", "a", listOf(a, b)).chain)
    }
    @Test fun nestingAndPerTurnBudgetAreBoundedWithoutDisablingLaterTurns() {
        val chain = (0..6).map { doc("$it.md") }.toMutableList()
        for (i in 0..5) chain[i] = chain[i].copy(configuration = chain[i].configuration.copy(children = listOf(SkillDependency(chain[i+1].configuration.id))))
        val history = mutableListOf<SkillInvocation>()
        for (i in 0..5) history += invoke(chain[i], "$i", if (i == 0) null else "${i-1}", history)
        assertThrows(IllegalArgumentException::class.java) { invoke(chain[6], "6", "5", history) }
        val many = (0 until SkillInvocation.MAX_CALLS).map { invoke(doc("root-$it.md"), "token-$it") }
        assertThrows(IllegalArgumentException::class.java) { invoke(doc("extra.md"), "extra", history = many) }
        assertEquals("later", invoke(doc("later.md"), "later", history = many, run = "next-turn").token)
    }
    @Test fun foreignOrMalformedStructuredResultsCannotAuthorizeFolderReads() {
        val d = doc("a.md")
        val frame = invoke(d, "actual")
        assertTrue(SkillInvocation.history(listOf(ToolCallData("other_tool", "{}", "", toolCallId = "actual", structuredResult = frame.encode()))).isEmpty())
        assertTrue(SkillInvocation.history(listOf(ToolCallData("invoke_skill", "{}", "", toolCallId = "foreign", structuredResult = frame.encode()))).isEmpty())
        assertEquals(listOf(frame), SkillInvocation.history(listOf(ToolCallData("invoke_skill", "{}", "", toolCallId = "actual", structuredResult = frame.encode()))))
    }
}
