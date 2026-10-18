package com.example.conversation.trajectory.model

import com.example.conversation.conversation.model.Conversation
import com.example.conversation.conversation.model.Message
import jakarta.persistence.*

import java.util.UUID

/** Progress of one model release through a conversation; no scan of previously analysed messages. */
@Entity
@Table(name = "analysis_cursor", uniqueConstraints = [UniqueConstraint(columnNames = ["conversation_id", "model_fingerprint"])])
class AnalysisCursor(
    @Id val id: UUID,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    val conversation: Conversation,
    @Column(nullable = false, length = 64) val modelFingerprint: String,
) {
    @Column(nullable = false)
    var lastMessageSequence: Long = 0
        protected set
    @Version
    var version: Long? = null
        protected set

    fun nextMessageSequence(): Long = Math.addExact(lastMessageSequence, 2)

    fun advance(response: Message) {
        response.assistantPosition()
        require(response.conversation.id == conversation.id && response.sequence == nextMessageSequence())
        lastMessageSequence = response.sequence
    }
}
