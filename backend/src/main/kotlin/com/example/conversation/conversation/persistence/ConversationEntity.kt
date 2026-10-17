package com.example.conversation.conversation.persistence

import com.example.conversation.identity.UserEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "conversation")
class ConversationEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,

    @Column(name = "title", nullable = false, length = 80)
    var title: String,

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    val createdAt: Instant,
)
