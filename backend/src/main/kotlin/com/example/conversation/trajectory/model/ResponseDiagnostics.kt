package com.example.conversation.trajectory.model

/** Content-token offsets, before special tokens; end is exclusive. */
data class TokenRange(val start: Int, val end: Int) {
    init { require(start >= 0 && end > start) }
}

data class ResponseCoverage(
    val totalTokens: Int,
    val processedTokens: Int,
    val windowCount: Int,
    val status: String = "complete",
) {
    init { require(totalTokens > 0 && processedTokens == totalTokens && windowCount > 0 && status == "complete") }
}

/** Immutable diagnostics of one full response; no original text or per-window embeddings. */
data class ResponseDiagnostics(
    val coverage: ResponseCoverage,
    val windowRanges: List<TokenRange>,
    val peakLogits: List<Float>,
    val peakWindowIndices: List<Int>,
) {
    init {
        require(windowRanges.size == coverage.windowCount && windowRanges.first().start == 0 &&
            windowRanges.last().end == coverage.totalTokens)
        windowRanges.zipWithNext().forEach { (a, b) -> require(b.start > a.start && b.start <= a.end && b.end > a.end) }
        require(peakLogits.size == 10 && peakLogits.all { it.isFinite() })
        require(peakWindowIndices.size == 10 && peakWindowIndices.all { it in windowRanges.indices })
    }

    fun peakRanges() = ModelDescriptor.HEADS.zip(peakWindowIndices.map { windowRanges[it] }).toMap()
}
