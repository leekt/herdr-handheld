# Contributing

Use JDK 17 and Android SDK 35. Run `./scripts/env.sh :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` and `npm ci && npm test` in `terminal-assets/`.

Preserve the native Android UI, local controller routing, explicit terminal control, host-key verification, and reconnect/input-generation checks. Read mode must never send agent input. Codex proposals must pass the native allowlist and recipient review.

Contract fixtures must use synthetic identifiers and output. Do not commit real terminal captures, host profiles, credentials, public keys, account details, device serial numbers, or local paths. Keep device reports clear about automated checks versus hands-on validation. Never run tests that replace preferences or uninstall the app on a paired device.

See [testing](docs/testing.md), [design decisions](docs/design-decisions.md), and [Codex integration](docs/codex-assistant.md).
