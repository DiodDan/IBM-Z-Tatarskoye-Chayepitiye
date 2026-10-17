package com.example.conversation.identity.repository

import com.example.conversation.identity.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {

    fun findByExternalId(externalId: UUID): User?

    /** Safe under concurrency: two first messages from the same browser can't both insert. */
    @Modifying
    @Query(
        value = """
            INSERT INTO app_user (id, external_id, created_at)
            VALUES (:id, :externalId, :createdAt)
            ON CONFLICT (external_id) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("id") id: UUID,
        @Param("externalId") externalId: UUID,
        @Param("createdAt") createdAt: Instant,
    ): Int
}
