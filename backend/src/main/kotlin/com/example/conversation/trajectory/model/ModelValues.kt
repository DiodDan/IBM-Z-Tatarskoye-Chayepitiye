package com.example.conversation.trajectory.model

/** One coordinate system: changing any trained component requires a new fingerprint. */
data class ModelDescriptor(
    val fingerprint: String,
    val dimension: Int,
    val maxSegment: Int,
    val sequenceLength: Int,
    val heads: List<String>,
) {
    init {
        require(fingerprint.matches(Regex("[0-9a-f]{64}")) && dimension == 256 && maxSegment >= 2 && sequenceLength > 0)
        require(heads == HEADS) { "Unsupported risk head order" }
    }

    companion object {
        val CATEGORIES = listOf("violence_weapons", "bio_chemical", "malicious_cyber", "fraud", "illicit_evasion",
            "hate_threats_harassment", "privacy_stalking", "sexual_exploitation", "self_harm_facilitation")
        val HEADS = listOf("overall") + CATEGORIES
    }
}

/** Owns its array so neither inference buffers nor callers can mutate stored state. */
class Embedding(values: FloatArray) {
    private val coordinates = values.copyOf()
    init { require(coordinates.size == 256 && coordinates.all { it.isFinite() }) { "Expected 256 finite coordinates" } }
    fun toFloatArray(): FloatArray = coordinates.copyOf()
    override fun toString(): String = "Embedding(dimension=256)"
}

class RiskLogits(values: FloatArray) {
    private val logits = values.copyOf()
    init { require(logits.size == 10 && logits.all { it.isFinite() }) { "Expected ten finite risk logits" } }
    fun toFloatArray(): FloatArray = logits.copyOf()
}

data class RiskScores(
    val overall: Float,
    val categories: Map<String, Float>,
    val calibrationId: String,
    val calibrationApplied: Boolean,
) {
    init {
        require(overall.isFinite() && overall in 0f..1f)
        require(categories.keys == ModelDescriptor.CATEGORIES.toSet() && categories.values.all { it.isFinite() && it in 0f..1f })
        require(calibrationId.isNotBlank())
    }
}

data class EncodedResponse(val embedding: Embedding, val riskLogits: RiskLogits, val diagnostics: ResponseDiagnostics? = null)
data class CompositionPrediction(val embedding: Embedding, val riskLogits: RiskLogits, val associationLogit: Float) {
    init { require(associationLogit.isFinite()) }
}
