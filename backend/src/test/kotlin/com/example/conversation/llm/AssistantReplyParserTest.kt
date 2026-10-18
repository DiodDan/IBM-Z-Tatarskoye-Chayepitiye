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

    @Test
    fun `accepts individually bracketed suggestions from later model replies`() {
        val reply = parser.parse("## More details\n\nUseful answer.\n[[read more stories] | explore genres | get recommendations ]")
        assertEquals("## More details\n\nUseful answer.", reply.text)
        assertEquals(listOf("read more stories", "explore genres", "get recommendations"), reply.suggestions)
    }

    @Test
    fun `does not treat a marker inside an unclosed code block as actions`() {
        val text = "Example:\n```text\n[[suggestions]] Explain again | More details"
        assertEquals(text, parser.parse(text).text)
        assertEquals(emptyList<String>(), parser.parse(text).suggestions)
    }

    @Test
    fun `keeps earlier marker examples and extracts only final followups`() {
        val text = "Example:\n```text\n[[suggestions]] First | Second\n```\n\n[[suggestions]] Actual follow-up | Next example"
        val reply = parser.parse(text)
        assertEquals("Example:\n```text\n[[suggestions]] First | Second\n```", reply.text)
        assertEquals(listOf("Actual follow-up", "Next example"), reply.suggestions)
    }

    @Test
    fun `extracts the real second response suffix appended to the final paragraph`() {
        val text = "A community garden is a shared space.\n\nFeel free to ask! [[more information] | additional resources | local examples | gardening tips]"
        val reply = parser.parse(text)
        assertEquals("A community garden is a shared space.\n\nFeel free to ask!", reply.text)
        assertEquals(listOf("more information", "additional resources", "local examples"), reply.suggestions)
    }
}
