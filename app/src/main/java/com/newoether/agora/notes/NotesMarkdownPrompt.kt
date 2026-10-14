package com.newoether.agora.notes

/** Approved image-to-Markdown instruction. Document metadata stays host-owned. */
internal object NotesMarkdownPrompt {
    const val TEXT = "Turn this handwritten note into nicely styled Markdown. Do your best to read the writing. Preserve the words and their order while improving the formatting. Use headings, lists, nested lists, checkboxes, bold, italics or strikethrough where appropriate. Give to-do items and subtasks checkboxes, preserving any completed states. Return only Markdown, without explanations, code fences, HTML or comments."
}
