package com.newoether.agora.remote.hermes

import com.newoether.agora.remote.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class HermesContractsTest {
    @Test fun nativeRowsOwnIdentityAndTextCannotConfirmDelivery() {
        val rows = Json.parseToJsonElement("""[{"role":"user","row_id":10,"text":"same"},{"role":"assistant","row_id":11,"text":"reply"}]""").jsonArray
        val first = hermesHistory(rows, emptyMap())
        assertNull(first.first().clientId)
        assertEquals("client", hermesHistory(rows, mapOf("10" to "client")).first().clientId)
        assertEquals("row:10", first.last().turnId)
        assertEquals(listOf("row:10", "row:11"), hermesPage(first, null).nodes.map { it.id })
    }
    @Test fun profilesAndAccountsHaveSeparateOwnersButPasswordsDoNotChangeIdentity() {
        val a = RemoteConnection("A", "https://host/", "secret", kind = RemoteBackendKind.HERMES, username = "a", profile = "work")
        assertEquals(a.id, a.copy(token = "rotated").id)
        assertNotEquals(a.id, a.copy(profile = "other").id)
        assertNotEquals(a.id, a.copy(username = "b").id)
        assertNotEquals(a.id, a.copy(kind = RemoteBackendKind.FILO).id)
    }
    @Test fun permissionRepliesCannotGrantPersistentOrUnadvertisedPermission() {
        val q = RemoteQuestion("q", "approval", Json.parseToJsonElement("""{"choices":["deny"]}""").jsonObject)
        validateHermesAnswer(q, buildJsonObject { put("choice", "deny") })
        for (choice in listOf("always", "session", "once")) assertThrows(FiloInputException::class.java) {
            validateHermesAnswer(q, buildJsonObject { put("choice", choice) })
        }
    }
    @Test fun invalidClarifyKeysAndChoicesAreRefused() {
        val q = RemoteQuestion("q", "clarify", Json.parseToJsonElement("""{"questions":[{"qid":"one","choices":["A","B"]}]}""").jsonObject)
        validateHermesAnswer(q, buildJsonObject {})
        validateHermesAnswer(q, Json.parseToJsonElement("""{"answers":{"one":"A"}}""").jsonObject)
        listOf("""{"answers":{"other":"A"}}""", """{"answers":{"one":"C"}}""").forEach {
            assertThrows(FiloInputException::class.java) { validateHermesAnswer(q, Json.parseToJsonElement(it).jsonObject) }
        }
    }
    @Test fun nativeImageDirectivesUseAuthenticatedSlotsWithoutAutomaticRemoteUrls() {
        val raw = "Caption\n@image:`/managed/photo one.png`"
        val (text, links) = hermesHistoryImages(raw)
        assertTrue(text.startsWith("Caption"))
        assertEquals("/managed/photo one.png", hermesImagePath(links.single()))
        val rows = buildJsonArray { add(buildJsonObject { put("row_id", 1); put("role", "user"); put("text", raw) }) }
        val message = hermesHistory(rows, emptyMap()).single()
        val page = hermesPage(listOf(message), null)
        assertEquals(1, page.nodes.single().imageCount)
        assertEquals(1, projectRemoteMessages(listOf(message)).single().markdownImages?.size)
        assertThrows(IllegalArgumentException::class.java) { hermesImagePath("https://foreign/image.png") }
    }

    @Test fun endpointCredentialsAndQuerySecretsAreRejected() {
        for (address in listOf("https://user:password@host/", "https://host/?token=secret", "https://host/path", "https://host/#secret")) {
            assertThrows(FiloConfigurationException::class.java) { HermesDashboardAuth(address, "user", "password") }
        }
    }
}
