package com.example.conversation.conversation.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MessageRepository : JpaRepository<MessageEntity, UUID> {

    /** Full history, oldest first. */
    fun findAllByConversationIdOrderBySequenceAsc(conversationId: UUID): List<MessageEntity>

    /** Newest messages first, limited by `pageable` (used for LLM context). */
    fun findByConversationIdOrderBySequenceDesc(conversationId: UUID, pageable: Pageable): List<MessageEntity>

    @Query("select coalesce(max(m.sequence), 0L) from MessageEntity m where m.conversationId = :conversationId")
    fun findMaxSequence(@Param("conversationId") conversationId: UUID): Long
}
