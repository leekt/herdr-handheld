# ANBERNIC RG Rotate

This report preserves the initial Herdr Handheld v0.1.0 evidence. Later real-mode checks are in the [real connection report](../validation/real-connection.md) and the current [build evidence](../../artifacts/validation-summary.json). See the [device matrix](README.md) for current support and remaining checks.

## Initial validation — 2026-09-14

This report records the initial **v0.1.0** demo/fixture validation. **v0.2.0 is now connected to the real development host Herdr session over Tailscale**; see [the later real-connection report](../validation/real-connection.md) for current results. The initial tests below remain historical evidence, with their original scope and limitations.

## Device observed

| Property | Observed value |
|---|---|
| Model | RG Rotate |
| Firmware build | V1.43 |
| Android | 12 / API 31 |
| Physical display | 720 × 720 pixels |
| Density | 240 dpi; approximately 480 dp square before system insets |
| Current WebView | Google WebView 150.0.7871.181 |
| Fallback WebView | Android WebView 90.0.4430.82; not exercised |
| Controller input device | `retrogame_joypad`, key and HAT event support |
| Device key layout | `Vendor_484b_Product_1101.kl` |
| USB debugging | Authorized; ADB installation and instrumentation succeeded |

The system font-scale setting has no explicit override (`null`). The app uses measured viewport dimensions and device density; no assumption that 720 pixels means 720 dp is made. Printed-button mappings have not been verified by a person holding the device.

## Executed validation

| Class of evidence | Result | Scope |
|---|---|---|
| Android debug build | PASS | Installable `dev.herdr.handheld` APK; API 31 minimum |
| Android lint | PASS, no errors | Remaining warnings recorded in the generated report |
| Local Kotlin/JVM tests | 17 passed | Input edges/generations, target identity, shell quoting, NDJSON bounds/chunks, demo response persistence |
| Local JavaScript tests | 3 passed | Every UTF-8 split, Korean/emoji/combining text, ANSI, alternate screen, OSC handlers |
| RG Rotate instrumentation | 5 passed | Native flow, lifecycle, immediate draft save/review/send, Keystore, WebView, SSH fixture |
| Installed Herdr CLI probe | 8 checks passed | Actual 0.9.0 CLI/server with isolated synthetic terminal; separate temporary configuration |
| Emulator | Not run | Physical RG Rotate used instead |
| Physical printed buttons | Not yet verified | Instrumentation injects Android gamepad events |
| Production SSH + existing agent | Not yet verified | Android SSH fixture and real Herdr CLI probe are separate tests |

The optional SSH device test uses a disposable Ed25519 OpenSSH key and a loopback-only synthetic SSH endpoint through ADB reverse forwarding. It validates fingerprint confirmation/change rejection, authentication, independent stdout/stderr, capability parsing, and fragmented terminal records. Its keys are included only in the test APK, never in the product APK.

The real Herdr probe checks two differently sized observers without a PTY resize, an initial full ANSI frame, exclusive-controller conflict, exact Korean multiline paste and Enter bytes, accepted resize/scroll commands, and survival of the underlying pane after release and stdin EOF. It does not alter the user's existing workspaces.

## Safety checklist and limits

| Requirement | Evidence / remaining limit |
|---|---|
| A long press / duplicate edges | JVM tests: one completed action; no repeat Enter |
| D-pad plus HAT duplication | JVM tests pass; physical timing still requires calibration |
| Read-only default | Device flow and input-state tests pass; renderer has no outbound terminal-data path |
| B / Start local escape | Device flow passes; neither maps to remote Esc or process termination |
| Old target output | Device renderer generation/reset test passes |
| No input replay after disconnect | Input-generation tests and implementation review; real radio loss during a write remains untested |
| Control collision | Real CLI conflict + client observer fallback; full app with two real clients remains untested |
| Agent exits / shell replacement | Identity checks and JVM coverage; real agent transition timing remains untested |
| Equal pane IDs in separate sessions | Full target reference and command-scope tests pass |
| Malformed / oversized NDJSON | Bounded decoder tests pass |
| Korean / emoji / ANSI | JS byte-split tests and device output rendering pass; screenshot review is not full glyph certification |
| Resize / Activity recreation | Device recreation and viewport flow pass; all orientations and IME combinations remain untested |
| Sustained output | 40 sequential acknowledged WebView writes pass; long-running flood and thermal behavior remain untested |
| Host-key change | Rejected on RG Rotate with the SSH fixture |
| Background / resume | Controller released; observer restored in device tests. ADB-triggered screen sleep/wake followed by dismissing the nonsecure lock screen also returned from INPUT to READ in demo |
| Process termination | Explicit local force-stop/relaunch walkthrough; remote EOF survival checked separately in CLI probe |
| HOME selection / revocation | Native chooser and Android default-home settings both opened on RG Rotate and listed Herdr Handheld, Quickstep (current default), and RGLauncher. Default unchanged; selecting/revoking a different default remains unverified |
| External navigation / OSC | Device external-link test and JS OSC tests pass; origin and message allowlists implemented |

## Issues found and fixed on this device

- Terminal initially had zero CSS viewport height in WebView. Fixed the local document to the viewport and verified visible terminal output.
- Native text initially inherited the wrong content color. Set explicit native content colors and dark system bars, then inspected device screenshots.
- Connection notices changed terminal height and could trigger repeated observer resizing. Reserved a fixed notice region.
- Android's built-in reduced `BC` crypto provider lacks SSHJ's X25519 algorithm. Selected the unmodified bundled provider in this app process; Ed25519 SSH authentication now passes.
- A renderer test read the DOM before xterm's next paint. It now waits for the actual painted target text after the parser acknowledgement.
- Immediate Start after editing could beat the draft debounce. Draft saves now capture the target/text before leaving; a physical-device regression test covers recovery and reviewed sending.
- Demo responses disappeared on control release. Demo terminal state now survives stream replacement and stays isolated per target.

Initial screenshots were inspected locally. Screenshots are excluded from the public repository and source distribution.

## Next hands-on session

1. Open **Settings → Controller lab → Calibrate buttons** and press the printed A/B/X/Y, L1/R1, Select and Start buttons. Check D-pad movement for duplication.
2. Repeat home → read → input → respond → B → L/R → home while holding the device. Record readability, finger travel, and accidental presses; the 100 ms physical-feedback target has not been measured.
3. Configure an app-specific SSH key and a reachable existing host/session. Compare the host fingerprint independently, then read two real agents before testing input.
4. After normal-app use is comfortable, choose and revoke the HOME role from Settings. Keep the original launcher installed.

No claim of completed usability validation is made until these hands-on steps are done.
