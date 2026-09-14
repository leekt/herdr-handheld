# Testing

The repository distinguishes local automated tests, on-device instrumentation, direct Herdr CLI probes, and hands-on user validation. An APK build is not treated as proof of device behavior.

## Reproduce

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
cd terminal-assets && npm ci --ignore-scripts && npm test && cd ..

# An authorized Android device must be connected. This can replace/uninstall
# the app under test; back up any important drafts by copying their text first.
./gradlew :app:connectedDebugAndroidTest

# Optional real CLI probes: isolated temporary Herdr config/session, no user panes.
python3 scripts/contract_probe.py
```

The unit tests also cover device-generated Ed25519 key parsing with SSHJ, literal plain-text/JSON-looking recent output, and holding new read output without shifting a paused view. The unit tests cover completed A presses, long presses, duplicate down/up and D-pad/HAT edges, mode-transition leakage, stale acquisition, cross-session identity, agent replacement, disconnected input, bounded NDJSON, arbitrary chunk boundaries, shell quoting, and user text encoded as data. Renderer tests cover UTF-8 splits, Korean/emoji/combining marks, ANSI cursor/clear/color behavior, alternate screens, and OSC handler consumption.

Android instrumentation exercises demo navigation/read/control/back/home, disabling L/R while controlling, background/resume, Activity recreation, actual Keystore encryption, sequential WebView write acknowledgements, old-generation frame rejection, and external-link isolation. These are injected Android events on physical hardware, not a person pressing the printed buttons.

## Optional Android SSH integration fixture

This test-only SSH server binds loopback, accepts one freshly generated disposable key, runs no shell, and serves only fixed synthetic responses. It is not an app runtime dependency or a deployable gateway.

```sh
uv venv .tools/test-venv
uv pip install --python .tools/test-venv/bin/python asyncssh==2.24.0
.tools/test-venv/bin/python scripts/ssh_fixture_server.py
```

In another terminal, with the fixture still running:

```sh
adb reverse tcp:18422 tcp:18422
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.runSshFixture=true
adb reverse --remove tcp:18422
```

Stop the fixture with Ctrl+C. Its generated keys are under ignored `.tools/`, included only in the test APK, and never included in the product APK. The test verifies Ed25519 authentication, exact host fingerprint confirmation, host-key change rejection, separate stdout/stderr, capability parsing, and terminal output delivered in seven-byte chunks.

## Paired-device read-only validation

Preserve the paired app's key and settings by updating with `adb install -r` and invoking the selected instrumentation directly. Gradle's connected-device test runner may uninstall the app; the demo smoke suite changes the profile mode. Do not use that suite on the paired installation.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  -e class dev.herdr.handheld.RealReadOnlyTest \
  -e runRealReadOnly true \
  dev.herdr.handheld.test/androidx.test.runner.AndroidJUnitRunner
```

This opt-in test requires the already paired real host and at least two agents, including recent output long enough to scroll. It sends no terminal input and changes no credentials. It opens real observers, verifies automatic read loading, injects D-pad/touch scrolling and touch-to-D-pad switching, tests Select hold/release and control confirmation, opens System and Settings, verifies single-line hint layouts inside the strip, checks the three-second fade, and taps the transient Control hint. The gesture coordinates target the RG Rotate's measured 720×720 display.

## Results and known limits

The latest results are in [real-connection-validation.md](real-connection-validation.md) and `artifacts/validation-summary.json`; [device-validation.md](device-validation.md) preserves the initial v0.1.0 results. Gradle reports are in `app/build/reports/`. Lint dependency-update notices and the unused third-party Bouncy Castle TLS helper warning are distinct from app errors. The app uses SSH host-key verification and does not use that TLS trust manager.

Real SSH authentication, agent listing, observation, controller acquisition/release and cold reconnection are verified on v0.2.0. Not yet claimed: sending task instructions or answering a real agent prompt during verification; physical printed-button calibration; HOME role selection/revocation by the user; long-session fatigue; measured physical-button feedback under 100 ms; all alternate-screen histories; all SSH formats; encrypted-key combinations; other firmware; and nonstandard terminal keyboard protocols.


## Real Codex subscription and conversation

Use the paired installation and explicit opt-in:

```sh
adb shell am instrument -w -e class dev.herdr.handheld.RealAssistantTest -e runRealAssistant true dev.herdr.handheld.test/androidx.test.runner.AndroidJUnitRunner
```

This consumes two actual subscription turns using the current agent inventory. It checks ChatGPT account status, a structured proposal that does not execute itself, the microphone/provider inventory, and resuming the same dedicated Codex conversation across closing/reopening the SSH stdio channel. It sends no Herdr terminal input. Test conversations remain private on the configured Codex host. Do not publish device screenshots or transcripts.

JVM tests additionally check stable target aliases, rejected unsupported actions/control characters, output exclusion, executable quoting, and late/cancelled recognition callbacks. Voice-provider availability is distinct from hands-on Korean/English dictation quality.


The final paired-device run also includes `RealVoiceTest` with `runRealVoice=true`. Approve Android microphone permission when requested; it verifies actual recognition startup and cancellation only, without sending captured speech.
