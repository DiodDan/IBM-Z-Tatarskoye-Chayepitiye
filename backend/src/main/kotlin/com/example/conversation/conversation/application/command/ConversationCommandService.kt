package com.example.conversation.conversation.application.command

import com.example.conversation.config.ChatProperties
import com.example.conversation.conversation.application.ConversationMutationGuard
import com.example.conversation.conversation.application.ConversationReader
import com.example.conversation.conversation.application.ConversationWriter
import com.example.conversation.conversation.application.ConversationTitleFactory
import com.example.conversation.conversation.application.dto.ConversationPreview
import com.example.conversation.conversation.application.dto.MessageExchange
import com.example.conversation.error.InvalidInput
import com.example.conversation.llm.LlmGateway
import com.example.conversation.llm.PromptFactory
import com.example.conversation.llm.AssistantReply
import com.example.conversation.llm.AssistantReplyParser
import com.example.conversation.llm.PromptMessage
import com.example.conversation.risk.PromptRiskAssessment
import com.example.conversation.risk.PromptRiskClassifier
import com.example.conversation.trajectory.application.ChatResponseAnalyzer
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Orchestrates create / send / rename / delete. Order matters:
 *   validate -> check ownership -> lock -> read (short tx) -> LLM -> write (short tx) -> analyse -> unlock
 * LLM and model inference run outside database transactions.
 * If the LLM fails, nothing is written.
 */
@Service
class ConversationCommandService(
    private val reader: ConversationReader,
    private val writer: ConversationWriter,
    private val guard: ConversationMutationGuard,
    private val titleFactory: ConversationTitleFactory,
    private val promptFactory: PromptFactory,
    private val llm: LlmGateway,
    private val properties: ChatProperties,
    private val analysis: ChatResponseAnalyzer,
    private val replyParser: AssistantReplyParser,
    private val riskClassifier: PromptRiskClassifier,
) {

    fun create(owner: UUID, rawContent: String): MessageExchange {
        val content = validateContent(rawContent)
        val title = titleFactory.fromFirstMessage(content)
        val conversationId = UUID.randomUUID()
        return guard.runExclusive(conversationId) {
            val risk = assessRisk(content)
            val reply = generateReply(promptFactory.build(history = emptyList(), currentInput = content))
            analyse(owner, writer.createWithExchange(owner, title, content, reply.text, conversationId,
                risk = risk, suggestions = reply.suggestions, responseTimeMs = reply.responseTimeMs))
        }
    }

    fun send(owner: UUID, conversationId: UUID, rawContent: String): MessageExchange {
        val content = validateContent(rawContent)
        // Check ownership BEFORE the lock, so someone else's busy conversation is still a 404.
        reader.requireOwned(owner, conversationId)
        return guard.runExclusive(conversationId) {
            val context = reader.readReplyContext(owner, conversationId, properties.contextExchanges)
            val risk = assessRisk(content)
            val reply = generateReply(promptFactory.build(context.recentExchanges, content))
            analyse(owner, writer.appendExchange(owner, conversationId, context.version, content, reply.text,
                risk = risk, suggestions = reply.suggestions, responseTimeMs = reply.responseTimeMs))
        }
    }

    private fun analyse(owner: UUID, exchange: MessageExchange): MessageExchange = exchange.copy(
        assistantMessage = exchange.assistantMessage.copy(analysis = analysis.analyse(owner, exchange.assistantMessage.id)),
    )

    private fun assessRisk(content: String): PromptRiskAssessment? =
        riskClassifier.classify(content)
            ?.takeIf { it.score.isFinite() && it.score in 0.0..1.0 }
            ?.let { it.copy(reason = it.reason?.trim()?.takeIf(String::isNotEmpty)) }

    private fun generateReply(prompt: List<PromptMessage>): AssistantReply {
        val started = System.nanoTime()
        val raw = llm.generate(prompt)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        return replyParser.parse(raw).copy(responseTimeMs = elapsedMs)
    }

    fun rename(owner: UUID, conversationId: UUID, rawTitle: String): ConversationPreview {
        val title = rawTitle.trim()
        val length = title.codePointCount(0, title.length)
        if (title.isEmpty() || length > properties.maxTitleCodePoints) {
            throw InvalidInput("Title must be 1-${properties.maxTitleCodePoints} characters.")
        }
        reader.requireOwned(owner, conversationId)
        return guard.runExclusive(conversationId) { writer.rename(owner, conversationId, title) }
    }

    fun delete(owner: UUID, conversationId: UUID) {
        reader.requireOwned(owner, conversationId)
        guard.runExclusive(conversationId) { writer.delete(owner, conversationId) }
    }

    /** Trim, non-blank, at most 20,000 Unicode code points (not UTF-16 units). */
    private fun validateContent(raw: String): String {
        val content = raw.trim()
        if (content.isEmpty()) throw InvalidInput("Message must not be empty.")
        if (content.codePointCount(0, content.length) > properties.maxMessageCodePoints) {
            throw InvalidInput("Message is too long.")
        }
        return content
    }
}
