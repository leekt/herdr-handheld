package dev.herdr.handheld.assistant

import dev.herdr.handheld.herdr.ContractException
import dev.herdr.handheld.herdr.NdjsonDecoder
import dev.herdr.handheld.ssh.SshChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** One reader demultiplexes replies and notifications; subscriptions precede triggering requests. */
internal class CodexRpc(private val channel: SshChannel) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sequence = AtomicInteger()
    private val writer = Mutex()
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
    private val subscribers = ConcurrentHashMap.newKeySet<Channel<JsonObject>>()
    @Volatile private var failure: Throwable? = null
    init {
        scope.launch {
            try {
                val records = java.util.ArrayDeque<String>()
                val decoder = NdjsonDecoder(1024 * 1024) { records.addLast(it) }
                val bytes = ByteArray(8192)
                while (isActive) {
                    val n = channel.stdout.read(bytes)
                    if (n < 0) { decoder.end(); break }
                    decoder.feed(bytes, n)
                    while (records.isNotEmpty()) route(Json.parseToJsonElement(records.removeFirst()).jsonObject)
                }
                throw ContractException("Codex channel closed. Reconnect; the last request is not retried.")
            } catch (e: Exception) { fail(e) }
        }
        scope.launch { runCatching { val bytes=ByteArray(2048); while(isActive && channel.stderr.read(bytes)>=0) {} } }
    }
    private suspend fun route(value: JsonObject) {
        if(value["method"] != null) {
            if(value["id"] != null) {
                write(buildJsonObject { put("id",value.getValue("id")); putJsonObject("error") {
                    put("code",-32601); put("message","This launcher does not execute server tool requests")
                } })
            } else for (subscriber in subscribers) {
                if (!subscriber.trySend(value).isSuccess) throw ContractException("Codex event limit exceeded. Reconnect; nothing is replayed.")
            }
        } else value["id"]?.jsonPrimitive?.intOrNull?.let { pending.remove(it)?.complete(value) }
    }
    fun subscribe(): Channel<JsonObject> {
        val events=Channel<JsonObject>(256)
        subscribers.add(events)
        failure?.let { events.close(it) }
        return events
    }
    fun unsubscribe(events: Channel<JsonObject>) { subscribers.remove(events); events.cancel() }
    suspend fun write(message: JsonObject) = writer.withLock {
        withContext(Dispatchers.IO) {
            failure?.let { throw it }
            val bytes=(message.toString()+"\n").toByteArray()
            require(bytes.size<=128*1024)
            channel.stdin.write(bytes); channel.stdin.flush()
        }
    }
    suspend fun call(method: String, params: JsonObject=buildJsonObject {}): JsonObject = withTimeout(30000) {
        val id=sequence.incrementAndGet()
        val response=CompletableDeferred<JsonObject>()
        pending[id]=response
        try {
            failure?.let { throw it }
            write(buildJsonObject { put("id",id); put("method",method); put("params",params) })
            val value=response.await()
            if(value["error"]!=null) throw ContractException("Codex rejected $method. Check the installed CLI contract and account.")
            value["result"] as? JsonObject ?: throw ContractException("Codex response is incompatible")
        } finally { pending.remove(id); response.cancel() }
    }
    private fun fail(error: Throwable) {
        failure=error
        pending.values.forEach { it.completeExceptionally(error) }; pending.clear()
        subscribers.forEach { it.close(error) }
    }
    suspend fun close() = withContext(NonCancellable) {
        fail(CancellationException("Codex disconnected"))
        channel.close(); scope.cancel()
    }
}

/** Show only the answer string while structured JSON arrives. Proposals wait for full validation. */
internal fun answerPreview(raw: String): String {
    val start=Regex("\\\"answer\\\"\\s*:\\s*\\\"").find(raw)?.range?.last?.plus(1) ?: return ""
    val value=StringBuilder(); var i=start
    while(i<raw.length && value.length<6000) {
        val c=raw[i++]
        if(c=='"')break
        if(c!='\\') { value.append(c); continue }
        if(i==raw.length)break
        when(val escape=raw[i++]) {
            '"','\\','/' -> value.append(escape)
            'n' -> value.append('\n'); 'r' -> value.append('\r'); 't' -> value.append('\t')
            'b' -> value.append('\b'); 'f' -> value.append('\u000c')
            'u' -> { if(i+4>raw.length)break; val code=raw.substring(i,i+4).toIntOrNull(16) ?: break; value.append(code.toChar()); i+=4 }
            else -> break
        }
    }
    // An incomplete surrogate pair must not briefly render a replacement glyph.
    if(value.isNotEmpty() && value.last().isHighSurrogate())value.deleteCharAt(value.lastIndex)
    return value.toString()
}
