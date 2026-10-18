package com.example.conversation.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.io.Resource
import java.time.Duration

@ConfigurationProperties("app.trajectory")
data class TrajectoryProperties(
    val enabled: Boolean = true,
    val resourcePrefix: String = "trajectory",
    val threads: Int = 3,
    val linkThreshold: Float = 0.8f,
    val maxActive: Int = 64,
    val maxResponsesPerRequest: Int = 32,
    val ttl: Duration = Duration.ofHours(24),
    val calibration: Resource? = null,
) {
    init {
        require(resourcePrefix.isNotBlank() && threads > 0 && maxActive in 1..10_000)
        require(maxResponsesPerRequest in 1..10_000)
        require(linkThreshold.isFinite() && linkThreshold in 0f..1f)
        require(!ttl.isNegative && !ttl.isZero)
    }
}
