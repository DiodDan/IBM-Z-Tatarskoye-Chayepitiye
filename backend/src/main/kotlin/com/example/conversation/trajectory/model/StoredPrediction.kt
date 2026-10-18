package com.example.conversation.trajectory.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/** Persistence representation; model inputs/outputs use the validated value types instead. */
@Embeddable
class StoredPrediction private constructor(
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "embedding", nullable = false, columnDefinition = "real array")
    private val vector: FloatArray,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "risk_logits", nullable = false, columnDefinition = "real array")
    private val logits: FloatArray,

    @Column(nullable = false)
    val overallScore: Float,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private val categoryScores: Map<String, Float>,

    @Column(nullable = false, length = 120)
    val calibrationId: String,

    @Column(nullable = false)
    val calibrationApplied: Boolean,
) {
    fun embedding() = Embedding(vector)
    fun riskLogits() = RiskLogits(logits)
    fun scores() = RiskScores(overallScore, categoryScores.toMap(), calibrationId, calibrationApplied)
    fun copy() = of(embedding(), riskLogits(), scores())

    companion object {
        fun of(embedding: Embedding, logits: RiskLogits, scores: RiskScores) = StoredPrediction(
            embedding.toFloatArray(), logits.toFloatArray(), scores.overall, scores.categories.toMap(),
            scores.calibrationId, scores.calibrationApplied)
    }
}
