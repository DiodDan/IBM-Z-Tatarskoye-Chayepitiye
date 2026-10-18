# Mainframe deployment guide (Linux s390x)

The Java service is the application adapter. It still calls **native machine code**:

```
Spring/Kotlin → AiB2BSaaSService.java
             → ONNX Runtime Java → ONNX JNI + runtime .so
             → DJL tokenizer Java → libtokenizers.so
```

Your ONNX weights/tokenizer files can stay identical. The JNI libraries must be built
for Linux s390x and compatible with the glibc **inside the execution environment**.
A successful test on a Mac is not a mainframe test. The implemented baseline is CPU
inference; it does not activate Telum/NNPA acceleration.

## 1. First run: collect the server information

Copy just `mainframe.mk` to the server; a checkout of the project is not required:

```bash
make -f mainframe.mk diagnose
```

Requires GNU Make, Bash and Python 3.9+ (Ubuntu 24.04 includes a suitable Python).
It creates `mainframe-report/report.txt` and `report.json`. Send either back for review.
It does not install anything, change system configuration, execute discovered binaries,
start containers or run inference. It executes installed inventory tools such as Java,
Docker version, `file` and `readelf`. Missing tools are recorded rather than aborting.
The library search is bounded; it is not an exhaustive search of the entire machine.
Reports contain platform details and library paths; no `.env`, credentials or complete
environment dump is read. There is no automatic upload.

For public registry connectivity checks:

```bash
make -f mainframe.mk diagnose NETWORK=1
```

From inside a repository checkout it also hashes every model bundle file and detects
Git LFS pointers. Use `PROJECT_DIR=/path/to/checkout` if you run the Makefile elsewhere.
The hash scan reads the full encoder (about 740 MB), but does not load it into memory.

## 2. Get the actual models onto the server

Clone/pull your branch using your existing GitHub access, then from the repository:

```bash
git lfs install --local
git lfs pull
make -f mainframe.mk diagnose
```

The report must show `bundle.all_hashes_match: true`. A 3-line file beginning with
`version https://git-lfs.github.com/spec/v1` is a pointer, not the model.
Keep `tokenizer/`, `manifest.json`, `golden.json` and both ONNX graphs together.

## 3. Find or obtain the native dependencies

Start with the report. It searches standard library locations, `/opt/ibm`, `/opt/onnxruntime`,
known Gradle/Maven/DJL caches, `backend/libs`, `native`, and library override directories.
It inspects ELF headers and symbol versions without loading the libraries. It also
reports locally available apt package names matching ONNX Runtime/tokenizers.

You need all of:

| Component | Required by this application |
|---|---|
| JDK | Java 21, Linux s390x (with `javac` for building) |
| ORT Java jar | API-compatible with the pinned ONNX Runtime 1.23.2; loader must understand s390x |
| ORT native runtime | s390x `libonnxruntime.so` and `libonnxruntime4j_jni.so`, either packaged in the jar or supplied externally |
| DJL native tokenizer | `libtokenizers.so`, s390x JNI build matching DJL tokenizers 0.36.0 |

A generic Python `onnxruntime` wheel, a `tokenizers` Python/Rust package, or a bare ORT
C library does **not** supply the Java JNI components. Do not select an amd64 or arm64
artifact just because the filename says Linux.

Manual inspection of a candidate library:

```bash
file /path/to/libtokenizers.so
readelf -h /path/to/libtokenizers.so
readelf -d /path/to/libtokenizers.so
readelf --version-info /path/to/libtokenizers.so
getconf GNU_LIBC_VERSION
jar tf /path/to/onnxruntime-s390x.jar
```

The ELF machine must be IBM S/390; required GLIBC/GLIBCXX symbol versions must be available
in the target runtime. A newer host glibc does not repair an older container glibc.
Jar native entries alone also do not prove the Java loader supports s390x.

Ask organizers for a prebuilt, compatible **Java/JNI** runtime or an approved s390x
inference image first. The stock ORT Java **1.23.2 loader rejects s390x**, even when
native libraries are supplied. The pinned source needs a loader/packaging patch or
an equivalent vendor-supported Java build. Do not silently change the runtime version
without rerunning verification.

If no compatible builds are available, this repository includes an **experimental**
source-build helper, pinned to ORT 1.23.2 and DJL 0.36.0:

```bash
JOBS=1 bash tools/mainframe/build-native.sh "$PWD/work/s390x-native"
```

Run it on the s390x machine. It clones official upstream sources, patches the missing
ORT Java architecture branches and requests only the JVM JNI component (so a headless
JDK works), compiles and produces:

```
work/s390x-native/onnxruntime-s390x.jar
work/s390x-native/native/libtokenizers.so
```

The helper builds the `onnxruntime4j_jni` target and its dependencies, rather than
the default `all` target. This avoids the standalone performance-test linker failure
observed on s390x. It checks the resulting jar contains the Java classes and both
s390x native libraries; numerical verification is still required.

