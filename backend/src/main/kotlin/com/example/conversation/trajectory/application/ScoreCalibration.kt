package com.example.conversation.trajectory.application

import com.example.conversation.trajectory.model.*
import kotlin.math.exp

data class AffineCalibration(val slope: Double = 1.0, val intercept: Double = 0.0) {
    init { require(slope.isFinite() && slope > 0 && intercept.isFinite()) }
    fun apply(logit: Float): Float {
        require(logit.isFinite())
        val x = slope * logit + intercept
        return (if (x >= 0) 1.0 / (1.0 + exp(-x)) else exp(x) / (1.0 + exp(x))).toFloat()
    }
}

/** File supplied calibrations are bound to the exact checkpoint, not just the backbone name. */
class ScoreCalibration(
    val id: String,
    val modelFingerprint: String,
    val applied: Boolean,
    private val risk: List<AffineCalibration>,
    private val association: AffineCalibration,
) {
    init { require(id.isNotBlank() && id.length <= 120 && modelFingerprint.isNotBlank() && risk.size == 10) }

    fun risk(logits: RiskLogits): RiskScores {
        val scores = logits.toFloatArray().mapIndexed { i, value -> risk[i].apply(value) }
        return RiskScores(scores.first(), ModelDescriptor.CATEGORIES.zip(scores.drop(1)).toMap(), id, applied)
    }

    fun association(logit: Float): Float = association.apply(logit)

    companion object {
        fun uncalibrated(model: ModelDescriptor) = ScoreCalibration("uncalibrated-sigmoid", model.fingerprint,
            false, List(10) { AffineCalibration() }, AffineCalibration())
    }
}
