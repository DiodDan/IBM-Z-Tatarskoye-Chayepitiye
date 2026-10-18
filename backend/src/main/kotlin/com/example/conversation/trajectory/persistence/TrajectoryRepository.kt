package com.example.conversation.trajectory.persistence

import com.example.conversation.trajectory.model.Trajectory
import com.example.conversation.trajectory.model.TrajectoryStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface TrajectoryRepository : JpaRepository<Trajectory, UUID> {
    fun findByConversationIdAndModelFingerprintAndStatusAndExpiresAtAfterOrderByLastPositionAscIdAsc(
        conversationId: UUID, modelFingerprint: String, status: TrajectoryStatus, expiresAt: Instant, pageable: Pageable,
    ): List<Trajectory>

    fun countByConversationIdAndModelFingerprintAndStatusAndExpiresAtAfter(
        conversationId: UUID, modelFingerprint: String, status: TrajectoryStatus, expiresAt: Instant,
    ): Long
}
