package com.example.conversation.admin

import com.example.conversation.admin.AdminConversationStore.Companion.escapeLike
import com.example.conversation.admin.AdminConversationStore.Companion.responseNumberIn
import com.example.conversation.admin.AdminConversationStore.Companion.searchTerm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AdminSearchTest {

    @Test
    fun `recognises pasted response numbers`() {
        assertEquals(12L, responseNumberIn("Response №12"))
        assertEquals(12L, responseNumberIn("  №12 "))
        assertEquals(12L, responseNumberIn("#12"))
        assertEquals(12L, responseNumberIn("12"))
        assertEquals(12L, responseNumberIn("response no. 12"))
        assertNull(responseNumberIn("what is 12?"))
    }

    @Test
    fun `searches the longest line of pasted text`() {
        val pasted = "Paris\n\nThe capital of France is Paris, on the Seine.\n"
        assertEquals("The capital of France is Paris, on the Seine.", searchTerm(pasted))
        assertEquals("cut off preview", searchTerm("cut off preview…"))
        assertNull(searchTerm("   \n  "))
    }

    @Test
    fun `escapes LIKE wildcards`() {
        assertEquals("100\\% sure\\_x", escapeLike("100% sure_x"))
    }
}
