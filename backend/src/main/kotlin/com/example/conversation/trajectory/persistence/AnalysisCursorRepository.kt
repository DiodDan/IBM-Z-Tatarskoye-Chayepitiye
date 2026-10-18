package com.example.conversation.trajectory.persistence

import com.example.conversation.trajectory.model.AnalysisCursor
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AnalysisCursorRepository : JpaRepository<AnalysisCursor, UUID> {
    fun findByConversationIdAndModelFingerprint(conversationId: UUID, modelFingerprint: String): AnalysisCursor?
}
