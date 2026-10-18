package com.example.conversation.llm

import org.springframework.ai.retry.NonTransientAiException
import org.springframework.ai.retry.TransientAiException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.client.ResponseErrorHandler
import java.net.URI

internal interface ProviderHttpFailure { val status: Int }
internal class ProviderClientFailure(override val status: Int) : NonTransientAiException("LLM provider HTTP $status"), ProviderHttpFailure
internal class ProviderServerFailure(override val status: Int) : TransientAiException("LLM provider HTTP $status"), ProviderHttpFailure

/** Retains the HTTP status that Spring AI's default handler otherwise flattens into a message. */
@Component
@ConditionalOnProperty(name = ["app.llm.provider"], havingValue = "openrouter", matchIfMissing = true)
class ProviderHttpErrorHandler : ResponseErrorHandler {
    override fun hasError(response: ClientHttpResponse): Boolean = response.statusCode.isError

    override fun handleError(url: URI, method: HttpMethod, response: ClientHttpResponse) {
        val status = response.statusCode.value()
        if (status >= 500) throw ProviderServerFailure(status)
        if (status >= 400) throw ProviderClientFailure(status)
    }
}
