package com.example.conversation.conversation.persistence.repository

import com.example.conversation.conversation.model.Conversation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType
import java.util.UUID

/** Every lookup is filtered by owner, so other users' conversations are simply "not found". */
interface ConversationRepository : JpaRepository<Conversation, UUID> {

    fun findAllByUserExternalIdOrderByCreatedAtDescIdAsc(externalId: UUID): List<Conversation>

    fun findByIdAndUserExternalId(id: UUID, externalId: UUID): Conversation?

    fun existsByIdAndUserExternalId(id: UUID, externalId: UUID): Boolean

    /** Used only while committing analysis results, never during inference. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id and c.user.externalId = :owner")
    fun lockOwned(@Param("id") id: UUID, @Param("owner") owner: UUID): Conversation?
}
