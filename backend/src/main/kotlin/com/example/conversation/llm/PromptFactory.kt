package com.example.conversation.llm

import com.example.conversation.config.ChatProperties
import org.springframework.stereotype.Component

/** Builds: [system prompt] + [selected history] + [current user input, exactly once]. */
@Component
class PromptFactory(
    properties: ChatProperties,
    private val policy: PromptPolicy,
) {
    private val systemPrompt: String =
        properties.systemPrompt.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }.trim()

    fun build(history: List<PromptExchange>, currentInput: String): List<PromptMessage> {
        val selected = policy.selectHistory(history, systemPrompt, currentInput)
        return buildList {
            add(PromptMessage(PromptRole.SYSTEM, systemPrompt))
            selected.forEach { exchange ->
                add(PromptMessage(PromptRole.USER, exchange.user))
                add(PromptMessage(PromptRole.ASSISTANT, exchange.assistant))
            }
            add(PromptMessage(PromptRole.USER, currentInput))
        }
    }
}
