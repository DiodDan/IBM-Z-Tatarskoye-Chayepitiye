package com.example.conversation.conversation.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
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
    @OnDelete(action = OnDeleteAction.CASCADE)
    val conversation: Conversation,

    @Column(nullable = false)
    val sequence: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val role: MessageRole,

    @Column(nullable = false, columnDefinition = "text")
    val content: String,

    @Column(nullable = false, columnDefinition = "timestamp with time zone")
    val createdAt: Instant,

    @Column(name = "prompt_risk")
    val promptRisk: Double? = null,

    @Column(name = "prompt_risk_reason", columnDefinition = "text")
    val promptRiskReason: String? = null,

    @Column(name = "response_time_ms")
    val responseTimeMs: Long? = null,

    @Column(name = "response_number", unique = true)
    val responseNumber: Long? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    val suggestions: List<String> = emptyList(),
) {
    /** The current chat contract stores USER/ASSISTANT pairs at positions 2n-1 / 2n. */
    fun assistantPosition(): Long {
        require(role == MessageRole.ASSISTANT && sequence > 0 && sequence % 2L == 0L) {
            "Assistant position requires a complete ordered exchange"
        }
        return sequence / 2
    }
}
