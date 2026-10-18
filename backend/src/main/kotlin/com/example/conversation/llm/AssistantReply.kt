package com.example.conversation.llm

import com.example.conversation.error.GenerationFailed
import org.springframework.stereotype.Component

/**
 * The model's answer split into the text shown to the user and the follow-up chips.
 * `responseTimeMs` is how long the model call took (admin-only), set by the caller that timed it.
 */
data class AssistantReply(val text: String, val suggestions: List<String>, val responseTimeMs: Long? = null)

/**
 * The system prompt asks the model to end its answer with one line:
 *   [[suggestions]] Explain again | Show example | Related facts
 * That line is removed from the stored text. If the model forgets it, there are simply no chips.
 */
@Component
class AssistantReplyParser {

    private val marker = Regex("""^\[\[\s*suggestions\s*]]\s*:?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val fence = Regex("""^ {0,3}(`{3,}|~{3,})(.*)$""")

    fun parse(raw: String): AssistantReply {
        if (raw.isBlank()) throw GenerationFailed()
        val trimmed = raw.trimEnd()
        val lineStart = trimmed.lastIndexOf('\n') + 1
        val suffixStart = trimmed.lastIndexOf("[[")
        if (suffixStart < lineStart) return AssistantReply(raw.trim(), emptyList())
        val lastLine = trimmed.substring(suffixStart).trim()
        // Follow-ups are metadata at the end of the final line and outside fenced code.
        var openFence: String? = null
        trimmed.substring(0, lineStart).lineSequence().forEach { line ->
            fence.matchEntire(line)?.let { match ->
                val run = match.groupValues[1]
                val current = openFence
                if (current == null) openFence = run
                else if (run.first() == current.first() && run.length >= current.length && match.groupValues[2].isBlank()) openFence = null
            }
        }
        if (openFence != null) return AssistantReply(raw.trim(), emptyList())
        val payload = marker.matchEntire(lastLine)?.groupValues?.get(1)
            // Some models return [[a | b | c]] or [[a] | b | c ].
            ?: lastLine.takeIf { it.startsWith("[[") && it.endsWith("]") && '|' in it }?.removePrefix("[[")
            ?: return AssistantReply(raw.trim(), emptyList())
        val suggestions = payload.split('|')
            .map { it.trim().trim('[', ']', '"', '\'', '*', '-').trim() }
            .filter { it.isNotEmpty() && it.codePointCount(0, it.length) <= MAX_SUGGESTION_CODE_POINTS }
            .distinct()
            .take(MAX_SUGGESTIONS)
        // A malformed suffix must not silently remove content without creating any usable action.
        if (suggestions.isEmpty()) return AssistantReply(raw.trim(), emptyList())
        val text = trimmed.substring(0, suffixStart).trim()
        if (text.isEmpty()) throw GenerationFailed()
        return AssistantReply(text, suggestions)
    }

    private companion object {
        const val MAX_SUGGESTIONS = 3
        const val MAX_SUGGESTION_CODE_POINTS = 40
    }
}
