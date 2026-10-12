package com.newoether.agora.ui.chat

import org.junit.Assert.*
import org.junit.Test

class InteractiveAnswerWebPolicyTest {
    @Test fun `only exact bundled filenames resolve as host resources`() {
        assertEquals("host.html", InteractiveAnswerWebPolicy.assetName(InteractiveAnswerWebPolicy.HOST_URL))
        for (url in listOf(
            "https://appassets.androidplatform.net/interactive/../secret",
            "https://appassets.androidplatform.net/interactive/%68ost.html",
            "https://appassets.androidplatform.net/interactive/host.html?x=1",
            "https://appassets.androidplatform.net/interactive/secret.json",
            "http://appassets.androidplatform.net/interactive/host.html",
            "https://appassets.androidplatform.net:8443/interactive/host.html",
        )) assertNull(url, InteractiveAnswerWebPolicy.assetName(url))
    }
    @Test fun `upstream libraries and images use exact https hosts`() {
        assertTrue(InteractiveAnswerWebPolicy.allowsNetwork("https://esm.sh/chart.js"))
        assertTrue(InteractiveAnswerWebPolicy.allowsNetwork("https://basemap.nationalmap.gov/tile/1"))
        for (url in listOf("https://esm.sh.evil.test/x", "https://evil.test", "file:///data/secret", "content://private", "javascript:alert(1)", "https://esm.sh:8443/x", "https://user@esm.sh/x", "http://esm.sh/x")) {
            assertFalse(url, InteractiveAnswerWebPolicy.allowsNetwork(url))
        }
    }
    @Test fun `links and followups reject non https credentials and invalid sizes`() {
        assertTrue(InteractiveAnswerWebPolicy.allowsLink("https://example.com/a"))
        assertFalse(InteractiveAnswerWebPolicy.allowsLink("https://user:password@example.com/a"))
        assertFalse(InteractiveAnswerWebPolicy.allowsLink("intent://x"))
        assertEquals("Explain 14", InteractiveAnswerWebPolicy.prompt(" Explain 14 "))
        assertNull(InteractiveAnswerWebPolicy.prompt(" "))
        assertNull(InteractiveAnswerWebPolicy.prompt("x".repeat(4001)))
    }
}
