# Codex assistant and voice

The launcher uses the user's existing Codex CLI on the configured SSH host. It opens `codex app-server --listen stdio://` on a separate SSH exec channel. No public port, new gateway, copied account token, or API-key billing is involved. This is an independent client integration, not an OpenAI product or an endorsement.

Open Assistant with Y on Home, or from terminal Actions. Hold Y outside INPUT to speak to the assistant. Within INPUT or Compose, hold Y for a plain message transcript for the current agent. Release Y to finish; review the transcript, then use the normal message review to send. Keyboard input follows the same path.

## Account

Settings → Codex assistant → Connect subscription reads the host's actual account. Standard Codex install locations are discovered, or an executable path can be entered. An existing ChatGPT sign-in is reused. If none exists, Sign in with ChatGPT requests the documented device-code flow and displays its actual address and code. Device-code availability depends on the account/CLI. The launcher does not log the shared host account out. Disconnect closes this launcher connection only.

The account page has no QR option, token-storage cards, invented account data, or mock sign-in code. SSH credentials stay in the existing encrypted device store; Codex manages its account credentials on the host.

## Dedicated conversation

The assistant creates its own named, durable Codex thread, scoped to the configured host profile and Herdr session. Subsequent requests resume that exact thread, including after disconnect. It never attaches to an existing Herdr agent's Codex thread. A small recent transcript and the thread reference are encrypted locally; Codex retains the full conversation on the host. The Context page shows recent exchanges. New conversation starts fresh without deleting the previous host conversation.

Every turn supplies a fresh real agent inventory. Stable opaque aliases map back to full host/session/terminal/pane/agent identity locally. Prior inventory and action proposals are historical; the app never replays them on reconnect. Terminal output is excluded unless included for that request. Requests also include installed application names/identifiers to support app selection. Account credentials and SSH host addresses are not part of the prompt.

## Execution boundary

Codex returns a bounded structured answer and up to three proposed actions. Native code validates every action. Supported actions are reading an agent, preparing a message draft, showing the response-needed/all filter, refreshing, opening app/home/settings screens, opening a known installed app, or changing the launcher's font size. Opening Android Wi-Fi/display settings is supported; changing Wi-Fi credentials, arbitrary device automation, and creating/killing Herdr agents/sessions are not implemented.

Selecting a proposed message opens Compose. Sending requires its separate recipient/text confirmation and a newly verified Herdr controller. The assistant cannot automatically approve prompts, steal control, bypass the terminal bridge, execute arbitrary shell commands, or silently retry uncertain delivery.

The Codex thread uses read-only sandboxing, no approvals, disabled shell/apps/plugins/MCP/web-search capabilities, and an empty environment list on each turn. Unsupported server tool/approval requests are rejected. The launcher never offers an SSH execution function to the model. Experimental `environments` fields are checked against the installed CLI's generated schema. An incompatible CLI fails visibly; it is not automatically upgraded.

## Voice

Android `SpeechRecognizer` is used with an explicit microphone permission and the selected Android speech provider. The provider may send audio over the network; offline transcription is not claimed. This does not use an OpenAI transcription REST endpoint or assume a ChatGPT subscription includes API credit. Choose device language, Korean, or English in Codex account.

The app stores no audio recording. Late recognition callbacks are rejected by recording ID, and dictation remains bound to its original recipient. Backgrounding/cancellation closes the microphone and prevents a late transcript from becoming input. No transcript is sent until the user reviews it.

## Compatibility and validation

Implementation contract: Codex CLI 0.153.4, generated local JSON Schema and the official [app-server documentation](https://learn.chatgpt.com/docs/app-server) and [authentication documentation](https://learn.chatgpt.com/docs/auth). Device test results are recorded separately in [real connection validation](real-connection-validation.md). Presence of a microphone/provider is not proof of spoken Korean/English recognition quality.
