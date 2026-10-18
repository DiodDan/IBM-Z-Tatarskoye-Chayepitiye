package com.example.conversation.config

import com.example.conversation.AiB2BSaaSService
import com.example.conversation.trajectory.application.*
import com.example.conversation.trajectory.inference.OnnxTrajectoryModel
import com.example.conversation.trajectory.model.ModelDescriptor
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(TrajectoryProperties::class)
@ConditionalOnProperty(prefix = "app.trajectory", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class TrajectoryConfiguration {
    @Bean(destroyMethod = "close")
    fun onnxClient(properties: TrajectoryProperties): AiB2BSaaSService =
        AiB2BSaaSService.fromResources(properties.resourcePrefix, properties.threads)

    @Bean
    fun trajectoryModel(client: AiB2BSaaSService): TrajectoryModel = OnnxTrajectoryModel(client)

    @Bean
    fun scoreCalibration(model: TrajectoryModel, properties: TrajectoryProperties, json: ObjectMapper): ScoreCalibration {
        val resource = properties.calibration ?: run {
            LoggerFactory.getLogger(javaClass).warn("Trajectory analysis uses uncalibrated sigmoid outputs; risk acceptance is not configured")
            return ScoreCalibration.uncalibrated(model.descriptor)
        }
        val root = resource.inputStream.use { json.readTree(it) }
        val fingerprint = root.required("model_fingerprint").asText()
        require(fingerprint == model.descriptor.fingerprint) { "Calibration belongs to another model release" }
        fun affine(node: com.fasterxml.jackson.databind.JsonNode) = AffineCalibration(
            node.required("slope").asDouble(), node.required("intercept").asDouble())
        val categories = root.required("categories")
        require(categories.fieldNames().asSequence().toSet() == ModelDescriptor.CATEGORIES.toSet())
        val risk = listOf(affine(root.required("overall"))) + ModelDescriptor.CATEGORIES.map { affine(categories.required(it)) }
        return ScoreCalibration(root.required("id").asText(), fingerprint, true, risk, affine(root.required("association")))
    }

    @Bean
    fun trajectoryEvaluator(model: TrajectoryModel, calibration: ScoreCalibration, properties: TrajectoryProperties) =
        TrajectoryEvaluator(model, calibration, properties.linkThreshold)
}
