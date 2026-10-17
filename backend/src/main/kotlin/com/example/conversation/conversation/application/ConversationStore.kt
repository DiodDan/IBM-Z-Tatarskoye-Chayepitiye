package com.example.conversation.conversation.application

import com.example.conversation.conversation.model.MessageRole
import com.example.conversation.conversation.persistence.ConversationEntity
import com.example.conversation.conversation.persistence.ConversationRepository
import com.example.conversation.conversation.persistence.MessageEntity
import com.example.conversation.conversation.persistence.MessageRepository
import com.example.conversation.error.ApiException
import com.example.conversation.identity.UserService
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
    private val entityManager: EntityManager,
    private val clock: Clock,
) {

    // ---------- Reads ----------

    @Transactional(readOnly = true)
    fun listPreviews(owner: UUID): List<ConversationPreview> =
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
        val now = now()
        val conversation = ConversationEntity(UUID.randomUUID(), user, title, now)
        entityManager.persist(conversation)
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
        return insertPair(conversation, firstSequence = currentMax + 1, userText, replyText, now())
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
        entityManager.remove(findOwned(owner, conversationId))
    }

    // ---------- Helpers ----------

    private fun insertPair(
        conversation: ConversationEntity,
        firstSequence: Long,
        userText: String,
        replyText: String,
        now: Instant,
    ): MessageExchange {
        val userMessage = MessageEntity(UUID.randomUUID(), conversation.id, firstSequence, MessageRole.USER, userText, now)
        val assistantMessage =
            MessageEntity(UUID.randomUUID(), conversation.id, firstSequence + 1, MessageRole.ASSISTANT, replyText, now)
        entityManager.persist(userMessage)
        entityManager.persist(assistantMessage)
        entityManager.flush() // surface constraint errors here, inside the transaction
        return MessageExchange(conversation.toPreview(), userMessage.toSnapshot(), assistantMessage.toSnapshot())
    }

    private fun findOwned(owner: UUID, conversationId: UUID): ConversationEntity =
        conversations.findByIdAndUserExternalId(conversationId, owner) ?: throw ApiException.conversationNotFound()

    /** PostgreSQL stores microseconds; truncate so the response matches what was saved. */
    private fun now(): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MICROS)

    private fun ConversationEntity.toPreview() = ConversationPreview(id, title, createdAt)

    private fun MessageEntity.toSnapshot() = MessageSnapshot(id, role, content, createdAt)
}
