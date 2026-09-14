# PDX roadmap

The current release is a real Android client for existing Herdr agents and a host-backed Codex assistant. These entries describe remaining work, not implemented features.

## Context management

1. Replace the single active assistant thread reference with an encrypted conversation index, retaining old conversations when starting a new one.
2. Add reliable Codex notification routing before exposing context usage, compaction progress, or branching. Check the installed CLI's schema and exercise actual exchanges.
3. Add editable pinned goals, preferences, and decisions scoped to the appropriate host/project. Store live agent status separately and refresh it before acting.
4. Add native Conversations and Context screens using the existing large-content layout and Select help. Voice and keyboard share the selected conversation.
5. Test process restart, reconnect, cross-host isolation, compaction continuity, and rejection of stale target proposals.

The ten locally retained messages are a display cache, not the Codex model's context limit. Codex remains the conversation runtime. Pi is not a dependency; an alternative assistant backend would require a separate implemented and tested integration.

## More handhelds

1. Validate a second Android 12+ handheld with the same APK, actual button calibration, and a different screen shape/density.
2. Address measured layout/input issues and publish device-specific evidence under `docs/devices/`.
3. Evaluate a native Linux/SteamOS or Windows client when a target device is available. Preserve the SSH, Herdr, input safety, and assistant review contracts. Keep the Android implementation working during that work.

Broader platform support is an objective, not a promise that arbitrary gaming consoles can install this APK.

## Assistant capabilities

The assistant can propose navigation, message drafts, app/settings access, and local text-size changes. Conversation browsing, durable pinned memory, arbitrary device control, and Herdr session creation/termination are not implemented. Any added action needs an explicit native contract, target validation, and an appropriate review path.

KVM, local inference, background gateways, automatic approval, and replacement orchestration remain outside the current product scope.
