package com.example.conversation.admin

import com.example.conversation.error.ApiException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
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

data class AdminResponseListResponse(
    val responses: List<AdminResponse>,
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

    /** All conversations, or with `q` only those matching a pasted response number or text. */
    @GetMapping("/conversations")
    fun list(@RequestParam(required = false) q: String?): AdminConversationListResponse {
        val result = if (q.isNullOrBlank()) store.listAll() else store.search(q)
        return AdminConversationListResponse(result.size, result)
    }

    @GetMapping("/conversations/{conversationId}")
    fun get(@PathVariable conversationId: UUID): AdminConversationHistoryResponse {
        val history = store.history(conversationId)
        return AdminConversationHistoryResponse(history.conversation, history.messages, properties.riskThreshold)
    }

    /** Every AI reply of every conversation, for the Responses panel. `sort`: newest, reason, risk-asc, risk-desc. */
    @GetMapping("/responses")
    fun responses(
        @RequestParam(defaultValue = "newest") sort: String,
        @RequestParam(defaultValue = "200") limit: Int,
    ): AdminResponseListResponse {
        val order = when (sort) {
            "newest" -> ResponseSort.NEWEST
            "reason" -> ResponseSort.REASON
            "risk-asc" -> ResponseSort.RISK_ASC
            "risk-desc" -> ResponseSort.RISK_DESC
            else -> throw ApiException.invalidRequest("sort must be newest, reason, risk-asc or risk-desc.")
        }
        if (limit !in 1..MAX_LIMIT) throw ApiException.invalidRequest("limit must be 1-$MAX_LIMIT.")
        return AdminResponseListResponse(store.responses(order, limit), properties.riskThreshold)
    }

    private companion object {
        const val MAX_LIMIT = 500
    }
}
