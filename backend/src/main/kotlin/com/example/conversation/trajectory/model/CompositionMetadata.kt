package com.example.conversation.trajectory.model

import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.ln1p

data class ResponseReference(val id: UUID, val conversationId: UUID, val position: Long, val occurredAt: Instant?) {
    init { require(position > 0) }
}

data class SegmentBoundary(
    val conversationId: UUID,
    val firstPosition: Long,
    val lastPosition: Long,
    val memberCount: Int,
    val lastOccurredAt: Instant?,
) {
    init { require(firstPosition > 0 && lastPosition >= firstPosition && memberCount > 0) }
}

class CompositionMetadata private constructor(private val values: FloatArray) {
    fun toFloatArray(): FloatArray = values.copyOf()

    companion object {
        val NAMES = listOf("log1p(time_gap_seconds)", "time_gap_missing", "log1p(left_members)",
            "log1p(right_members)", "conversation_changed", "log1p(skipped_positions)")

        fun continuation(earlier: SegmentBoundary, later: ResponseReference): CompositionMetadata {
            require(earlier.conversationId == later.conversationId) { "Cross-conversation composition is disabled" }
            require(earlier.lastPosition < later.position) { "Composition must be chronological and disjoint" }
            val missing = earlier.lastOccurredAt == null || later.occurredAt == null
            val seconds = if (missing) 0.0 else {
                val gap = Duration.between(earlier.lastOccurredAt, later.occurredAt)
                require(!gap.isNegative) { "Timestamp contradicts message order" }
                gap.seconds.toDouble() + gap.nano / 1_000_000_000.0
            }
            return CompositionMetadata(floatArrayOf(ln1p(seconds).toFloat(), if (missing) 1f else 0f,
                ln1p(earlier.memberCount.toDouble()).toFloat(), ln1p(1.0).toFloat(), 0f,
                ln1p((later.position - earlier.lastPosition - 1).toDouble()).toFloat()))
        }
    }
}
