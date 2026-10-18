package com.example.conversation.conversation.application.dto

import com.example.conversation.conversation.model.MessageRole
import java.time.Instant
import java.util.UUID

// Immutable copies of database rows. They leave the transaction; JPA entities never do.

data class ConversationPreview(
    val id: UUID,
    val title: String,
    val createdAt: Instant,
    /** Shortened text of the newest message, for the sidebar. Null when there are no messages. */
    val lastMessage: String?,
)

data class MessageSnapshot(
    val id: UUID,
    val role: MessageRole,
    val content: String,
    val createdAt: Instant,
    val promptRisk: Double?,
    val promptRiskReason: String?,
    val responseTimeMs: Long?,
    val suggestions: List<String>,
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
    val recentMessages: List<MessageSnapshot>,
    /** Used to detect that someone else appended in the meantime. */
    val maxSequence: Long,
)
