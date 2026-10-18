package com.example.conversation.trajectory.application

import com.example.conversation.trajectory.model.*
import java.util.UUID

enum class AnalysisStatus { COMPLETED, PENDING, FAILED, DISABLED }

/** Current trajectory state is explicitly distinguished from the immutable atomic assessment. */
data class TrajectoryAssessment(
    val id: UUID,
    val responseCount: Int,
    val lastPosition: Long,
    val scores: RiskScores?,
    val associationScore: Float?,
)

data class WindowPeakAssessment(val scores: RiskScores, val peakTokenRanges: Map<String, TokenRange>)

data class ResponseAnalysis(
    val status: AnalysisStatus,
    val modelFingerprint: String? = null,
    val atomic: RiskScores? = null,
    val trajectory: TrajectoryAssessment? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val coverage: ResponseCoverage? = null,
    val windowPeak: WindowPeakAssessment? = null,
    val failedResponseId: UUID? = null,
) {
    companion object {
        fun completed(result: TrackedResponse) = ResponseAnalysis(AnalysisStatus.COMPLETED, result.modelFingerprint,
            result.atomicScores, TrajectoryAssessment(result.trajectoryId, result.responseCount, result.trajectoryLastPosition,
                result.trajectoryScores, result.associationScore), coverage = result.diagnostics?.coverage,
            windowPeak = result.diagnostics?.let { d ->
                // Document calibrations do not apply to maxima of window scores.
                val raw = d.peakLogits.map { AffineCalibration().apply(it) }
                WindowPeakAssessment(RiskScores(raw.first(), ModelDescriptor.CATEGORIES.zip(raw.drop(1)).toMap(),
                    "uncalibrated-window-sigmoid", false), d.peakRanges())
            })
        fun pending(fingerprint: String?) = ResponseAnalysis(AnalysisStatus.PENDING, fingerprint,
            errorCode = "NOT_ANALYSED", message = "This response has not been analysed yet.")
        fun disabled() = ResponseAnalysis(AnalysisStatus.DISABLED, errorCode = "ANALYSIS_DISABLED",
            message = "Response analysis is explicitly disabled in configuration.")
    }
}

interface ChatResponseAnalyzer {
    fun analyse(owner: UUID, assistantMessageId: UUID): ResponseAnalysis
}

/** The response remains saved if the synchronous backfill budget is exhausted. */
class AnalysisBacklog : RuntimeException("Earlier assistant responses still need analysis.")
