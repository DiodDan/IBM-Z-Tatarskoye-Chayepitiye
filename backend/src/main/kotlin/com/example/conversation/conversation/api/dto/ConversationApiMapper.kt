package com.example.conversation.conversation.api

import com.example.conversation.conversation.api.dto.ConversationHistoryResponse
import com.example.conversation.conversation.api.dto.ConversationListResponse
import com.example.conversation.conversation.api.dto.ConversationPreviewResponse
import com.example.conversation.conversation.api.dto.MessageExchangeResponse
import com.example.conversation.conversation.api.dto.MessageResponse
import com.example.conversation.conversation.application.ConversationPreview
import com.example.conversation.conversation.application.ConversationSnapshot
import com.example.conversation.conversation.application.MessageExchange
import com.example.conversation.conversation.application.MessageSnapshot
import org.springframework.stereotype.Component

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
        MessageResponse(message.id, message.role.apiValue, message.content, message.createdAt)
}
