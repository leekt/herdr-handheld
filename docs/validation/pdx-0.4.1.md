# PDX 0.4.1 validation

Date: 2026-09-15. Native Android app; stable `dev.herdr.handheld` install identity.

## Automated build

`python3 scripts/check.py` completed fresh: 55 JVM tests and 3 xterm renderer tests passed, zero failures/skips. Android lint: 0 errors, 30 warnings. Debug app and instrumentation APKs built successfully with JDK 17 and SDK 35. The app includes locked JLine 3.30.17 and CommonMark 0.24.0 dependencies and their licenses.

Tests cover native ANSI foreground/background and style preservation, Unicode, OSC isolation, bounded malformed input, paused colors, native Markdown, typed history roles/order, opaque pagination, read-only Codex method sequence, thread identity mismatch, stale callbacks and input safety.

## Physical-device checks

RG Rotate, Android 12/API 31, 720×720, density 240. The initial targeted run passed all three integration tests: real terminal reading, voice input and agent chat. The final APK passed all 8 full regression tests in 80.387 seconds, with zero failures or skips: native read/color scrolling, compact layouts/hints, voice/controller routing, existing-agent chat, renderer isolation, real assistant, conversation branch/compaction, and SSH/Codex without Herdr. The log’s APK hash matches the published artifact. Test reports distinguish injected gamepad/touch events on real hardware from a person pressing physical buttons.

## Behavior and limits

- Supported Codex sessions open as real persisted chat messages. Six recent turns are read through Codex 0.153.4’s experimental `thread/turns/list` API, with explicit identity checks and earlier-page cursors. No existing agent thread is resumed, renamed, forked, compacted or prompted by this reader. Other agents and unavailable history keep the Terminal view.
- Chat is a saved display summary, not a live token/tool feed. Terminal remains available for current output and interactive choices. Markdown links are displayed as labels; they do not open automatically. Remote images/HTML are never loaded.
- Native Terminal displays actual ANSI palette and RGB colors, backgrounds and text attributes. Explicit RGB values are preserved; custom desktop palette/theme synchronization is not implemented. Touch and D-pad scroll without a Recent output menu step.
- Hold Y inside CHAT/READ/INPUT or Compose to dictate to the current agent; release to finish. Mic is also available by touch and from Input/Actions. A finishes recording or uses a reviewed transcript; Y switches to keyboard. Actual sending still requires recipient/text review and explicit controller acquisition.
- Device microphone tests prove recognition startup, held-Y routing, target binding, release, cancellation and late-callback rejection. No test instruction or recognized transcript was sent to an existing agent. Spoken Korean/English accuracy and long-session comfort remain unverified.
- The encrypted SSH private/public key blobs are byte-for-byte unchanged across the in-place upgrade; all 15 existing encrypted files remain. The default launcher was not changed. A second handheld, HOME role selection/revocation and human button-latency measurements were not tested in this release.

## Artifact

APK SHA-256: `3a7338cd6a8a3f1bffc84bf28b26d1eb6a2113405a56f0bd9848d2a0085c85d6`.

See the sanitized [validation JSON](../../artifacts/validation-summary.json). Private host addresses, agent names, transcripts, screenshots, recognition audio, credentials and raw device logs are excluded from source and release assets.
