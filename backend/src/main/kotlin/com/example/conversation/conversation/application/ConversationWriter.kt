package com.example.conversation.conversation.application

import com.example.conversation.conversation.application.dto.ConversationPreview
import com.example.conversation.conversation.application.dto.MessageExchange
import com.example.conversation.conversation.model.Conversation
import com.example.conversation.conversation.model.MessagePair
import com.example.conversation.conversation.persistence.repository.ConversationRepository
import com.example.conversation.error.ConversationChanged
import com.example.conversation.identity.service.UserService
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Short write transactions only. Provider and inference calls belong to application scenarios. */
@Service
class ConversationWriter(
    private val conversations: ConversationRepository,
    private val users: UserService,
    private val entities: EntityManager,
    private val clock: Clock,
) {
    @Transactional
    fun createWithExchange(owner: UUID, title: String, userText: String, replyText: String, conversationId: UUID = UUID.randomUUID()): MessageExchange {
        val now = Instant.now(clock)
        val conversation = Conversation(conversationId, users.findOrCreate(owner), title, now)
        entities.persist(conversation)
        return persist(conversation, conversation.appendExchange(userText, replyText, now))
    }

    @Transactional
    fun appendExchange(owner: UUID, conversationId: UUID, expectedVersion: Long, userText: String, replyText: String): MessageExchange {
        val conversation = conversations.requireOwned(owner, conversationId)
        if (conversation.version != expectedVersion) throw ConversationChanged()
        val pair = conversation.appendExchange(userText, replyText, Instant.now(clock))
        // Claim the parent's version before inserting children: a losing writer gets an optimistic conflict.
        conversations.flush()
        return persist(conversation, pair)
    }

    @Transactional
    fun rename(owner: UUID, conversationId: UUID, title: String): ConversationPreview =
        conversations.requireOwned(owner, conversationId).also { it.rename(title) }.toPreview()

    @Transactional
    fun delete(owner: UUID, conversationId: UUID) {
        entities.remove(conversations.requireOwned(owner, conversationId))
    }

    private fun persist(conversation: Conversation, pair: MessagePair): MessageExchange {
        entities.persist(pair.user)
        entities.persist(pair.assistant)
        return MessageExchange(conversation.toPreview(), pair.user.toSnapshot(), pair.assistant.toSnapshot())
    }
}
