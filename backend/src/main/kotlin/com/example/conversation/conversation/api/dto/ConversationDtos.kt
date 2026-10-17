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
)

data class ConversationHistoryResponse(
    val conversation: ConversationPreviewResponse,
    val messages: List<MessageResponse>,
)

data class MessageExchangeResponse(
    val conversation: ConversationPreviewResponse,
    val userMessage: MessageResponse,
    val assistantMessage: MessageResponse,
)