Re-run the same command after resolving a build error: the helper resumes existing
pinned checkouts and preserves compiler outputs. Do not delete `work/s390x-native`.

Prerequisites include JDK 21, Git, Python 3, C/C++, CMake, Ninja, Rust/Cargo,
pkg-config and OpenSSL development headers. Exact compiler/dependency compatibility
is checked during the upstream builds. Start with the diagnostics report before
installing packages. This helper has not been executed on a mainframe: source/dependency
issues can remain. Compilation can consume hours and many GB on a 3-vCPU/8-GB VM.
It is not a guaranteed one-command deployment. Build in an environment compatible
with the final container's glibc; Ubuntu 24.04 binaries may require newer glibc than UBI 9.

## 4. Run the numerical acceptance test BEFORE starting Spring

From the repository root, with Java 21 selected in `JAVA_HOME`/`PATH`:

```bash
make -f mainframe.mk verify \
  ORT_JAR="$PWD/work/s390x-native/onnxruntime-s390x.jar" \
  TOKENIZER_LIB_DIR="$PWD/work/s390x-native/native"
```

This runs Gradle `verifyModel` directly, without Spring, a database or an external LLM.
It enforces an s390x JVM by default. It checks:

- Hashes of every model bundle artifact.
- Exact token IDs/masks for six texts, including Unicode and the token limit.
- Encoder vectors/risk logits against PyTorch reference values.
- Six recursive composition cases using the JVM's own preceding vectors.
- Diagnostic decision parity and rejection of empty/overlength input.

Success writes **`backend/build/reports/trajectory/verification.json`** with
`passed: true`, `architecture: s390x`, the manifest hash, errors and timings.
The tracked historical `resources/trajectory/verification-java.json` is from earlier
work and does not certify the server. Conversion verification does not validate model
quality or provide production calibration/thresholds.

If a check fails, send the error and diagnostic report. Do not loosen the tolerances
or spoof `os.arch` to get a green result. Re-run these checks in the final container;
a successful host test does not certify a different container environment.

## 5. Connect the accepted runtime to Spring

Use the **full library filename** for `RUST_LIBRARY_PATH`, rather than a directory.
DJL 0.36.0 can dereference a null CUDA architecture in its directory-override path,
even during CPU inference. The file-override branch bypasses that lookup.
`mainframe.mk verify` now sets this automatically.

The existing Gradle property already replaces the standard ORT dependency:

```bash
export RUST_LIBRARY_PATH="$PWD/work/s390x-native/native/libtokenizers.so"
bash backend/gradlew -p backend bootRun \
  -PortJar="$PWD/work/s390x-native/onnxruntime-s390x.jar"
```

Configure/start PostgreSQL and your LLM settings as usual. Use the echo profile to test
without calling an external model. The current root Makefile is primarily a Mac developer
entry point; the explicit Gradle commands above avoid its macOS Java selection logic.

For containers, the current Dockerfile still needs s390x integration: supply the custom
jar to the Gradle build, copy the native tokenizer into the runtime, set `RUST_LIBRARY_PATH`,
and verify all builder/database images support the selected platform. Merely passing
`--platform=linux/s390x` does not perform those steps. Java/frontend compilation can also
run in a separate supported build environment, but the packaged JNI must target s390x.
The UBI 9 runtime image supports s390x; its existence does not certify the whole stack.

Then check `/actuator/health` and send an echo message. The console should show
`[trajectory] role=ASSISTANT dimensions=256 vector=[...]`. That is a useful smoke check;
use the standalone numerical acceptance report as the deployment gate.

## 6. Performance and acceleration

The report's inference timings are smoke measurements, not a concurrent HTTP benchmark.
Begin with `THREADS=2` on the 3-vCPU machine and measure application latency under load.
One model instance should be reused. Native runtime memory is additional to the JVM heap.

ONNX Runtime CPU inference in this adapter does not use NNPA. IBM zDLC/ONNX-MLIR can
provide an alternative compiled execution path, but needs integration and independent
numerical checks. Do not assume an environment variable enables it here.

Official references:

- [ONNX Runtime CPU source builds and Java binding](https://onnxruntime.ai/docs/build/inferencing.html)
- [Pinned ORT Java loader](https://github.com/microsoft/onnxruntime/blob/v1.23.2/java/src/main/java/ai/onnxruntime/OnnxRuntime.java)
- [Pinned DJL native loading and RUST_LIBRARY_PATH](https://github.com/deepjavalibrary/djl/blob/v0.36.0/extensions/tokenizers/src/main/java/ai/djl/huggingface/tokenizers/jni/LibUtils.java)
- [DJL tokenizer build](https://github.com/deepjavalibrary/djl/blob/v0.36.0/extensions/tokenizers/build.sh)
- [IBM zDLC](https://github.com/IBM/zDLC)
