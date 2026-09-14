package dev.herdr.handheld.input

import dev.herdr.handheld.herdr.*

data class InputTicket(val generation: Long, val target: TargetRef)

/** Checked again immediately before a transport write, never used as a server-side lock. */
class InputSafety {
    var generation: Long = 0; private set
    var target: TargetRef? = null; private set
    var access = TerminalAccess.NONE; private set
    var mode = InputMode.NAVIGATION; private set
    var connected = false; private set
    var acquiring = false; private set

    fun invalidate(newTarget: TargetRef? = target): Long {
        generation++
        target = newTarget
        access = TerminalAccess.NONE
        mode = InputMode.NAVIGATION
        acquiring = false
        return generation
    }
    fun connection(ready: Boolean) { connected = ready; if (!ready) invalidate() }
    fun observed(g: Long) { if (g == generation && connected) access = TerminalAccess.OBSERVER }
    fun request(): InputTicket? {
        if (!connected || target == null || acquiring) return null
        acquiring = true
        return InputTicket(generation, target!!)
    }
    fun acquired(ticket: InputTicket): Boolean {
        if (!acquiring || !current(ticket) || !connected) return false
        acquiring = false
        access = TerminalAccess.CONTROLLER
        mode = InputMode.REMOTE_KEYS
        return true
    }
    fun compose() { mode = InputMode.COMPOSE }
    fun navigation() { mode = InputMode.NAVIGATION }
    fun ticket() = target?.let { InputTicket(generation, it) }
    fun current(ticket: InputTicket) = ticket.generation == generation && ticket.target == target
    fun canSend(ticket: InputTicket) = current(ticket) && connected && access == TerminalAccess.CONTROLLER && mode != InputMode.NAVIGATION
}
