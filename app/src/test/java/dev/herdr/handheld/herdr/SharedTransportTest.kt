package dev.herdr.handheld.herdr

import dev.herdr.handheld.ssh.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SharedTransportTest {
    @Test fun missingHerdrDoesNotCloseOrReconnectTheSharedSshTransport()=runBlocking {
        var disconnected=false;var connected=false
        val transport=object: SshTransport {
            override suspend fun connect(profile: HostProfile) { connected=true }
            override suspend fun disconnect() { disconnected=true }
            override suspend fun exec(command: String)=ExecResult("","",127)
            override suspend fun open(command: String): SshChannel=error("not needed")
        }
        val client=SshHerdrClient(HostProfile(),transport,ownsTransport=false)
        assertTrue(runCatching { client.connect() }.exceptionOrNull() is HerdrUnavailable)
        client.disconnect()
        assertFalse(connected);assertFalse(disconnected)
    }
}
