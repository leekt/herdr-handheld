package dev.herdr.handheld.herdr

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class DemoHerdrClient : HerdrClient {
    private val terminalStates=mutableMapOf<String,DemoTerminalState>()
    private val targets = listOf(
        AgentTarget(TargetRef("demo","studio","demo-review","demo:p1","codex","demo-review-1"),"Review authentication","blocked","handheld"),
        AgentTarget(TargetRef("demo","studio","demo-build","demo:p2","claude","demo-build-1"),"Build controller support","working","handheld"),
        AgentTarget(TargetRef("demo","studio","demo-tests","demo:p3","codex","demo-tests-1"),"Check reconnect behavior","done","handheld"),
        AgentTarget(TargetRef("demo","studio","demo-notes","demo:p4","claude","demo-notes-1"),"Explore next steps","unknown","handheld"),
    )
    override suspend fun connect(): Capabilities { delay(150); return Capabilities("Demo","Demo",true,true,true,"Simulated agents • no network or remote input") }
    override suspend fun agents() = targets
    override suspend fun terminal(target: TargetRef, control: Boolean, cols: Int, rows: Int): TerminalStream =
        DemoTerminal(target,control,cols,rows,terminalStates.getOrPut(target.key) { DemoTerminalState() })
    override suspend fun recent(target: TargetRef) = "DEMO · simulated recent output\n\n${targets.first { it.ref == target }.title}\n\n"+
        (terminalStates[target.key]?.received?.takeIf { it.isNotBlank() } ?: "The reconnect path now returns to read mode.\nNo remote agent was contacted.\n\n한글과 emoji 확인: 작업 내용을 읽고 다음 단계를 선택하세요. ✓")
    override suspend fun disconnect() {}
}

private class DemoTerminalState(var selected: Int=0,var received: String="",var pasted: Boolean=false)

private class DemoTerminal(private val target: TargetRef, private val controller: Boolean, private var cols: Int, private var rows: Int,private val state: DemoTerminalState) : TerminalStream {
    private val updates = Channel<Unit>(Channel.CONFLATED)
    private var selected: Int get()=state.selected;set(value) { state.selected=value }
    private var received: String get()=state.received;set(value) { state.received=value }
    private var seq = 0L
    private var closed = false
    override suspend fun read(emit: suspend (TerminalEvent) -> Unit) {
        emit(TerminalEvent.Frame(frame()))
        for (ignored in updates) { if(closed) break; emit(TerminalEvent.Frame(frame())) }
    }
    override suspend fun input(bytes: ByteArray) {
        check(controller && !closed)
        val text=bytes.toString(Charsets.UTF_8)
        when(text) {
            "\u001b[A", "\u001bOA" -> selected=(selected+2)%3
            "\u001b[B", "\u001bOB" -> selected=(selected+1)%3
            "\r" -> if(state.pasted) state.pasted=false else received="Option ${selected+1} sent in demo.\n\nThis is a simulated response.\nNo remote agent was contacted."
            "\u0003" -> received="Ctrl+C sent in demo."
            else -> {
                state.pasted=text.startsWith("\u001b[200~")
                received=if(text.contains("Suggest up to three concrete next steps"))
                    "Simulated next steps\n\n1. Inspect the proposed changes.\n   Check what will change first.\n\n2. Run the focused tests.\n   Confirm the expected behavior.\n\n3. Review the remaining risks.\n   Decide what needs your input.\n\nChoose a next step when ready."
                else "Text received in demo (${bytes.size} bytes).\n\nThis is a simulated response.\nNo remote agent was contacted."
            }
        }
        updates.trySend(Unit)
    }
    override suspend fun resize(cols: Int, rows: Int) { this.cols=cols;this.rows=rows;updates.trySend(Unit) }
    override suspend fun close() { closed=true; updates.close() }
    private fun frame(): TerminalFrame {
        val text = buildString {
            append("\u001b[?2004h\u001b[?1l\u001b[2J\u001b[H")
            append("\u001b[36mDEMO / ${target.agentKind}\u001b[0m\r\n\r\n")
            if(received.isNotEmpty()) {
                append(received.replace("\n","\r\n"));append("\r\n")
                return@buildString
            }
            when(target.terminalId) {
                "demo-review" -> {
                    append("I found two authentication edge cases.\r\n")
                    append("Review them before continuing.\r\n\r\n")
                    listOf("Show the proposed changes", "Explain the remaining risks", "Keep the current behavior").forEachIndexed { i,v ->
                        append(if(i==selected) "\u001b[30;104m› " else "  ");append("${i+1}. $v");append("\u001b[0m\r\n")
                    }
                    append("\r\n읽은 뒤 직접 선택하세요.\r\n")
                }
                "demo-build" -> append("Controller input router\r\n\r\n\u001b[32m✓\u001b[0m One Enter per complete press\r\n\u001b[32m✓\u001b[0m D-pad / HAT deduplication\r\n\u001b[33m…\u001b[0m Testing sleep and resume\r\n\r\nThe agent is working. You can read\r\nthis output without interrupting it.\r\n")
                "demo-tests" -> append("Reconnect review complete\r\n\r\n+ Return to observer after reconnect\r\n+ Keep unsent drafts on this device\r\n+ Never replay uncertain input\r\n\r\nHerdr reports: done\r\nRead the result before deciding\r\nwhether the work meets your needs.\r\n")
                else -> append("What should happen next?\r\n\r\nUse X → Ask for next steps.\r\nReview the prompt and send it to\r\nthe selected agent. It can propose\r\noptions for you to choose from.\r\n\r\nSuggestions never execute themselves.\r\n")
            }
        }
        return TerminalFrame(++seq,cols,rows,true,text.toByteArray())
    }
}
