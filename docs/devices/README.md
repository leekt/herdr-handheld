# Handheld support

PDX aims to support handheld gaming devices. The shipped implementation currently requires **Android 12 / API 31 or later**. No Linux, SteamOS, Windows, or closed-console build is available yet.

| Device/platform | Status | Evidence |
|---|---|---|
| ANBERNIC RG Rotate, Android 12 | First tested device; real SSH/Herdr, reading, Codex, and microphone startup exercised | [Measurements](rg-rotate.md), [real connection report](../validation/real-connection.md) |
| Other Android 12+ handhelds | Candidate devices; not yet validated | Use the report below |
| Linux / SteamOS / Windows handhelds | Future platform work; Android APK is not a native implementation for these systems | [Roadmap](../product/roadmap.md) |
| Closed console platforms | No implementation or support claim | Requires a supported distribution and runtime path |

Even on RG Rotate, physical printed-button calibration, voice recognition quality, HOME selection/revocation, and long-session comfort have remaining hands-on checks. The device does not need to be rooted; gamepad and microphone hardware are optional in the manifest. Voice also requires an available Android recognition provider.

## Add a device report

Create `docs/devices/<manufacturer>-<model>.md` and record:

1. PDX version, device model, firmware, Android/API version, and WebView version.
2. Physical pixels, density, font scale, usable window dimensions, and supported orientations.
3. Actual key codes and HAT/axis events; duplicated D-pad sources; calibration results for printed buttons.
4. Real host connection and integration versions, with host addresses, account names, agent titles, and serial numbers removed.
5. Touch/D-pad scrolling, one-line Select hints, completed A presses, B/Start escape behavior, text/IME resizing, and background/reconnect behavior.
6. Microphone/provider availability and separately the quality of spoken dictation.
7. Optional HOME selection, Android settings access, opening other apps, and returning to the previous launcher.
8. Separate results for automated tests, injected device events, physical button checks, and hands-on comfort. Mark untested items explicitly.

Use the same APK and calibration before adding model-specific code. Never make network settings, private controller identifiers, or device serials part of a committed default profile. The existing coordinate-based read UI test targets RG Rotate; it must be adapted and reported before being used as evidence for another display.
