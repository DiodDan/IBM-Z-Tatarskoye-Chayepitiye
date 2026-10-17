package com.example.conversation.llm

import com.example.conversation.config.ChatProperties
import com.example.conversation.conversation.application.dto.MessageSnapshot
import com.example.conversation.conversation.model.MessageRole
import org.springframework.stereotype.Component

/** Builds: [system prompt] + [selected history] + [current user input, exactly once]. */
@Component
class PromptFactory(
    properties: ChatProperties,
    private val policy: PromptPolicy,
) {
    private val systemPrompt: String =
        properties.systemPrompt.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }.trim()

    fun build(history: List<MessageSnapshot>, currentInput: String): List<PromptMessage> {
        val selected = policy.selectHistory(history, systemPrompt, currentInput)
        return buildList {
            add(PromptMessage(PromptRole.SYSTEM, systemPrompt))
            selected.forEach { message ->
                val role = if (message.role == MessageRole.USER) PromptRole.USER else PromptRole.ASSISTANT
                add(PromptMessage(role, message.content))
            }
            add(PromptMessage(PromptRole.USER, currentInput))
        }
    }
}
