package com.example.conversation.trajectory.application

import com.example.conversation.conversation.model.MessageRole
import com.example.conversation.conversation.persistence.repository.MessageRepository
import com.example.conversation.conversation.persistence.repository.ConversationRepository
import com.example.conversation.error.*
import com.example.conversation.trajectory.model.*
import com.example.conversation.trajectory.persistence.*
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class TrackedResponse(
    val responseId: UUID,
    val embeddingId: UUID,
    val trajectoryId: UUID,
    val atomicScores: RiskScores,
    val modelFingerprint: String,
    val trajectoryScores: RiskScores?,
    val associationScore: Float?,
    val responseCount: Int,
    val trajectoryLastPosition: Long,
    val diagnostics: ResponseDiagnostics? = null,
)

internal fun TrajectoryMember.toTrackedResponse() = TrackedResponse(responseEmbedding.response.id,
    responseEmbedding.id, trajectory.id, responseEmbedding.prediction.scores(), responseEmbedding.modelFingerprint,
    if (trajectory.responseCount > 1) trajectory.prediction.scores() else null, associationScore,
    trajectory.responseCount, trajectory.lastPosition, responseEmbedding.diagnostics)
data class TrackingContext(val response: ResponseReference, val text: String, val existing: TrackedResponse?, val candidates: List<TrajectorySnapshot>)

@Service
class TrajectoryReader(
    private val messages: MessageRepository,
    private val embeddings: ResponseEmbeddingRepository,
    private val members: TrajectoryMemberRepository,
    private val trajectories: TrajectoryRepository,
    private val cursors: AnalysisCursorRepository,
    private val conversations: ConversationRepository,
) {
    @Transactional(readOnly = true)
    fun conversationId(owner: UUID, responseId: UUID): UUID = assistant(owner, responseId).conversation.id

    @Transactional(readOnly = true)
    fun reference(owner: UUID, responseId: UUID): ResponseReference = assistant(owner, responseId).let {
        ResponseReference(it.id, it.conversation.id, it.assistantPosition(), it.createdAt)
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun nextPending(owner: UUID, conversationId: UUID, fingerprint: String): ResponseReference? {
        if (!conversations.existsByIdAndUserExternalId(conversationId, owner)) throw ConversationNotFound()
        val sequence = cursors.findByConversationIdAndModelFingerprint(conversationId, fingerprint)?.nextMessageSequence() ?: 2L
        return messages.findByConversationIdAndSequence(conversationId, sequence)?.let {
            if (it.role != MessageRole.ASSISTANT) throw AnalysisConflict("Stored conversation violates assistant response order.")
            ResponseReference(it.id, conversationId, it.assistantPosition(), it.createdAt)
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun prepare(owner: UUID, responseId: UUID, fingerprint: String, now: Instant, maxActive: Int): TrackingContext {
        val message = assistant(owner, responseId)
        val reference = ResponseReference(message.id, message.conversation.id, message.assistantPosition(), message.createdAt)
        val existing = embeddings.findByResponseIdAndModelFingerprint(responseId, fingerprint)
        if (existing != null) {
            val member = checkNotNull(members.findByResponseEmbeddingId(existing.id))
            return TrackingContext(reference, "", member.toTrackedResponse(), emptyList())
        }
        val nextSequence = cursors.findByConversationIdAndModelFingerprint(message.conversation.id, fingerprint)?.nextMessageSequence() ?: 2L
        if (message.sequence != nextSequence) throw AnalysisConflict("Analyse assistant responses in conversation order.")
        val active = trajectories.findByConversationIdAndModelFingerprintAndStatusAndExpiresAtAfterOrderByLastPositionAscIdAsc(
            message.conversation.id, fingerprint, TrajectoryStatus.ACTIVE, now, PageRequest.of(0, maxActive + 1))
        if (active.size > maxActive) throw AnalysisRejected("Active trajectory limit exceeded; analysis coverage would be incomplete.")
        return TrackingContext(reference, message.content, null, active.map {
            TrajectorySnapshot(it.id, checkNotNull(it.version), it.modelFingerprint, it.prediction.embedding(), it.boundary())
        })
    }

    private fun assistant(owner: UUID, id: UUID) =
        (messages.findByIdAndConversationUserExternalId(id, owner) ?: throw ConversationNotFound()).also {
            if (it.role != MessageRole.ASSISTANT) throw AnalysisRejected("Only assistant responses may be analysed.")
        }
}
