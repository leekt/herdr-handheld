#!/usr/bin/env python3
"""Explicit paired-device validation; installs in place and never uninstalls or switches HOME."""
import argparse
import hashlib
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parent.parent
parser=argparse.ArgumentParser()
parser.add_argument('--adb',default='adb')
parser.add_argument('--real-host',action='store_true',help='Allow actual Codex turns and temporarily test Herdr unavailability; restore its profile afterward')
args=parser.parse_args()
if not args.real_host:
    parser.error('These suites require a paired real host; pass --real-host to run them')
apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
tests=ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
for path in (apk,tests):
    subprocess.run([args.adb,'install','-r',str(path)],check=True,stdout=subprocess.DEVNULL)
print('PDX_TEST_APK_SHA256: '+hashlib.sha256(apk.read_bytes()).hexdigest(),flush=True)
suites=['RealReadOnlyTest','ResponsiveLayoutTest','RealVoiceTest','RealAgentChatTest','RendererDeviceTest','RealAssistantTest','RealConversationTest','RealServicesTest']
command=[args.adb,'shell','am','instrument','-w','-r','-e','runRealReadOnly','true','-e','runRealVoice','true','-e','runRealAssistant','true','-e','class',','.join('dev.herdr.handheld.'+suite for suite in suites),'dev.herdr.handheld.test/androidx.test.runner.AndroidJUnitRunner']
result=subprocess.run(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
print(result.stdout,flush=True)
if result.returncode or 'FAILURES!!!' in result.stdout or 'INSTRUMENTATION_FAILED' in result.stdout or f'OK ({len(suites)} tests)' not in result.stdout:
    sys.exit(1)
