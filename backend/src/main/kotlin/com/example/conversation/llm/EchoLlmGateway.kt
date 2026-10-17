package com.example.conversation.llm

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Fake model for development (`make backend-echo`): replies with the user's last message.
 * Lets the frontend talk to the real backend + database without an API key.
 */
@Component
@ConditionalOnProperty(name = ["app.llm.provider"], havingValue = "echo")
class EchoLlmGateway : LlmGateway {
    override fun generate(messages: List<PromptMessage>): String {
        val last = messages.lastOrNull { it.role == PromptRole.USER }?.content.orEmpty()
        val historySize = messages.count { it.role != PromptRole.SYSTEM } - 1
        // The trailing line mimics what the real model is asked to add (see prompts/system.txt).
        return "(echo, $historySize earlier messages in context) You said: $last\n" +
            "[[suggestions]] Say it again | Tell me more | Give an example"
    }
}
