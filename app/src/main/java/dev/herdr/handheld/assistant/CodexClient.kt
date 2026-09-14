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
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val messages=Channel<JsonObject>(64)
    private var sequence=0
    private var closed=false
    init {
        scope.launch {
            try {
                val decoder=NdjsonDecoder(1024*1024) { line ->
                    val message=Json.parseToJsonElement(line).jsonObject
                    if(!messages.trySend(message).isSuccess) throw ContractException("Codex response queue exceeded its limit")
                }
                val bytes=ByteArray(8192)
                while(isActive) { val n=channel.stdout.read(bytes);if(n<0)break;decoder.feed(bytes,n) }
                decoder.end();messages.close()
            } catch(e: Exception) { messages.close(e) }
        }
        // Drain diagnostics separately. Neither prompts nor remote stderr enter Android logs.
        scope.launch { runCatching { val buffer=ByteArray(2048);while(isActive && channel.stderr.read(buffer)>=0){} } }
    }
    private suspend fun write(message: JsonObject)=withContext(Dispatchers.IO) {
        val bytes=(message.toString()+"\n").toByteArray()
        require(bytes.size<=128*1024)
        channel.stdin.write(bytes);channel.stdin.flush()
    }
    private suspend fun next(): JsonObject {
        while(true) {
            val value=messages.receive()
            if(value["method"]!=null && value["id"]!=null) {
                // Never approve arbitrary commands, permission requests, MCP calls or elicitation.
                write(buildJsonObject { put("id",value.getValue("id"));putJsonObject("error") { put("code",-32601);put("message","This launcher does not execute server tool requests") } })
                continue
            }
            return value
        }
    }
    suspend fun call(method: String,params: JsonObject=buildJsonObject {}): JsonObject=withTimeout(30000) {
        val id=++sequence
        write(buildJsonObject { put("id",id);put("method",method);put("params",params) })
        while(true) {
            val value=next()
            if(value["id"]?.jsonPrimitive?.intOrNull==id) {
                if(value["error"]!=null)throw ContractException("Codex rejected $method. Check the installed CLI version and account.")
                return@withTimeout value["result"] as? JsonObject ?: throw ContractException("Codex response is incompatible")
            }
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }
    suspend fun initialize() {
        call("initialize",buildJsonObject {
            putJsonObject("clientInfo") { put("name","pdx");put("title","PDX");put("version",dev.herdr.handheld.BuildConfig.VERSION_NAME) }
            // environments:[] disables host environment access on the verified CLI contract.
            putJsonObject("capabilities") { put("experimentalApi",true) }
        })
        write(buildJsonObject { put("method","initialized");putJsonObject("params") {} })
    }
    suspend fun account(): CodexAccount {
        val value=call("account/read",buildJsonObject { put("refreshToken",false) })["account"] as? JsonObject
        return CodexAccount(value?.text("type").orEmpty(),value?.text("planType").orEmpty())
    }
    suspend fun login(): DeviceLogin {
        val value=call("account/login/start",buildJsonObject { put("type","chatgptDeviceCode") })
        require(value.text("type")=="chatgptDeviceCode")
        val url=value.text("verificationUrl")
        require(url=="https://auth.openai.com/codex/device") { "Unexpected account verification origin" }
        return DeviceLogin(value.text("loginId"),url,value.text("userCode"))
    }
    suspend fun awaitLogin(id: String) = withTimeout(300000) {
        while(true) {
            val value=next();val params=value["params"] as? JsonObject ?: continue
            if(value.text("method")=="account/login/completed" && params.text("loginId")==id) {
                if(params["success"]?.jsonPrimitive?.booleanOrNull!=true)throw ContractException("Sign-in did not complete. Try again from the account screen.")
                break
            }
        }
    }
    suspend fun ask(prompt: String,resumeId: String,onThread: suspend (String)->Unit): AssistantAnswer = withTimeout(180000) {
        val config=call("config/read",buildJsonObject { put("includeLayers",false) })["config"] as? JsonObject
        val overrides=buildJsonObject {
            for(flag in listOf("shell_tool","unified_exec","apps","plugins","multi_agent","memories","shell_snapshot"))put("features.$flag",false)
            put("web_search","disabled");put("project_doc_max_bytes",0)
            (config?.get("mcp_servers") as? JsonObject)?.keys?.forEach { put("mcp_servers.$it.enabled",false) }
        }
        val thread=call(if(resumeId.isBlank())"thread/start"else "thread/resume",buildJsonObject {
            if(resumeId.isBlank())put("ephemeral",false)else put("threadId",resumeId)
            put("cwd","/tmp");put("sandbox","read-only");put("approvalPolicy","never")
            if(resumeId.isBlank())putJsonArray("environments") {};put("config",overrides)
            put("baseInstructions",AssistantContract.instructions)
            put("developerInstructions","Use only the provided launcher data. Never call tools, read files, run commands, or access the host environment. Propose actions for the user to review; never claim execution.")
        })["thread"]?.jsonObject?.text("id") ?: throw ContractException("Missing Codex thread")
        onThread(thread)
        if(resumeId.isBlank())call("thread/name/set",buildJsonObject { put("threadId",thread);put("name","PDX assistant") })
        call("turn/start",buildJsonObject {
            put("threadId",thread);putJsonArray("environments") {};putJsonArray("input") { add(buildJsonObject { put("type","text");put("text",prompt) }) }
            put("outputSchema",AssistantContract.schema)
        })
        var answer: String?=null
        while(true) {
            val value=next();val params=value["params"] as? JsonObject ?: continue
            if(params.text("threadId")!=thread)continue
            when(value.text("method")) {
                "item/completed" -> {
                    val item=params["item"] as? JsonObject ?: continue
                    if(item.text("type")=="agentMessage") answer=item.text("text").takeIf { it.length<=24000 }
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
    }
    suspend fun close() {
        if(closed)return;closed=true
        withContext(NonCancellable) { channel.close();scope.cancel();messages.cancel() }
    }
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
