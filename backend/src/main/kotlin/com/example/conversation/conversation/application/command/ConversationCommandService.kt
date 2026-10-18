package com.example.conversation.conversation.application.command

import com.example.conversation.config.ChatProperties
import com.example.conversation.conversation.application.ConversationMutationGuard
import com.example.conversation.conversation.application.ConversationStore
import com.example.conversation.conversation.application.ConversationTitleFactory
import com.example.conversation.conversation.application.dto.ConversationPreview
import com.example.conversation.conversation.application.dto.MessageExchange
import com.example.conversation.error.ApiException
import com.example.conversation.llm.AssistantReply
import com.example.conversation.llm.AssistantReplyParser
import com.example.conversation.llm.LlmGateway
import com.example.conversation.llm.PromptFactory
import com.example.conversation.llm.PromptMessage
import com.example.conversation.risk.PromptRiskAssessment
import com.example.conversation.risk.PromptRiskClassifier
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Orchestrates create / send / rename / delete. Order matters:
 *   validate -> check ownership -> lock -> read (short tx) -> risk score + LLM (NO tx) -> write (short tx) -> unlock
 * If the LLM fails, nothing is written.
 */
@Service
class ConversationCommandService(
    private val store: ConversationStore,
    private val guard: ConversationMutationGuard,
    private val titleFactory: ConversationTitleFactory,
    private val promptFactory: PromptFactory,
    private val llm: LlmGateway,
    private val replyParser: AssistantReplyParser,
    private val riskClassifier: PromptRiskClassifier,
    private val properties: ChatProperties,
) {

    fun create(owner: UUID, rawContent: String): MessageExchange {
        val content = validateContent(rawContent)
        val title = titleFactory.fromFirstMessage(content)
        val risk = assessRisk(content)
        val reply = generateReply(promptFactory.build(history = emptyList(), currentInput = content))
        return store.createWithExchange(owner, title, content, risk, reply)
    }

    fun send(owner: UUID, conversationId: UUID, rawContent: String): MessageExchange {
        val content = validateContent(rawContent)
        // Check ownership BEFORE the lock, so someone else's busy conversation is still a 404.
        ensureOwned(owner, conversationId)
        return guard.runExclusive(conversationId) {
            val context = store.readContext(owner, conversationId, properties.contextExchanges * 2)
            val risk = assessRisk(content)
            val reply = generateReply(promptFactory.build(context.recentMessages, content))
            store.appendExchange(owner, conversationId, context.maxSequence, content, risk, reply)
        }
    }

    fun rename(owner: UUID, conversationId: UUID, rawTitle: String): ConversationPreview {
        val title = rawTitle.trim()
        val length = title.codePointCount(0, title.length)
        if (title.isEmpty() || length > properties.maxTitleCodePoints) {
            throw ApiException.invalidRequest("Title must be 1-${properties.maxTitleCodePoints} characters.")
        }
        ensureOwned(owner, conversationId)
        return guard.runExclusive(conversationId) { store.rename(owner, conversationId, title) }
    }

    fun delete(owner: UUID, conversationId: UUID) {
        ensureOwned(owner, conversationId)
        guard.runExclusive(conversationId) { store.delete(owner, conversationId) }
    }

    /** Out-of-range or non-finite scores are dropped (stored as "not classified") instead of failing the send. */
    private fun assessRisk(content: String): PromptRiskAssessment? =
        riskClassifier.classify(content)
            ?.takeIf { it.score.isFinite() && it.score in 0.0..1.0 }
            ?.let { it.copy(reason = it.reason?.trim()?.takeIf(String::isNotEmpty)) }

    /** Calls the model and records how long it took (shown on the admin page as "Timing"). */
    private fun generateReply(prompt: List<PromptMessage>): AssistantReply {
        val started = System.nanoTime()
        val raw = llm.generate(prompt)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        return replyParser.parse(raw).copy(responseTimeMs = elapsedMs)
    }

    private fun ensureOwned(owner: UUID, conversationId: UUID) {
        if (!store.isOwned(owner, conversationId)) throw ApiException.conversationNotFound()
    }

    /** Trim, non-blank, at most 20,000 Unicode code points (not UTF-16 units). */
    private fun validateContent(raw: String): String {
        val content = raw.trim()
        if (content.isEmpty()) throw ApiException.invalidRequest("Message must not be empty.")
        if (content.codePointCount(0, content.length) > properties.maxMessageCodePoints) {
            throw ApiException.invalidRequest("Message is too long.")
        }
        return content
    }
}
