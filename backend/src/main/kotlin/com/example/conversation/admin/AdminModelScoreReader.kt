package com.example.conversation.admin

import com.example.conversation.conversation.api.dto.ResponseAnalysisResponse
import com.example.conversation.conversation.api.mapper.ConversationApiMapper
import com.example.conversation.trajectory.application.*
import com.example.conversation.trajectory.persistence.TrajectoryMemberRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import com.example.conversation.error.ApiException
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import com.example.conversation.trajectory.model.RiskScores
import java.time.Instant
import java.util.UUID

data class AdminMembership(
    val ordinal: Int,
    val associationLogit: Float?,
    val linkThreshold: Float?,
    val status: String,
    val updatedAt: Instant,
    val expiresAt: Instant,
)
data class AdminModelAssessment(val analysis: ResponseAnalysisResponse, val membership: AdminMembership?)

data class AdminTrajectoryMember(
    val messageId: UUID, val responseNumber: Long?, val conversationId: UUID,
    val content: String, val ordinal: Int, val atomic: RiskScores,
    val associationScore: Float?, val associationLogit: Float?, val linkThreshold: Float?,
)
data class AdminTrajectoryDetail(
    val id: UUID, val status: String, val modelFingerprint: String,
    val responseCount: Int, val scores: RiskScores, val updatedAt: Instant,
    val members: List<AdminTrajectoryMember>,
)

/** Read stored scores in one query, never rerun inference while viewing the admin page. */
@Component
class AdminModelScoreReader(
    private val members: TrajectoryMemberRepository,
    private val models: ObjectProvider<TrajectoryModel>,
    private val mapper: ConversationApiMapper,
) {
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    fun trajectory(id: UUID): AdminTrajectoryDetail {
        val rows = members.findByTrajectoryIdOrderByOrdinalAsc(id)
        val t = rows.firstOrNull()?.trajectory
            ?: throw ApiException(HttpStatus.NOT_FOUND, "TRAJECTORY_NOT_FOUND", "Trajectory does not exist.")
        return AdminTrajectoryDetail(t.id, t.status.name.lowercase(), t.modelFingerprint, t.responseCount,
            t.prediction.scores(), t.updatedAt, rows.map {
                val response = it.responseEmbedding.response
                AdminTrajectoryMember(response.id, response.responseNumber, response.conversation.id, response.content,
                    it.ordinal, it.responseEmbedding.prediction.scores(), it.associationScore, it.associationLogit, it.linkThreshold)
            })
    }

    fun fingerprint(): String? = models.ifAvailable?.descriptor?.fingerprint

    fun read(ids: Collection<UUID>): Map<UUID, AdminModelAssessment> {
        if (ids.isEmpty()) return emptyMap()
        val fingerprint = fingerprint()
        val missing = mapper.toAnalysis(if (fingerprint == null) ResponseAnalysis.disabled() else ResponseAnalysis.pending(fingerprint))
        val result = ids.associateWith { AdminModelAssessment(missing, null) }.toMutableMap()
        if (fingerprint == null) return result
        members.findByResponseEmbeddingResponseIdInAndTrajectoryModelFingerprint(ids, fingerprint).forEach { member ->
            val t = member.trajectory
            result[member.responseEmbedding.response.id] = AdminModelAssessment(
                mapper.toAnalysis(ResponseAnalysis.completed(member.toTrackedResponse())),
                AdminMembership(member.ordinal, member.associationLogit, member.linkThreshold,
                    t.status.name.lowercase(), t.updatedAt, t.expiresAt))
        }
        return result
    }
}
