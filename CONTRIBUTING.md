# Contributing to PDX

PDX is Pocket Dispatch & eXecution. Its first implementation is native Android; device and backend support must be backed by real validation. Start with the [architecture](docs/architecture.md), [documentation index](docs/README.md), and [device report requirements](docs/devices/README.md).

Use JDK 17 and Android SDK 35. Run `./scripts/env.sh :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` and `npm ci --ignore-scripts && npm test` in `terminal-assets/`.

Preserve the native Android UI, local controller routing, explicit terminal control, host-key verification, and reconnect/input-generation checks. Read mode must never send agent input. Codex proposals must pass the native allowlist and recipient review.

Contract fixtures must use synthetic identifiers and output. Do not commit real terminal captures, host profiles, credentials, public keys, account details, device serial numbers, or local paths. Keep device reports clear about automated checks versus hands-on validation. Never run tests that replace preferences or uninstall the app on a paired device.

See [testing](docs/development/testing.md), [design decisions](docs/product/design.md), and [Codex integration](docs/integrations/codex.md).

Keep the stable Android package, Activity, Keystore alias, and preference identifiers unchanged during branding work. Use `scripts/package-debug.sh` for the PDX-named APK; preserve earlier reports in `artifacts/history/`. See the [release procedure](docs/development/releases.md).
