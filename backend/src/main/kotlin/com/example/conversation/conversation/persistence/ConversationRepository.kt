package com.example.conversation.conversation.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Every lookup is filtered by owner, so other users' conversations are simply "not found". */
interface ConversationRepository : JpaRepository<ConversationEntity, UUID> {

    fun findAllByUserExternalIdOrderByCreatedAtDescIdAsc(externalId: UUID): List<ConversationEntity>

    fun findByIdAndUserExternalId(id: UUID, externalId: UUID): ConversationEntity?

    fun existsByIdAndUserExternalId(id: UUID, externalId: UUID): Boolean
}
