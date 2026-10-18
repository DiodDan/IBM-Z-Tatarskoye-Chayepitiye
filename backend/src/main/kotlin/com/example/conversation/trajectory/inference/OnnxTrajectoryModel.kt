package com.example.conversation.trajectory.inference

import com.example.conversation.AiB2BSaaSService
import com.example.conversation.error.AnalysisRejected
import com.example.conversation.trajectory.application.TrajectoryModel
import com.example.conversation.trajectory.model.*

class OnnxTrajectoryModel(private val client: AiB2BSaaSService) : TrajectoryModel {
    override val descriptor: ModelDescriptor

    init {
        val manifest = client.modelManifest()
        require(manifest.required("input_mode").asText() == "response_only")
        require(manifest.required("metadata").map { it.asText() } == CompositionMetadata.NAMES)
        descriptor = ModelDescriptor(client.modelFingerprint(), manifest.required("dimension").asInt(),
            manifest.required("max_segment").asInt(), manifest.required("sequence_length").asInt(),
            manifest.required("heads").map { it.asText() })
    }

    override fun encode(assistantResponse: String): EncodedResponse {
        val output = try { client.encodeResponse(assistantResponse) }
            catch (e: AiB2BSaaSService.InputRejected) { throw AnalysisRejected(e.message ?: "Invalid assistant response") }
        val plan = output.plan()
        val diagnostics = ResponseDiagnostics(ResponseCoverage(plan.totalTokens(), plan.totalTokens(), plan.windows().size),
            plan.windows().map { TokenRange(it.start(), it.end()) }, output.peakLogits().toList(), output.peakWindowIndices().toList())
        return EncodedResponse(Embedding(output.vector()), RiskLogits(output.riskLogits()), diagnostics)
    }

    override fun compose(earlier: Embedding, later: Embedding, metadata: CompositionMetadata): CompositionPrediction {
        val output = client.compose(earlier.toFloatArray(), later.toFloatArray(), metadata.toFloatArray())
        return CompositionPrediction(Embedding(output.getValue("vector").single()),
            RiskLogits(output.getValue("risk_logits").single()), output.getValue("association_logit").single().single())
    }
}
