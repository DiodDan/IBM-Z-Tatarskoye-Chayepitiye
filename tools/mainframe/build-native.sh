#!/usr/bin/env bash
# CPU baseline. Run ON the s390x machine; this is not a cross compiler.
set -euo pipefail
[[ $(uname -s) == Linux && $(uname -m) == s390x ]] || { echo 'Run on Linux s390x' >&2; exit 1; }
[[ $# == 1 ]] || { echo 'Usage: build-native.sh BUILD_DIRECTORY (new or existing)' >&2; exit 1; }
for command in git cmake ninja python3 javac cargo; do command -v "$command" >/dev/null || { echo "Missing prerequisite: $command" >&2; exit 1; }; done
mkdir -p "$1"
build_root=$(cd "$1" && pwd)
export JAVA_HOME=${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}
export CMAKE_BUILD_PARALLEL_LEVEL=${JOBS:-2}
export CARGO_BUILD_JOBS=${JOBS:-2}
printf '%s\n' 'Building native CPU dependencies. On a small VM this can take hours and substantial disk space.'
clone_or_resume() {
  local repo_url=$1 repo_tag=$2 repo_dir=$3
  if [[ -e "$repo_dir" ]]; then
    [[ -d "$repo_dir/.git" ]] || { echo "Not a Git checkout: $repo_dir" >&2; exit 1; }
    [[ $(git -C "$repo_dir" rev-parse HEAD) == $(git -C "$repo_dir" rev-parse "$repo_tag^{commit}") ]] || {
      echo "Existing checkout is not $repo_tag: $repo_dir; refusing to reset it" >&2; exit 1;
    }
    echo "Resuming existing $repo_tag checkout: $repo_dir"
  else
    git clone --branch "$repo_tag" --depth 1 --recurse-submodules --shallow-submodules "$repo_url" "$repo_dir"
  fi
}
clone_or_resume https://github.com/microsoft/onnxruntime.git v1.23.2 "$build_root/onnxruntime"
# ORT supports s390x CPU builds, but this release's Java loader/packager omit it.
python3 - "$build_root/onnxruntime" <<'PY'
from pathlib import Path
import sys
root=Path(sys.argv[1])
changes=[('java/src/main/java/ai/onnxruntime/OnnxRuntime.java',
'    } else if (isAndroid()) {\n      detectedArch = arch;',
'    } else if (arch.equals("s390x")) {\n      detectedArch = "s390x";\n    } else if (isAndroid()) {\n      detectedArch = arch;'),
('cmake/onnxruntime_java.cmake','elseif (ANDROID)\n  set(JNI_ARCH ${ANDROID_ABI})',
'elseif(CMAKE_SYSTEM_PROCESSOR STREQUAL "s390x")\n  set(JNI_ARCH s390x)\nelseif (ANDROID)\n  set(JNI_ARCH ${ANDROID_ABI})'),
('cmake/onnxruntime_java.cmake','find_package(JNI REQUIRED)',
'find_package(JNI REQUIRED COMPONENTS JVM)')]
for name,old,new in changes:
    p=root/name;s=p.read_text()
    if new in s: continue  # Patch already applied by a prior attempt.
    if s.count(old)!=1: raise SystemExit('Source patch no longer matches: '+name)
    p.write_text(s.replace(old,new))
PY
(
 cd "$build_root/onnxruntime"
 git diff > "$build_root/ort-java-s390x.patch"
 ./build.sh --config Release --build_shared_lib --build_java --skip_tests --target onnxruntime4j_jni --parallel "${JOBS:-2}" --compile_no_warning_as_error
)
# Copy the native runtime jar; Gradle can load it directly without publication.
python3 - "$build_root" <<'PY'
from pathlib import Path
import sys,shutil,zipfile
root=Path(sys.argv[1]);candidates=[]
required={"ai/onnxruntime/OrtEnvironment.class",
          "ai/onnxruntime/native/linux-s390x/libonnxruntime.so",
          "ai/onnxruntime/native/linux-s390x/libonnxruntime4j_jni.so"}
for p in (root/'onnxruntime/build').rglob('onnxruntime*.jar'):
    if any(x in p.name for x in ('sources','javadoc')): continue
    with zipfile.ZipFile(p) as jar:
        if required.issubset(jar.namelist()): candidates.append(p)
if len(candidates)!=1: raise SystemExit('Expected one packaged s390x JNI runtime jar; inspect '+str(candidates))
shutil.copy2(candidates[0],root/'onnxruntime-s390x.jar')
PY
clone_or_resume https://github.com/deepjavalibrary/djl.git v0.36.0 "$build_root/djl"
(cd "$build_root/djl/extensions/tokenizers" && bash build.sh s390x cpu)
mkdir -p "$build_root/native"
cp "$build_root/djl/extensions/tokenizers/build/jnilib/linux-s390x/cpu/libtokenizers.so" "$build_root/native/"
printf '\nNative build finished; inference is NOT verified yet.\nRUST_LIBRARY_PATH=%s/native/libtokenizers.so\nRun Gradle verification with -PortJar=/absolute/path/to/onnxruntime-s390x.jar\n' "$build_root"
