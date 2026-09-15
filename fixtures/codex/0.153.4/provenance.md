# Codex protocol fixture

CLI: 0.153.4. Source: the installed CLI's `app-server generate-json-schema --experimental` output, checked against the official app-server documentation. Real `initialize`, `account/read`, `thread/start`, `thread/resume`, `thread/name/set`, `turn/start` and item/turn completion exchanges were exercised over stdio and the RG Rotate's SSH channel.

`account.ndjson` is a synthetic, privacy-safe contract fixture. Its fields match the inspected response shapes; paths, account details and request IDs are not captured personal data. The inserted unsupported approval request checks that the client refuses to execute it. It is never loaded by the shipping UI.

Used experimental field: `environments: []` on thread creation and every turn. It is not a thread/resume field. Unsupported installed versions fail visibly; no private tokens or undocumented OAuth flow are used.

The v0.4.1 agent reader also checked real `thread/read` (`includeTurns:false`) and `thread/turns/list` (`limit:6`, `sortDirection:desc`, `itemsView:summary`) against existing Codex sessions, without resuming them. `agent-chat.json` contains synthetic messages in those verified shapes, including opaque pagination cursors. It is only a test fixture. User/agent roles come from typed items; command output and reasoning are not reclassified as messages. Tests assert the exact read-only method sequence and reject mismatched thread identity.
