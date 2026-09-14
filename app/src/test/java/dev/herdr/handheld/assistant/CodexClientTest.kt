package dev.herdr.handheld.assistant

import dev.herdr.handheld.ssh.SshChannel
import java.io.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Interactive in-memory peer: responses are emitted only after the corresponding request. */
private class Peer(val respond: (JsonObject,(JsonObject)->Unit)->Unit): SshChannel {
    override val stdout=PipedInputStream(65536)
    private val remote=PipedOutputStream(stdout)
    override val stderr=ByteArrayInputStream(byteArrayOf())
    val received=mutableListOf<JsonObject>()
    override val stdin=object: ByteArrayOutputStream() {
        override fun flush() {
            val lines=toString("UTF-8");reset()
            lines.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                val request=Json.parseToJsonElement(line).jsonObject
                synchronized(received) { received.add(request) }
                respond(request) { value -> synchronized(remote) { remote.write((value.toString()+"\n").toByteArray());remote.flush() } }
            }
        }
    }
    override suspend fun close() { remote.close();stdout.close() }
}
private fun reply(request: JsonObject,result: JsonObject=buildJsonObject {})=buildJsonObject { put("id",request.getValue("id"));put("result",result) }
private fun event(method: String,params: JsonObject)=buildJsonObject { put("method",method);put("params",params) }
class CodexClientTest {
    @Test fun notificationsBeforeAcknowledgementAreNotLostAndApprovalsAreDenied()=runBlocking {
        val peer=Peer { request,send -> when(request.text("method")) {
            "initialize" -> { send(buildJsonObject { put("id","server-approval");put("method","item/commandExecution/requestApproval") });send(reply(request)) }
            "account/read" -> send(reply(request,buildJsonObject { putJsonObject("account") { put("type","chatgpt");put("planType","plus") } }))
            "account/login/start" -> {
                send(event("account/login/completed",buildJsonObject { put("loginId","login-1");put("success",true) }))
                send(reply(request,buildJsonObject { put("type","chatgptDeviceCode");put("loginId","login-1");put("verificationUrl","https://auth.openai.com/codex/device");put("userCode","TEST") }))
            }
        } }
        val client=CodexClient(peer)
        try {
            client.initialize();assertTrue(client.account().subscribed)
            val login=client.login();withTimeout(2000) { client.awaitLogin(login.id) }
            assertTrue(peer.received.any { it["error"]?.jsonObject?.get("code")?.jsonPrimitive?.intOrNull == -32601 })
        } finally { client.close() }
    }
    @Test fun reverseOrderRepliesAndInterleavedEventsReachTheirOwners()=runBlocking {
        var first: JsonObject?=null
        val peer=Peer { request,send ->
            if(first==null)first=request else {
                send(event("thread/tokenUsage/updated",buildJsonObject { put("threadId","thread") }))
                send(reply(request,buildJsonObject { put("value",request.text("method")) }))
                send(reply(first!!,buildJsonObject { put("value",first!!.text("method")) }))
            }
        }
        val rpc=CodexRpc(peer);val events=rpc.subscribe()
        try {
            val a=async { rpc.call("a") };val b=async { rpc.call("b") }
            withTimeout(2000) { assertEquals("a",a.await().text("value"));assertEquals("b",b.await().text("value"));assertEquals("thread/tokenUsage/updated",events.receive().text("method")) }
        } finally { rpc.unsubscribe(events);rpc.close() }
    }
    @Test fun completeTurnBeforeStartReplyStillProducesOneValidatedAnswer()=runBlocking {
        val raw="""{"answer":"안녕 👋","actions":[]}"""
        val peer=Peer { request,send -> when(request.text("method")) {
            "config/read" -> send(reply(request))
            "thread/start" -> send(reply(request,buildJsonObject { putJsonObject("thread") { put("id","thread") } }))
            "thread/name/set" -> send(reply(request))
            "turn/start" -> {
                send(event("item/agentMessage/delta",buildJsonObject { put("threadId","thread");put("turnId","turn");put("delta",raw) }))
                send(event("item/completed",buildJsonObject { put("threadId","thread");put("turnId","turn");putJsonObject("item") { put("type","agentMessage");put("text",raw) } }))
                send(event("turn/completed",buildJsonObject { put("threadId","thread");putJsonObject("turn") { put("id","turn");put("status","completed") } }))
                send(reply(request,buildJsonObject { putJsonObject("turn") { put("id","turn") } }))
            }
        } }
        val client=CodexClient(peer);var preview=""
        try {
            val answer=withTimeout(3000) { client.ask("test","",{},onProgress={preview=it}) }
            assertEquals("안녕 👋",answer.answer);assertEquals(answer.answer,preview)
        } finally { client.close() }
    }
    @Test fun previewDoesNotExposeActionsOrIncompleteEscapes() {
        assertEquals("Hi\n",answerPreview("""{"answer":"Hi\n\uD83D"""))
        assertEquals("Hi",answerPreview("""{"answer":"Hi","actions":[{"text":"secret"}]}"""))
        assertEquals("",answerPreview("{\"actions\":[]}"))
    }
}
