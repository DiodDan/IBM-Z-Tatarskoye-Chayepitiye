package com.example.conversation.conversation.persistence.model

import com.example.conversation.identity.model.User
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
class Conversation(
    @Id
    @Column(nullable = false)
    val id: UUID,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    val user: User,

    @Column(nullable = false, length = 80)
    var title: String,

    @Column(nullable = false, columnDefinition = "timestamptz")
    val createdAt: Instant,
)
