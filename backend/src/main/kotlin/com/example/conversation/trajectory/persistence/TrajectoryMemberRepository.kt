package com.example.conversation.trajectory.persistence

import com.example.conversation.trajectory.model.TrajectoryMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.EntityGraph
import java.util.UUID

interface TrajectoryMemberRepository : JpaRepository<TrajectoryMember, UUID> {
    @EntityGraph(attributePaths = ["responseEmbedding", "responseEmbedding.response", "trajectory"])
    fun findByResponseEmbeddingId(responseEmbeddingId: UUID): TrajectoryMember?
    @EntityGraph(attributePaths = ["responseEmbedding", "responseEmbedding.response", "trajectory"])
    fun findByTrajectoryConversationIdAndTrajectoryModelFingerprint(conversationId: UUID, modelFingerprint: String): List<TrajectoryMember>
    @EntityGraph(attributePaths = ["responseEmbedding", "responseEmbedding.response", "trajectory"])
    fun findByResponseEmbeddingResponseIdInAndTrajectoryModelFingerprint(responseIds: Collection<UUID>, modelFingerprint: String): List<TrajectoryMember>
    @EntityGraph(attributePaths = ["responseEmbedding", "responseEmbedding.response", "responseEmbedding.response.conversation", "trajectory"])
    fun findByTrajectoryIdOrderByOrdinalAsc(trajectoryId: UUID): List<TrajectoryMember>
}
