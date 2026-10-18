package com.example.conversation.conversation.persistence.repository

import com.example.conversation.conversation.persistence.model.Message
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MessageRepository : JpaRepository<Message, UUID> {

    fun findByIdAndConversationUserExternalId(id: UUID, externalId: UUID): Message?
    fun findByConversationIdAndSequence(conversationId: UUID, sequence: Long): Message?

    /** Full history, oldest first. */
    fun findAllByConversationIdOrderBySequenceAsc(conversationId: UUID): List<Message>

    /** Newest messages first, limited by `pageable` (used for LLM context). */
    fun findByConversationIdOrderBySequenceDesc(conversationId: UUID, pageable: Pageable): List<Message>

    @Query(value = "select nextval('response_number_seq')", nativeQuery = true)
    fun nextResponseNumber(): Long

    @Query(
        """
        select m from Message m
        where m.conversation.user.externalId = :owner
          and m.sequence = (select max(m2.sequence) from Message m2 where m2.conversation = m.conversation)
        """,
    )
    fun findLatestPerConversation(@Param("owner") owner: UUID): List<Message>

    @Query(
        """
        select m from Message m
        where m.sequence = (select max(m2.sequence) from Message m2 where m2.conversation = m.conversation)
        """,
    )
    fun findLatestOfAllConversations(): List<Message>
}
