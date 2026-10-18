package com.example.conversation.trajectory.application

import com.example.conversation.trajectory.model.*
import java.util.UUID

data class TrajectorySnapshot(
    val id: UUID,
    val version: Long,
    val modelFingerprint: String,
    val embedding: Embedding,
    val boundary: SegmentBoundary,
)

data class SelectedContinuation(
    val previous: TrajectorySnapshot,
    val prediction: CompositionPrediction,
    val scores: RiskScores,
    val associationScore: Float,
)

data class TrackingPlan(
    val response: ResponseReference,
    val modelFingerprint: String,
    val encoded: EncodedResponse,
    val atomicScores: RiskScores,
    val continuation: SelectedContinuation?,
    val linkThreshold: Float,
)

/** Calculates a proposed update without database writes or HTTP decisions. */
class TrajectoryEvaluator(
    private val model: TrajectoryModel,
    private val calibration: ScoreCalibration,
    private val linkThreshold: Float,
) {
    init {
        require(linkThreshold.isFinite() && linkThreshold in 0f..1f)
        require(calibration.modelFingerprint == model.descriptor.fingerprint)
    }

    fun evaluate(response: ResponseReference, assistantText: String, candidates: List<TrajectorySnapshot>): TrackingPlan {
        require(candidates.map { it.id }.distinct().size == candidates.size)
        // Validate provenance and chronology before spending time encoding.
        candidates.forEach {
            require(it.modelFingerprint == model.descriptor.fingerprint) { "Incompatible stored vector" }
            CompositionMetadata.continuation(it.boundary, response)
        }
        val encoded = model.encode(assistantText)
        val matches = candidates.filter { it.boundary.memberCount < model.descriptor.maxSegment }.mapNotNull { old ->
            val prediction = model.compose(old.embedding, encoded.embedding, CompositionMetadata.continuation(old.boundary, response))
            val association = calibration.association(prediction.associationLogit)
            if (association <= linkThreshold) null else
                SelectedContinuation(old, prediction, calibration.risk(prediction.riskLogits), association)
        }
        val selected = matches.sortedWith(compareByDescending<SelectedContinuation> { it.associationScore }
            .thenBy { it.previous.boundary.firstPosition }.thenBy { it.previous.id.toString() }).firstOrNull()
        // No risk-growth condition: safe early steps must also remain available for future continuations.
        return TrackingPlan(response, model.descriptor.fingerprint, encoded, calibration.risk(encoded.riskLogits), selected, linkThreshold)
    }
}
