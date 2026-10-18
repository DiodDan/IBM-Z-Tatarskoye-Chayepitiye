package com.example.conversation.admin

import com.example.conversation.conversation.persistence.model.Message
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/** One AI reply in the admin Responses panel, with the user prompt it answers and that prompt's risk data. */
data class AdminResponse(
    val responseNumber: Long,
    val conversationId: UUID,
    val conversationTitle: String,
    val userId: UUID,
    val messageId: UUID,
    val prompt: String,
    val content: String,
    val createdAt: Instant,
    val promptRisk: Double?,
    val promptRiskReason: String?,
    val responseTimeMs: Long?,
) {
    var modelAssessment: AdminModelAssessment? = null
}

private const val RESPONSE_ROWS = """
    select new com.example.conversation.admin.AdminResponse(
        a.responseNumber, c.id, c.title, owner.externalId, a.id, u.content, a.content, a.createdAt,
        u.promptRisk, u.promptRiskReason, a.responseTimeMs)
    from Message a join a.conversation c join c.user owner, Message u
    where a.responseNumber is not null and u.conversation = c and u.sequence = a.sequence - 1
"""

/** ADMIN ONLY, read-only queries across every user's messages. */
interface AdminResponseRepository : Repository<Message, UUID> {

    @Query("$RESPONSE_ROWS order by a.responseNumber desc")
    fun newestFirst(page: Pageable): List<AdminResponse>

    @Query("$RESPONSE_ROWS order by u.promptRiskReason asc nulls last, a.responseNumber desc")
    fun byReason(page: Pageable): List<AdminResponse>

    @Query("""
        select new com.example.conversation.admin.AdminResponse(
            a.responseNumber, c.id, c.title, owner.externalId, a.id, u.content, a.content, a.createdAt,
            u.promptRisk, u.promptRiskReason, a.responseTimeMs)
        from Message a join a.conversation c join c.user owner
        left join ResponseEmbedding e on e.response = a and e.modelFingerprint = :fingerprint,
        Message u
        where a.responseNumber is not null and u.conversation = c and u.sequence = a.sequence - 1
        order by e.prediction.overallScore asc nulls last, a.responseNumber desc
    """)
    fun byRiskAscending(@Param("fingerprint") fingerprint: String?, page: Pageable): List<AdminResponse>

    @Query("""
        select new com.example.conversation.admin.AdminResponse(
            a.responseNumber, c.id, c.title, owner.externalId, a.id, u.content, a.content, a.createdAt,
            u.promptRisk, u.promptRiskReason, a.responseTimeMs)
        from Message a join a.conversation c join c.user owner
        left join ResponseEmbedding e on e.response = a and e.modelFingerprint = :fingerprint,
        Message u
        where a.responseNumber is not null and u.conversation = c and u.sequence = a.sequence - 1
        order by e.prediction.overallScore desc nulls last, a.responseNumber desc
    """)
    fun byRiskDescending(@Param("fingerprint") fingerprint: String?, page: Pageable): List<AdminResponse>

    fun findByResponseNumber(responseNumber: Long): Message?

    /** First message per conversation containing `pattern` (lower-case LIKE pattern, `\` escapes). */
    @Query(
        """
        select m from Message m
        where m.conversation.id in :ids and lower(m.content) like :pattern escape '\'
        order by m.sequence asc
        """,
    )
    fun findContaining(@Param("ids") ids: Collection<UUID>, @Param("pattern") pattern: String): List<Message>
}
