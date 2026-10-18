package com.example.conversation.admin

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/** Values from `app.admin.*` in application.yaml. */
@ConfigurationProperties("app.admin")
data class AdminProperties(
    /** Prompts scoring at or above this are flagged as high risk on the admin page. */
    val riskThreshold: Double = 0.5,
) {
    init {
        require(riskThreshold in 0.0..1.0) { "app.admin.risk-threshold must be between 0 and 1" }
    }
}

@Configuration
@EnableConfigurationProperties(AdminProperties::class)
class AdminConfig
