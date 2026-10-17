package com.example.conversation.identity.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "app_user")
class User(
    @Id
    @Column(nullable = false)
    val id: UUID,

    @Column(nullable = false, unique = true)
    val externalId: UUID,

    @Column(nullable = false, columnDefinition = "timestamptz")
    val createdAt: Instant,
)
