package com.example.conversation.conversation.api.dto

import java.time.Instant
import java.util.UUID

// Request/response JSON. Field names match backend-requirements.md §4 (camelCase).

data class SendMessageRequest(val content: String)

data class RenameConversationRequest(val title: String)

data class ConversationPreviewResponse(
    val id: UUID,
    val title: String,
    val createdAt: Instant,
    val lastMessage: String? = null,
)

data class ConversationListResponse(
    val conversationCount: Long,
    val conversations: List<ConversationPreviewResponse>,
)

data class MessageResponse(
    val id: UUID,
    /** "user" or "assistant" */
    val role: String,
    val content: String,
    val createdAt: Instant,
    val analysis: ResponseAnalysisResponse? = null,
    val suggestions: List<String> = emptyList(),
)

data class RiskScoresResponse(
    val overall: Float,
    val categories: Map<String, Float>,
    val calibrationId: String,
    val calibrationApplied: Boolean,
)

data class TrajectoryAssessmentResponse(
    val id: UUID,
    val responseCount: Int,
    val lastPosition: Long,
    val scores: RiskScoresResponse?,
    val associationScore: Float?,
)

data class ResponseAnalysisResponse(
    val status: String,
    val modelFingerprint: String?,
    val atomic: RiskScoresResponse?,
    val trajectory: TrajectoryAssessmentResponse?,
    val errorCode: String?,
    val message: String?,
    val coverage: ResponseCoverageResponse?,
    val windowPeak: WindowPeakResponse?,
    val failedResponseId: UUID?,
)

data class ResponseCoverageResponse(val status: String, val totalTokens: Int, val processedTokens: Int, val windowCount: Int)
data class TokenRangeResponse(val start: Int, val end: Int)
data class WindowPeakResponse(val scores: RiskScoresResponse, val peakTokenRanges: Map<String, TokenRangeResponse>)

data class ConversationHistoryResponse(
    val conversation: ConversationPreviewResponse,
    val messages: List<MessageResponse>,
)

data class MessageExchangeResponse(
    val conversation: ConversationPreviewResponse,
    val userMessage: MessageResponse,
    val assistantMessage: MessageResponse,
)
