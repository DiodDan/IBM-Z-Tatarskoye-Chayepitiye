package com.example.conversation.admin

import com.example.conversation.conversation.persistence.model.Conversation
import com.example.conversation.identity.model.User
import com.example.conversation.trajectory.model.*
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.util.UUID

@DataJpaTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:adminsort;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdminResponseSortingTest @Autowired constructor(
    private val em: EntityManager,
    private val repository: AdminResponseRepository,
) {
    @Test fun `sorts assistant model scores before paging and puts missing current model last`() {
        val now = Instant.now()
        val fingerprint = "a".repeat(64)
        val user = User(UUID.randomUUID(), UUID.randomUUID(), now)
        em.persist(user)
        val conversation = Conversation(UUID.randomUUID(), user, "Scores", now)
        em.persist(conversation)
        val ids = (1L..3L).map { number ->
            val pair = conversation.appendExchange("Prompt $number", "Response $number", now,
                promptRisk = if (number == 1L) .99 else .01, responseNumber = number)
            em.persist(pair.user); em.persist(pair.assistant)
            if (number < 3) {
                val score = if (number == 1L) .1f else .9f
                val prediction = StoredPrediction.of(Embedding(FloatArray(256)), RiskLogits(FloatArray(10)),
                    RiskScores(score, ModelDescriptor.CATEGORIES.associateWith { score }, "test", false))
                em.persist(ResponseEmbedding(UUID.randomUUID(), pair.assistant, fingerprint, prediction, now))
            }
            pair.assistant.id
        }
        em.flush(); em.clear()
        val page = PageRequest.of(0, 10)
        assertEquals(listOf(ids[1], ids[0], ids[2]), repository.byRiskDescending(fingerprint, page).map { it.messageId })
        assertEquals(listOf(ids[0], ids[1], ids[2]), repository.byRiskAscending(fingerprint, page).map { it.messageId })
        assertEquals(ids[1], repository.byRiskDescending(fingerprint, PageRequest.of(0, 1)).single().messageId)
        assertEquals(listOf(3L, 2L, 1L), repository.byRiskDescending("b".repeat(64), page).map { it.responseNumber })
    }
}
