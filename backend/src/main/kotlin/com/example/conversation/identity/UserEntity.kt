package com.example.conversation.identity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "app_user")
class UserEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID,

    /** The browser's user_id cookie value. */
    @Column(name = "external_id", nullable = false, unique = true)
    val externalId: UUID,

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    val createdAt: Instant,
)
