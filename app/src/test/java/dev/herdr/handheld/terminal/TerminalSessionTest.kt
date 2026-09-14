package dev.herdr.handheld.terminal

import dev.herdr.handheld.herdr.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TerminalSessionTest {
    @Test fun controllerRequiresFullInitialFrameAndRejectsPreviousGeneration()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val incoming=Channel<TerminalEvent>(8)
        var closeCount=0;var opened=false;var failed=false;var frames=0;var current=true
        val session=TerminalSession(this)
        session.renderer=object: TerminalRenderer {
            override suspend fun reset(generation: Long,fontSize: Int) {}
            override suspend fun render(frame: TerminalFrame,generation: Long) { frames++ }
            override fun scroll(lines: Int,generation: Long) {}
            override fun font(size: Int,generation: Long) {}
        }
        val client=object: HerdrClient {
            override suspend fun connect(): Capabilities=error("not used")
            override suspend fun agents()=emptyList<AgentTarget>()
            override suspend fun recent(target: TargetRef)=""
            override suspend fun disconnect() {}
            override suspend fun terminal(target: TargetRef,control: Boolean,cols: Int,rows: Int): TerminalStream {
                assertTrue(control);opened=true
                return object: TerminalStream {
                    override suspend fun read(emit: suspend (TerminalEvent)->Unit) { for(event in incoming)emit(event) }
                    override suspend fun input(bytes: ByteArray)=error("No input requested")
                    override suspend fun resize(cols: Int,rows: Int) {}
                    override suspend fun close() { closeCount++ }
                }
            }
        }
        try {
            val target=TargetRef("host","named","terminal","pane",null,null)
            session.open(client,target,1,40,20,17,{current},{},{failed=it})
            runCurrent();assertTrue(opened)
            incoming.send(TerminalEvent.Frame(TerminalFrame(1,40,20,false,"partial".toByteArray())))
            runCurrent();assertTrue(failed);assertEquals(0,frames)
            session.close()
            session.open(client,target,2,40,20,17,{current},{},{error("unexpected stream error")})
            runCurrent()
            incoming.send(TerminalEvent.Frame(TerminalFrame(1,40,20,true,"initial".toByteArray())))
            runCurrent();assertEquals(1,frames)
            current=false
            incoming.send(TerminalEvent.Frame(TerminalFrame(2,40,20,false,"late".toByteArray())))
            runCurrent();assertEquals(1,frames)
        } finally {
            session.close();incoming.close();runCurrent()
            coroutineContext.job.children.toList().joinAll()
            Dispatchers.resetMain()
        }
        assertTrue(closeCount>0)
    }
}
