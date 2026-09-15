package dev.herdr.handheld.herdr

import dev.herdr.handheld.ssh.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentOutputTest {
    @Test fun requestsAnsiInTheChosenSessionAndPreservesItsBytes()=runBlocking {
        assertTrue(HerdrCommandBuilder(profile).recent(target).endsWith("'--format' 'ansi'"))
        val text="\u001b[38;2;137;180;250mactual color\u001b[0m\n"
        assertEquals(text,client(text).recent(target))
    }
    private val profile=HostProfile(id="unit",session="named")
    private val target=TargetRef("unit","named","opaque-terminal","opaque-pane","codex",null)
    private fun client(output: String)=SshHerdrClient(profile,object: SshTransport {
        override suspend fun connect(profile: HostProfile) {}
        override suspend fun disconnect() {}
        override suspend fun exec(command: String)=ExecResult(output,"",0)
        override suspend fun open(command: String): SshChannel=error("Snapshot must use exec, not a terminal stream")
    })
    @Test fun paneReadPreservesPlainUtf8AndLineBreaks()=runBlocking {
        val text="작업 결과 👋\n  Indented output\n\nReady for your next instruction.\n"
        assertEquals(text,client(text).recent(target))
    }
    @Test fun jsonLookingAgentOutputIsStillLiteralText()=runBlocking {
        val text="{\"result\":{\"text\":\"This JSON belongs to the agent output\"}}\n"
        assertEquals(text,client(text).recent(target))
    }
}
