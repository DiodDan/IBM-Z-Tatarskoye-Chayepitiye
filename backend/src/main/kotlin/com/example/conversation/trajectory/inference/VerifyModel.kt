package com.example.conversation.trajectory.inference

import com.example.conversation.AiB2BSaaSService
import com.example.conversation.error.AnalysisRejected
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Path
import java.nio.file.Files
import java.time.Instant
import kotlin.math.abs

/** Export parity check. Golden values are numerical fixtures, not evidence of classifier quality. */
object VerifyModel {
    @JvmStatic
    fun main(args: Array<String>) {
        check(!java.lang.Boolean.getBoolean("trajectory.requireS390x") || System.getProperty("os.arch") == "s390x") { "Verification requires an s390x JVM" }
        val report = Path.of(System.getProperty("trajectory.verificationReport", "build/reports/trajectory/verification.json"))
        Files.createDirectories(report.toAbsolutePath().parent)
        Files.deleteIfExists(report)
        val bundle = args.getOrElse(0) { "classpath:trajectory" }
        val threads = args.getOrElse(1) { "3" }.toInt()
        val client = if (bundle.startsWith("classpath:")) AiB2BSaaSService.fromResources(bundle.removePrefix("classpath:"), threads)
            else AiB2BSaaSService(Path.of(bundle), threads)
        val json = ObjectMapper()
        val golden = if (bundle.startsWith("classpath:"))
            checkNotNull(javaClass.classLoader.getResourceAsStream(bundle.removePrefix("classpath:") + "/golden.json")).use { json.readTree(it) }
        else json.readTree(Path.of(bundle).resolve("golden.json").toFile())
        client.use {
            val count = verify(it, golden)
            json.writerWithDefaultPrettyPrinter().writeValue(report.toFile(), mapOf("passed" to true,
                "generated_utc" to Instant.now().toString(), "coordinates_checked" to count,
                "backend" to "onnxruntime-java", "format" to "trajectory-java-v2", "model_fingerprint" to it.modelFingerprint(),
                "threads" to threads, "architecture" to System.getProperty("os.arch"), "java" to System.getProperty("java.version"),
                "quality_acceptance" to false))
        }
    }

    fun verify(client: AiB2BSaaSService, golden: JsonNode): Int {
        val typed = OnnxTrajectoryModel(client)
        val tolerance = client.modelManifest().required("tolerances")
        val atol = tolerance.required("atol").asDouble()
        val rtol = tolerance.required("rtol").asDouble()
        var checked = 0
        fun compare(actual: FloatArray, expected: JsonNode) {
            check(actual.size == expected.size())
            actual.forEachIndexed { i, value ->
                val reference = expected[i].asDouble()
                check(value.isFinite() && abs(value - reference) <= atol + rtol * abs(reference)) { "Golden output mismatch at coordinate $i" }
                checked++
            }
        }
        for (case in golden.required("responses")) {
            val text = case.required("text").asText()
            val plan = client.tokenizeResponse(text)
            check(plan.totalTokens() == case.required("coverage").required("total_tokens").asInt())
            check(plan.windows().size == case.required("windows").size())
            val windowLogits = mutableListOf<FloatArray>()
            plan.windows().forEachIndexed { index, window ->
                val expected = case.required("windows")[index]
                check(window.start() == expected.required("range")[0].asInt() && window.end() == expected.required("range")[1].asInt())
                for ((name, actual) in window.inputs()) {
                    val tokens = expected.required("inputs").required(name)[0].map { it.asLong() }.toLongArray()
                    check(actual.single().contentEquals(tokens)) { "Tokenizer mismatch: window=$index input=$name" }
                }
                val encoded = client.encodeTokens(window.inputs())
                windowLogits.add(encoded.getValue("risk_logits").single())
                compare(encoded.getValue("vector").single(), expected.required("vector")[0])
                compare(encoded.getValue("risk_logits").single(), expected.required("risk_logits")[0])
            }
            val output = typed.encode(text)
            compare(output.embedding.toFloatArray(), case.required("vector")[0])
            compare(output.riskLogits.toFloatArray(), case.required("risk_logits")[0])
            val diagnostics = checkNotNull(output.diagnostics)
            check(diagnostics.coverage.processedTokens == plan.totalTokens())
            for (head in 0 until 10) {
                val best = windowLogits.withIndex().maxBy { it.value[head] }
                check(diagnostics.peakWindowIndices[head] == best.index)
            }
            val expectedPeaks = ObjectMapper().valueToTree<JsonNode>((0 until 10).map { head ->
                case.required("windows").maxOf { it.required("risk_logits")[0][head].asDouble() } })
            compare(diagnostics.peakLogits.toFloatArray(), expectedPeaks)
        }
        val composition = golden.required("composition")
        fun input(name: String) = composition.required("inputs").required(name)[0].map { it.asDouble().toFloat() }.toFloatArray()
        val composed = client.compose(input("earlier"), input("later"), input("metadata"))
        for (name in listOf("vector", "risk_logits", "association_logit")) {
            compare(composed.getValue(name).single(), composition.required("outputs").required(name)[0])
        }
        check(golden.required("responses").any { it.required("coverage").required("total_tokens").asInt() > typed.descriptor.sequenceLength })
        for (text in listOf("", " ", "\n\t")) check(runCatching { typed.encode(text) }.exceptionOrNull() is AnalysisRejected)
        println("ONNX v2 verification passed: $checked coordinates; complete response windows, aggregator and composer; model=${typed.descriptor.fingerprint}")
        return checked
    }
}
