package com.example.conversation.conversation.persistence.repository

import com.example.conversation.conversation.persistence.model.Conversation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Every lookup is filtered by owner, so other users' conversations are simply "not found". */
interface ConversationRepository : JpaRepository<Conversation, UUID> {

    fun findAllByUserExternalIdOrderByCreatedAtDescIdAsc(externalId: UUID): List<Conversation>

    fun findByIdAndUserExternalId(id: UUID, externalId: UUID): Conversation?

    fun existsByIdAndUserExternalId(id: UUID, externalId: UUID): Boolean
}
