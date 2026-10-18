package com.example.conversation.conversation.application

import com.example.conversation.conversation.application.dto.ConversationContext
import com.example.conversation.conversation.persistence.repository.ConversationRepository
import com.example.conversation.conversation.persistence.repository.MessageRepository
import com.example.conversation.error.ConversationNotFound
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ConversationReader(
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
) {
    @Transactional(readOnly = true)
    fun requireOwned(owner: UUID, conversationId: UUID) {
        if (!conversations.existsByIdAndUserExternalId(conversationId, owner)) throw ConversationNotFound()
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun readReplyContext(owner: UUID, conversationId: UUID, maxExchanges: Int): ConversationContext {
        require(maxExchanges >= 0)
        val conversation = conversations.requireOwned(owner, conversationId)
        val recent = if (maxExchanges == 0) emptyList() else
            messages.findByConversationIdOrderBySequenceDesc(conversationId, PageRequest.of(0, Math.multiplyExact(maxExchanges, 2)))
                .map { it.toSnapshot() }.reversed()
        return ConversationContext(conversation.toPreview(), recent.toPromptExchanges(), checkNotNull(conversation.version))
    }
}
