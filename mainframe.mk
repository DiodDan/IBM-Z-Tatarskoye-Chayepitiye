# Standalone diagnostics: copy JUST this file to the server and run:
# make -f mainframe.mk diagnose
SHELL := /bin/bash
.DEFAULT_GOAL := help
REPORT_DIR ?= mainframe-report
PROJECT_DIR ?= .
NETWORK ?= 0
ORT_JAR ?=
TOKENIZER_LIB_DIR ?=
THREADS ?= 2
REQUIRE_S390X ?= true
export REPORT_DIR PROJECT_DIR NETWORK

.PHONY: help diagnose verify
help:
	@echo 'make -f mainframe.mk diagnose                 Read-only server inventory (Python 3.9+ required)'
	@echo 'make -f mainframe.mk diagnose NETWORK=1       Also probe public dependency registries'
	@echo 'make -f mainframe.mk verify ORT_JAR=/path/runtime.jar TOKENIZER_LIB_DIR=/path/native'
	@echo 'Send mainframe-report/report.txt and report.json back for deployment planning.'

define DIAG_PROGRAM
import datetime, hashlib, json, os, pathlib, platform, re, shutil, subprocess, sys, zipfile
root = pathlib.Path(os.environ['PROJECT_DIR']).resolve()
out = pathlib.Path(os.environ['REPORT_DIR']).resolve()
out.mkdir(parents=True, exist_ok=True)
report = {'generated_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
          'architecture': platform.machine(), 'os': platform.system(), 'kernel': platform.release(),
          'byteorder': sys.byteorder, 'python': platform.python_version(),
          'scope': 'Host inventory only; NOT an inference or container compatibility certification.'}
def run(args, timeout=12, versions_only=False):
    try:
        p = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout, text=True, errors='replace')
        output = ' '.join(sorted(set(re.findall(r'(?:GLIBCXX|GLIBC|CXXABI)_[0-9.]+',p.stdout)))) if versions_only else p.stdout[:16000]
        return {'exit': p.returncode, 'output': output}
    except FileNotFoundError:
        return {'available': False}
    except subprocess.TimeoutExpired:
        return {'error': 'timeout'}
    except Exception as e:
        return {'error': type(e).__name__}
def digest(p):
    h = hashlib.sha256()
    with p.open('rb') as stream:
        for block in iter(lambda: stream.read(1024*1024), b''): h.update(block)
    return h.hexdigest()
if pathlib.Path('/etc/os-release').exists():
    report['distribution'] = dict(line.rstrip().split('=',1) for line in pathlib.Path('/etc/os-release').read_text().splitlines() if '=' in line)
report['system'] = {name: run(args) for name,args in {
    'glibc':['getconf','GNU_LIBC_VERSION'], 'cpu':['lscpu'], 'memory':['free','-h'],
    'disk':['df','-h',str(root)], 'java':['java','-version'], 'javac':['javac','-version'],
    'git_lfs':['git','lfs','version'], 'cmake':['cmake','--version'], 'gcc':['gcc','--version'],
    'gxx':['g++','--version'], 'ninja':['ninja','--version'], 'cargo':['cargo','--version'],
    'rust':['rustc','--version'], 'node':['node','--version'], 'npm':['npm','--version'],
    'pkg_config':['pkg-config','--version'], 'openssl_headers':['pkg-config','--modversion','openssl'],
    'apt_onnx_packages':['apt-cache','search','onnxruntime'],
    'apt_tokenizer_packages':['apt-cache','search','tokenizers'],
    'docker':['docker','version','--format','{{json .}}'], 'compose':['docker','compose','version'],
    'docker_platform':['docker','info','--format','{{.Architecture}} {{.OSType}} cpus={{.NCPU}} memory={{.MemTotal}}'],
    'container_images':['docker','image','ls','--format','{{.Repository}}:{{.Tag}} {{.ID}}'],
    'onnx_mlir':['onnx-mlir','--version'],
    'podman':['podman','version','--format','json']}.items()}
