package com.example.conversation.identity.service

import com.example.conversation.identity.model.User
import com.example.conversation.identity.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val clock: Clock,
) {
    /**
     * Returns the internal user for this cookie, creating it if needed.
     * Must run inside the caller's write transaction (first successful conversation creation).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun findOrCreate(externalId: UUID): User {
        userRepository.findByExternalId(externalId)?.let { return it }
        userRepository.insertIfAbsent(UUID.randomUUID(), externalId, Instant.now(clock))
        return userRepository.findByExternalId(externalId)
            ?: error("User row missing after insert")
    }
}
