package com.example.conversation.conversation.persistence.model

import com.example.conversation.conversation.model.MessageRole
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "message")
class Message(
    @Id
    @Column(nullable = false)
    val id: UUID,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    val conversation: Conversation,

    @Column(nullable = false)
    val sequence: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val role: MessageRole,

    @Column(nullable = false, columnDefinition = "text")
    val content: String,

    @Column(nullable = false, columnDefinition = "timestamptz")
    val createdAt: Instant,

    /** Risk score of a user prompt (0..1). Null when not classified; always null for assistant rows. */
    @Column(name = "prompt_risk")
    val promptRisk: Double? = null,

    /** The classifier's explanation for promptRisk. Null when not classified; always null for assistant rows. */
    @Column(name = "prompt_risk_reason", columnDefinition = "text")
    val promptRiskReason: String? = null,

    /** How long the model took to produce this reply, in ms. Assistant rows only. */
    @Column(name = "response_time_ms")
    val responseTimeMs: Long? = null,

    /** Follow-up prompts proposed by the model; empty for user rows. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    val suggestions: List<String> = emptyList(),
)
