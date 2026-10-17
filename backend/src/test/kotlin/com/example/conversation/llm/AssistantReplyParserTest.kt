package com.example.conversation.llm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AssistantReplyParserTest {

    private val parser = AssistantReplyParser()

    @Test
    fun `strips the suggestions line and splits it`() {
        val reply = parser.parse("2 + 2 = 4.\n\n[[suggestions]] Explain again | Show example | Related facts")
        assertEquals("2 + 2 = 4.", reply.text)
        assertEquals(listOf("Explain again", "Show example", "Related facts"), reply.suggestions)
    }

    @Test
    fun `accepts a bare bracketed list on the last line`() {
        val reply = parser.parse("The capital of France is Paris.\n\n[[Show me the population | What is the currency]]")
        assertEquals("The capital of France is Paris.", reply.text)
        assertEquals(listOf("Show me the population", "What is the currency"), reply.suggestions)
    }

    @Test
    fun `no line means no chips and untouched text`() {
        val reply = parser.parse("  Just an answer.  ")
        assertEquals("Just an answer.", reply.text)
        assertEquals(emptyList<String>(), reply.suggestions)
    }

    @Test
    fun `drops overlong chips and keeps at most three`() {
        val long = "x".repeat(41)
        val reply = parser.parse("Answer\n[[suggestions]] a | $long | b | c | d")
        assertEquals(listOf("a", "b", "c"), reply.suggestions)
    }
}
