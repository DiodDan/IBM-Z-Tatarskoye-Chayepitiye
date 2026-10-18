package com.example.conversation.error

/** Failures of application operations, independent of HTTP. */
sealed class ApplicationFailure(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class ConversationNotFound : ApplicationFailure("Conversation does not exist or belongs to another user.")
class ConversationBusy : ApplicationFailure("Conversation has an operation in progress.")
class ConversationChanged : ApplicationFailure("Conversation changed while the response was being generated.")
class InvalidInput(message: String) : ApplicationFailure(message)
class ContextTooLarge : ApplicationFailure("The message exceeds the configured prompt budget.")
class GenerationFailed(cause: Throwable? = null) : ApplicationFailure("AI generation failed. Nothing was saved.", cause)
class GenerationUnavailable(cause: Throwable? = null) : ApplicationFailure("The AI model is unavailable.", cause)
class GenerationTimeout(cause: Throwable? = null) : ApplicationFailure("The AI took too long to answer.", cause)
class AnalysisConflict(message: String) : ApplicationFailure(message)
class AnalysisRejected(message: String) : ApplicationFailure(message)
