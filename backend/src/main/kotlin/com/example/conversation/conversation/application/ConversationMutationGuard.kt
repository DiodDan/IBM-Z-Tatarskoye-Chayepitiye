package com.example.conversation.conversation.application

import com.example.conversation.error.ApiException
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * "One mutation at a time per conversation", without waiting: a second send/rename/delete
 * while the first is running gets 409. Entries are removed when done, so the map only
 * holds conversations that are busy right now.
 *
 * In-memory: only correct with ONE backend instance (the agreed setup).
 */
@Component
class ConversationMutationGuard {

    private val busy: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun <T> runExclusive(conversationId: UUID, action: () -> T): T {
        if (!busy.add(conversationId)) throw ApiException.conversationBusy()
        try {
            return action()
        } finally {
            busy.remove(conversationId)
        }
    }
}
