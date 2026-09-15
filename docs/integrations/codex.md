# Codex integration: assistant and voice

PDX uses the user's existing Codex CLI on the configured SSH host. It opens `codex app-server --listen stdio://` on a separate SSH exec channel. No public port, new gateway, copied account token, or API-key billing is involved. This is an independent client integration, not an OpenAI product or an endorsement.

Open Assistant with Y on Home, or from terminal Actions. Hold Y on Home or Assistant to speak to the assistant. Within an agent’s CHAT, READ, INPUT or Compose screen, hold Y to dictate a message for that agent. Mic is also available by touch and in the Input/Actions menus. Release Y to finish; review the transcript, then use the normal message review to send. Keyboard input follows the same path.

## Account

Settings → Codex assistant → Connect subscription reads the host's actual account. Standard Codex install locations are discovered, or an executable path can be entered. An existing ChatGPT sign-in is reused. If none exists, Sign in with ChatGPT requests the documented device-code flow and displays its actual address and code. Device-code availability depends on the account/CLI. The launcher does not log the shared host account out. Disconnect closes this launcher connection only.

The account page has no QR option, token-storage cards, invented account data, or mock sign-in code. SSH credentials stay in the existing encrypted device store; Codex manages its account credentials on the host.

## Dedicated conversation

The assistant creates its own named, durable Codex thread (new threads are named "PDX assistant"), scoped to the configured host profile and Herdr session. Subsequent requests resume that exact thread, including after disconnect. It never attaches to an existing Herdr agent's Codex thread. A small recent transcript and the thread reference are encrypted locally; Codex retains the full conversation on the host. The Assistant page combines recent exchanges, composer and reviewed proposals. Chats selects earlier conversations or starts a new one without deleting host threads. Local encrypted storage migrates the old single-thread pointer, retaining its backup. Up to 100 conversation entries and 20 recent messages per conversation are retained, with a 48 KiB transcript budget; the host keeps the full thread. Pinned notes are editable per conversation and included in future requests.

When Herdr is ready and fresh, each request supplies a bounded real inventory (selected, mentioned and blocked agents first, up to 12). Otherwise the request marks Herdr unavailable and supplies no stale agent targets. SSH/Codex can still answer device questions. Stable opaque aliases map back to full host/session/terminal/pane/agent identity locally. Prior inventory and action proposals are historical; the app never replays them on reconnect. Terminal output is excluded unless included for that request. Only installed applications named in the request are included, up to 12. Omitted counts are explicit. Agent inventory and optional output have separate timestamps; selected output is limited to 8,000 characters. Account credentials and SSH host addresses are not part of the prompt.

## Execution boundary

Codex returns a bounded structured answer and up to three proposed actions. Native code validates every action. Supported actions are reading an agent, preparing a message draft, showing the response-needed/all filter, refreshing, opening app/home/settings screens, opening a known installed app, or changing the launcher's font size. Opening Android Wi-Fi/display settings is supported; changing Wi-Fi credentials, arbitrary device automation, and creating/killing Herdr agents/sessions are not implemented.

Selecting a proposed message opens Compose. Sending requires its separate recipient/text confirmation and a newly verified Herdr controller. The assistant cannot automatically approve prompts, steal control, bypass the terminal bridge, execute arbitrary shell commands, or silently retry uncertain delivery.

The Codex thread uses read-only sandboxing, no approvals, disabled shell/apps/plugins/MCP/web-search capabilities, and an empty environment list on each turn. Unsupported server tool/approval requests are rejected. The launcher never offers an SSH execution function to the model. Experimental `environments` fields are checked against the installed CLI's generated schema. An incompatible CLI fails visibly; it is not automatically upgraded.

## Voice

Android `SpeechRecognizer` is used with an explicit microphone permission and the selected Android speech provider. The provider may send audio over the network; offline transcription is not claimed. This does not use an OpenAI transcription REST endpoint or assume a ChatGPT subscription includes API credit. Choose device language, Korean, or English in Codex account.

The app stores no audio recording. Late recognition callbacks are rejected by recording ID, and dictation remains bound to its original recipient. Backgrounding/cancellation closes the microphone and prevents a late transcript from becoming input. No transcript is sent until the user reviews it.

## Compatibility and validation

Implementation contract: Codex CLI 0.153.4, generated local JSON Schema and the official [app-server documentation](https://learn.chatgpt.com/docs/app-server) and [authentication documentation](https://learn.chatgpt.com/docs/auth). Device test results are recorded separately in [real connection validation](../validation/real-connection.md). Presence of a microphone/provider is not proof of spoken Korean/English recognition quality.

## Conversation controls

Chats offers explicit branch and confirmed compaction commands. A branch selects a new host thread while retaining the original entry, recent transcript and notes. Compaction resumes the current thread with restricted tools, calls `thread/compact/start`, and waits for its `contextCompaction` item to complete. It keeps local transcript and pinned notes. A rejected/unsupported operation leaves the existing conversation available.

The assistant displays the last turn's token usage and model context window when Codex reports them. This is reported usage, not an inferred percentage of remaining capacity. Stop uses `turn/interrupt` when a turn ID is known, then closes this client's channel. Backgrounding closes the client and does not automatically replay requests.

Wire methods/fields were checked against CLI 0.153.4's generated schema. `thread/resume` uses `excludeTurns` to avoid sending a full historical transcript back through the handheld; it does not accept the experimental `environments` field used by `thread/start` and `turn/start`. Event subscriptions are established before requests and filter thread/turn IDs. Tests exercise notifications arriving before request acknowledgements.

See [v0.4 validation](../validation/pdx-0.4.0.md) for the operations exercised on the actual host and RG Rotate. Older reports remain historical evidence.

## Agent chat reading (v0.4.1)

Supported Codex agents open in a native chat view. This is the existing agent’s saved conversation, separate from the PDX assistant thread. Herdr’s reported agent session ID is passed to `thread/read` with `includeTurns:false`; the returned identity must match. `thread/turns/list` reads six newest turns with `itemsView:summary`, and an opaque cursor loads an earlier page. These read-only calls neither resume nor subscribe to the agent thread. No `turn/start`, fork, compaction, name change or history edit is used on an agent’s thread.

This experimental history contract was checked against installed Codex CLI 0.153.4 and real sessions. Non-Codex agents and missing session IDs use the colored Terminal view. For supported agent identities, unavailable history or an unverified CLI version shows a Messages error with Retry; the user can explicitly select Terminal. The chat displays actual user/agent items from persisted display summaries; tool output and live choices remain in Terminal. It is not a live token stream or a complete tool transcript. History refreshes on the visible latest page without overlapping requests; scrolling pauses visible updates. Earlier pages stay still until Latest is selected. No agent chat is written to local disk. Markdown is rendered as native styled text; HTML, remote images and automatic link navigation are disabled.

Chat replies and voice transcripts still pass through the existing recipient review and explicit Herdr controller acquisition. Reading chat never uses a second Codex turn to send a reply to an active agent.

Contract source: [Codex App Server — read threads and page turns](https://learn.chatgpt.com/docs/app-server#read-a-stored-thread-without-resuming). Fixtures contain synthetic content following verified response shapes, not user history.

In v0.4.2, the user-facing name is Messages. Both read views expose Messages / Terminal tabs; the first Actions row provides the same toggle (X, then A). Opening an agent, returning from controller input and resuming the app select Messages for supported identities. Read failures never silently select Terminal. Choosing Terminal remains an explicit temporary reading choice.
