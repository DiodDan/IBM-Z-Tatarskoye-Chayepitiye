package com.example.conversation.identity

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
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
    fun findOrCreate(externalId: UUID): UserEntity {
        userRepository.findByExternalId(externalId)?.let { return it }
        userRepository.insertIfAbsent(UUID.randomUUID(), externalId, now())
        return userRepository.findByExternalId(externalId)
            ?: error("User row missing after insert")
    }

    private fun now(): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MICROS)
}
