#!/usr/bin/env python3
"""Run checks and generate a report from this run only; raw device logs stay private."""
import argparse
import datetime as dt
import hashlib
import json
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

def now():
    return dt.datetime.now(dt.timezone.utc).isoformat()

def counts(paths):
    result = dict(tests=0, failures=0, errors=0, skipped=0)
    for path in paths:
        root = ET.parse(path).getroot()
        cases = list(root.iter('testcase'))
        result['tests'] += len(cases)
        for tag in ('failure', 'error', 'skipped'):
            result[{'failure': 'failures', 'error': 'errors', 'skipped': 'skipped'}[tag]] += sum(case.find(tag) is not None for case in cases)
    return result

def device_result(log_path, label, expected_sha):
    log = log_path.read_text()
    matches = re.findall(r'OK \((\d+) tests?\)', log)
    codes = [int(code) for code in re.findall(r'^INSTRUMENTATION_STATUS_CODE: (-?\d+)$', log, re.MULTILINE)]
    skipped = sum(code in (-3, -4) for code in codes)
    errors = sum(code in (-1, -2) for code in codes)
    passed = codes.count(0)
    matching = f'PDX_TEST_APK_SHA256: {expected_sha}' in log
    failed = any(token in log for token in ('FAILURES!!!', 'INSTRUMENTATION_FAILED', 'Process crashed'))
    return {'surface': label, 'status': 'passed' if matches and codes and matching and not failed and not errors and not skipped else 'failed', 'tests': sum(map(int, matches)), 'passed': passed, 'skipped': skipped, 'failures': errors, 'apk_matches': matching, 'log_sha256': hashlib.sha256(log_path.read_bytes()).hexdigest(), 'captured_at': dt.datetime.fromtimestamp(log_path.stat().st_mtime,dt.timezone.utc).isoformat()}

def automated_passed(report):
    return all(v == 'passed' for v in report['checks'].values()) and all(report.get(suite, {}).get('tests', 0) > 0 and report[suite].get('failures', 1) == 0 and report[suite].get('errors', 1) == 0 for suite in ('jvm', 'renderer')) and report.get('lint', {}).get('errors', 1) == 0

def source_digest():
    names=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z'],cwd=ROOT).decode().split('\0')
    digest=hashlib.sha256()
    for name in sorted(set(names)):
        path=ROOT/name
        if not name or not path.is_file():
            continue
        if Path(name).parts[0] not in ('app','terminal-assets','fixtures','gradle','scripts','.github') and name not in ('build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','gradlew.bat'):
            continue
        digest.update(name.encode()+b'\0'+path.read_bytes()+b'\0')
    return digest.hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--device-log', type=Path)
    parser.add_argument('--attach-device-log', type=Path,help='Attach results to the existing report without rebuilding')
    parser.add_argument('--device-label', choices=['rg-rotate', 'emulator'])
    args = parser.parse_args()
    if bool(args.device_log or args.attach_device_log) != bool(args.device_label):
        parser.error('device log and label must be supplied together')
    if args.attach_device_log:
        path=ROOT/'artifacts/validation-summary.json'
        report=json.loads(path.read_text())
        actual=hashlib.sha256((ROOT/'artifacts/pdx-debug.apk').read_bytes()).hexdigest()
        if actual!=report.get('apk',{}).get('sha256'):
            parser.error('APK differs from the existing validation report; run checks again')
        report['device']=device_result(args.attach_device_log,args.device_label,actual)
        report['status']='passed' if automated_passed(report) and report['device']['status']=='passed' else 'failed'
        path.write_text(json.dumps(report,indent=2)+'\n')
        return 0 if report['status']=='passed' else 1
    private = ROOT / '.tools/checks'
    private.mkdir(parents=True, exist_ok=True)
    started = time.time()
    report = dict(started_at=now(), checks={}, device={'status': 'not_run'})
    version = re.search(r'versionName = "([^"]+)"', (ROOT/'app/build.gradle.kts').read_text()).group(1)
    report['version'] = version
    report['source_sha256'] = source_digest()
    # Fresh XML is mandatory: a cached green result must not become a new validation run.
    shutil.rmtree(ROOT/'app/build/test-results/testDebugUnitTest', ignore_errors=True)
    js_xml = private/'renderer.xml'
    js_xml.unlink(missing_ok=True)
    for path in (ROOT/'app/build/reports').glob('lint-results-debug.*'):
        path.unlink()
    commands = [
        ('npm_install', ['npm', 'ci', '--ignore-scripts'], ROOT/'terminal-assets'),
        ('terminal_bundle', ['npm', 'run', 'build'], ROOT/'terminal-assets'),
        ('asset_consistency', ['git', 'diff', '--exit-code', '--', 'app/src/main/assets/terminal'], ROOT),
        ('renderer', ['node', '--test', '--test-reporter=junit', *[str(p.relative_to(ROOT/'terminal-assets')) for p in sorted((ROOT/'terminal-assets/test').glob('*.test.mjs'))]], ROOT/'terminal-assets'),
        ('android', ['./scripts/env.sh', ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug', ':app:assembleDebugAndroidTest', '--rerun-tasks'], ROOT),
    ]
    for name, command, cwd in commands:
        output = js_xml if name == 'renderer' else private/f'{name}.log'
        with output.open('w') as log:
            code = subprocess.run(command, cwd=cwd, stdout=log, stderr=subprocess.STDOUT).returncode
        report['checks'][name] = 'passed' if code == 0 else 'failed'
        print(f'{name}: {report["checks"][name]}', flush=True)
    xmls = [p for p in (ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml') if p.stat().st_mtime >= started]
    report['jvm'] = counts(xmls)
    try:
        report['renderer'] = counts([js_xml])
    except ET.ParseError:
        report['renderer'] = {'status': 'invalid_report'}
    lint = ROOT/'app/build/reports/lint-results-debug.xml'
    if lint.exists() and lint.stat().st_mtime >= started:
        issues = ET.parse(lint).getroot().findall('issue')
        report['lint'] = {'errors': sum(i.get('severity') in ('Error', 'Fatal') for i in issues), 'warnings': sum(i.get('severity') == 'Warning' for i in issues)}
    apk = ROOT/'app/build/outputs/apk/debug/app-debug.apk'
    artifacts = ROOT/'artifacts'
    artifacts.mkdir(exist_ok=True)
    if report['checks']['android'] == 'passed' and apk.exists():
        destination = artifacts/'pdx-debug.apk'
        shutil.copyfile(apk, destination)
        digest = hashlib.sha256(destination.read_bytes()).hexdigest()
        (artifacts/'pdx-debug.apk.sha256').write_text(f'{digest}  pdx-debug.apk\n')
        report['apk'] = {'name': destination.name, 'bytes': destination.stat().st_size, 'sha256': digest}
    if args.device_log:
        report['device']=device_result(args.device_log,args.device_label,report.get('apk',{}).get('sha256',''))
    report['finished_at'] = now()
    report['status'] = 'passed' if automated_passed(report) and report['device']['status'] != 'failed' else 'failed'
    (artifacts/'validation-summary.json').write_text(json.dumps(report, indent=2)+'\n')
    return 0 if report['status'] == 'passed' else 1

if __name__ == '__main__':
    sys.exit(main())
