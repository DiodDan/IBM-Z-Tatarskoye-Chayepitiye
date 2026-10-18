package com.example.conversation.conversation.persistence.repository

import com.example.conversation.conversation.persistence.model.Conversation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/** Every lookup is filtered by owner, so other users' conversations are simply "not found". */
interface ConversationRepository : JpaRepository<Conversation, UUID> {

    fun findAllByUserExternalIdOrderByCreatedAtDescIdAsc(externalId: UUID): List<Conversation>

    fun findByIdAndUserExternalId(id: UUID, externalId: UUID): Conversation?

    fun existsByIdAndUserExternalId(id: UUID, externalId: UUID): Boolean

    /** ADMIN ONLY: every user's conversations, newest first, with the owner loaded in the same query. */
    @Query("select c from Conversation c join fetch c.user order by c.createdAt desc, c.id asc")
    fun findAllWithUserNewestFirst(): List<Conversation>

    /** ADMIN ONLY: one conversation regardless of owner. */
    @Query("select c from Conversation c join fetch c.user where c.id = :id")
    fun findWithUser(@Param("id") id: UUID): Conversation?
}
