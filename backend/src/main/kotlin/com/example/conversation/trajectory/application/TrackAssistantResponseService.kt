package com.example.conversation.trajectory.application

import com.example.conversation.config.TrajectoryProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Identifies the actual failing response during ordered backfill, without retaining its text. */
class ResponseTrackingFailure(val responseId: UUID, cause: Exception) : RuntimeException("Response analysis did not complete", cause)

/** Explicit analysis operation. No risk-based blocking is implied by updating a trajectory. */
@Service
@ConditionalOnProperty(prefix = "app.trajectory", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class TrackAssistantResponseService(
    private val reader: TrajectoryReader,
    private val writer: TrajectoryWriter,
    private val evaluator: TrajectoryEvaluator,
    private val model: TrajectoryModel,
    private val properties: TrajectoryProperties,
    private val clock: Clock,
) {
    fun track(owner: UUID, assistantMessageId: UUID): TrackedResponse {
        val context = reader.prepare(owner, assistantMessageId, model.descriptor.fingerprint, Instant.now(clock), properties.maxActive)
        return context.existing ?: run {
            // The chat scenario owns the conversation guard. Standalone retries are protected by the writer's DB checks.
            // No database transaction spans encode/compose.
            val plan = evaluator.evaluate(context.response, context.text, context.candidates)
            writer.apply(owner, plan, Instant.now(clock), properties.ttl, properties.maxActive, model.descriptor.maxSegment)
        }
    }

    fun trackThrough(owner: UUID, assistantMessageId: UUID): TrackedResponse {
        val target = reader.reference(owner, assistantMessageId)
        repeat(properties.maxResponsesPerRequest) {
            val next = reader.nextPending(owner, target.conversationId, model.descriptor.fingerprint)
            if (next == null || next.position > target.position) return trackPending(owner, assistantMessageId)
            val result = trackPending(owner, next.id)
            if (next.id == assistantMessageId) return result
        }
        throw AnalysisBacklog()
    }

    private fun trackPending(owner: UUID, responseId: UUID): TrackedResponse = try {
        track(owner, responseId)
    } catch (e: Exception) {
        throw ResponseTrackingFailure(responseId, e)
    }
}
