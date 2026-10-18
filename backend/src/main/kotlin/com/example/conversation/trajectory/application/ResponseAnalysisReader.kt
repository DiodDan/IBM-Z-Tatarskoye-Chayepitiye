package com.example.conversation.trajectory.application

import com.example.conversation.trajectory.persistence.TrajectoryMemberRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.util.UUID

/** Batched history projection; its caller owns the read transaction and checks conversation ownership. */
@Service
class ResponseAnalysisReader(
    private val members: TrajectoryMemberRepository,
    private val models: ObjectProvider<TrajectoryModel>,
) {
    fun read(conversationId: UUID): Map<UUID, ResponseAnalysis> {
        val fingerprint = fingerprint() ?: return emptyMap()
        return members.findByTrajectoryConversationIdAndTrajectoryModelFingerprint(conversationId, fingerprint)
            .associate { it.responseEmbedding.response.id to ResponseAnalysis.completed(it.toTrackedResponse()) }
    }

    fun missing(): ResponseAnalysis = fingerprint()?.let(ResponseAnalysis::pending) ?: ResponseAnalysis.disabled()
    private fun fingerprint(): String? = models.ifAvailable?.descriptor?.fingerprint
}