report['running_as_root'] = hasattr(os, 'geteuid') and os.geteuid() == 0
if os.environ.get('JAVA_HOME'):
    report['java_home_runtime'] = run([str(pathlib.Path(os.environ['JAVA_HOME'])/'bin/java'),'-version'])
report['nnpa_advertised_by_lscpu'] = bool(re.search(r'\bnnpa\b',report['system']['cpu'].get('output',''),re.I))
# No arbitrary environment dump, credentials, registry login, or application logs.
report['override_presence'] = {name: bool(os.environ.get(name)) for name in
    ['JAVA_HOME','RUST_LIBRARY_PATH','LD_LIBRARY_PATH','ONNX_MLIR_HOME']}
ld = run(['ldconfig','-p'])
if 'output' in ld:
    ld['output'] = '\n'.join(x for x in ld['output'].splitlines() if re.search(r'onnx|tokeniz|stdc\+\+|libc\.so|libm\.so',x,re.I))
report['linker_cache'] = ld
# Bounded search. Does not walk all of / or execute discovered libraries.
home = pathlib.Path.home()
bases = [pathlib.Path(x) for x in ['/usr/lib','/usr/lib64','/usr/local/lib','/opt/ibm','/opt/onnxruntime']]
bases += [home/'.djl.ai/tokenizers',home/'.m2/repository/com/microsoft/onnxruntime',
          home/'.gradle/caches/modules-2/files-2.1/com.microsoft.onnxruntime',root/'backend/libs',root/'native']
for name in ['RUST_LIBRARY_PATH','LD_LIBRARY_PATH']:
    bases += [pathlib.Path(p) for p in os.environ.get(name,'').split(os.pathsep) if p]
candidates = set(); visited = 0; truncated = False
for base in dict.fromkeys(bases):
    if not base.is_dir(): continue
    for folder, dirs, files in os.walk(base, followlinks=False):
        visited += 1
        dirs.sort()
        if visited > 12000: truncated = True; break
        for name in files:
            if ((name.startswith('libonnxruntime') or name.startswith('libtokenizers') or name.startswith('libdjl_tokenizer'))
                and ('.so' in name or name.endswith('.dylib'))) or (name.startswith('onnxruntime') and name.endswith('.jar')):
                candidates.add(pathlib.Path(folder)/name)
        if len(candidates) >= 100: truncated = True; break
    if truncated: break
report['library_search'] = {'directories_visited':visited,'truncated':truncated,'scope':'standard library directories and known dependency caches'}
report['libraries'] = []
for p in sorted(candidates)[:100]:
    entry = {'path':str(p),'file':run(['file','-L',str(p)])}
    if p.suffix == '.jar':
        try:
            with zipfile.ZipFile(p) as z:
                entry['native_entries'] = [n for n in z.namelist() if n.endswith(('.so','.dylib','.dll'))][:150]
                entry['has_s390x_entries'] = any('s390' in n for n in entry['native_entries'])
                entry['note'] = 'Presence of native entries does not prove the Java loader supports s390x.'
        except Exception as e: entry['jar_error'] = type(e).__name__
    else:
        entry['elf_header'] = run(['readelf','-h',str(p)])
        entry['dependencies'] = run(['readelf','-d',str(p)])
        versions = run(['readelf','--version-info',str(p)], versions_only=True)
        entry['required_symbol_versions'] = sorted(set(re.findall(r'(?:GLIBCXX|GLIBC|CXXABI)_[0-9.]+',versions.get('output',''))))
    report['libraries'].append(entry)
