package com.example.conversation.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Builds the Spring AI ChatClient for OpenRouter (OpenAI-compatible API).
 * Timeouts come from `spring.http.client.*` in application.yaml.
 * Not created with the `echo` profile, where no model is configured.
 */
@Configuration
@EnableConfigurationProperties(ChatProperties::class)
class AiConfig {

    @Bean
    @ConditionalOnProperty(name = ["app.llm.provider"], havingValue = "openrouter", matchIfMissing = true)
    fun chatClient(builder: ChatClient.Builder): ChatClient = builder.build()
}
