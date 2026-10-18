package com.example.conversation.admin

import com.example.conversation.conversation.api.mapper.ConversationApiMapper
import com.example.conversation.conversation.persistence.model.Conversation
import com.example.conversation.identity.model.User
import com.example.conversation.trajectory.application.TrajectoryModel
import com.example.conversation.trajectory.model.*
import com.example.conversation.trajectory.persistence.TrajectoryMemberRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.ObjectProvider
import java.time.Duration
import java.time.Instant
import java.util.UUID

class AdminModelScoresTest {
    private val fingerprint = "a".repeat(64)
    private val repository = mock(TrajectoryMemberRepository::class.java)
    @Suppress("UNCHECKED_CAST")
    private val provider = mock(ObjectProvider::class.java) as ObjectProvider<TrajectoryModel>
    private val reader = AdminModelScoreReader(repository, provider, ConversationApiMapper())

    @Test fun `disabled analysis is explicit and does not query model data`() {
        val id = UUID.randomUUID()
        val result = reader.read(listOf(id)).getValue(id)
        assertEquals("disabled", result.analysis.status)
        assertNull(result.analysis.atomic)
        verifyNoInteractions(repository)
    }

    @Test fun `batched read projects atomic and latest combined scores without vectors`() {
        val model = mock(TrajectoryModel::class.java)
        `when`(model.descriptor).thenReturn(ModelDescriptor(fingerprint, 256, 128, 512, ModelDescriptor.HEADS))
        `when`(provider.ifAvailable).thenReturn(model)
        val now = Instant.parse("2026-10-18T10:00:00Z")
        val user = User(UUID.randomUUID(), UUID.randomUUID(), now)
        val conversation = Conversation(UUID.randomUUID(), user, "Test", now)
        val first = conversation.appendExchange("Prompt", "First", now).assistant
        val second = conversation.appendExchange("Prompt 2", "Second", now.plusSeconds(1)).assistant
        fun prediction(score: Float) = StoredPrediction.of(Embedding(FloatArray(256)), RiskLogits(FloatArray(10)),
            RiskScores(score, ModelDescriptor.CATEGORIES.associateWith { score }, "test-sigmoid", false))
        val a = ResponseEmbedding(UUID.randomUUID(), first, fingerprint, prediction(.1f), now)
        val b = ResponseEmbedding(UUID.randomUUID(), second, fingerprint, prediction(.2f), now)
        val trajectory = Trajectory.start(a, now, Duration.ofHours(24))
        trajectory.append(b, prediction(.8f), now.plusSeconds(1), Duration.ofHours(24), 128)
        val member = TrajectoryMember(UUID.randomUUID(), trajectory, a, 1, null, null, null)
        val missing = UUID.randomUUID()
        val ids = listOf(first.id, missing)
        `when`(repository.findByResponseEmbeddingResponseIdInAndTrajectoryModelFingerprint(ids, fingerprint)).thenReturn(listOf(member))
        val result = reader.read(ids)
        val completed = result.getValue(first.id)
        assertEquals("completed", completed.analysis.status)
        assertEquals(.1f, completed.analysis.atomic!!.overall)
        assertEquals(.8f, completed.analysis.trajectory!!.scores!!.overall)
        assertEquals(2, completed.analysis.trajectory!!.responseCount)
        assertEquals(1, completed.membership!!.ordinal)
        assertNull(completed.analysis.trajectory!!.associationScore)
        assertEquals("pending", result.getValue(missing).analysis.status)
        assertFalse(completed.analysis.atomic!!.calibrationApplied)
        verify(repository, times(1)).findByResponseEmbeddingResponseIdInAndTrajectoryModelFingerprint(ids, fingerprint)
    }
}
