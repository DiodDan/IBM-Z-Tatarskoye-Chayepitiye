package com.example.conversation.llm

import com.example.conversation.error.ApplicationFailure
import com.example.conversation.error.GenerationFailed
import com.example.conversation.error.GenerationTimeout
import com.example.conversation.error.GenerationUnavailable
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
import org.springframework.web.client.RestClientResponseException

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
        if (reply.isNullOrBlank()) throw GenerationFailed()
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
    private fun classify(e: Exception): ApplicationFailure {
        for (cause in generateSequence<Throwable>(e) { it.cause }) {
            when (cause) {
                is SocketTimeoutException, is HttpTimeoutException -> return GenerationTimeout(e)
                is ConnectException, is UnknownHostException -> return GenerationUnavailable(e)
            }
            val status = when (cause) {
                is ProviderHttpFailure -> cause.status
                is RestClientResponseException -> cause.statusCode.value()
                else -> null
            }
            if (status != null) {
                if (status in setOf(401, 402, 403, 404, 429) || status >= 500) {
                    log.warn("LLM provider refused request: status={}", status)
                    return GenerationUnavailable(e)
                }
                return GenerationFailed(e)
            }
        }
        return GenerationFailed(e)
    }
}
