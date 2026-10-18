package com.example.conversation.trajectory.application

import com.example.conversation.error.AnalysisConflict
import com.example.conversation.error.AnalysisRejected
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.UUID

/** Runs after the exchange commits; an analysis failure must not pretend that the reply was not saved. */
@Service
@ConditionalOnProperty(prefix = "app.trajectory", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class InlineResponseAnalyzer(
    private val tracking: TrackAssistantResponseService,
    private val model: TrajectoryModel,
) : ChatResponseAnalyzer {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun analyse(owner: UUID, assistantMessageId: UUID): ResponseAnalysis = try {
        ResponseAnalysis.completed(tracking.trackThrough(owner, assistantMessageId))
    } catch (e: AnalysisBacklog) {
        ResponseAnalysis(AnalysisStatus.PENDING, model.descriptor.fingerprint, errorCode = "ANALYSIS_BACKLOG", message = e.message)
    } catch (e: Exception) {
        val failedId = (e as? ResponseTrackingFailure)?.responseId ?: assistantMessageId
        val failure = if (e is ResponseTrackingFailure) checkNotNull(e.cause) else e
        val (code, message) = when (failure) {
            is AnalysisRejected -> "ANALYSIS_REJECTED" to failure.message
            is AnalysisConflict -> "ANALYSIS_CONFLICT" to failure.message
            else -> "ANALYSIS_FAILED" to "The reply was saved, but its risk assessment could not be calculated."
        }
        // Expected failures have controlled diagnostic messages; arbitrary exception text may contain provider data.
        log.warn("Response analysis failed: requested={} failedResponse={} type={} code={} reason={}",
            assistantMessageId, failedId, failure.javaClass.simpleName, code, message)
        ResponseAnalysis(AnalysisStatus.FAILED, model.descriptor.fingerprint, errorCode = code, message = message, failedResponseId = failedId)
    }
}

@Service
@ConditionalOnProperty(prefix = "app.trajectory", name = ["enabled"], havingValue = "false")
class DisabledResponseAnalyzer : ChatResponseAnalyzer {
    override fun analyse(owner: UUID, assistantMessageId: UUID) = ResponseAnalysis.disabled()
}
