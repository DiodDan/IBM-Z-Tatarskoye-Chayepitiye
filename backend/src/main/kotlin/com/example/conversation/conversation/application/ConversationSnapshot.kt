package com.example.conversation.conversation.application

import com.example.conversation.conversation.model.MessageRole
import java.time.Instant
import java.util.UUID

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
