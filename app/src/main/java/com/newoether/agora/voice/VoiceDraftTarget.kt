package com.newoether.agora.voice

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange

/** A completed dictation may edit only the draft whose visible owner accepted the microphone tap. */
internal class VoiceDraftTarget(private val state: TextFieldState, private val isCurrent: () -> Boolean) {
    private var cancelled = false
    fun cancel() { cancelled = true }
    fun insert(transcript: String): Boolean {
        if (cancelled || !isCurrent() || transcript.isBlank()) return false
        state.edit {
            val content = asCharSequence()
            val start = minOf(selection.start, selection.end)
            val end = maxOf(selection.start, selection.end)
            val spoken = transcript.trim()
            val before = if (start > 0 && !content[start - 1].isWhitespace() && spoken.first().isLetterOrDigit()) " " else ""
            val after = if (end < length && content[end].isLetterOrDigit()) " " else ""
            val insertion = before + spoken + after
            replace(start, end, insertion)
            selection = TextRange(start + insertion.length)
        }
        return true
    }
}
