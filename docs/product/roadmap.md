# PDX roadmap

## Implemented in v0.4

- Independent SSH, Herdr and Codex availability.
- Unified assistant transcript/composer with reviewed actions, encrypted conversation picker, pinned notes, host branching/compaction and reported token usage.
- Correlated JSON-RPC replies and bounded notifications; streaming answer previews and explicit turn interruption.
- Default native snapshot reading without a hidden WebView or observer; live controller initialized on explicit input.
- Shared controller command descriptions, per-target draft storage and a separate terminal-session owner.
- Reproducible checks, pinned CI actions, generated validation reports and optional maintainer release signing.

Actual verification is recorded in [v0.4 validation](../validation/pdx-0.4.0.md). Codex is the conversation runtime; Pi is not a dependency. The local transcript limit is not the model context limit.

## Next device evidence

The first physical target remains RG Rotate. Narrow and wide constrained Android layouts are regression coverage, not certification of a second handheld. Obtain community results from another Android 12+ handheld for button events, density, HOME selection, microphone quality and recovery. Use the [device report template](../devices/report-template.md).

Evaluate Linux/SteamOS or Windows only with a concrete target device. Keep the Android app working and retain SSH, target identity, controller ownership and reviewed input contracts. Arbitrary game consoles are not promised to run an Android APK.

## Remaining product decisions

- Measure sustained reading/input latency and battery impact during actual handheld use; startup or layout checks alone cannot establish fatigue or energy improvements.
- Validate spoken Korean/English dictation quality with the user's selected speech provider.
- Decide a stable public signing-key custody process before changing from the current maintainer-signed debug update channel to production-signed distribution.
- Add action capabilities only with exact native contracts and review paths. Arbitrary device control and creating/killing Herdr sessions remain unsupported.

KVM, local inference, background gateways, automatic approval and replacement orchestration remain outside scope.
