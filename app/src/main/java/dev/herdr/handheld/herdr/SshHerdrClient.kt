package dev.herdr.handheld.herdr

import dev.herdr.handheld.ssh.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

class SshHerdrClient(private val profile: HostProfile, private val transport: SshTransport, private val ownsTransport: Boolean=true) : HerdrClient {
    private val builder = HerdrCommandBuilder(profile)
    override suspend fun connect(): Capabilities {
        if(ownsTransport)transport.connect(profile)
        val version = checked(builder.command("--version")).stdout.trim()
        val status = checked(builder.command("status")).stdout
        val serverVersion = Regex("server:[\\s\\S]*?version: ([^\\s]+)").find(status)?.groupValues?.get(1).orEmpty()
        if (!status.contains("status: running")) throw HerdrUnavailable("The selected Herdr session is not running. SSH and Codex remain available.")
        val help = transport.exec(builder.command("terminal", "session", "--help"))
        val schema = checked(builder.command("api", "schema", "--json")).stdout
        val schemaObject = Json.parseToJsonElement(schema).jsonObject
        val supported = version == "herdr 0.9.0" && serverVersion == "0.9.0" && status.contains("endpoint_compatible: yes") &&
            schemaObject["protocol"]?.jsonPrimitive?.intOrNull == 22 &&
            schemaObject["schema_version"]?.jsonPrimitive?.intOrNull == 1 && help.exitCode == 0 &&
            help.stdout.contains("observe") && help.stdout.contains("control")
        return Capabilities(version,serverVersion,supported,supported,true,
            if(supported) "Herdr 0.9.0 terminal contract" else "Unverified terminal contract. Read-only recent output is available; live input is disabled.")
    }
    override suspend fun agents() = HerdrWire.agents(checked(builder.agents()).stdout,profile)
    override suspend fun terminal(target: TargetRef, control: Boolean, cols: Int, rows: Int): TerminalStream =
        SshTerminalStream(transport.open(if(control) builder.control(target,cols,rows) else builder.observe(target,cols,rows)),control)
    override suspend fun recent(target: TargetRef): String {
        // `pane read` is a text-producing CLI command, unlike the JSON socket API.
        // Even JSON-looking agent output must remain literal terminal content.
        return checked(builder.recent(target)).stdout
    }
    override suspend fun disconnect() { if(ownsTransport)transport.disconnect() }
    private suspend fun checked(command: String): ExecResult {
        val result = transport.exec(command)
        if(result.exitCode != 0) throw HerdrUnavailable("Herdr CLI failed (exit ${result.exitCode}). Check executable path, session, and compatibility.")
        return result
    }
}

private class SshTerminalStream(private val channel: SshChannel, private val controller: Boolean) : TerminalStream {
    private val writer = Mutex()
    @Volatile private var closed = false
    override suspend fun read(emit: suspend (TerminalEvent) -> Unit) = withContext(Dispatchers.IO) {
        coroutineScope {
            // stderr must be drained independently, but terminal text/errors are never logged.
            val errors = async(Dispatchers.IO) {
                val scratch = ByteArray(2048); var count = 0
                while(!closed) { val n=channel.stderr.read(scratch); if(n<0) break; count+=n
                    if(count>32768) throw ContractException("Terminal diagnostics exceeded limit") }
            }
            val records = java.util.ArrayDeque<String>()
            val decoder = NdjsonDecoder { records.addLast(it) }
            val buffer = ByteArray(8192)
            try {
                while(currentCoroutineContext().isActive && !closed) {
                    val count = channel.stdout.read(buffer)
                    if(count<0) { decoder.end(); break }
                    decoder.feed(buffer,count)
                    while(records.isNotEmpty()) emit(HerdrWire.envelope(records.removeFirst()))
                }
                if(!closed) emit(TerminalEvent.Closed("Stream ended; return to reading or reconnect."))
            } finally { channel.close(); errors.cancel() }
        }
    }
    override suspend fun input(bytes: ByteArray) {
        check(controller && !closed)
        write(HerdrWire.input(bytes))
    }
    override suspend fun resize(cols: Int, rows: Int) { check(controller && !closed); write(HerdrWire.resize(cols,rows)) }
    private suspend fun write(line: String) = withContext(Dispatchers.IO) {
        writer.withLock { check(!closed); channel.stdin.write(line.toByteArray()); channel.stdin.flush() }
    }
    override suspend fun close() {
        // Closing only this exec channel detaches the bridge; it never signals the pane process.
        closed = true
        channel.close()
    }
}

class HerdrUnavailable(message: String): Exception(message)
