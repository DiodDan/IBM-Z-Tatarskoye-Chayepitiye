package com.example.conversation.conversation.application.dto

/** Result of a successful send: the conversation plus the newly saved user/assistant pair. */
data class MessageExchange(
    val conversation: ConversationPreview,
    val userMessage: MessageSnapshot,
    val assistantMessage: MessageSnapshot,
)
