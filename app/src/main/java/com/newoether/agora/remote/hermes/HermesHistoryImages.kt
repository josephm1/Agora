package com.newoether.agora.remote.hermes

import java.net.URLEncoder
import java.net.URLDecoder

/** Only native user attachment directives become authenticated, lazy image slots. */
private val imageRef = Regex("""(?m)^@(image|file):(?:`([^`\n]+)`|"([^"\n]+)"|'([^'\n]+)'|(\S+))[ \t]*$""")
internal fun hermesHistoryImages(text: String): Pair<String, List<String>> {
    val links = mutableListOf<String>()
    val rendered = imageRef.replace(text) { match ->
        val path = match.groupValues.drop(2).firstOrNull { it.isNotEmpty() }.orEmpty()
        if (match.groupValues[1] == "file" && path.substringAfterLast('.').lowercase() !in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp"))
            return@replace "[File: " + path.substringAfterLast('/').substringAfterLast('\\') + "]"
        val link = "hermes-image:" + URLEncoder.encode(path, "UTF-8")
        links += link
        "![Image]($link)"
    }
    return rendered to links
}
internal fun hermesImagePath(link: String): String {
    require(link.startsWith("hermes-image:"))
    return URLDecoder.decode(link.removePrefix("hermes-image:"), "UTF-8").also {
        require(it.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp"))
        require(!it.contains('\u0000') && !it.startsWith("http:") && !it.startsWith("https:"))
    }
}
