# Real connection validation — v0.2.0, 2026-09-14

The validation RG Rotate is now paired with the configured development host SSH account and Herdr `default` session over Tailscale. Real mode is enabled and persists across process restarts. No host settings, credentials, public keys, or private terminal screenshots are bundled in the APK or source archive.

## Changes

- A fresh installation starts with real connection setup. The shipping UI has no demo toggle.
- **SSH key → Create device key** generates an Ed25519 OpenSSH key using the existing, unmodified Bouncy Castle library. Only its public key is shown or copied. The private key is encrypted by the existing Android Keystore-backed secret store and stays on the device.
- The development host has one new `authorized_keys` entry for this device, restricted to the RG Rotate's Tailscale address with PTY and forwarding disabled. Existing entries were preserved. No SSH service, gateway, Herdr server, or agent was installed or restarted.
- The first SSH fingerprint was compared with `/etc/ssh/ssh_host_ed25519_key.pub` locally on the host before confirming it in the app.
- Real-host testing found that `herdr pane read --format text` emits plain text, unlike the socket API. The client now preserves that output literally, including JSON-looking content. READ now opens that output automatically. It starts at its latest lines, supports touch and D-pad scrolling, and holds new output separately while the user browses older text.

## Executed checks

| Check | Result |
|---|---|
| Debug APK build and lint | Passed; no lint errors |
| Final combined device run | 3 passed (`RealReadOnlyTest`, `RealAssistantTest`, `RealVoiceTest`), 23.214 seconds |
| Kotlin/JVM suite | 32 passed, including generated-key interoperability with SSHJ and plain-text snapshot regression tests |
| Updated Android SSH fixture test | 1 passed on the RG Rotate; includes actual SSH authentication, host pinning, plain-text snapshots and fragmented terminal output |
| Real Tailscale SSH | Authenticated from RG Rotate to the configured development host account using the device-generated key |
| Live Herdr compatibility | CLI/server 0.9.0, endpoint compatible, socket protocol 22 |
| Agent list | 10 real agents observed during the initial connection; status labels retained |
| Two real terminal targets | Selected and rendered different existing agent outputs through observer streams |
| Controller ownership | Acquired and released against the real server; returned to READ without sending agent input |
| Default reading and hint strips | Opt-in `RealReadOnlyTest` opens two real targets, loads output automatically, switches touch/D-pad scrolling, exercises map/System/Settings/control-confirmation modals, checks hint layout, then returns Home in observer mode |
| Real Codex subscription | Passed on RG Rotate: account/read reports ChatGPT; two real structured turns; the same dedicated conversation resumes after closing/reopening its stdio connection. No proposal auto-execution. |
| Microphone and speech provider | Built-in microphone and Android recognition service reached Listening on the device. Cancellation rejected a late callback; no transcript was sent. Spoken-language quality remains hands-on verification. |
| Cold process restart | Reconnected to the real host and returned to the home list; demo and input mode were not restored |
| Network dependency | The real host uses Tailscale TCP/22, with no ADB reverse tunnel required |

The native visual choice and actual-device review are documented in [design-decisions.md](../product/design.md).

The original v0.1.0 test report remains at [device-validation.md](../devices/rg-rotate.md). Its five-device-test run and original JavaScript renderer checks are historical; the three renderer checks were rerun after the bundled-font/color update. The updated SSH instrumentation was invoked directly with `am instrument`, preserving the now-paired app's settings and credentials.

Existing agents received no task instructions, Enter, Ctrl+C, approvals, or termination commands during this verification. Controller acquisition can temporarily resize the terminal according to Herdr's normal bridge contract. Workspaces and pane focus were not changed.

## Use and revoke

On this RG Rotate, open **PDX** (named Herdr Handheld in v0.2.0) to see the real agent list. A reads an agent; L/R switches agents; A in READ opens control confirmation; B exits input to reading; Start opens System and holding Start returns home. Select tap shows hints for three seconds; holding Select shows the map. The default READ view wraps real output. Swipe or use D-pad to browse, and return to the bottom or tap **Latest ↓** to follow updates. Y opens a draft, and sending requires review.

To revoke this pairing, remove only the `~/.ssh/authorized_keys` line matching this device's public key on the host. Removing the device key or uninstalling the app deletes the local private key, but does not remove the host's public-key entry. No recovery copy of the device private key was exported.

Still requiring hands-on verification: printed-button calibration, long-session readability and comfort, a real prompt response or task instruction chosen by the user, radio loss during a write, and selecting/revoking the HOME role. Quickstep remains the default launcher.

Implementation references: [Bouncy Castle OpenSSH encoding](https://downloads.bouncycastle.org/java/docs/bcprov-jdk18on-javadoc/org/bouncycastle/crypto/util/OpenSSHPrivateKeyUtil.html), [OpenSSH authorized-key restrictions](https://man.openbsd.org/sshd#AUTHORIZED_KEYS_FILE_FORMAT). The pinned library version and actual device results determine support.

The measurements above are preserved as v0.2.0 evidence. Later PDX packaging and regression checks are recorded in the [v0.3.0 report](pdx-0.3.0.md) and the [latest build report](../../artifacts/validation-summary.json); a branding update does not retroactively rerun these tests.
