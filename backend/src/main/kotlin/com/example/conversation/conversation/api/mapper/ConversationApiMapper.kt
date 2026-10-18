package com.example.conversation.conversation.api.mapper

import com.example.conversation.conversation.api.dto.ConversationHistoryResponse
import com.example.conversation.conversation.api.dto.ConversationListResponse
import com.example.conversation.conversation.api.dto.ConversationPreviewResponse
import com.example.conversation.conversation.api.dto.MessageExchangeResponse
import com.example.conversation.conversation.api.dto.MessageResponse
import com.example.conversation.conversation.application.dto.ConversationPreview
import com.example.conversation.conversation.application.dto.ConversationSnapshot
import com.example.conversation.conversation.application.dto.MessageExchange
import com.example.conversation.conversation.application.dto.MessageSnapshot
import org.springframework.stereotype.Component
import com.example.conversation.conversation.api.dto.ResponseAnalysisResponse
import com.example.conversation.conversation.api.dto.RiskScoresResponse
import com.example.conversation.conversation.api.dto.TrajectoryAssessmentResponse
import com.example.conversation.conversation.api.dto.ResponseCoverageResponse
import com.example.conversation.conversation.api.dto.WindowPeakResponse
import com.example.conversation.conversation.api.dto.TokenRangeResponse
import com.example.conversation.trajectory.application.ResponseAnalysis
import com.example.conversation.trajectory.model.RiskScores

@Component
class ConversationApiMapper {

    fun toList(previews: List<ConversationPreview>) =
        ConversationListResponse(previews.size.toLong(), previews.map { toPreview(it) })

    fun toHistory(snapshot: ConversationSnapshot) =
        ConversationHistoryResponse(toPreview(snapshot.conversation), snapshot.messages.map { toMessage(it) })

    fun toExchange(exchange: MessageExchange) =
        MessageExchangeResponse(
            toPreview(exchange.conversation),
            toMessage(exchange.userMessage),
            toMessage(exchange.assistantMessage),
        )

    fun toPreview(preview: ConversationPreview) =
        ConversationPreviewResponse(preview.id, preview.title, preview.createdAt)

    private fun toMessage(message: MessageSnapshot) =
        MessageResponse(message.id, message.role.apiValue, message.content, message.createdAt, message.analysis?.let(::toAnalysis))

    private fun toAnalysis(value: ResponseAnalysis) = ResponseAnalysisResponse(
        value.status.name.lowercase(), value.modelFingerprint, value.atomic?.let(::toScores),
        value.trajectory?.let { TrajectoryAssessmentResponse(it.id, it.responseCount, it.lastPosition,
            it.scores?.let(::toScores), it.associationScore) }, value.errorCode, value.message,
        value.coverage?.let { ResponseCoverageResponse(it.status, it.totalTokens, it.processedTokens, it.windowCount) },
        value.windowPeak?.let { WindowPeakResponse(toScores(it.scores), it.peakTokenRanges.mapValues { (_, range) -> TokenRangeResponse(range.start, range.end) }) },
        value.failedResponseId,
    )

    private fun toScores(value: RiskScores) = RiskScoresResponse(value.overall, value.categories,
        value.calibrationId, value.calibrationApplied)
}
