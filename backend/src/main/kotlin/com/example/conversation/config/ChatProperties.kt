package com.example.conversation.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.io.Resource

/** Values from `app.chat.*` in application.yaml. */
@ConfigurationProperties("app.chat")
data class ChatProperties(
    /** Last N complete user+assistant exchanges sent to the model. */
    val contextExchanges: Int = 10,
    /** Approximate prompt budget in characters (not tokens). */
    val maxPromptChars: Int = 24_000,
    val maxMessageCodePoints: Int = 20_000,
    val maxTitleCodePoints: Int = 80,
    val systemPrompt: Resource,
) {
    init {
        require(contextExchanges in 0..10_000 && maxPromptChars > 0 && maxMessageCodePoints > 0)
        require(maxTitleCodePoints in 1..80)
    }
}
