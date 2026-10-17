package com.example.conversation.error

import org.springframework.http.HttpStatus

/**
 * Typed application failure. Throw it anywhere; ApiExceptionHandler turns it into
 * the HTTP status + ApiError body listed in backend-requirements.md §4.
 */
open class ApiException(
    val status: HttpStatus,
    val code: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    companion object {
        fun invalidRequest(message: String = "Malformed JSON or invalid request fields.") =
            ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message)

        fun invalidUserId() =
            ApiException(HttpStatus.UNAUTHORIZED, "INVALID_USER_ID", "Missing or invalid user_id UUID cookie.")

        fun forbiddenOrigin() =
            ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_ORIGIN", "Cross-origin requests are not allowed.")

        fun conversationNotFound() =
            ApiException(
                HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND",
                "Conversation does not exist or belongs to another user.",
            )

        fun conversationBusy() =
            ApiException(HttpStatus.CONFLICT, "CONVERSATION_BUSY", "Conversation has an AI response in progress.")

        fun contextTooLarge() =
            ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY, "CONTEXT_TOO_LARGE",
                "The message is too long for the model's configured prompt limit.",
            )

        fun generationFailed(cause: Throwable? = null) =
            ApiException(HttpStatus.BAD_GATEWAY, "GENERATION_FAILED", "AI generation failed. Nothing was saved.", cause)

        fun modelUnavailable(cause: Throwable? = null) =
            ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE",
                "The AI model is not reachable right now.", cause,
            )

        fun generationTimeout(cause: Throwable? = null) =
            ApiException(HttpStatus.GATEWAY_TIMEOUT, "GENERATION_TIMEOUT", "The AI took too long to answer.", cause)
    }
}
