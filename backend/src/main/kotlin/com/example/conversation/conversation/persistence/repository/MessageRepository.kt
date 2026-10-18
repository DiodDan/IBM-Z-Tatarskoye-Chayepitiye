package com.example.conversation.conversation.persistence.repository

import com.example.conversation.conversation.model.Message
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MessageRepository : JpaRepository<Message, UUID> {

    fun findByIdAndConversationUserExternalId(id: UUID, externalId: UUID): Message?
    fun findByConversationIdAndSequence(conversationId: UUID, sequence: Long): Message?

    /** Full history, oldest first. */
    fun findAllByConversationIdOrderBySequenceAsc(conversationId: UUID): List<Message>

    /** Newest messages first, limited by `pageable` (used for LLM context). */
    fun findByConversationIdOrderBySequenceDesc(conversationId: UUID, pageable: Pageable): List<Message>

}
