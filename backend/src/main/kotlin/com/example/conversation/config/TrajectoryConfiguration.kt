package com.example.conversation.config

import com.example.conversation.AiB2BSaaSService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TrajectoryConfiguration {
    @Bean(destroyMethod = "close")
    fun aiB2BSaaSService(): AiB2BSaaSService =
        AiB2BSaaSService.fromResources("trajectory", 3)
}

// Inject AiB2BSaaSService into your service:
// val atomic = model.encode(assistantResponse)
// val vector = atomic[0][0]       // 256 float values
// val riskLogits = atomic[1][0]   // overall + category heads, NOT probabilities
// Persist vector + model.modelFingerprint() + member positions/timestamps.
// After checking chronology, same session/model, disjoint membership and max_segment:
// val combined = model.compose(previousVector, vector, metadata)
// combined[0][0]: new vector; [1][0]: risk logits; [2][0][0]: association logit.
// Do not retain/log assistantResponse if vector-only storage is required.
