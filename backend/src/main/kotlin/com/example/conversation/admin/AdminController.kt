package com.example.conversation.admin

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class AdminConversationListResponse(
    val conversationCount: Int,
    val conversations: List<AdminConversation>,
)

data class AdminConversationHistoryResponse(
    val conversation: AdminConversation,
    val messages: List<AdminMessage>,
    /** Prompts with promptRisk >= this are high risk. */
    val riskThreshold: Double,
)

/**
 * Read-only admin API: every user's conversations, including prompt risk scores and response timing.
 *
 * WARNING: there is NO authentication yet (team decision for local development).
 * Anyone who can reach /api/admin can read all conversations. Add a login or password check
 * here before deploying anywhere other people can reach.
 */
@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val store: AdminConversationStore,
    private val properties: AdminProperties,
) {

    @GetMapping("/conversations")
    fun list(): AdminConversationListResponse {
        val all = store.listAll()
        return AdminConversationListResponse(all.size, all)
    }

    @GetMapping("/conversations/{conversationId}")
    fun get(@PathVariable conversationId: UUID): AdminConversationHistoryResponse {
        val history = store.history(conversationId)
        return AdminConversationHistoryResponse(history.conversation, history.messages, properties.riskThreshold)
    }
}
