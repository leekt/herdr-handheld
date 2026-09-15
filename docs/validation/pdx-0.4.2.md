# PDX 0.4.2 validation

Date: 2026-09-15. Native Android app; stable `dev.herdr.handheld` install identity.

## Automated build

`python3 scripts/check.py` completed fresh: 55 JVM tests and 3 xterm renderer tests passed, with zero failures or skips. Android lint: 0 errors, 30 warnings. Debug app and instrumentation APKs built successfully. No dependency versions changed in this release.

## Physical-device checks

RG Rotate, Android 12/API 31, 720×720, density 240. The final APK passed all 6 targeted instrumentation tests in 27.492 seconds, with zero failures or skips: real read/color scrolling, compact layouts, voice/controller routing, existing-agent chat, message-mode defaults and renderer isolation. The device log's APK hash matches the released artifact.

- Existing Codex agents open in Messages. Touch can select either Messages or Terminal; the current selection is exposed to accessibility services.
- X opens Actions with the view switch selected first; A switches views. These events were dispatched through the app's logical input router on the physical device.
- Explicit controller acquisition followed by B releases control and returns to Messages. No remote key or agent prompt was sent by this test.
- Activity recreation returns to Messages and reconnects successfully.
- A temporarily invalid Codex executable path, held only in memory, produces a history error with Retry while keeping Messages selected. Terminal remains explicitly selectable. Restoring the path loads the real history again.
- Both mode labels stay on one line within their container at 320×440dp and 440×280dp. A private screenshot of real history was visually inspected at the device's native 720×720 resolution.

## Behavior and limits

Messages is the default for supported Codex identities when opening an agent, leaving controller input or resuming the app. The header's Messages / Terminal buttons and X → A switch are available in the reading view. Unsupported agents or missing Codex thread identifiers use Terminal. Messages displays persisted history; current interactive output remains available in Terminal.

This release's tests use real paired-host data. They did not send instructions to existing agents. The assistant, branching and compaction integration suites were not rerun for this UI change; their previous full regression result is documented in [0.4.1 validation](pdx-0.4.1.md). Spoken transcription accuracy, human button-latency measurements, long-session comfort, other handhelds and HOME role selection/revocation remain unverified in this release. Instrumentation on physical hardware is distinct from a person pressing its buttons.

The in-place upgrade retained all 18 existing encrypted files. SSH private/public key and trusted host-key blobs are byte-for-byte unchanged. The default Android launcher was not changed.

## Artifact

APK SHA-256: `59e828f028b2b366cdaf555caafb4be7bcfb9e6539575bb5f0dc6a6a76191816`.

See the sanitized [validation JSON](../../artifacts/validation-summary.json). Private host addresses, agent names, transcripts, screenshots, credentials and raw device logs are excluded from source and release assets.
