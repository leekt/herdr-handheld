# PDX v0.4.0 validation

Date: 2026-09-15 (Asia/Seoul). This report distinguishes automated checks from physical-device results. The source and APK identifiers are generated in [validation-summary.json](../../artifacts/validation-summary.json).

| Check | Actual result |
|---|---|
| Fresh JVM contract/state tests | 41 passed; no failures or skips |
| Bundled terminal renderer tests | 3 passed |
| Android lint | 0 errors, 30 warnings |
| Debug app and instrumentation APK | Built with JDK 17 / SDK 35 |
| RG Rotate instrumentation | 7 passed; 0 skipped; exact tested APK hash matches artifact |
| Constrained layouts on RG Rotate | 320×440dp and 440×280dp; major pages and one-line hint geometry |
| Unsigned release variant | Built without signing environment variables |
| Optional maintainer signing configuration | Built and certificate-verified using the existing local debug certificate for this check only |
| In-place update | Installed with `adb install -r`; encrypted blobs identical before first launch |
| Public source / uncompressed APK privacy scan | No private addresses, host/device identifiers, user project names or embedded private keys found |

APK: `pdx-debug.apk`, 37,887,332 bytes. SHA-256:

```text
773bb5224b0fdcc36fd068bef0c951cfb26d2602432011750a910eb13f6f92bc
```

The existing Android package, Activity, Keystore alias and signing certificate remain unchanged. The current public channel is still a signed debug APK. Optional production signing does not silently switch that channel or create new keys.

## What ran on the actual device and host

- Opened two existing real agents into native READ; observed no controller, no terminal frames and no WebView in the view hierarchy. Verified latest-output loading, touch scrolling, D-pad scrolling after touch, Select tap/fade/hold, local confirmation and B/Start escape paths.
- Ran actual Codex subscription turns, resumed the same dedicated thread, and verified proposed actions remained unapplied. Branched a separate assistant conversation, saved pinned notes, compacted the host context, created a new conversation and selected the original again. Original notes and thread identity were retained.
- Temporarily selected a nonexistent Herdr executable. SSH remained READY while Herdr was OFFLINE, and the assistant completed a real request with no stale agent inventory. Restored the original profile and a healthy Herdr connection afterward.
- Started and cancelled the real Android speech recognizer; late callbacks were ignored. No speech recording was sent to an agent.
- Exercised the bundled WebView renderer, sequential acknowledgements, old-generation rejection and restricted external navigation.

The final seven-suite run took 84.096 seconds. Tests inject Android controller/touch events; this does not substitute for a person calibrating every printed button. Existing Herdr agents received no instruction, Enter, Ctrl+C or approval from these suites. No default-HOME change was made.

## Measurements and limits

A single cold `am start -W` launch reported TotalTime 1205 ms and WaitTime 1208 ms. This is Activity launch timing, not SSH readiness or local button latency. One 10.12-second native READ sample consumed 0.51 app-process CPU seconds (5.04% of one core). There is no matched previous-version baseline and no battery or sustained-performance claim.

Real-data reader and assistant screenshots were visually inspected and kept private. Constrained layouts ran on RG Rotate, not an emulator or a second physical handheld. A second device, spoken Korean/English quality, manual HOME selection/withdrawal, sustained-use comfort, and measured sub-100ms button feedback remain unverified. See the [device report template](../devices/report-template.md).

CI runs the same automated checks and produces a separate report with device status `not_run`. Its debug certificate is disposable; CI artifacts are not in-place updates for this paired installation. Device logs, private screenshots, signing material and host profiles are excluded from the public repository and release.
