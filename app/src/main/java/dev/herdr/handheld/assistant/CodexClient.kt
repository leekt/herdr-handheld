package dev.herdr.handheld.assistant

import dev.herdr.handheld.herdr.ContractException
import dev.herdr.handheld.herdr.HerdrCommandBuilder
import dev.herdr.handheld.herdr.NdjsonDecoder
import dev.herdr.handheld.ssh.SshChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.*

/** Existing Codex app-server over an SSH exec channel. No HTTP endpoint or copied account tokens. */
class CodexClient(private val channel: SshChannel) {
    private val rpc=CodexRpc(channel)
    private var loginEvents: Channel<JsonObject>?=null
    @Volatile private var activeTurn: Pair<String,String>?=null
    suspend fun call(method: String,params: JsonObject=buildJsonObject {})=rpc.call(method,params)
    suspend fun initialize() {
        call("initialize",buildJsonObject {
            putJsonObject("clientInfo") { put("name","pdx");put("title","PDX");put("version",dev.herdr.handheld.BuildConfig.VERSION_NAME) }
            // environments:[] disables host environment access on the verified CLI contract.
            putJsonObject("capabilities") { put("experimentalApi",true) }
        })
        rpc.write(buildJsonObject { put("method","initialized");putJsonObject("params") {} })
    }
    suspend fun account(): CodexAccount {
        val value=call("account/read",buildJsonObject { put("refreshToken",false) })["account"] as? JsonObject
        return CodexAccount(value?.text("type").orEmpty(),value?.text("planType").orEmpty())
    }
    suspend fun login(): DeviceLogin {
        loginEvents?.let(rpc::unsubscribe)
        loginEvents=rpc.subscribe()
        val value=call("account/login/start",buildJsonObject { put("type","chatgptDeviceCode") })
        require(value.text("type")=="chatgptDeviceCode")
        val url=value.text("verificationUrl")
        require(url=="https://auth.openai.com/codex/device") { "Unexpected account verification origin" }
        return DeviceLogin(value.text("loginId"),url,value.text("userCode"))
    }
    suspend fun awaitLogin(id: String) = withTimeout(300000) {
        val events=loginEvents ?: throw ContractException("No pending sign-in")
        try { while(true) {
            val value=events.receive();val params=value["params"] as? JsonObject ?: continue
            if(value.text("method")=="account/login/completed" && params.text("loginId")==id) {
                if(params["success"]?.jsonPrimitive?.booleanOrNull!=true)throw ContractException("Sign-in did not complete. Try again from the account screen.")
                break
            }
        } } finally { rpc.unsubscribe(events);loginEvents=null }
    }
    suspend fun resume(resumeId: String): String {
        val config=call("config/read",buildJsonObject { put("includeLayers",false) })["config"] as? JsonObject
        val overrides=buildJsonObject {
            for(flag in listOf("shell_tool","unified_exec","apps","plugins","multi_agent","memories","shell_snapshot"))put("features.$flag",false)
            put("web_search","disabled");put("project_doc_max_bytes",0)
            (config?.get("mcp_servers") as? JsonObject)?.keys?.forEach { put("mcp_servers.$it.enabled",false) }
        }
        return call(if(resumeId.isBlank())"thread/start"else "thread/resume",buildJsonObject {
            if(resumeId.isBlank())put("ephemeral",false)else { put("threadId",resumeId);put("excludeTurns",true) }
            put("cwd","/tmp");put("sandbox","read-only");put("approvalPolicy","never")
            if(resumeId.isBlank())putJsonArray("environments") {};put("config",overrides)
            put("baseInstructions",AssistantContract.instructions)
            put("developerInstructions","Use only the provided launcher data. Never call tools, read files, run commands, or access the host environment. Propose actions for the user to review; never claim execution.")
        })["thread"]?.jsonObject?.text("id") ?: throw ContractException("Missing Codex thread")
    }
    suspend fun ask(prompt: String,resumeId: String,onThread: suspend (String)->Unit,onProgress: (String)->Unit={},onUsage: (Long,Long?)->Unit={ _,_ -> }): AssistantAnswer = withTimeout(180000) {
        val thread=resume(resumeId)
        onThread(thread)
        if(resumeId.isBlank())call("thread/name/set",buildJsonObject { put("threadId",thread);put("name","PDX assistant") })
        val events=rpc.subscribe()
        try {
        val turn=call("turn/start",buildJsonObject {
            put("threadId",thread);putJsonArray("environments") {};putJsonArray("input") { add(buildJsonObject { put("type","text");put("text",prompt) }) }
            put("outputSchema",AssistantContract.schema)
        })
        val turnId=(turn["turn"] as? JsonObject)?.text("id") ?: throw ContractException("Missing Codex turn")
        activeTurn=thread to turnId
        var answer: String?=null
        var partial=""
        while(true) {
            val value=events.receive();val params=value["params"] as? JsonObject ?: continue
            if(params.text("threadId")!=thread)continue
            val eventTurn=params.text("turnId").ifBlank { (params["turn"] as? JsonObject)?.text("id").orEmpty() }
            if(eventTurn.isNotEmpty() && eventTurn!=turnId)continue
            when(value.text("method")) {
                "item/agentMessage/delta" -> {
                    partial+=params.text("delta")
                    if(partial.length>24000)throw ContractException("Assistant answer exceeded its limit")
                    onProgress(answerPreview(partial))
                }
                "thread/tokenUsage/updated" -> {
                    val usage=params["tokenUsage"] as? JsonObject
                    val last=usage?.get("last") as? JsonObject
                    onUsage(last?.get("totalTokens")?.jsonPrimitive?.longOrNull ?: 0,usage?.get("modelContextWindow")?.jsonPrimitive?.longOrNull)
                }
                "item/started", "item/completed" -> {
                    val item=params["item"] as? JsonObject ?: continue
                    if(value.text("method")=="item/completed" && item.text("type")=="agentMessage") answer=item.text("text").takeIf { it.length<=24000 }
                    if(item.text("type") in setOf("commandExecution","fileChange","mcpToolCall","webSearch"))
                        throw ContractException("Unexpected tool activity. Assistant connection closed.")
                }
                "turn/completed" -> {
                    if((params["turn"] as? JsonObject)?.text("status")!="completed")throw ContractException("Assistant turn did not complete. Nothing was applied.")
                    return@withTimeout AssistantContract.decode(answer ?: throw ContractException("Codex returned no answer"))
                }
            }
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
        } finally { activeTurn=null;rpc.unsubscribe(events) }
    }
    suspend fun interrupt() {
        val turn=activeTurn ?: return
        call("turn/interrupt",buildJsonObject { put("threadId",turn.first);put("turnId",turn.second) })
    }
    suspend fun compact(threadId: String) = withTimeout(180000) {
        val events=rpc.subscribe()
        try {
            call("thread/compact/start",buildJsonObject { put("threadId",threadId) })
            while(true) {
                val event=events.receive();val params=event["params"] as? JsonObject ?: continue
                if(params.text("threadId")!=threadId)continue
                if(event.text("method")=="item/completed" && (params["item"] as? JsonObject)?.text("type")=="contextCompaction")break
                if(event.text("method")=="error")throw ContractException("Compaction failed. Your conversation is retained.")
            }
        } finally { rpc.unsubscribe(events) }
    }
    suspend fun fork(threadId: String): String = call("thread/fork",buildJsonObject {
        put("threadId",threadId);put("excludeTurns",true);put("sandbox","read-only");put("approvalPolicy","never")
    })["thread"]?.jsonObject?.text("id") ?: throw ContractException("Missing forked thread")
    suspend fun close() = rpc.close()
    companion object {
        fun command(binary: String): String {
            require(binary.isNotBlank() && binary.length<=512)
            return "exec "+HerdrCommandBuilder.quote(binary)+" app-server --listen stdio://"
        }
    }
}
internal fun JsonObject.text(key: String)=(this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
data class CodexAccount(val type: String="",val plan: String="") { val subscribed get()=type=="chatgpt" }
data class DeviceLogin(val id: String,val url: String,val code: String)
