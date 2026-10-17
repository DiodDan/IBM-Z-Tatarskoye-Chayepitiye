package com.example.conversation.conversation.persistence.repository

import com.example.conversation.conversation.persistence.model.Message
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MessageRepository : JpaRepository<Message, UUID> {

    /** Full history, oldest first. */
    fun findAllByConversationIdOrderBySequenceAsc(conversationId: UUID): List<Message>

    /** Newest messages first, limited by `pageable` (used for LLM context). */
    fun findByConversationIdOrderBySequenceDesc(conversationId: UUID, pageable: Pageable): List<Message>

    @Query("select coalesce(max(m.sequence), 0L) from Message m where m.conversation.id = :conversationId")
    fun findMaxSequence(@Param("conversationId") conversationId: UUID): Long

    /** The newest message of every conversation the user owns (sidebar previews). */
    @Query(
        """
        select m from Message m
        where m.conversation.user.externalId = :owner
          and m.sequence = (select max(m2.sequence) from Message m2 where m2.conversation = m.conversation)
        """,
    )
    fun findLatestPerConversation(@Param("owner") owner: UUID): List<Message>
}
