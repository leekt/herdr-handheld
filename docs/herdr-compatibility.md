# Herdr compatibility

## Verified baseline

- Installed CLI: `herdr 0.9.0`.
- Existing server: 0.9.0, endpoint-compatible, private/socket protocol 22.
- CLI schema: schema version 1, protocol 22. This is the socket API schema, not a complete terminal bridge schema.
- Public implementation inspected at tag `v0.9.0`: `src/client/terminal_sessions.rs`, `src/server/headless.rs`, `src/server/pane_input.rs`, and `src/server/render_stream.rs`. The source was inspected without changing or vendoring Herdr.
- Actual installed CLI behavior tested in a disposable `handheld-contract` session with a separate temporary config directory. Existing user sessions were not stopped, restarted, resized, or sent input by these probes.

See `artifacts/contract-probe.json` and `fixtures/herdr/0.9.0/`. Fixtures are either redacted captured shapes or synthetic output from the isolated test terminal. User terminal output is not included.

| Capability | Baseline | App behavior |
|---|---|---|
| Agent list | Captured actual JSON envelope | Preserves reported status and identity |
| Recent output | `pane read --format text` emits plain UTF-8 stdout | Displays literal text; never parses the content as a JSON wrapper |
| Named session | Explicit `--session` on every call | Session-scoped command builder |
| Observe | Initial full frame + ANSI frame stream | Default read mode |
| Two observers | Separate viewport dimensions; no underlying PTY resize | Reading does not acquire resize ownership |
| Control | First frame follows successful acquisition | No input until first full frame is rendered |
| Existing controller | `terminal.closed` with owner-conflict reason | Returns to observer; no takeover |
| Input | Text or base64 bytes, never both | Base64 stdin payload |
| Multiline paste | Exact complete bracketed paste recognized server-side | One paste record, then optional separate Enter |
| Resize | Positive cols/rows accepted by controller | Debounced; observer size changes reopen observation |
| Scroll | Controller command exists | Observer remote scroll disabled; native recent-output view available |
| Release / stdin EOF | Detaches connection, underlying pane remains alive | Client never issues pane close/server stop |
| Keyboard modes in frames | Original modes are not exported | Explicit normal/application arrow profile; no inference from renderer |
| Automatic xterm responses | Not needed for rendered-screen stream | All renderer outbound terminal data blocked |
| Other Herdr versions | Unverified | Live terminal/control disabled; snapshot path only where list/read/schema commands work |

## Exact terminal envelopes

Output is NDJSON, not one record per network read:

```json
{"type":"terminal.frame","seq":1,"encoding":"ansi","width":44,"height":18,"full":true,"bytes":"BASE64_ANSI_BYTES"}
{"type":"terminal.closed","reason":"optional server reason"}
```

Actual control inputs:

```json
{"type":"terminal.input","bytes":"BASE64_INPUT_BYTES"}
{"type":"terminal.input","text":"alternative UTF-8 text field"}
{"type":"terminal.resize","cols":44,"rows":18,"cell_width_px":0,"cell_height_px":0}
{"type":"terminal.scroll","direction":"up","lines":3,"source":"wheel"}
{"type":"terminal.release"}
```

Resize cell dimensions default to zero. Scroll direction is `up|down`; source is `wheel|page_key`, with optional column, row, and modifiers. These fields come from the installed-version source and actual probe, not inferred API names.

Malformed control commands are ignored with an error on stderr. Successful stdin writes do **not** produce per-input delivery or execution acknowledgements. Unexpected stdout EOF also ends a stream; a `terminal.closed` record is not guaranteed on every disconnect. The app shows transport success or uncertain delivery, never agent success.

## Viewport and input limits

Observe renders the existing terminal state into the observer's rectangle without resizing the PTY. This does not promise independent reflow of a full-screen TUI: content outside that rectangle can be absent. The app includes a wrapping recent-output snapshot for reading, and does not describe renderer scrollback as a complete command transcript. Controller acquisition can resize the underlying terminal; releasing it lets existing Herdr geometry policies take effect again.

0.9.0 recognizes a complete `ESC[200~ ... ESC[201~` input record as paste, adapting it to the remote terminal's actual bracketed-paste state. An Enter appended inside the same record defeats that complete-paste detection, so the app sends Enter separately on the same channel. A disconnect between these writes is uncertain and is not retried. Pasting into a terminal without bracketed-paste protection has ordinary interactive-terminal limits; choose the target and review multiline content carefully.

Normal CSI arrows and application SS3 arrows are explicit settings. Additional keyboard protocols, every agent TUI, and every SSH key format are not claimed as verified. Local generation and refreshed agent identity reduce mistaken input but cannot provide a server-side atomic guarantee between checking the agent and writing to its terminal.

## SSH on Android

SSHJ 0.40.0 with bundled Bouncy Castle 1.80.2 has been exercised on the RG Rotate's Android 12 using a disposable Ed25519 OpenSSH key and an authenticated loopback SSH fixture forwarded through ADB. First-use and changed-host-key behavior, stdout/stderr separation, and a fragmented terminal stream were tested. Selecting Android's reduced `BC` provider initially failed with missing X25519; selecting the bundled provider in the app process resolved it.

The original transport fixture and real Herdr CLI probe are separate tests. The subsequent v0.2.0 build has also connected from the RG Rotate over Tailscale to the development host's real SSH account and existing default Herdr session. Live agent listing, observation of two targets, and controller acquisition/release were verified. Sending actual task instructions to existing agents was not part of that verification. See [real connection validation](real-connection-validation.md).

References: [Herdr CLI](https://herdr.dev/docs/cli-reference/), [terminal bridge](https://herdr.dev/docs/persistence-remote/), [socket schema](https://herdr.dev/docs/socket-api/), [0.9.0 bridge source](https://github.com/herdrdev/herdr/blob/v0.9.0/src/client/terminal_sessions.rs), [SSHJ](https://github.com/hierynomus/sshj), [xterm security](https://xtermjs.org/docs/guides/security/).
