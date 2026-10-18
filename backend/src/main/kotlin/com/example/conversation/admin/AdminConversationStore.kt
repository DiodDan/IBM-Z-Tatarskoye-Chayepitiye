package com.example.conversation.admin

import com.example.conversation.conversation.persistence.model.Conversation
import com.example.conversation.conversation.persistence.model.Message
import com.example.conversation.conversation.persistence.repository.ConversationRepository
import com.example.conversation.conversation.persistence.repository.MessageRepository
import com.example.conversation.error.ApiException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** One conversation as the admin sees it: who owns it plus the sidebar preview. */
data class AdminConversation(
    val id: UUID,
    val userId: UUID,
    val title: String,
    val createdAt: Instant,
    val lastMessage: String?,
)

/** A message including the admin-only fields. */
data class AdminMessage(
    val id: UUID,
    val role: String,
    val content: String,
    val createdAt: Instant,
    val suggestions: List<String>,
    val promptRisk: Double?,
    val promptRiskReason: String?,
    val responseTimeMs: Long?,
)

data class AdminConversationHistory(val conversation: AdminConversation, val messages: List<AdminMessage>)

/**
 * Read-only database access for the admin page. Unlike ConversationStore it does NOT filter by owner:
 * it sees every user's conversations. Nothing here writes.
 */
@Component
class AdminConversationStore(
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
) {

    @Transactional(readOnly = true)
    fun listAll(): List<AdminConversation> {
        val latest = messages.findLatestOfAllConversations().associate { it.conversation.id to it.content }
        return conversations.findAllWithUserNewestFirst().map { it.toAdmin(latest[it.id]) }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun history(conversationId: UUID): AdminConversationHistory {
        val conversation = conversations.findWithUser(conversationId) ?: throw ApiException.conversationNotFound()
        val history = messages.findAllByConversationIdOrderBySequenceAsc(conversationId).map { it.toAdmin() }
        return AdminConversationHistory(conversation.toAdmin(history.lastOrNull()?.content), history)
    }

    private fun Conversation.toAdmin(lastMessage: String?) =
        AdminConversation(id, user.externalId, title, createdAt, lastMessage?.let { shorten(it) })

    private fun Message.toAdmin() = AdminMessage(
        id, role.apiValue, content, createdAt, suggestions.toList(), promptRisk, promptRiskReason, responseTimeMs,
    )

    /** Same one-line preview as the user sidebar. */
    private fun shorten(text: String): String {
        val normalized = text.replace(whitespace, " ").trim()
        if (normalized.codePointCount(0, normalized.length) <= PREVIEW_CODE_POINTS) return normalized
        return normalized.substring(0, normalized.offsetByCodePoints(0, PREVIEW_CODE_POINTS)).trimEnd() + "…"
    }

    private companion object {
        const val PREVIEW_CODE_POINTS = 120
        val whitespace = Regex("\\s+")
    }
}