bundle = root/'backend/src/main/resources/trajectory'
if not bundle.exists() and (root/'manifest.json').exists(): bundle = root
report['bundle'] = {'found':bundle.is_dir()}
if bundle.is_dir():
    try:
        manifest_path = bundle/'manifest.json'
        if manifest_path.read_bytes().startswith(b'version https://git-lfs.github.com/spec/v1'):
            report['bundle']['error'] = 'LFS pointer instead of manifest; run git lfs pull.'
        else:
            m = json.loads(manifest_path.read_text())
            report['bundle'].update(format=m.get('format'),dimension=m.get('dimension'),sequence_length=m.get('sequence_length'),manifest_sha256=digest(manifest_path))
            rows=[]
            for name,expected in m['files'].items():
                p=(bundle/name).resolve()
                if not p.is_relative_to(bundle.resolve()): raise ValueError('unsafe manifest path')
                row={'name':name,'exists':p.is_file()}
                if p.is_file():
                    row['bytes']=p.stat().st_size
                    with p.open('rb') as stream: row['lfs_pointer']=stream.read(80).startswith(b'version https://git-lfs.github.com/spec/v1')
                    row['sha256_matches']=digest(p)==expected
                rows.append(row)
            report['bundle']['files']=rows
            report['bundle']['all_hashes_match']=all(x.get('sha256_matches',False) for x in rows)
    except Exception as e: report['bundle']['error']=type(e).__name__+': '+str(e)
# Optional probes: public URLs only; never pass a token or user-specific query.
if os.environ['NETWORK']=='1':
    report['network']={url:run(['curl','--head','--location','--silent','--show-error','--max-time','10',url],12) for url in
        ['https://repo.maven.apache.org/maven2/','https://github.com/','https://publish.djl.ai/','https://registry.access.redhat.com/v2/']}
    for probe in report['network'].values():
        if 'output' in probe:
            probe['output'] = '\n'.join(line for line in probe['output'].splitlines() if line.startswith(('HTTP/', 'curl:')))
checks = []
if platform.machine()!='s390x': checks.append('This report is not from s390x; do not certify the mainframe from it.')
if not report['bundle'].get('all_hashes_match'): checks.append('Model bundle absent, incomplete, or corrupt; obtain real Git LFS files first.')
java_runtime = report.get('java_home_runtime',report['system']['java'])
java_major = re.search(r'version\s+"(\d+)',java_runtime.get('output',''))
if not java_major or java_major.group(1) != '21' or report['system']['javac'].get('exit') != 0:
    checks.append('Install/select a working JDK 21 for this backend.')
checks += ['Need a compatible ORT Java jar AND its s390x JNI/native runtime.',
           'Need DJL 0.36.0-compatible s390x tokenizer JNI.',
           'Run verifyModel on s390x before deployment. CPU inference does not enable NNPA.']
report['next_checks']=checks
encoded=json.dumps(report,indent=2,ensure_ascii=False)
(out/'report.json').write_text(encoded+'\n')
(out/'report.txt').write_text('MAINFRAME READINESS REPORT\n'+encoded+'\n')
print('Wrote '+str(out/'report.txt'))
print('Wrote '+str(out/'report.json'))
print('No inference was executed. Review the reports before sharing; they include library paths and platform details.')
endef
export DIAG_PROGRAM

diagnose:
	@command -v python3 >/dev/null || { echo "Python 3.9+ is required" >&2; exit 1; }
	@python3 -c "$$DIAG_PROGRAM"

define VERIFY_PROGRAM
cd "$$PROJECT_DIR"
if [[ -n "$$ORT_JAR" && ! -f "$$ORT_JAR" ]]; then echo 'ORT_JAR file not found' >&2; exit 1; fi
if [[ -n "$$TOKENIZER_LIB_DIR" ]]; then
    test -f "$$TOKENIZER_LIB_DIR/libtokenizers.so" || { echo 'libtokenizers.so not found' >&2; exit 1; }
    # DJL 0.36.0 directory override dereferences a null CUDA arch on CPU platforms.
    export RUST_LIBRARY_PATH="$$TOKENIZER_LIB_DIR/libtokenizers.so"
fi
args=(bash backend/gradlew -p backend verifyModel "-PrequireS390x=$$REQUIRE_S390X" "-Pthreads=$$THREADS" --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process)
if [[ -n "$$ORT_JAR" ]]; then args+=("-PortJar=$$ORT_JAR"); fi
"$${args[@]}"
endef
export VERIFY_PROGRAM ORT_JAR TOKENIZER_LIB_DIR THREADS REQUIRE_S390X

verify:
	@bash -eu -c "$$VERIFY_PROGRAM"
