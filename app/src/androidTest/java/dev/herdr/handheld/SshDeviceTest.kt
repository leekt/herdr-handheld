package dev.herdr.handheld

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.herdr.handheld.herdr.*
import dev.herdr.handheld.ssh.*
import dev.herdr.handheld.storage.SecretStore
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SshDeviceTest {
    @Test fun sshjAuthenticationHostTrustChannelsAndChunkedStream()=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runSshFixture")=="true")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val assets=instrumentation.context.assets
        val fixture=Json.parseToJsonElement(assets.open("ssh-fixture.json").bufferedReader().readText()).jsonObject
        val profile=HostProfile(id="instrumentation-ssh",host="127.0.0.1",port=18422,username="fixture",session="fixture")
        val pin="hostkey:127.0.0.1:18422"
        val vault=SecretStore(instrumentation.targetContext)
        var failureDetail=""
        val transport=SshjTransport(vault) { failure -> failureDetail=failure.stackTraceToString() }
        try {
            vault.remove(pin)
            vault.put("ssh:${profile.id}",assets.open("ssh-fixture-key").readBytes())
            val initialFailure=runCatching { transport.connect(profile) }.exceptionOrNull()
            val unknown=initialFailure as? SshFailure
            assertNotNull("First connection must require host verification: $initialFailure\n$failureDetail",unknown?.challenge)
            assertFalse(unknown!!.challenge!!.changed)
            assertEquals(fixture.getValue("fingerprint").jsonPrimitive.content,unknown.challenge!!.fingerprint)
            vault.put(pin,unknown.challenge!!.fingerprint.toByteArray())
            transport.connect(profile)
            val result=transport.exec("probe")
            assertEquals(0,result.exitCode)
            assertEquals("{\"ok\":true}\n",result.stdout)
            assertEquals("fixture diagnostic\n",result.stderr)
            val client=SshHerdrClient(profile,transport)
            assertTrue(client.connect().control)
            val target=client.agents().first()
            val recent=client.recent(target.ref)
            assertTrue(recent.contains("작업 결과"))
            assertTrue(recent.contains("{\"result\":{\"text\":"))
            val channel=client.terminal(target.ref,true,44,18)
            val first=CompletableDeferred<Unit>();val echoed=CompletableDeferred<Unit>()
            val expected="한글 👋 fixture"
            val reader=launch(Dispatchers.IO) {
                channel.read { event -> if(event is TerminalEvent.Frame) {
                    first.complete(Unit)
                    if(event.value.bytes.toString(Charsets.UTF_8).contains(expected))echoed.complete(Unit)
                } }
            }
            try {
                withTimeout(5000) { first.await() }
                channel.input(expected.toByteArray())
                withTimeout(5000) { echoed.await() }
            } finally { channel.close();reader.cancelAndJoin() }
            client.disconnect()
            vault.put(pin,"SHA256:deliberately-wrong-fixture-pin".toByteArray())
            val changed=runCatching { transport.connect(profile) }.exceptionOrNull() as? SshFailure
            assertTrue("Changed host key must be rejected",changed?.challenge?.changed==true)
        } finally {
            transport.disconnect();vault.remove(pin);vault.remove("ssh:${profile.id}")
        }
    }
}
