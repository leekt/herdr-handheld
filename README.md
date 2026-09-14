# Herdr Handheld for RG Rotate

An independent Kotlin Android app for supervising existing Herdr agents with a controller. It includes a native agent home, controller calibration, an embedded offline xterm.js renderer, SSH connection management, encrypted keys and drafts, and an optional Android HOME entry point.

No Termux, external terminal, Herdr fork, gateway, local agent, or KVM is required. The app starts with real connection setup and does not change your default launcher. The shipping UI displays real data only; synthetic clients are retained for isolated tests.

## First run

Download the debug APK from this repository's Releases page, or build it below. Open **Herdr Handheld** as a normal app.

1. Use **Settings → Controller lab → Calibrate buttons** to associate the physical buttons with logical actions. The defaults follow Android button codes, not the printed labels.
2. Configure the SSH connection below. Select a real agent with A to read it. A in READ opens a target confirmation; A then requests control; D-pad sends arrows in input mode; A sends one Enter when released; B returns to reading.
3. Use L1/R1 to change agents in read mode. X opens Actions, Y opens the native editor, Start opens System, and holding Start returns to the app home. Hold Select to see the button map.
4. **Y on Home** opens the Codex assistant. Connect your subscription in **Settings → Codex assistant**. It has its own persistent conversation and proposes actions for review. Hold Y to speak; inside INPUT, holding Y dictates a message for the current agent. See [assistant setup and limits](docs/codex-assistant.md).

| Input | Home / read | Input mode |
|---|---|---|
| D-pad | Navigate / local scroll | Remote arrows |
| A | Open / READ control confirmation | One Enter per complete press |
| B | Local back | Release control, return to read |
| X | Actions | Actions; buttons only operate the menu |
| Y tap / hold | Home: assistant / voice assistant; READ: draft / voice assistant | Native draft / dictation review |
| L1 / R1 | Previous / next agent | Disabled |
| Select tap / hold | Hints for 3 seconds / full button map | Hints for 3 seconds / full button map |
| L2 / R2 | Smaller / larger text | Smaller / larger text (local) |
| Start tap / hold | System / app home | Release control, System / app home |

OS Home and Back retain their Android roles. They are not remapped as remote keys.

## Connect to an existing host

The host must already run SSH and an existing Herdr session. The first supported terminal contract is **Herdr CLI and server 0.9.0, socket protocol 22**. See [compatibility](docs/herdr-compatibility.md).

1. In Settings, enter the host/IP, SSH port and username, Herdr session (`default` is explicit), and executable path. Use the absolute Herdr path if it is absent from the noninteractive SSH PATH. `~` is not shell-expanded in this field.
2. Choose **SSH key → Create device key**. The app generates a dedicated Ed25519 key and encrypts its private part on the device. Copy the displayed **public key** into the host account's `~/.ssh/authorized_keys`. Alternatively, import an existing private key with Android's file picker, entering its passphrase first if needed. Other key formats remain unverified.
3. Choose **Connect to host**. Compare the displayed SHA-256 host-key fingerprint with one obtained independently on the server. Trust only the matching key. A changed key is rejected until explicitly verified and replaced.
4. The agent list polls every two seconds while the app is visible. Open a target in read mode, then press A and confirm the target only when you intend to type.

SSH uses the device's existing network. A LAN address or an already configured Tailscale address works if reachable; the app does not install or operate a VPN. No HTTP/WebSocket endpoint or additional public port is required.

The connected RG Rotate has been paired with the development host over Tailscale; those credentials and its host profile live only on the device and are not bundled in this APK or source. See [real connection validation](docs/real-connection-validation.md). Removing the app deletes its private key. Revoke a device by removing only its matching public-key entry from the host's `authorized_keys`.

**Compose** saves an encrypted draft for the full host/session/terminal/agent identity. Review shows the recipient and content. **Send + Enter** sends a complete paste command followed by a separate Enter on the same control channel. **Text only** omits Enter. A successful transport write is not proof of execution or task success. Drafts remain available until cleared. Uncertain delivery is never replayed.

**Read mode opens a wrapping, scrollable view of real recent output automatically.** Swipe or use D-pad Up/Down to scroll; Left/Right moves in larger steps. It opens at the latest lines, refreshes every two seconds while visible, and keeps the displayed text still while you browse older lines. Scroll to the bottom or tap **Latest ↓** to follow again. L1/R1 switches agents; B returns Home. A opens a target confirmation; confirming requests control and opens the live ANSI viewport. The reader contains Herdr’s recent-output window (up to 160 source lines), not an unlimited transcript. Some terminal applications require application-mode arrows; **Settings → Arrow key protocol** switches explicitly between normal CSI and application SS3 sequences because Herdr's screen frames do not export the original keyboard modes.

## Build and install

Prerequisites: JDK 17, Android SDK platform 35, Build Tools 35.0.0, platform-tools/ADB. Set `JAVA_HOME` and `ANDROID_HOME`, or set `sdk.dir` in your untracked `local.properties`. Android 12/API 31 is the minimum device version.

```sh
# Bundled assets are checked in. Regenerate when changing terminal-assets/.
cd terminal-assets
npm ci --ignore-scripts
npm run build
cd ..

./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.herdr.handheld/.MainActivity
```

`scripts/env.sh` runs the wrapper with the optional project-local toolchain used during development. Normal Android Studio/JDK installations do not require `.tools/`. Gradle's distribution checksum, version catalog, Gradle lockfiles, and npm lockfile are committed. This is a directly installable debug APK; a release signing key is not included. Debug signatures generated on different machines may differ.

## Optional home launcher and recovery

After testing the normal app, choose **Settings → Use as home app** and confirm through Android's HOME role chooser. The app never automatically requests or claims the role at startup.

To switch back, choose **Settings → Change home app** and select the previous launcher. **Android settings** and **Open another app** remain available. If the firmware cannot open HOME settings directly, the app falls back to default-app settings and then general Android settings.

If the app becomes unusable, open Android Settings from the system shade, change the default home app, or uninstall this app through Settings/ADB. Do not remove or disable the original launcher. Uninstalling clears this app's profiles, imported keys, and drafts; it does not stop remote agents.

## Validation and limitations

Every app page prioritizes larger content. Select is reserved for help: tap for a bottom hint strip that fades after three seconds, or hold for the full button map. The strip omits obvious D-pad/back controls and keeps button legends on one line. The updated native design uses bare agent rows, a full-screen reader, and dark modals. Bundled Space Grotesk and JetBrains Mono match the reference offline. Status details are in the Select-hold map; Start opens system access. Android system bars can be revealed with an edge swipe. For the measured RG Rotate display, see [design decisions](docs/design-decisions.md).

See [device validation](docs/device-validation.md), [test commands and results](docs/testing.md), and [scope and UX decisions](docs/handoff.md). Build success, injected-event device tests, physical button tests, and hands-on comfort measurements are reported separately.

Long-form editing, local AI inference, automatic approval, background notifications, takeover, host installation/update, and unrestricted device automation are not implemented. The Codex assistant uses the documented app-server interface on the SSH host; terminal messages still require separate confirmation.

Third-party license information is in [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES) and `licenses/`.
