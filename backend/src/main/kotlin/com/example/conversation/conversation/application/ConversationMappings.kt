package com.example.conversation.conversation.application

import com.example.conversation.conversation.application.dto.ConversationPreview
import com.example.conversation.conversation.application.dto.MessageSnapshot
import com.example.conversation.conversation.model.MessageRole
import com.example.conversation.conversation.model.Conversation
import com.example.conversation.conversation.model.Message
import com.example.conversation.conversation.persistence.repository.ConversationRepository
import com.example.conversation.error.ConversationNotFound
import com.example.conversation.llm.PromptExchange
import java.util.UUID

internal fun ConversationRepository.requireOwned(owner: UUID, id: UUID): Conversation =
    findByIdAndUserExternalId(id, owner) ?: throw ConversationNotFound()

internal fun Conversation.toPreview() = ConversationPreview(id, title, createdAt)
internal fun Message.toSnapshot() = MessageSnapshot(id, role, content, createdAt)

/** Validate the stored pair contract at the point where history becomes an LLM prompt. */
internal fun List<MessageSnapshot>.toPromptExchanges(): List<PromptExchange> {
    check(size % 2 == 0) { "Conversation history contains an incomplete exchange" }
    return chunked(2).map { (user, assistant) ->
        check(user.role == MessageRole.USER && assistant.role == MessageRole.ASSISTANT) {
            "Conversation history violates exchange order"
        }
        PromptExchange(user.content, assistant.content)
    }
}
