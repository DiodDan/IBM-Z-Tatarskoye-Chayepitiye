package com.example.conversation.trajectory.application

import com.example.conversation.conversation.model.MessageRole
import com.example.conversation.conversation.persistence.repository.ConversationRepository
import com.example.conversation.conversation.persistence.repository.MessageRepository
import com.example.conversation.error.*
import com.example.conversation.trajectory.model.*
import com.example.conversation.trajectory.persistence.*
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class TrajectoryWriter(
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val embeddings: ResponseEmbeddingRepository,
    private val trajectories: TrajectoryRepository,
    private val members: TrajectoryMemberRepository,
    private val cursors: AnalysisCursorRepository,
    private val entities: EntityManager,
) {
    @Transactional
    fun apply(owner: UUID, plan: TrackingPlan, now: Instant, ttl: Duration, maxActive: Int, maxSegment: Int): TrackedResponse {
        val conversation = conversations.lockOwned(plan.response.conversationId, owner) ?: throw ConversationNotFound()
        embeddings.findByResponseIdAndModelFingerprint(plan.response.id, plan.modelFingerprint)?.let { existing ->
            val member = checkNotNull(members.findByResponseEmbeddingId(existing.id))
            return member.toTrackedResponse()
        }
        val response = messages.findByIdAndConversationUserExternalId(plan.response.id, owner) ?: throw ConversationNotFound()
        require(response.role == MessageRole.ASSISTANT && response.conversation.id == conversation.id)
        require(response.assistantPosition() == plan.response.position && response.createdAt.equals(plan.response.occurredAt))
        val cursor = cursors.findByConversationIdAndModelFingerprint(conversation.id, plan.modelFingerprint)
            ?: AnalysisCursor(UUID.randomUUID(), conversation, plan.modelFingerprint).also { entities.persist(it) }
        if (cursor.nextMessageSequence() != response.sequence) throw AnalysisConflict("Assistant response order changed.")
        val encoded = ResponseEmbedding(UUID.randomUUID(), response, plan.modelFingerprint,
            StoredPrediction.of(plan.encoded.embedding, plan.encoded.riskLogits, plan.atomicScores), now, plan.encoded.diagnostics)
        val selected = plan.continuation
        val trajectory = if (selected == null) {
            if (trajectories.countByConversationIdAndModelFingerprintAndStatusAndExpiresAtAfter(
                    conversation.id, plan.modelFingerprint, TrajectoryStatus.ACTIVE, now) >= maxActive)
                throw AnalysisRejected("Active trajectory limit reached; no history was silently discarded.")
            Trajectory.start(encoded, now, ttl).also { entities.persist(it) }
        } else {
            val current = trajectories.findById(selected.previous.id).orElseThrow { AnalysisConflict("Trajectory no longer exists.") }
            if (current.version != selected.previous.version) throw AnalysisConflict("Trajectory changed during inference.")
            if (current.status != TrajectoryStatus.ACTIVE || !current.expiresAt.isAfter(now))
                throw AnalysisConflict("Trajectory expired or was closed during inference; prepare the analysis again.")
            current.append(encoded, StoredPrediction.of(selected.prediction.embedding, selected.prediction.riskLogits, selected.scores), now, ttl, maxSegment)
            current
        }
        entities.persist(encoded)
        val member = TrajectoryMember(UUID.randomUUID(), trajectory, encoded, trajectory.responseCount,
            selected?.prediction?.associationLogit, selected?.associationScore, if (selected == null) null else plan.linkThreshold)
        entities.persist(member)
        cursor.advance(response)
        return member.toTrackedResponse()
    }
}
