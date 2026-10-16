package com.newoether.agora.skills

import org.junit.Assert.*
import org.junit.Test

class SkillDocumentTest {
    @Test fun legacyMarkdownRemainsLiteral() {
        val source = "# Examiner\nRead source files.\n"
        val document = SkillDocument.decode("examiner.md", source)
        assertEquals(source, document.instructions)
        assertEquals(document.configuration.id, SkillDocument.decode("examiner.md", source).configuration.id)
    }
    @Test fun configurationRoundTripsWithoutChangingInstructionContent() {
        val source = SkillDocument("examiner.md", "# Instructions\nLiteral --> and <tag> text\n", SkillConfiguration(
            shortcut = "osce", tools = listOf("file_read"), folders = listOf(SkillFolder("source", "/home/agora/cases")),
            children = listOf(SkillDependency("reader.md", true)), callExamples = "read <source> -->"))
        assertEquals(source, SkillDocument.decode(source.name, source.encode()))
    }
    @Test fun malformedAndFutureConfigurationIsNotTreatedAsInstructions() {
        assertThrows(IllegalArgumentException::class.java) { SkillDocument.decode("x.md", "<!-- agora-skill/v1\n{}") }
        assertThrows(IllegalArgumentException::class.java) { SkillConfiguration(version = 2).validate() }
    }
    @Test fun duplicateFoldersToolsAndReservedShortcutsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { SkillConfiguration(shortcut = "skills").validate() }
        assertThrows(IllegalArgumentException::class.java) { SkillConfiguration(tools = listOf("read", "read")).validate() }
        assertThrows(IllegalArgumentException::class.java) {
            SkillConfiguration(folders = listOf(SkillFolder("x", "/a"), SkillFolder("x", "/b"))).validate()
        }
    }
    @Test fun sourcePathsCannotEscapeTheDeclaredFolder() {
        listOf("../private", "a/../../b", "/private", "a\\b", "a/./b").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) { safeSkillRelativePath(path) }
        }
        assertEquals("/home/agora/cases/cardio/a.md", skillFolderPath(SkillFolder("cases", "/home/agora/cases"), "cardio/a.md"))
    }
    @Test fun onlyExplicitSkillCommandsAreExpanded() {
        assertNull(skillCommand("Explain /skill examiner.md"))
        assertNull(skillCommand("/osce"))
        assertEquals("examiner.md" to "case input", skillCommand("/skill examiner.md case input"))
    }
    @Test fun filesystemListingsReturnUsableRelativePathsAndExplicitPagination() {
        val folder = SkillFolder("sources", "/home/agora/cases")
        val raw = "{\"files\":[\"/home/agora/cases/cardio/acs.md\"],\"truncated\":false}"
        val result = skillFileListing(folder, "", 0, raw)
        assertTrue(result.contains("cardio/acs.md"))
        assertFalse(result.contains("/home/agora"))
        assertThrows(IllegalArgumentException::class.java) {
            skillFileListing(folder, "", 0, "{\"files\":[\"/private/secret.md\"]}")
        }
        assertEquals("/", skillFolderPath(SkillFolder("root", "/"), ""))
    }

    @Test fun pastedAndroidPathsMapOnlyToAnAuthorizedLocalSharedMount() {
        val android = SkillFolder("sources", "/storage/emulated/0/Documents/cases")
        assertEquals("/mnt/shared/Documents/cases", mapSkillSharedPath(android, "/storage/emulated/0", true).location)
        assertThrows(IllegalArgumentException::class.java) { mapSkillSharedPath(android, "/storage/emulated/0", false) }
        val remote = android.copy(server = "My computer")
        assertEquals(remote, mapSkillSharedPath(remote, "/storage/emulated/0", false))
        val sandbox = android.copy(location = "/home/agora/cases")
        assertEquals(sandbox, mapSkillSharedPath(sandbox, "/storage/emulated/0", false))
    }

}
