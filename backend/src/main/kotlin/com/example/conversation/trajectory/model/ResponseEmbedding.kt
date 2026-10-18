package com.example.conversation.trajectory.model

import com.example.conversation.conversation.persistence.model.MessageRole
import com.example.conversation.conversation.persistence.model.Message
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "response_embedding", uniqueConstraints = [UniqueConstraint(columnNames = ["response_id", "model_fingerprint"])])
class ResponseEmbedding(
    @Id val id: UUID,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "response_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    val response: Message,
    @Column(nullable = false, length = 64)
    val modelFingerprint: String,
    @Embedded val prediction: StoredPrediction,
    @Column(nullable = false, columnDefinition = "timestamp with time zone")
    val createdAt: Instant,
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "analysis_details", columnDefinition = "jsonb")
    val diagnostics: ResponseDiagnostics? = null,
) {
    init {
        require(response.role == MessageRole.ASSISTANT) { "Only assistant outputs may be encoded" }
        require(modelFingerprint.length == 64)
    }
}
