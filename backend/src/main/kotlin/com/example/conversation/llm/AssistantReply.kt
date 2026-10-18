package com.example.conversation.llm

import com.example.conversation.error.ApiException
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

    private val marker = Regex("""^\s*\[\[\s*suggestions\s*]]\s*:?\s*(.*)$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    /** Some free models drop the keyword and wrap the list instead: a LAST line like `[[a | b | c]]`. */
    private val bareList = Regex("""^\s*\[\[([^\[\]\n]*\|[^\[\]\n]*)]]\s*\z""", RegexOption.MULTILINE)

    fun parse(raw: String): AssistantReply {
        val match = marker.findAll(raw).lastOrNull()
            ?: bareList.find(raw.trimEnd())
            ?: return AssistantReply(raw.trim(), emptyList())

        val text = raw.removeRange(match.range).trim()
        if (text.isEmpty()) throw ApiException.generationFailed()

        val suggestions = match.groupValues[1]
            .split('|')
            .map { it.trim().trim('"', '\'', '*', '-').trim() }
            .filter { it.isNotEmpty() && it.codePointCount(0, it.length) <= MAX_SUGGESTION_CODE_POINTS }
            .distinct()
            .take(MAX_SUGGESTIONS)
        return AssistantReply(text, suggestions)
    }

    private companion object {
        const val MAX_SUGGESTIONS = 3
        const val MAX_SUGGESTION_CODE_POINTS = 40
    }
}
