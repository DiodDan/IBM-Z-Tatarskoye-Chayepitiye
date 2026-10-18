package com.example.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelBundleVerificationTest {
    @TempDir Path root;
    private final ObjectMapper json = new ObjectMapper();

    private ObjectNode bundle() throws Exception {
        ObjectNode manifest = json.createObjectNode();
        manifest.put("format", "trajectory-java-v2");
        manifest.put("tokenizer", "tokenizer/tokenizer.json");
        ObjectNode files = manifest.putObject("files");
        for (String name : List.of("encoder.onnx", "response_aggregator.onnx", "composer.onnx", "golden.json", "tokenizer/tokenizer.json")) {
            Path file = root.resolve(name);
            Files.createDirectories(file.getParent());
            Files.writeString(file, "fixture for " + name);
            files.put(name, AiB2BSaaSService.sha256(file));
        }
        save(manifest);
        return manifest;
    }

    private void save(ObjectNode manifest) throws Exception {
        json.writeValue(root.resolve("manifest.json").toFile(), manifest);
    }

    @Test void verifiesFileIntegrityWithoutLoadingNativeModels() throws Exception {
        ObjectNode manifest = bundle();
        assertEquals(manifest, AiB2BSaaSService.verifyBundle(root));
    }

    @Test void reportsEveryMismatchedModelWithoutChangingManifest() throws Exception {
        bundle();
        String before = Files.readString(root.resolve("manifest.json"));
        Files.writeString(root.resolve("encoder.onnx"), "old encoder");
        Files.writeString(root.resolve("composer.onnx"), "old composer");
        var error = assertThrows(IllegalArgumentException.class, () -> new AiB2BSaaSService(root, 1));
        assertTrue(error.getMessage().contains("encoder.onnx: SHA-256 mismatch"));
        assertTrue(error.getMessage().contains("composer.onnx: SHA-256 mismatch"));
        assertTrue(error.getMessage().contains("expected="));
        assertTrue(error.getMessage().contains("actual="));
        assertEquals(before, Files.readString(root.resolve("manifest.json")));
    }

    @Test void detectsLfsPointersAndMissingFiles() throws Exception {
        bundle();
        Files.writeString(root.resolve("encoder.onnx"), "version https://git-lfs.github.com/spec/v1\noid sha256:abc\nsize 100\n");
        Files.delete(root.resolve("composer.onnx"));
        var error = assertThrows(IllegalArgumentException.class, () -> AiB2BSaaSService.verifyBundle(root));
        assertTrue(error.getMessage().contains("encoder.onnx: Git LFS pointer"));
        assertTrue(error.getMessage().contains("composer.onnx: missing file"));
    }

    @Test void rejectsUnlistedRequiredGraphs() throws Exception {
        ObjectNode manifest = bundle();
        ((ObjectNode) manifest.get("files")).remove("response_aggregator.onnx");
        save(manifest);
        var error = assertThrows(IllegalArgumentException.class, () -> AiB2BSaaSService.verifyBundle(root));
        assertTrue(error.getMessage().contains("response_aggregator.onnx: checksum missing"));
    }

    @Test void rejectsPathTraversal() throws Exception {
        ObjectNode manifest = bundle();
        ((ObjectNode) manifest.get("files")).put("../outside.onnx", "0".repeat(64));
        save(manifest);
        var error = assertThrows(IllegalArgumentException.class, () -> AiB2BSaaSService.verifyBundle(root));
        assertTrue(error.getMessage().contains("unsafe bundle path"));
    }

    @Test void rejectsV1ManifestBeforeLoadingNativeModels() throws Exception {
        ObjectNode manifest = bundle();
        manifest.put("format", "trajectory-java-v1");
        save(manifest);
        assertThrows(IllegalArgumentException.class, () -> AiB2BSaaSService.verifyBundle(root));
    }
}
