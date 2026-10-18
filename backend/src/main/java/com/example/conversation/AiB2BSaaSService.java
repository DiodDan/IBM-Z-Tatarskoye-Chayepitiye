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
    public static final class InputRejected extends IllegalArgumentException {
        public InputRejected(String message) { super(message); }
    }
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JsonNode manifest;
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

    private final HuggingFaceTokenizer tokenizer, windowTokenizer;
    private final OrtEnvironment environment;
    private final OrtSession encoder, responseAggregator, composer;
    private final int length, dimension, windowTokens, overlapTokens;

    public record TokenWindow(int start, int end, Map<String, long[][]> inputs) {}
    public record WindowPlan(int totalTokens, List<TokenWindow> windows) {
        public WindowPlan { windows = List.copyOf(windows); }
    }
    public record ResponseEncoding(float[] vector, float[] riskLogits, WindowPlan plan,
                                   float[] peakLogits, int[] peakWindowIndices) {
        public ResponseEncoding {
            vector = vector.clone(); riskLogits = riskLogits.clone();
            peakLogits = peakLogits.clone(); peakWindowIndices = peakWindowIndices.clone();
        }
        @Override public float[] vector() { return vector.clone(); }
        @Override public float[] riskLogits() { return riskLogits.clone(); }
        @Override public float[] peakLogits() { return peakLogits.clone(); }
        @Override public int[] peakWindowIndices() { return peakWindowIndices.clone(); }
    }

    public static String sha256(Path file) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[1024 * 1024];
            int n;
            while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Checks the complete export before allocating native sessions; never rewrites checksums. */
    public static JsonNode verifyBundle(Path bundle) throws Exception {
        Path root = bundle.toRealPath();
        Path manifestFile = root.resolve("manifest.json");
        if (isLfsPointer(manifestFile))
            throw new IllegalArgumentException("manifest.json is a Git LFS pointer; run git lfs pull");
        JsonNode manifest = JSON.readTree(manifestFile.toFile());
        if (!manifest.path("format").asText().equals("trajectory-java-v2"))
            throw new IllegalArgumentException("Expected trajectory-java-v2; install a matching window-aware model bundle");
        JsonNode files = manifest.required("files");
        if (!files.isObject() || files.isEmpty())
            throw new IllegalArgumentException("Model manifest must contain nonempty file checksums");
        List<String> problems = new ArrayList<>();
        List<String> required = new ArrayList<>(List.of(
                "encoder.onnx", "response_aggregator.onnx", "composer.onnx", "golden.json"));
        String tokenizer = manifest.required("tokenizer").asText();
        if (tokenizer.isBlank()) throw new IllegalArgumentException("Missing tokenizer path");
        required.add(tokenizer);
        for (String name : required)
            if (!files.has(name)) problems.add(name + ": checksum missing from manifest");
        var entries = files.fields();
        while (entries.hasNext()) {
            var item = entries.next();
            String name = item.getKey();
            Path file = root.resolve(name).normalize();
            if (name.isBlank() || Path.of(name).isAbsolute() || !file.startsWith(root)) {
                problems.add(name + ": unsafe bundle path");
                continue;
            }
            if (!Files.isRegularFile(file)) {
                problems.add(name + ": missing file");
                continue;
            }
            file = file.toRealPath();
            if (!file.startsWith(root)) {
                problems.add(name + ": symlink escapes bundle");
                continue;
            }
            if (isLfsPointer(file)) {
                problems.add(name + ": Git LFS pointer instead of file contents; run git lfs pull");
                continue;
            }
            String expected = item.getValue().asText();
            if (!expected.matches("[0-9a-f]{64}")) {
                problems.add(name + ": invalid SHA-256 in manifest");
                continue;
            }
            String actual = sha256(file);
            if (!actual.equals(expected))
                problems.add(name + ": SHA-256 mismatch; expected=" + expected + "; actual=" + actual);
        }
        if (!problems.isEmpty())
            throw new IllegalArgumentException("Invalid model bundle:\n - " + String.join("\n - ", problems)
                    + "\nInstall all files from the same trajectory-java-v2 export. "
                    + "If the files are present but hashes differ after merging, replace the mixed export; do not edit manifest checksums.");
        return manifest;
    }

    private static boolean isLfsPointer(Path file) throws java.io.IOException {
        try (var input = Files.newInputStream(file)) {
            return new String(input.readNBytes(128), java.nio.charset.StandardCharsets.US_ASCII)
                    .startsWith("version https://git-lfs.github.com/spec/v1");
        }
    }

    public AiB2BSaaSService(Path bundle, int threads) throws Exception {
        if (threads < 1) throw new IllegalArgumentException("threads must be positive");
        Path root = bundle.toRealPath();
        manifest = verifyBundle(root);
        length = manifest.required("sequence_length").asInt();
        dimension = manifest.required("dimension").asInt();
        var contract = manifest.required("response_encoding");
        windowTokens = contract.required("content_tokens_per_window").asInt();
        overlapTokens = contract.required("overlap_tokens").asInt();
        if (!contract.required("kind").asText().equals("windowed_v1")
                || !contract.required("coverage_required").asText().equals("complete")
                || !contract.required("initial_state").asText().equals("first_window_vector")
                || contract.required("response_count_increment").asInt() != 1
                || !contract.required("geometry").equals(JSON.valueToTree(List.of("start_fraction", "end_fraction", "new_token_fraction", "overlap_fraction")))
                || windowTokens <= 0 || overlapTokens < 0 || overlapTokens >= windowTokens
                || !manifest.required("padding_side").asText().equals("right"))
            throw new IllegalArgumentException("Unsupported response window contract");
        if (threads < 1) throw new IllegalArgumentException("threads must be positive");
        environment = OrtEnvironment.getEnvironment();
        OrtSession first = null, aggregate = null, second = null;
        HuggingFaceTokenizer tok = null, windowTok = null;
        try (var options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(threads);
            options.setInterOpNumThreads(1);
            first = environment.createSession(root.resolve("encoder.onnx").toString(), options);
            aggregate = environment.createSession(root.resolve("response_aggregator.onnx").toString(), options);
            second = environment.createSession(root.resolve("composer.onnx").toString(), options);
            validateGraph(first, manifest.required("graphs").required("encoder"));
            validateGraph(aggregate, manifest.required("graphs").required("response_aggregator"));
            validateGraph(second, manifest.required("graphs").required("composer"));
            tok = HuggingFaceTokenizer.newInstance(root.resolve(manifest.required("tokenizer").asText()), Map.of("padding", "false", "truncation", "false"));
            int specialCount = tok.encode("", true, false).getIds().length;
            if (specialCount <= 0 || windowTokens + specialCount > length)
                throw new IllegalArgumentException("Window and special tokens exceed encoder length");
            windowTok = HuggingFaceTokenizer.newInstance(root.resolve(manifest.required("tokenizer").asText()),
                    Map.of("padding", "false", "truncation", "longest_first", "maxLength", Integer.toString(windowTokens + specialCount),
                           "stride", Integer.toString(overlapTokens), "modelMaxLength", Integer.toString(length)));
        } catch (Exception e) {
            if (windowTok != null) windowTok.close();
            if (tok != null) tok.close();
            if (second != null) second.close();
            if (aggregate != null) aggregate.close();
            if (first != null) first.close();
            throw e;
        }
        encoder = first;
        composer = second;
        responseAggregator = aggregate;
        tokenizer = tok;
        windowTokenizer = windowTok;
    }

    public String modelFingerprint() {
        return manifest.required("model_fingerprint").asText();
    }

    public JsonNode modelManifest() {
        return manifest.deepCopy();
    }

    private static void validateGraph(OrtSession session, JsonNode graph) throws Exception {
        validateNodes(session.getInputInfo(), graph.required("inputs"));
        validateNodes(session.getOutputInfo(), graph.required("outputs"));
    }

    private static void validateNodes(Map<String, NodeInfo> actual, JsonNode expected) {
        Set<String> names = new HashSet<>();
        for (JsonNode node : expected) {
            String name = node.required("name").asText();
            names.add(name);
            NodeInfo info = actual.get(name);
            if (info == null || !(info.getInfo() instanceof TensorInfo tensor))
                throw new IllegalArgumentException("Missing tensor: " + name);
            long[] shape = new long[node.required("shape").size()];
            for (int i = 0; i < shape.length; i++) shape[i] = node.required("shape").get(i).asLong();
            if (!Arrays.equals(tensor.getShape(), shape) || !tensor.type.name().equals(node.required("dtype").asText()))
                throw new IllegalArgumentException("Tensor contract mismatch: " + name);
        }
        if (!actual.keySet().equals(names)) throw new IllegalArgumentException("Unexpected tensors in graph");
    }

    /** Both tokenizations use the same pinned native tokenizer, never decoded substrings. */
    public synchronized WindowPlan tokenizeResponse(String text) {
        if (text == null || text.isBlank()) throw new InputRejected("Empty assistant response");
        long[] original = tokenizer.encode(text, false, false).getIds();
        if (original.length == 0) throw new InputRejected("No content tokens");
        var first = windowTokenizer.encode(text, true, true);
        var pieces = new ArrayList<ai.djl.huggingface.tokenizers.Encoding>();
        pieces.add(first); Collections.addAll(pieces, first.getOverflowing());
        var windows = new ArrayList<TokenWindow>();
        int start = 0;
        for (var piece : pieces) {
            int end = Math.min(original.length, Math.addExact(start, windowTokens));
            long[] content = java.util.stream.IntStream.range(0, piece.getIds().length)
                    .filter(i -> piece.getSpecialTokenMask()[i] == 0 && piece.getAttentionMask()[i] != 0)
                    .mapToLong(i -> piece.getIds()[i]).toArray();
            if (start >= end || !Arrays.equals(content, Arrays.copyOfRange(original, start, end)))
                throw new IllegalStateException("Tokenizer windows do not preserve source token coverage");
            windows.add(new TokenWindow(start, end, pad(piece)));
            start = end == original.length ? end : end - overlapTokens;
        }
        if (windows.isEmpty() || windows.getFirst().start() != 0 || windows.getLast().end() != original.length)
            throw new IllegalStateException("Incomplete response token coverage");
        return new WindowPlan(original.length, windows);
    }

    private Map<String, long[][]> pad(ai.djl.huggingface.tokenizers.Encoding e) {
        if (e.getIds().length > length) throw new IllegalStateException("Prepared window exceeds encoder length");
        var values = Map.of("input_ids", e.getIds(), "attention_mask", e.getAttentionMask(), "special_tokens_mask", e.getSpecialTokenMask(), "token_type_ids", e.getTypeIds());
        var result = new LinkedHashMap<String, long[][]>();
        for (var entry : values.entrySet()) {
            long[] padded = new long[length];
            if (entry.getKey().equals("input_ids")) Arrays.fill(padded, manifest.required("pad_token_id").asLong());
            if (entry.getKey().equals("special_tokens_mask")) Arrays.fill(padded, 1);
            System.arraycopy(entry.getValue(), 0, padded, 0, entry.getValue().length);
            result.put(entry.getKey(), new long[][]{padded});
        }
        boolean content = false;
        for (int i = 0; i < length; i++)
            content |= result.get("attention_mask")[0][i] != 0 && result.get("special_tokens_mask")[0][i] == 0;
        if (!content) throw new InputRejected("No content tokens");
        return result;
    }

    /** Complete response -> one vector. Window peaks remain separate evidence. */
    public ResponseEncoding encodeResponse(String text) throws Exception {
        var plan = tokenizeResponse(text);
        float[] state = null, logits = null;
        float[] peaks = new float[10]; Arrays.fill(peaks, Float.NEGATIVE_INFINITY);
        int[] peakIndices = new int[10];
        int previousEnd = 0;
        for (int index = 0; index < plan.windows().size(); index++) {
            var window = plan.windows().get(index);
            var encoded = encodeTokens(window.inputs());
            var vector = encoded.get("vector")[0];
            var atomicLogits = encoded.get("risk_logits")[0];
            for (int head = 0; head < peaks.length; head++) {
                if (atomicLogits[head] > peaks[head]) { peaks[head] = atomicLogits[head]; peakIndices[head] = index; }
            }
            if (state == null) { state = vector; logits = atomicLogits; }
            else {
                float[] geometry = {(float)(window.start() / (double)plan.totalTokens()),
                    (float)(window.end() / (double)plan.totalTokens()),
                    (float)((window.end() - previousEnd) / (double)plan.totalTokens()),
                    (float)(Math.max(0, previousEnd - window.start()) / (double)(window.end() - window.start()))};
                var composed = aggregateResponse(state, vector, geometry);
                state = composed.get("vector")[0]; logits = composed.get("risk_logits")[0];
            }
            previousEnd = window.end();
        }
        return new ResponseEncoding(state, logits, plan, peaks, peakIndices);
    }

    public Map<String, float[][]> aggregateResponse(float[] earlier, float[] later, float[] geometry) throws Exception {
        validate(earlier, dimension); validate(later, dimension); validate(geometry, 4);
        return run(responseAggregator, Map.of("earlier", new float[][]{earlier}, "later", new float[][]{later}, "geometry", new float[][]{geometry}));
    }

    public Map<String, float[][]> encodeTokens(Map<String, long[][]> values) throws Exception {
        return run(encoder, values);
    }

    /**
     * Returns vector, risk logits, association logit. Caller must validate member chronology and model identity.
     */
    public Map<String, float[][]> compose(float[] earlier, float[] later, float[] metadata) throws Exception {
        validate(earlier, dimension);
        validate(later, dimension);
        validate(metadata, 6);
        return run(composer, Map.of("earlier", new float[][]{earlier}, "later", new float[][]{later}, "metadata", new float[][]{metadata}));
    }

    private static void validate(float[] value, int n) {
        if (value.length != n) throw new IllegalArgumentException("Wrong vector length");
        for (float x : value) if (!Float.isFinite(x)) throw new IllegalArgumentException("Nonfinite input");
    }

    private Map<String, float[][]> run(OrtSession session, Map<String, ?> values) throws Exception {
        var inputs = new LinkedHashMap<String, OnnxTensor>();
        try {
            for (String name : session.getInputNames())
                inputs.put(name, OnnxTensor.createTensor(environment, Objects.requireNonNull(values.get(name), name)));
            try (var result = session.run(inputs)) {
                var outputs = new LinkedHashMap<String, float[][]>();
                for (String name : session.getOutputNames()) {
                    Object value = result.get(name).orElseThrow().getValue();
                    if (!(value instanceof float[][] matrix)) throw new IllegalStateException("Expected rank-two float output: " + name);
                    for (float[] row : matrix) for (float element : row)
                        if (!Float.isFinite(element)) throw new IllegalStateException("Nonfinite model output: " + name);
                    outputs.put(name, matrix);
                }
                return Collections.unmodifiableMap(outputs);
            }
        } finally {
            for (var tensor : inputs.values()) tensor.close();
        }
    }

    public void close() throws Exception {
        try {
            tokenizer.close();
            windowTokenizer.close();
            try {
                encoder.close();
            } finally {
                try { responseAggregator.close(); } finally { composer.close(); }
            }
        } finally {
            if (extractedResources != null) deleteDirectory(extractedResources);
        }
    }
}
