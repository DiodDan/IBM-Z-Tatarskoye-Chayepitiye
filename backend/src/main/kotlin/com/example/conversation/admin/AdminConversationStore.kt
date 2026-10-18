package com.example.conversation.admin

import com.example.conversation.conversation.persistence.model.Conversation
import com.example.conversation.conversation.persistence.model.Message
import com.example.conversation.conversation.persistence.repository.ConversationRepository
import com.example.conversation.conversation.persistence.repository.MessageRepository
import com.example.conversation.error.ApiException
import org.springframework.data.domain.PageRequest
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
    /** Search results only: the message that matched, so the page can open the chat right at it. */
    val matchedMessageId: UUID? = null,
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
    val responseNumber: Long?,
    val modelAssessment: AdminModelAssessment? = null,
)

data class AdminConversationHistory(val conversation: AdminConversation, val messages: List<AdminMessage>)

enum class ResponseSort { NEWEST, REASON, RISK_ASC, RISK_DESC }

/**
 * Read-only database access for the admin page. Unlike ConversationStore it does NOT filter by owner:
 * it sees every user's conversations. Nothing here writes.
 */
@Component
class AdminConversationStore(
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val responses: AdminResponseRepository,
    private val modelScores: AdminModelScoreReader,
) {

    @Transactional(readOnly = true)
    fun listAll(): List<AdminConversation> {
        val latest = messages.findLatestOfAllConversations().associate { it.conversation.id to it.content }
        return conversations.findAllWithUserNewestFirst().map { it.toAdmin(latest[it.id]) }
    }

    /**
     * What an admin pastes into the search box:
     *  - a response number ("Response №12", "№12", "#12", "12") -> the conversation containing that reply, first;
     *  - any text (a title, or text copied from a reply) -> every conversation whose title or messages contain it.
     * Each result carries the matching message, so the page can jump straight to it.
     */
    @Transactional(readOnly = true)
    fun search(rawQuery: String): List<AdminConversation> {
        val byNumber = responseNumberIn(rawQuery)?.let { responses.findByResponseNumber(it) }
        val term = searchTerm(rawQuery)
        val pattern = term?.let { "%" + escapeLike(it.lowercase()) + "%" }

        val found = LinkedHashMap<UUID, Conversation>()
        byNumber?.let { found[it.conversation.id] = conversations.findWithUser(it.conversation.id)!! }
        pattern?.let { p -> conversations.searchAllWithUser(p).forEach { found.putIfAbsent(it.id, it) } }
        if (found.isEmpty()) return emptyList()

        val matched = HashMap<UUID, UUID>()
        byNumber?.let { matched[it.conversation.id] = it.id }
        pattern?.let { p ->
            responses.findContaining(found.keys, p).forEach { matched.putIfAbsent(it.conversation.id, it.id) }
        }
        val latest = messages.findLatestOfAllConversations().associate { it.conversation.id to it.content }
        return found.values.map { it.toAdmin(latest[it.id]).copy(matchedMessageId = matched[it.id]) }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun history(conversationId: UUID): AdminConversationHistory {
        val conversation = conversations.findWithUser(conversationId) ?: throw ApiException.conversationNotFound()
        val stored = messages.findAllByConversationIdOrderBySequenceAsc(conversationId)
        val scores = modelScores.read(stored.filter { it.role.apiValue == "assistant" }.map { it.id })
        val history = stored.map { it.toAdmin().copy(modelAssessment = scores[it.id]) }
        return AdminConversationHistory(conversation.toAdmin(history.lastOrNull()?.content), history)
    }

    @Transactional(readOnly = true)
    fun responses(sort: ResponseSort, limit: Int): List<AdminResponse> {
        val page = PageRequest.of(0, limit)
        val fingerprint = modelScores.fingerprint()
        val rows = when (sort) {
            ResponseSort.NEWEST -> responses.newestFirst(page)
            ResponseSort.REASON -> responses.byReason(page)
            ResponseSort.RISK_ASC -> responses.byRiskAscending(fingerprint, page)
            ResponseSort.RISK_DESC -> responses.byRiskDescending(fingerprint, page)
        }
        val scores = modelScores.read(rows.map { it.messageId })
        return rows.onEach { it.modelAssessment = scores[it.messageId] }
    }

    private fun Conversation.toAdmin(lastMessage: String?) =
        AdminConversation(id, user.externalId, title, createdAt, lastMessage?.let { shorten(it) })

    private fun Message.toAdmin() = AdminMessage(
        id, role.apiValue, content, createdAt, suggestions.toList(),
        promptRisk, promptRiskReason, responseTimeMs, responseNumber,
    )

    /** Same one-line preview as the user sidebar. */
    private fun shorten(text: String): String {
        val normalized = text.replace(whitespace, " ").trim()
        if (normalized.codePointCount(0, normalized.length) <= PREVIEW_CODE_POINTS) return normalized
        return normalized.substring(0, normalized.offsetByCodePoints(0, PREVIEW_CODE_POINTS)).trimEnd() + "…"
    }

    companion object {
        private const val PREVIEW_CODE_POINTS = 120
        private const val MAX_TERM_LENGTH = 200
        private val whitespace = Regex("\\s+")
        private val responseNumber = Regex("""^(?:response\s*)?(?:№|no\.?|#)?\s*(\d{1,18})$""", RegexOption.IGNORE_CASE)

        fun responseNumberIn(query: String): Long? =
            responseNumber.matchEntire(query.trim())?.groupValues?.get(1)?.toLongOrNull()

        /**
         * Text copied from a reply may span paragraphs, and the browser may copy the line breaks differently
         * than they are stored. So search for its longest single line, which is stored exactly as shown.
         * A trailing "…" (from a shortened preview) is dropped.
         */
        fun searchTerm(query: String): String? =
            query.lines()
                .map { it.trim().removeSuffix("…").trim() }
                .filter { it.isNotEmpty() }
                .maxByOrNull { it.length }
                ?.take(MAX_TERM_LENGTH)

        fun escapeLike(text: String): String =
            text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}
