package com.example.conversation.llm

import com.example.conversation.config.ChatProperties
import com.example.conversation.error.ContextTooLarge
import org.springframework.stereotype.Component

/**
 * Chooses which history fits in the prompt. Keeps whole user+assistant exchanges,
 * newest first, until the character budget is reached. The system prompt and the
 * current input are always kept; if even those don't fit -> 422.
 *
 * Note: characters are only a rough stand-in for tokens. Older details of a long
 * conversation are intentionally "forgotten" by the model.
 */
@Component
class PromptPolicy(private val properties: ChatProperties) {

    fun selectHistory(history: List<PromptExchange>, systemPrompt: String, currentInput: String): List<PromptExchange> {
        var budget = properties.maxPromptChars - systemPrompt.length - currentInput.length
        if (budget < 0) throw ContextTooLarge()

        val exchanges = history.takeLast(properties.contextExchanges)
        val kept = ArrayDeque<PromptExchange>()
        for (exchange in exchanges.asReversed()) {
            val size = exchange.user.length + exchange.assistant.length
            if (size > budget) break
            budget -= size
            kept.addFirst(exchange)
        }
        return kept.toList()
    }
}
