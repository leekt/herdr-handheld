package dev.herdr.handheld.herdr

interface TerminalStream {
    suspend fun read(emit: suspend (TerminalEvent) -> Unit)
    suspend fun input(bytes: ByteArray)
    suspend fun resize(cols: Int, rows: Int)
    suspend fun close()
}
interface HerdrClient {
    suspend fun connect(): Capabilities
    suspend fun agents(): List<AgentTarget>
    suspend fun terminal(target: TargetRef, control: Boolean, cols: Int, rows: Int): TerminalStream
    suspend fun recent(target: TargetRef): String
    suspend fun disconnect()
}
