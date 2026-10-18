package com.example.conversation;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.*;
import com.fasterxml.jackson.databind.*;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * One long-lived instance per application; text is not retained after encode().
 */
public final class AiB2BSaaSService implements AutoCloseable {
    public static final ObjectMapper JSON = new ObjectMapper();
    public final JsonNode manifest;
    private Path extractedResources;

    /**
     * Works in an IDE and inside a Spring Boot jar; streams models to temporary disk.
     */
    public static AiB2BSaaSService fromResources(String prefix, int threads) throws Exception {
        if (!prefix.endsWith("/")) prefix += "/";
        Path temp = Files.createTempDirectory("trajectory-model-");
        try {
            copyResource(prefix + "manifest.json", temp.resolve("manifest.json"));
            var manifest = JSON.readTree(temp.resolve("manifest.json").toFile());
            var names = manifest.required("files").fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                Path target = temp.resolve(name).normalize();
                if (!target.startsWith(temp)) throw new IllegalArgumentException("Unsafe resource path");
                Files.createDirectories(target.getParent());
                copyResource(prefix + name, target);
            }
            var service = new AiB2BSaaSService(temp, threads);
            service.extractedResources = temp;
            return service;
        } catch (Exception e) {
            try {
                deleteDirectory(temp);
            } catch (Exception cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }

    private static void copyResource(String name, Path target) throws Exception {
        try (var input = AiB2BSaaSService.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) throw new IllegalArgumentException("Missing classpath resource: " + name);
            Files.copy(input, target);
        }
    }

    private static void deleteDirectory(Path root) throws java.io.IOException {
        try (var paths = Files.walk(root)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }

    private final HuggingFaceTokenizer tokenizer;
    private final OrtEnvironment environment;
    private final OrtSession encoder, composer;
    private final int length, dimension;

    public static String sha256(Path file) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[1024 * 1024];
            int n;
            while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public AiB2BSaaSService(Path bundle, int threads) throws Exception {
        Path root = bundle.toRealPath();
        manifest = JSON.readTree(root.resolve("manifest.json").toFile());
        if (!manifest.path("format").asText().equals("trajectory-java-v1"))
            throw new IllegalArgumentException("Unsupported bundle format");
        var files = manifest.required("files").fields();
        while (files.hasNext()) {
            var item = files.next();
            Path file = root.resolve(item.getKey()).toRealPath();
            if (!file.startsWith(root) || !sha256(file).equals(item.getValue().asText()))
                throw new IllegalArgumentException("Corrupt bundle: " + item.getKey());
        }
        length = manifest.required("sequence_length").asInt();
        dimension = manifest.required("dimension").asInt();
        if (threads < 1) throw new IllegalArgumentException("threads must be positive");
        environment = OrtEnvironment.getEnvironment();
        OrtSession first = null, second = null;
        HuggingFaceTokenizer tok = null;
        try (var options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(threads);
            options.setInterOpNumThreads(1);
            first = environment.createSession(root.resolve("encoder.onnx").toString(), options);
            second = environment.createSession(root.resolve("composer.onnx").toString(), options);
            tok = HuggingFaceTokenizer.newInstance(root.resolve(manifest.required("tokenizer").asText()), Map.of("padding", "false", "truncation", "false"));
        } catch (Exception e) {
            if (tok != null) tok.close();
            if (second != null) second.close();
            if (first != null) first.close();
            throw e;
        }
        encoder = first;
        composer = second;
        tokenizer = tok;
    }

    public String modelFingerprint() {
        return manifest.required("model_fingerprint").asText();
    }

    public synchronized Map<String, long[][]> tokenize(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Empty assistant response");
        var e = tokenizer.encode(text);
        if (e.getIds().length > length)
            throw new IllegalArgumentException("Input exceeds token budget; no silent truncation");
        var values = Map.of("input_ids", e.getIds(), "attention_mask", e.getAttentionMask(), "special_tokens_mask", e.getSpecialTokenMask(), "token_type_ids", e.getTypeIds());
        var result = new LinkedHashMap<String, long[][]>();
        int offset = manifest.path("padding_side").asText("right").equals("left") ? length - e.getIds().length : 0;
        for (var entry : values.entrySet()) {
            long[] padded = new long[length];
            if (entry.getKey().equals("input_ids")) Arrays.fill(padded, manifest.required("pad_token_id").asLong());
            if (entry.getKey().equals("special_tokens_mask")) Arrays.fill(padded, 1);
            System.arraycopy(entry.getValue(), 0, padded, offset, entry.getValue().length);
            result.put(entry.getKey(), new long[][]{padded});
        }
        boolean content = false;
        for (int i = 0; i < length; i++)
            content |= result.get("attention_mask")[0][i] != 0 && result.get("special_tokens_mask")[0][i] == 0;
        if (!content) throw new IllegalArgumentException("No content tokens");
        return result;
    }

    public float[][][] encode(String text) throws Exception {
        return encodeTokens(tokenize(text));
    }

    public float[][][] encodeTokens(Map<String, long[][]> values) throws Exception {
        return run(encoder, values);
    }

    /**
     * Returns vector, risk logits, association logit. Caller must validate member chronology and model identity.
     */
    public float[][][] compose(float[] earlier, float[] later, float[] metadata) throws Exception {
        validate(earlier, dimension);
        validate(later, dimension);
        validate(metadata, 6);
        return run(composer, Map.of("earlier", new float[][]{earlier}, "later", new float[][]{later}, "metadata", new float[][]{metadata}));
    }

    private static void validate(float[] value, int n) {
        if (value.length != n) throw new IllegalArgumentException("Wrong vector length");
        for (float x : value) if (!Float.isFinite(x)) throw new IllegalArgumentException("Nonfinite input");
    }

    private float[][][] run(OrtSession session, Map<String, ?> values) throws Exception {
        var inputs = new LinkedHashMap<String, OnnxTensor>();
        try {
            for (String name : session.getInputNames())
                inputs.put(name, OnnxTensor.createTensor(environment, Objects.requireNonNull(values.get(name), name)));
            try (var result = session.run(inputs)) {
                float[][][] outputs = new float[result.size()][][];
                for (int i = 0; i < outputs.length; i++) outputs[i] = (float[][]) result.get(i).getValue();
                return outputs;
            }
        } finally {
            for (var tensor : inputs.values()) tensor.close();
        }
    }

    public void close() throws Exception {
        try {
            tokenizer.close();
            try {
                encoder.close();
            } finally {
                composer.close();
            }
        } finally {
            if (extractedResources != null) deleteDirectory(extractedResources);
        }
    }
}
