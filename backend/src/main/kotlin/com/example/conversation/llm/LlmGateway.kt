package com.example.conversation.llm

/**
 * The boundary to the language model. Implementations:
 *  - SpringAiLlmGateway (OpenRouter, default)
 *  - EchoLlmGateway (`echo` profile, for development without a model)
 *
 * Future: this is where the safety check on AI replies plugs in
 * (e.g. a decorator that evaluates the reply before returning it).
 */
interface LlmGateway {
    /**
     * @param messages system prompt, history and the new user input, in order
     * @return the complete, non-blank reply text
     * @throws com.example.conversation.error.ApiException 502 / 503 / 504 on provider problems
     */
    fun generate(messages: List<PromptMessage>): String
}
