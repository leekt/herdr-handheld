package dev.herdr.handheld.assistant

import dev.herdr.handheld.ssh.SshChannel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CodexClientTest {
    @Test fun fragmentedResponsesKeepAccountFieldsSeparateAndRejectServerApprovals()=runBlocking {
        val fixture=javaClass.classLoader!!.getResourceAsStream("codex/0.153.4/account.ndjson")!!.readBytes()
        val output=ByteArrayOutputStream()
        val channel=object: SshChannel {
            override val stdout=object: ByteArrayInputStream(fixture) {
                override fun read(b: ByteArray,off: Int,len: Int)=super.read(b,off,minOf(len,3))
            }
            override val stderr=ByteArrayInputStream(byteArrayOf())
            override val stdin=output
            override suspend fun close() {}
        }
        val client=CodexClient(channel)
        try {
            client.initialize()
            assertTrue(client.account().subscribed)
            val written=output.toString("UTF-8")
            assertTrue(written.contains("initialized"))
            assertTrue(written.contains("\"code\":-32601"))
            assertFalse(written.contains("approved"))
        } finally { client.close() }
    }
}
