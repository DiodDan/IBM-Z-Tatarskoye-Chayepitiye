package com.example.conversation.conversation.application

import com.example.conversation.config.ChatProperties
import org.springframework.stereotype.Component

@Component
class ConversationTitleFactory(private val properties: ChatProperties) {

    private val whitespace = Regex("\\s+")

    /** Collapse whitespace, trim, keep the first 80 Unicode code points (emoji-safe). */
    fun fromFirstMessage(content: String): String {
        val normalized = content.replace(whitespace, " ").trim()
        val codePoints = normalized.codePoints().limit(properties.maxTitleCodePoints.toLong()).toArray()
        return String(codePoints, 0, codePoints.size)
    }
}
