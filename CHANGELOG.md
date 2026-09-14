# Changelog

## 0.4.0 — Conversations and simpler reading

- Keep SSH/Codex available when Herdr is missing or incompatible.
- Add a unified assistant transcript/composer, saved conversations, pinned notes, host branching/compaction, reported usage, streamed answer previews and turn interruption.
- Route concurrent RPC replies and early notifications reliably with bounded queues; bound fresh inventory and optional output independently.
- Remove production demo code and hidden observer/WebView work from default native READ.
- Share controller command descriptions and availability; preserve Select/Start calibration IDs and single-line hints. Extract live terminal ownership and per-target draft persistence.
- Add pinned CI, generated fresh validation reports, optional maintainer signing, privacy-preserving device checks and a new-device report template.
- Preserve installed package identity and existing encrypted credentials. See [executed validation](docs/validation/pdx-0.4.0.md).

## 0.3.0 — PDX

- Rename the product to **PDX — Pocket Dispatch & eXecution**; update the launcher label, icon, terminal title, Codex client title, and new assistant conversation names.
- Organize documentation by product, integrations, devices, development, and validation. Add a device support matrix, architecture map, and context-management/platform roadmap.
- Add reproducible `pdx-debug.apk` packaging and preserve historical build evidence.
- Keep the Android application/component identity and encrypted storage compatible with existing paired installations.
- Keep RG Rotate as the first tested device. Other platforms and the planned context-management controls are not implemented by this release.

## 0.2.0 — Herdr Handheld

Real Herdr read/control integration, automatic touch and D-pad reading, transient Select hints, a persistent host-backed Codex assistant, and reviewed Android dictation. See the [historical report](artifacts/history/v0.2.0/validation.json).

## 0.1.0 — Initial implementation

Independent Android app, controller calibration, SSH/terminal contracts, and initial device/fixture verification. See the [historical report](artifacts/history/v0.1.0/validation.json).
