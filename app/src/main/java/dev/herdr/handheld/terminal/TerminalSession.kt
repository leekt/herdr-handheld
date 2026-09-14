package dev.herdr.handheld.terminal

import dev.herdr.handheld.herdr.*
import kotlinx.coroutines.*

/** Owns the single live controller channel and its renderer. READ snapshots need neither. */
class TerminalSession(private val scope: CoroutineScope) {
    var renderer: TerminalRenderer?=null
    var stream: TerminalStream?=null; private set
    private var job: Job?=null
    private var resizeJob: Job?=null
    fun close() {
        job?.cancel();job=null;resizeJob?.cancel();resizeJob=null
        val old=stream;stream=null
        if(old!=null)scope.launch(Dispatchers.IO) { runCatching { old.close() } }
    }
    fun open(client: HerdrClient,target: TargetRef,generation: Long,cols: Int,rows: Int,font: Int,
             current: ()->Boolean,onFrame: (Boolean)->Unit,onError: (Boolean)->Unit) {
        val render=renderer ?: return
        close()
        job=scope.launch {
            var opened: TerminalStream?=null;var first=true;var lastSeq=-1L;var watchdog: Job?=null
            try {
                render.reset(generation,font)
                if(!current())return@launch
                opened=client.terminal(target,true,cols,rows)
                if(!current())return@launch
                stream=opened
                watchdog=scope.launch { delay(7000);if(first && current())opened.close() }
                opened.read { event -> withContext(Dispatchers.Main) {
                    if(!current())return@withContext
                    when(event) {
                        is TerminalEvent.Frame -> {
                            val frame=event.value
                            if(first && !frame.full)throw ContractException("Initial terminal frame must be full")
                            if(frame.seq<=lastSeq || (!first && !frame.full && frame.seq!=lastSeq+1))throw ContractException("Terminal sequence lost synchronization")
                            render.render(frame,generation)
                            if(!current())return@withContext
                            onFrame(first);first=false;watchdog?.cancel();lastSeq=frame.seq
                        }
                        is TerminalEvent.Closed -> throw ContractException(event.reason)
                    }
                } }
            } catch(e: Exception) {
                if(e is CancellationException && e !is TimeoutCancellationException)throw e
                if(current())onError(first)
            } finally { watchdog?.cancel();withContext(NonCancellable+Dispatchers.IO) { runCatching { opened?.close() } } }
        }
    }
    fun resize(cols: Int,rows: Int,current: ()->Boolean,onError: ()->Unit) {
        resizeJob?.cancel()
        resizeJob=scope.launch { delay(180);if(current())runCatching { stream?.resize(cols,rows) }.onFailure { onError() } }
    }
}
