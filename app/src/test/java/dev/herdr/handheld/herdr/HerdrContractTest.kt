package dev.herdr.handheld.herdr

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class HerdrContractTest {
    private fun fixture(name: String)=javaClass.classLoader!!.getResource("herdr/0.9.0/$name")!!.readText()
    @Test fun capturedAgentShapePreservesStatusAndIdentity() {
        val a=HerdrWire.agents(fixture("agents.json"),HostProfile(session="work"))
        assertEquals(listOf("blocked","working","idle","done","unknown"),a.map { it.status })
        assertEquals("work",a.first().ref.session)
        assertEquals("agent-review-1",a.first().ref.agentSessionId)
    }
    @Test fun everyNetworkSplitDecodesTheSameFrames() {
        val sample=fixture("frames.ndjson").toByteArray()
        for(split in 0..sample.size) {
            val records=mutableListOf<String>();val d=NdjsonDecoder(onLine=records::add)
            d.feed(sample.copyOfRange(0,split));d.feed(sample.copyOfRange(split,sample.size));d.end()
            assertEquals(2,records.size);assertTrue(HerdrWire.envelope(records[0]) is TerminalEvent.Frame)
        }
    }
    @Test fun byteByByteKoreanEnvelope() {
        val lines=mutableListOf<String>();val d=NdjsonDecoder(onLine=lines::add)
        "{\"message\":\"한글 👋\"}\r\n".toByteArray().forEach { d.feed(byteArrayOf(it)) };d.end()
        assertEquals(listOf("{\"message\":\"한글 👋\"}"),lines)
    }
    @Test fun malformedAndOversizedMessagesAreBounded() {
        assertThrows(ContractException::class.java) { NdjsonDecoder(8){}.feed("123456789".toByteArray()) }
        assertThrows(ContractException::class.java) { HerdrWire.envelope("{") }
        assertThrows(ContractException::class.java) { NdjsonDecoder { }.apply { feed("{\"x\":1}".toByteArray());end() } }
        assertThrows(ContractException::class.java) { NdjsonDecoder { }.feed(byteArrayOf(0xFF.toByte(),10)) }
    }
    @Test fun commandsQuoteUntrustedArgumentsAndScopeSessions() {
        assertEquals("'a'\"'\"'b'",HerdrCommandBuilder.quote("a'b"))
        val profile=HostProfile(session="work; touch /tmp/no",binary="/path with space/herdr")
        val b=HerdrCommandBuilder(profile)
        assertTrue(b.agents().contains("'work; touch /tmp/no'"))
        assertThrows(IllegalArgumentException::class.java) { b.observe(TargetRef("primary","other","term","w1:p1",null,null),44,20) }
        assertThrows(IllegalArgumentException::class.java) { HerdrCommandBuilder.quote("bad\nargument") }
    }
    @Test fun userTextIsEncodedOnlyAsControlData() {
        val text="한글; $(touch /tmp/no) `whoami`".toByteArray()
        val payload=HerdrWire.input(text)
        assertFalse(payload.contains("touch"));assertTrue(payload.contains(Base64.getEncoder().encodeToString(text)))
    }
}
