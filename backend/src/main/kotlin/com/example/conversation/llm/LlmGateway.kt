package com.example.conversation.llm

/**
 * The boundary to the language model. Implementations:
 *  - SpringAiLlmGateway (OpenRouter, default)
 *  - EchoLlmGateway (`echo` profile, for development without a model)
 *
 * Stateful response analysis is a separate application operation: it also needs
 * conversation identity, response identity and the version of its stored state.
 */
interface LlmGateway {
    /**
     * @param messages system prompt, history and the new user input, in order
     * @return the complete, non-blank reply text
     * @throws com.example.conversation.error.ApplicationFailure on provider problems
     */
    fun generate(messages: List<PromptMessage>): String
}
