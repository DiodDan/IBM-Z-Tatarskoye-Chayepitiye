package com.example.conversation.conversation.persistence.model

import com.example.conversation.identity.model.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "conversation")
class Conversation(
    @Id
    @Column(nullable = false)
    val id: UUID,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    val user: User,

    @Column(nullable = false, length = 80)
    var title: String,

    @Column(nullable = false, columnDefinition = "timestamp with time zone")
    val createdAt: Instant,
) {
    @Column(nullable = false)
    var lastMessageSequence: Long = 0
        protected set

    @Version
    var version: Long? = null
        protected set

    /** Positions belong to this conversation; the writer persists the whole exchange atomically. */
    fun appendExchange(
        userText: String,
        assistantText: String,
        at: Instant,
        promptRisk: Double? = null,
        promptRiskReason: String? = null,
        suggestions: List<String> = emptyList(),
        responseTimeMs: Long? = null,
        responseNumber: Long? = null,
    ): MessagePair {
        require(userText.isNotBlank() && assistantText.isNotBlank()) { "An exchange needs two nonblank messages" }
        val first = Math.addExact(lastMessageSequence, 1)
        val last = Math.addExact(first, 1)
        val userMessage = Message(UUID.randomUUID(), this, first, MessageRole.USER, userText, at,
            promptRisk = promptRisk, promptRiskReason = promptRiskReason)
        val assistantMessage = Message(UUID.randomUUID(), this, last, MessageRole.ASSISTANT, assistantText, at,
            suggestions = suggestions.toList(), responseTimeMs = responseTimeMs, responseNumber = responseNumber)
        lastMessageSequence = last
        return MessagePair(userMessage, assistantMessage)
    }

    fun rename(title: String) {
        require(title.isNotBlank() && title.codePointCount(0, title.length) <= 80)
        this.title = title
    }
}

data class MessagePair(val user: Message, val assistant: Message)
