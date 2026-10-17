package com.example.conversation.conversation.application

import com.example.conversation.conversation.application.dto.ConversationContext
import com.example.conversation.conversation.application.dto.ConversationPreview
import com.example.conversation.conversation.application.dto.ConversationSnapshot
import com.example.conversation.conversation.application.dto.MessageExchange
import com.example.conversation.conversation.application.dto.MessageSnapshot
import com.example.conversation.conversation.model.MessageRole
import com.example.conversation.conversation.persistence.model.Conversation
import com.example.conversation.conversation.persistence.repository.ConversationRepository
import com.example.conversation.conversation.persistence.model.Message
import com.example.conversation.conversation.persistence.repository.MessageRepository
import com.example.conversation.error.ApiException
import com.example.conversation.identity.service.UserService
import jakarta.persistence.EntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * All database work, in SHORT transactions. A separate bean from ConversationCommandService
 * so @Transactional is applied through the Spring proxy, and so no transaction is ever open
 * while the LLM is generating (backend-requirements.md §7).
 *
 * Every method re-checks ownership: `owner` is the cookie UUID.
 */
@Component
class ConversationStore(
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val userService: UserService,
    private val clock: Clock,
) {

    // ---------- Reads ----------

    @Transactional(readOnly = true)
    fun listConversationsPreviews(owner: UUID): List<ConversationPreview> =
        conversations.findAllByUserExternalIdOrderByCreatedAtDescIdAsc(owner).map { it.toPreview() }

    @Transactional(readOnly = true)
    fun isOwned(owner: UUID, conversationId: UUID): Boolean =
        conversations.existsByIdAndUserExternalId(conversationId, owner)

    /** One repeatable-read transaction so metadata and messages come from the same moment. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun readHistory(owner: UUID, conversationId: UUID): ConversationSnapshot {
        val conversation = findOwned(owner, conversationId)
        val history = messages.findAllByConversationIdOrderBySequenceAsc(conversationId).map { it.toSnapshot() }
        return ConversationSnapshot(conversation.toPreview(), history)
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun readContext(owner: UUID, conversationId: UUID, maxMessages: Int): ConversationContext {
        val conversation = findOwned(owner, conversationId)
        val recent = if (maxMessages <= 0) {
            emptyList()
        } else {
            messages.findByConversationIdOrderBySequenceDesc(conversationId, PageRequest.of(0, maxMessages))
                .map { it.toSnapshot() }
                .reversed()
        }
        return ConversationContext(conversation.toPreview(), recent, messages.findMaxSequence(conversationId))
    }

    // ---------- Writes ----------

    /** First message: user row (if new) + conversation + messages 1 and 2, all or nothing. */
    @Transactional
    fun createWithExchange(owner: UUID, title: String, userText: String, replyText: String): MessageExchange {
        val user = userService.findOrCreate(owner)
        val now = Instant.now(clock)
        val conversation = Conversation(UUID.randomUUID(), user, title, now)
        conversations.save(conversation)
        return insertPair(conversation, firstSequence = 1, userText, replyText, now)
    }

    /**
     * Follow-up message. `expectedMaxSequence` is what we saw before calling the LLM;
     * if it changed, something else wrote in between and we refuse (409) instead of mixing histories.
     */
    @Transactional
    fun appendExchange(
        owner: UUID,
        conversationId: UUID,
        expectedMaxSequence: Long,
        userText: String,
        replyText: String,
    ): MessageExchange {
        val conversation = findOwned(owner, conversationId)
        val currentMax = messages.findMaxSequence(conversationId)
        if (currentMax != expectedMaxSequence) throw ApiException.conversationBusy()
        return insertPair(conversation, firstSequence = currentMax + 1, userText, replyText, Instant.now(clock))
    }

    @Transactional
    fun rename(owner: UUID, conversationId: UUID, title: String): ConversationPreview {
        val conversation = findOwned(owner, conversationId)
        conversation.title = title
        return conversation.toPreview()
    }

    /** Messages are removed by the database (ON DELETE CASCADE) in the same transaction. */
    @Transactional
    fun delete(owner: UUID, conversationId: UUID) {
        conversations.delete(findOwned(owner, conversationId))
    }

    // ---------- Helpers ----------

    private fun insertPair(
        conversation: Conversation,
        firstSequence: Long,
        userText: String,
        replyText: String,
        now: Instant,
    ): MessageExchange {
        val userMessage = Message(UUID.randomUUID(), conversation, firstSequence, MessageRole.USER, userText, now)
        val assistantMessage =
            Message(UUID.randomUUID(), conversation, firstSequence + 1, MessageRole.ASSISTANT, replyText, now)
        messages.save(userMessage)
        messages.save(assistantMessage)
        return MessageExchange(conversation.toPreview(), userMessage.toSnapshot(), assistantMessage.toSnapshot())
    }

    private fun findOwned(owner: UUID, conversationId: UUID): Conversation =
        conversations.findByIdAndUserExternalId(conversationId, owner) ?: throw ApiException.conversationNotFound()

    private fun Conversation.toPreview() = ConversationPreview(id, title, createdAt)

    private fun Message.toSnapshot() = MessageSnapshot(id, role, content, createdAt)
}
