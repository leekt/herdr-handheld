package dev.herdr.handheld.herdr

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DemoClientTest {
    @Test fun nextStepResponseSurvivesControlReleaseAndTargetSwitch()=runBlocking {
        val client=DemoHerdrClient()
        val targets=client.agents()
        val first=targets.first().ref
        val controller=client.terminal(first,true,44,18)
        controller.input("\u001b[200~Suggest up to three concrete next steps\u001b[201~".toByteArray())
        controller.input(byteArrayOf(13))
        controller.close()
        assertFalse(client.recent(targets[1].ref).contains("Simulated next steps"))
        var output=""
        val observer=client.terminal(first,false,44,18)
        observer.read { event ->
            if(event is TerminalEvent.Frame)output=event.value.bytes.toString(Charsets.UTF_8)
            observer.close()
        }
        assertTrue(output.contains("Simulated next steps"))
        assertTrue(output.contains("Inspect the proposed changes"))
    }
}
