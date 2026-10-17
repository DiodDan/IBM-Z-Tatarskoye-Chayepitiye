package com.example.conversation.conversation.persistence

import com.example.conversation.conversation.model.MessageRole
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A message references its conversation by id only (no JPA collection on the conversation),
 * so loading a conversation never drags its whole history along. Deleting a conversation
 * removes its messages through the database's ON DELETE CASCADE.
 */
@Entity
@Table(name = "message")
class MessageEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID,

    @Column(name = "conversation_id", nullable = false)
    val conversationId: UUID,

    /** 1, 2, 3, ... within the conversation. A user message is always followed by its reply. */
    @Column(name = "sequence", nullable = false)
    val sequence: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    val role: MessageRole,

    @Column(name = "content", nullable = false, columnDefinition = "text")
    val content: String,

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    val createdAt: Instant,
)
