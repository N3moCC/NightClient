#!/usr/bin/env python3
"""Build exact release artifacts, preserving separate compile/audit/runtime evidence."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def source_fingerprint(official_capes=True):
    paths = []
    for module in ['api', 'core', 'renderer', 'ui', 'runtime', 'platform', 'universal']:
        paths.extend(path for path in (ROOT / 'modules' / module / 'src').rglob('*') if path.is_file())
    paths.extend(path for path in (ROOT / 'modules/platform/src/templates/backends').rglob('*') if path.is_file())
    paths.extend((ROOT / 'tools/generators').glob('generate_*.py'))
    paths.extend(ROOT / 'tools' / name for name in
                 ['build.py', 'matrix/build_matrix.py', 'matrix/audit_compatibility.py'])
    paths.extend(ROOT / 'tools' / name for name in
                 ['matrix/assemble_universal.py', 'matrix/UniversalJarAssembler.java', 'build_universal.py']
                 if (ROOT / 'tools' / name).is_file())
    paths.extend((ROOT / 'modules/platform/compatibility').glob('*.json'))
    paths.extend(ROOT / name for name in ['settings.gradle', 'build.gradle', 'gradle.properties', 'LICENSE']
                 if (ROOT / name).is_file())
    paths.extend(path for path in (ROOT / 'gradle/wrapper').glob('*') if path.is_file())
    digest = hashlib.sha256()
    digest.update(f'officialCapes={1 if official_capes else 0}\0'.encode())
    for path in sorted(set(paths)):
        digest.update(str(path.relative_to(ROOT)).encode())
        digest.update(b'\0')
        digest.update(path.read_bytes())
    return digest.hexdigest()


def write_summary(reports, versions):
    """Individual rows survive an interrupted matrix run; never discard their evidence."""
    rows = []
    for version in versions:
        result = reports / version / 'result.json'
        rows.append(json.loads(result.read_text()) if result.exists() else {
            'minecraft': version, 'compiled': False, 'apiAudit': 'not_run',
            'runtime': 'not_run', 'opengl': 'not_run', 'vulkan': 'not_run'})
    output = reports / 'matrix.json'
    temporary = output.with_suffix('.json.tmp')
    temporary.write_text(json.dumps(rows, indent=2) + '\n')
    temporary.replace(output)
    return output


def find_java_home(requested):
    candidates = [requested, os.environ.get('JAVA_HOME')]
    java = shutil.which('java')
    if java:
        candidates.append(str(Path(java).resolve().parents[1]))
    for candidate in candidates:
        if candidate and (Path(candidate) / 'bin/java').is_file():
            return str(Path(candidate).resolve())
    raise SystemExit('JDK 25 is required. Set JAVA_HOME or pass --java-home /path/to/jdk-25.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--versions', help='Comma-separated exact versions; default: all 24 releases.')
    parser.add_argument('--java-home')
    parser.add_argument('--offline', action='store_true')
    parser.add_argument('--no-tests', action='store_true', help='Skip the shared tests (recorded in the report).')
    parser.add_argument('--no-audit', action='store_true')
    parser.add_argument('--stop-on-error', action='store_true')
    parser.add_argument('--build-timeout', type=int, default=600,
                        help='Maximum seconds per Gradle target; terminate a stalled download cleanly (default 600).')
    parser.add_argument('--stop-file', type=Path,
                        default=ROOT / 'build/compatibility/stop-requested',
                        help='If this file appears, finish the current target, save evidence, and stop (exit2).')
    parser.add_argument('--refresh-stale', action='store_true',
                        help='Rebuild targets whose recorded source fingerprint differs from the current source.')
    parser.add_argument('--profile', choices=('full', 'store', 'modrinth', 'curseforge', 'clean'),
                        default='full',
                        help='Build profile: full (default) or store/modrinth/curseforge/clean (omits official Mojang capes).')
    parser.add_argument('--no-official-capes', action='store_true',
                        help='Shorthand for --profile store (omits official Mojang capes).')
    args = parser.parse_args()
    official_capes = (not args.no_official_capes) and (args.profile == 'full')
    if args.build_timeout < 30:
        parser.error('--build-timeout must be at least 30 seconds')
    targets = json.loads((ROOT / 'modules/platform/compatibility/versions.json').read_text())
    versions = [target['minecraft'] for target in targets]
    selected = versions if not args.versions else args.versions.split(',')
    unknown = set(selected) - set(versions)
    if unknown:
        parser.error('Unknown release(s): ' + ', '.join(sorted(unknown)))
    selected = list(dict.fromkeys(selected))
    env = os.environ.copy()
    env['JAVA_HOME'] = find_java_home(args.java_home)
    env['PATH'] = str(Path(env['JAVA_HOME']) / 'bin') + os.pathsep + env.get('PATH', '')
    base = [str(ROOT / 'gradlew'), '--console=plain', '-PofficialCapes=' + ('true' if official_capes else 'false')]
    if args.offline:
        base.append('--offline')
    reports = ROOT / 'build/compatibility'
    reports.mkdir(parents=True, exist_ok=True)
    if args.refresh_stale:
        fingerprint = source_fingerprint(official_capes=official_capes)
        fresh = []
        for version in selected:
            result_file = reports / version / 'result.json'
            row = json.loads(result_file.read_text()) if result_file.exists() else {}
            artifact = ROOT / row.get('artifact', '')
            artifact_matches = (artifact.is_file() and row.get('artifactSha256')
                                == hashlib.sha256(artifact.read_bytes()).hexdigest())
            if (not row.get('compiled') or row.get('apiAudit') != 'passed'
                    or row.get('sourceFingerprint') != fingerprint or row.get('sourceChangedWhileBuilding')
                    or not artifact_matches):
                fresh.append(version)
        selected = fresh
    shared_tests = 'not_run'
    if not args.no_tests:
        shared = subprocess.run(base + ['-PcommonOnly=true', '--max-workers=2', 'test'], cwd=ROOT, env=env)
        shared_tests = 'passed' if shared.returncode == 0 else 'failed'
        if shared.returncode:
            raise SystemExit(shared.returncode)
    failures = []
    paused = False
    for index, version in enumerate(selected, 1):
        if args.stop_file.exists():
            paused = True
            print(f'Stopped at a target boundary: {args.stop_file}', flush=True)
            break
        print(f'[{index}/{len(selected)}] Minecraft {version}', flush=True)
        project = ':mc' + version.replace('.', '_')
        report_dir = reports / version
        report_dir.mkdir(parents=True, exist_ok=True)
        log = report_dir / 'build.log'
        command = base + ['-PmcVersion=' + version, project + ':build', project + ':writeAuditClasspath']
        row = {'minecraft': version, 'startedAt': now(), 'sharedTests': shared_tests,
               'officialCapes': official_capes,
               'compiled': False, 'apiAudit': 'not_run', 'runtime': 'not_run',
               'opengl': 'not_run', 'vulkan': 'not_run', 'buildLog': str(log.relative_to(ROOT))}
        source_before = source_fingerprint(official_capes=official_capes)
        with log.open('w') as output:
            process = subprocess.Popen(command, cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
            row['buildTimedOut'] = False
            try:
                returncode = process.wait(timeout=args.build_timeout)
            except subprocess.TimeoutExpired:
                row['buildTimedOut'] = True
                print(f'{version}: Gradle exceeded {args.build_timeout}s; terminating the stalled target.', flush=True)
                process.terminate()
                try:
                    returncode = process.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    process.kill()
                    returncode = process.wait(timeout=5)
        row['compiled'] = returncode == 0 and not row['buildTimedOut']
        row['buildExitCode'] = returncode
        if not row['compiled']:
            print(log.read_text()[-12000:], file=sys.stderr)
            failures.append(version)
        else:
            info = json.loads((report_dir / 'classpath.json').read_text())
            row['artifact'] = 'build/adapters/' + Path(info['artifact']).name
            row['artifactSha256'] = hashlib.sha256((ROOT / row['artifact']).read_bytes()).hexdigest()
            if not args.no_audit:
                audit = subprocess.run([sys.executable, str(ROOT / 'tools/matrix/audit_compatibility.py'),
                                        '--version', version], cwd=ROOT, env=env)
                row['apiAudit'] = 'passed' if audit.returncode == 0 else 'failed'
                if audit.returncode:
                    failures.append(version)
        row['completedAt'] = now()
        row['sourceFingerprint'] = source_before
        row['sourceChangedWhileBuilding'] = source_before != source_fingerprint(official_capes=official_capes)
        result_file = report_dir / 'result.json'
        result_temporary = result_file.with_suffix('.json.tmp')
        result_temporary.write_text(json.dumps(row, indent=2) + '\n')
        result_temporary.replace(result_file)
        write_summary(reports, versions)
        print(f"  compiled={row['compiled']} audit={row['apiAudit']} runtime=not_run", flush=True)
        if version in failures and args.stop_on_error:
            break
    summary = write_summary(reports, versions)
    print(f'Report: {summary}', flush=True)
    current_fingerprint = source_fingerprint(official_capes=official_capes)
    stale = []
    for version in selected:
        result_file = reports / version / 'result.json'
        row = json.loads(result_file.read_text()) if result_file.exists() else {}
        if (row.get('sourceFingerprint') != current_fingerprint or row.get('sourceChangedWhileBuilding')):
            stale.append(version)
    if stale and not paused:
        print('Source changes require --refresh-stale: ' + ', '.join(stale), flush=True)
    raise SystemExit(1 if failures else (2 if paused else (3 if stale else 0)))


if __name__ == '__main__':
    main()
