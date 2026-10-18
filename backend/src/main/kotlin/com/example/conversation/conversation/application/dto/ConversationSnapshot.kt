package com.example.conversation.conversation.application.dto

import com.example.conversation.conversation.model.MessageRole
import java.time.Instant
import java.util.UUID
import com.example.conversation.llm.PromptExchange
import com.example.conversation.trajectory.application.ResponseAnalysis

// Immutable copies of database rows. They leave the transaction; JPA entities never do.

data class ConversationPreview(
    val id: UUID,
    val title: String,
    val createdAt: Instant,
)

data class MessageSnapshot(
    val id: UUID,
    val role: MessageRole,
    val content: String,
    val createdAt: Instant,
    val analysis: ResponseAnalysis? = null,
)

/** A conversation with its full history (GET /conversations/{id}). */
data class ConversationSnapshot(
    val conversation: ConversationPreview,
    val messages: List<MessageSnapshot>,
)

/** What the send workflow needs before calling the LLM. */
data class ConversationContext(
    val conversation: ConversationPreview,
    /** Most recent messages, oldest first, always whole user+assistant pairs. */
    val recentExchanges: List<PromptExchange>,
    /** Version of the conversation on which generation is based. */
    val version: Long,
)
