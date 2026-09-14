# PDX product scope

**PDX — Pocket Dispatch & eXecution.** Native Android is the current implementation; broader handheld support is tracked in the [roadmap](roadmap.md) and [device matrix](../devices/README.md).

Product intent: home → agent needing attention → read output → explicitly enter input or compose → respond → check another agent → home/sleep. A single independent Android app owns these screens and its controller input. Existing Herdr and remote agents remain unchanged.

The implementation uses one Android app module, Kotlin/Compose, coroutines/StateFlow, DataStore, SSHJ behind `SshTransport`, and bundled xterm.js inside a restricted local WebView. No forked app or terminal component is used.

## Accepted changes from the design review

- v0.2.0 starts in real mode. The paired device displays actual Herdr agents, statuses, and output. Synthetic examples remain confined to isolated tests.
- Native handheld styling replaces the default web-theme reference, as requested by the user.
- Modal Actions consume controller navigation locally. Compose releases control, saves a target-specific encrypted draft, and reacquires control only when the reviewed draft is explicitly sent.
- Late controller acquisition is rejected by a local generation. Target changes, backgrounding, and focus loss invalidate input and clear button presses.
- Supported Herdr behavior is pinned to a measured 0.9.0 contract. A generic schema-driven protocol adapter is intentionally not introduced.
- The default READ view is native, wrapping recent output with touch and D-pad scrolling, refreshed through the measured plain-text CLI. It freezes visible content while browsing; Latest resumes following. The ANSI viewport appears when explicitly entering input. Observer reads do not resize the remote PTY; controller acquisition can resize it.
- The user’s `Launcher design scope` references inform the bare Home list, white focus outline, full-screen reader, grey/amber mode edge, modal panels, bundled reference fonts, and transient bottom button hints. See [design decisions](design.md). Every page prioritizes larger content. Select tap reveals hints for three seconds; holding it shows the map. Obvious D-pad/back hints are omitted; errors remain visible.

## Next-step assistance

The Codex assistant connects the user's ChatGPT subscription through the existing host's Codex app-server over SSH stdio. It owns a separate durable conversation, resumes context, refreshes real agent inventory per turn, and produces allowlisted proposed actions. Keyboard and Android speech input share a review path; hold Y in INPUT dictates a target-bound message instead. See [Codex assistant](../integrations/codex.md). No hidden terminal submission or automatic approval is added.

## State and input rules

`InputSafety` tracks connection readiness, a full target reference, generation, `NAVIGATION | REMOTE_KEYS | COMPOSE`, and `NONE | OBSERVER | CONTROLLER`. `ConnectionCoordinator` owns polling and one live stream. Native events are processed by `AndroidInputRouter`; its platform-independent `InputRouter` emits one completed non-directional action per press and deduplicates D-pad/HAT edges.

- Controller acquisition succeeds locally only after a valid initial full frame has been rendered.
- B cancels pending acquisition. A late result cannot restore input.
- Every write validates its generation, control access, freshness, current stream, and freshly queried agent identity. These checks are not a server-side atomic identity lock.
- Input has no offline queue. Busy input is not accumulated. Any possible partial write requires the user to inspect output; no automatic retry occurs.
- L/R cannot change targets in remote-key mode. Start and B never send remote termination keys. Start tap opens System and releases control; hold returns Home. A in READ opens control confirmation. Select is help-only: tap for transient hints, hold for the map until release.
- Reconnection refreshes capabilities and targets, then reopens an observer. Controller state is not restored.
- Polls are serial, every two seconds while visible. Transport liveness, last successful list check, and last frame are distinct; a quiet terminal is not assumed disconnected solely because it produces no frames.
- Native/renderer queues are bounded. The renderer acknowledges one outstanding write, and malformed, oversized, missing-sequence, or stalled output closes the stream for resynchronization. No ANSI byte fragments are dropped.

## Security boundary

Host keys use explicit SHA-256 pinning. SSH executable, session, and target arguments are shell-quoted; prompt content goes through control-channel stdin. Private keys, passphrases, and drafts use Keystore-backed AES-GCM blobs in `noBackupFilesDir`, with backup and device transfer exclusions. Terminal output is kept in memory only.

The WebView loads only bundled terminal assets. It blocks external navigation, networking, downloads, new windows, permissions, OSC clipboard/link/image handlers, and all emulator outbound data. The native message bridge verifies origin and main frame, enforces a 2 KiB limit, and allows only readiness, viewport, reset, and write acknowledgements. JavaScript cannot access SSH or credentials.

SSHJ needs the unmodified bundled Bouncy Castle provider selected explicitly in this app process on Android 12; the reduced Android provider with the same `BC` name lacks X25519. AndroidKeyStore and platform providers otherwise retain their positions. This is not an OS provider installation.

## Work still requiring user environment or hands-on validation

- Pairing is complete in v0.2.0: a device-generated key connects to the development host over Tailscale. Real list/observe/control acquisition have been exercised; submitting actual agent prompts remains hands-on work.
- Physically calibrate the printed buttons and record any firmware-specific mappings; injected events are not a substitute.
- Select and revoke the HOME role manually on this firmware, and assess long-session comfort and button latency while holding the device.
- Check other SSH key formats, encrypted-key combinations, server versions, and nonstandard terminal keyboard protocols before declaring them supported.

No KVM, Clutch, app/Herdr fork, external terminal dependency, local agent, always-on service, boot receiver, automatic approval, or new orchestration engine is included.
