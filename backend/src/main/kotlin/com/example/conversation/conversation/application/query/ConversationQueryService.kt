package com.example.conversation.conversation.application.query

import com.example.conversation.conversation.application.requireOwned
import com.example.conversation.conversation.application.toPreview
import com.example.conversation.conversation.application.toSnapshot
import com.example.conversation.conversation.application.dto.ConversationPreview
import com.example.conversation.conversation.application.dto.ConversationSnapshot
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Isolation
import com.example.conversation.conversation.persistence.repository.ConversationRepository
import com.example.conversation.conversation.persistence.repository.MessageRepository
import java.util.UUID
import com.example.conversation.conversation.model.MessageRole
import com.example.conversation.trajectory.application.ResponseAnalysisReader

@Service
class ConversationQueryService(
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val analysis: ResponseAnalysisReader,
) {

    /** An unknown (new) browser simply gets an empty list; no user row is created. */
    @Transactional(readOnly = true)
    fun list(owner: UUID): List<ConversationPreview> =
        conversations.findAllByUserExternalIdOrderByCreatedAtDescIdAsc(owner).map { it.toPreview() }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun conversationSnapshot(owner: UUID, conversationId: UUID): ConversationSnapshot {
        val conversation = conversations.requireOwned(owner, conversationId)
        val assessments = analysis.read(conversationId)
        val history = messages.findAllByConversationIdOrderBySequenceAsc(conversationId).map {
            val snapshot = it.toSnapshot()
            if (it.role == MessageRole.ASSISTANT) snapshot.copy(analysis = assessments[it.id] ?: analysis.missing()) else snapshot
        }
        return ConversationSnapshot(conversation.toPreview(), history)
    }
}
