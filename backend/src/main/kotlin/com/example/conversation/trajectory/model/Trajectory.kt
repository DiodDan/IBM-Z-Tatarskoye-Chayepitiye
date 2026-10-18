package com.example.conversation.trajectory.model

import com.example.conversation.conversation.persistence.model.Conversation
import jakarta.persistence.*
import java.time.Duration
import java.time.Instant
import java.util.UUID

enum class TrajectoryStatus { ACTIVE, CLOSED }

/** A mutable current state. Original response embeddings and ordered membership remain immutable. */
@Entity
@Table(name = "trajectory")
class Trajectory private constructor(
    @Id val id: UUID,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    val conversation: Conversation,
    @Column(nullable = false, length = 64)
    val modelFingerprint: String,
    prediction: StoredPrediction,
    firstPosition: Long,
    lastOccurredAt: Instant?,
    now: Instant,
    ttl: Duration,
) {
    @Embedded
    var prediction: StoredPrediction = prediction
        protected set
    @Column(nullable = false)
    var responseCount: Int = 1
        protected set
    @Column(nullable = false)
    var firstPosition: Long = firstPosition
        protected set
    @Column(nullable = false)
    var lastPosition: Long = firstPosition
        protected set
    @Column(columnDefinition = "timestamp with time zone")
    val firstOccurredAt: Instant? = lastOccurredAt
    @Column(columnDefinition = "timestamp with time zone")
    var lastOccurredAt: Instant? = lastOccurredAt
        protected set
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: TrajectoryStatus = TrajectoryStatus.ACTIVE
        protected set
    @Column(nullable = false, columnDefinition = "timestamp with time zone")
    val createdAt: Instant = now
    @Column(nullable = false, columnDefinition = "timestamp with time zone")
    var updatedAt: Instant = now
        protected set
    @Column(nullable = false, columnDefinition = "timestamp with time zone")
    var expiresAt: Instant = now.plus(ttl)
        protected set
    @Version
    var version: Long? = null
        protected set

    fun boundary() = SegmentBoundary(conversation.id, firstPosition, lastPosition, responseCount, lastOccurredAt)

    fun append(response: ResponseEmbedding, next: StoredPrediction, now: Instant, ttl: Duration, maxSegment: Int) {
        require(status == TrajectoryStatus.ACTIVE && expiresAt.isAfter(now)) { "Trajectory is not active" }
        require(response.modelFingerprint == modelFingerprint) { "Model versions cannot be mixed" }
        require(!ttl.isNegative && !ttl.isZero && responseCount < maxSegment)
        require(!now.isBefore(updatedAt))
        CompositionMetadata.continuation(boundary(), ResponseReference(response.response.id,
            response.response.conversation.id, response.response.assistantPosition(), response.response.createdAt))
        prediction = next.copy()
        responseCount = Math.addExact(responseCount, 1)
        lastPosition = response.response.assistantPosition()
        lastOccurredAt = response.response.createdAt
        updatedAt = now
        expiresAt = now.plus(ttl)
    }

    fun close() { status = TrajectoryStatus.CLOSED }

    companion object {
        fun start(response: ResponseEmbedding, now: Instant, ttl: Duration): Trajectory {
            require(!ttl.isNegative && !ttl.isZero)
            return Trajectory(UUID.randomUUID(), response.response.conversation, response.modelFingerprint,
                response.prediction.copy(), response.response.assistantPosition(), response.response.createdAt, now, ttl)
        }
    }
}
