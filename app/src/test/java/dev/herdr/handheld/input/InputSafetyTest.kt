package dev.herdr.handheld.input

import dev.herdr.handheld.herdr.*
import org.junit.Assert.*
import org.junit.Test

class InputSafetyTest {
    private val target=TargetRef("host","session-a","term1","w1:p1","codex","agent1")
    private fun connected()=InputSafety().apply { connection(true);invalidate(this@InputSafetyTest.target);observed(generation) }
    @Test fun observerCannotSendAnyInput() { val s=connected();assertFalse(s.canSend(s.ticket()!!)) }
    @Test fun cancelledAcquireCannotRestoreInput() {
        val s=connected();val ticket=s.request()!!;s.invalidate();assertFalse(s.acquired(ticket));assertFalse(s.canSend(ticket))
    }
    @Test fun disconnectedOrSwitchedTargetRejectsOldTickets() {
        val s=connected();val t=s.request()!!;assertTrue(s.acquired(t));assertTrue(s.canSend(t))
        s.invalidate(target.copy(session="session-b"));assertFalse(s.canSend(t))
        val next=s.request()!!;assertTrue(s.acquired(next));s.connection(false);assertFalse(s.canSend(next))
    }
    @Test fun agentExitOrReplacementInvalidatesInput() {
        val s=connected();val t=s.request()!!;s.acquired(t);s.invalidate(target.copy(agentKind=null,agentSessionId=null))
        assertEquals(InputMode.NAVIGATION,s.mode);assertFalse(s.canSend(t))
    }
    @Test fun localNavigationBlocksControllerWrites() {
        val s=connected();val t=s.request()!!;s.acquired(t);s.navigation();assertFalse(s.canSend(t));s.compose();assertTrue(s.canSend(t))
    }
}
