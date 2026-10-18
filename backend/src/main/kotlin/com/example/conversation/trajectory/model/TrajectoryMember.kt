package com.example.conversation.trajectory.model

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "trajectory_member", uniqueConstraints = [
    UniqueConstraint(columnNames = ["response_embedding_id"]),
    UniqueConstraint(columnNames = ["trajectory_id", "ordinal"]),
])
class TrajectoryMember(
    @Id val id: UUID,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trajectory_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    val trajectory: Trajectory,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "response_embedding_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    val responseEmbedding: ResponseEmbedding,
    @Column(nullable = false) val ordinal: Int,
    val associationLogit: Float?,
    val associationScore: Float?,
    val linkThreshold: Float?,
) {
    init {
        require(ordinal > 0)
        require((associationLogit == null) == (associationScore == null))
        require((associationScore == null) == (linkThreshold == null))
        associationLogit?.let { require(it.isFinite()) }
        associationScore?.let { require(it.isFinite() && it in 0f..1f) }
        linkThreshold?.let { require(it.isFinite() && it in 0f..1f) }
    }
}
