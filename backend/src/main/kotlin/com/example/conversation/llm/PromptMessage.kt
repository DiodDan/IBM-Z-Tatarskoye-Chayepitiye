package com.example.conversation.llm

enum class PromptRole { SYSTEM, USER, ASSISTANT }

/** One prompt line, independent of Spring AI and of the HTTP DTOs. */
data class PromptMessage(val role: PromptRole, val content: String)
