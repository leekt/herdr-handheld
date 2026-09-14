package dev.herdr.handheld.herdr

import kotlinx.serialization.Serializable

@Serializable
data class HostProfile(
    val id: String = "primary",
    val name: String = "Development machine",
    val host: String = "",
    val port: Int = 22,
    val username: String = "",
    val session: String = "default",
    val binary: String = "herdr",
)

@Serializable
data class TargetRef(
    val profileId: String,
    val session: String,
    val terminalId: String,
    val paneId: String,
    val agentKind: String?,
    val agentSessionId: String?,
) {
    val key: String get() = listOf(profileId, session, terminalId, paneId, agentKind.orEmpty(), agentSessionId.orEmpty()).joinToString("\u001f")
}

@Serializable
data class AgentTarget(val ref: TargetRef, val title: String, val status: String, val workspace: String = "") {
    val needsResponse get() = status == "blocked"
}

data class TerminalFrame(val seq: Long, val width: Int, val height: Int, val full: Boolean, val bytes: ByteArray)
sealed interface TerminalEvent {
    data class Frame(val value: TerminalFrame) : TerminalEvent
    data class Closed(val reason: String) : TerminalEvent
}

data class Capabilities(
    val cliVersion: String,
    val serverVersion: String,
    val liveTerminal: Boolean,
    val control: Boolean,
    val recentOutput: Boolean,
    val notes: String,
)

enum class InputMode { NAVIGATION, REMOTE_KEYS, COMPOSE }
enum class TerminalAccess { NONE, OBSERVER, CONTROLLER }
enum class ConnectionPhase { DISCONNECTED, CONNECTING, AUTH_REQUIRED, READY, RECONNECTING, OFFLINE }
enum class Screen { HOME, TERMINAL, COMPOSE, ACTIONS, SETTINGS, DIAGNOSTICS, APPS, SYSTEM, CONTROL, ASSISTANT, CODEX, VOICE, CONVERSATION }
