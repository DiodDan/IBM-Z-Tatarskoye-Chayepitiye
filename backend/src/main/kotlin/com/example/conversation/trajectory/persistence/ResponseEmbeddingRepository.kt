package com.example.conversation.trajectory.persistence

import com.example.conversation.trajectory.model.ResponseEmbedding
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ResponseEmbeddingRepository : JpaRepository<ResponseEmbedding, UUID> {
    fun findByResponseIdAndModelFingerprint(responseId: UUID, modelFingerprint: String): ResponseEmbedding?
}
