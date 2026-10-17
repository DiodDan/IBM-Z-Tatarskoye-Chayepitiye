package com.example.conversation.llm

import com.example.conversation.error.ApiException
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.http.HttpTimeoutException

/**
 * Calls the model on OpenRouter through Spring AI's ChatClient (OpenAI-compatible API), synchronously.
 * Spring AI types stay in this package. The API key never leaves the backend.
 */
@Component
@ConditionalOnProperty(name = ["app.llm.provider"], havingValue = "openrouter", matchIfMissing = true)
class SpringAiLlmGateway(
    private val chatClient: ChatClient,
    @Value("\${spring.ai.openai.api-key:}") apiKey: String,
    @Value("\${spring.ai.openai.chat.options.model:}") model: String,
) : LlmGateway {

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        // Fail at startup instead of on the first user message (backend-requirements.md §9).
        check(apiKey.isNotBlank()) {
            "OPENROUTER_API_KEY is not set. Put your key in .env, or run without a model using `make backend-echo`."
        }
        check(model.isNotBlank()) {
            "OPENROUTER_MODEL is not set. Put a model id from https://openrouter.ai/models in .env."
        }
    }

    override fun generate(messages: List<PromptMessage>): String {
        val aiMessages: List<Message> = messages.map { it.toSpringAi() }
        val reply = try {
            chatClient.prompt().messages(aiMessages).call().content()
        } catch (e: Exception) {
            throw classify(e)
        }
        if (reply.isNullOrBlank()) throw ApiException.generationFailed()
        return reply.trim()
    }

    private fun PromptMessage.toSpringAi(): Message = when (role) {
        PromptRole.SYSTEM -> SystemMessage(content)
        PromptRole.USER -> UserMessage(content)
        PromptRole.ASSISTANT -> AssistantMessage(content)
    }

    /**
     * 504 = timeout, 503 = OpenRouter unreachable or refusing us (bad key, no credits, rate limit,
     * unknown model), 502 = anything else. The log line says which, so you can fix .env.
     */
    private fun classify(e: Exception): ApiException {
        for (cause in generateSequence<Throwable>(e) { it.cause }) {
            when (cause) {
                is SocketTimeoutException, is HttpTimeoutException -> return ApiException.generationTimeout(e)
                is ConnectException, is UnknownHostException -> return ApiException.modelUnavailable(e)
            }
            val message = cause.message.orEmpty()
            val hint = when {
                "401" in message || "403" in message -> "OpenRouter rejected the API key (check OPENROUTER_API_KEY)"
                "402" in message -> "OpenRouter account has no credits left (or use a :free model)"
                "429" in message -> "OpenRouter rate limit reached (free models are heavily limited)"
                "404" in message || "not a valid model" in message -> "Unknown model (check OPENROUTER_MODEL)"
                else -> null
            }
            if (hint != null) {
                log.warn("LLM call refused: {}", hint)
                return ApiException.modelUnavailable(e)
            }
        }
        return ApiException.generationFailed(e)
    }
}
