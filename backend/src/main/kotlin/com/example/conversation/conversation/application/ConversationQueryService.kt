package com.example.conversation.conversation.application

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ConversationQueryService(private val store: ConversationStore) {

    /** An unknown (new) browser simply gets an empty list; no user row is created. */
    fun list(owner: UUID): List<ConversationPreview> = store.listPreviews(owner)

    fun history(owner: UUID, conversationId: UUID): ConversationSnapshot = store.readHistory(owner, conversationId)
}
