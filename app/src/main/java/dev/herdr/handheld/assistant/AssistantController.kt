package dev.herdr.handheld.assistant

import dev.herdr.handheld.herdr.ContractException
import dev.herdr.handheld.herdr.HerdrCommandBuilder
import dev.herdr.handheld.ssh.SshTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.herdr.handheld.storage.SecretStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

data class AssistantState(
    val binary: String="codex",val version: String="",val account: CodexAccount=CodexAccount(),
    val connected: Boolean=false,val busy: Boolean=false,val draft: String="",val includeOutput: Boolean=false,
    val status: String="Connect Codex using the account on your SSH host.",val login: DeviceLogin?=null,
    val preview: String="",val tokens: Long=0,val contextWindow: Long?=null,
    val conversations: List<ConversationEntry> = emptyList(),val notesDraft: String="",
    val answer: AssistantAnswer?=null,val context: AssistantContext?=null,val memory: AssistantMemory=AssistantMemory(),
)
class AssistantController(private val scope: CoroutineScope,private val transport: ()->SshTransport?,private val memoryScope: ()->String,private val secrets: SecretStore,private val persistBinary: (String)->Unit) {
    private val mutable=MutableStateFlow(AssistantState())
    val state=mutable.asStateFlow()
    private var client: CodexClient?=null
    private var job: Job?=null
    private var epoch=0L
    private var loadedScope=""
    private var index=ConversationIndex()
    private val storageLock=Mutex()
    fun binary(value: String) { if(value.length<=512)mutable.update { it.copy(binary=value) } }
    fun edit(value: String) { if(value.length<=8000)mutable.update { it.copy(draft=value) } }
    fun includeOutput(value: Boolean) { mutable.update { it.copy(includeOutput=value) } }
    fun clearAnswer() { mutable.update { it.copy(answer=null,context=null) } }
    fun notice(value: String) { mutable.update { it.copy(status=value) } }
    fun stop(message: String="Assistant stopped. No actions were applied.") {
        epoch++;job?.cancel();job=null
        val old=client;client=null
        if(old!=null)scope.launch { old.close() }
        mutable.update { it.copy(busy=false,connected=false,login=null,answer=null,context=null,status=message) }
    }
    suspend fun restoreContext() {
        val bound=memoryScope()
        if(loadedScope==bound)return
        storageLock.withLock {
            val loaded=withContext(Dispatchers.IO) {
                val saved=secrets.get("conversations:$bound")?.let { Json.decodeFromString<ConversationIndex>(it.toString(Charsets.UTF_8)) }
                if(saved!=null) {
                    val memory=secrets.get("conversation:$bound:${saved.selected}")?.let { Json.decodeFromString<AssistantMemory>(it.toString(Charsets.UTF_8)) }
                        ?: throw ContractException("Conversation storage is incomplete. Previous host threads are retained.")
                    saved to memory
                } else {
                    // Migrate the existing pointer without removing its encrypted backup.
                    val memory=secrets.get("assistant:$bound")?.let { Json.decodeFromString<AssistantMemory>(it.toString(Charsets.UTF_8)) } ?: AssistantMemory()
                    val migrated=ConversationIndex().select(memory)
                    secrets.put("conversation:$bound:${memory.id}",Json.encodeToString(memory).toByteArray())
                    secrets.put("conversations:$bound",Json.encodeToString(migrated).toByteArray())
                    migrated to memory
                }
            }
            if(memoryScope()!=bound)return@withLock
            index=loaded.first;loadedScope=bound
            mutable.update { it.copy(memory=loaded.second,conversations=index.entries,notesDraft=loaded.second.notes,answer=null,context=null,preview="") }
        }
    }
    private suspend fun persist(memory: AssistantMemory,bound: String=loadedScope) = storageLock.withLock {
        if(bound!=memoryScope() || bound!=loadedScope)throw CancellationException("Conversation scope changed")
        val updated=index.select(memory)
        withContext(Dispatchers.IO) {
            secrets.put("conversation:$bound:${memory.id}",Json.encodeToString(memory).toByteArray())
            secrets.put("conversations:$bound",Json.encodeToString(updated).toByteArray())
        }
        if(bound!=memoryScope())return@withLock
        index=updated
        mutable.update { it.copy(memory=memory,conversations=index.entries,notesDraft=memory.notes) }
    }
    fun notes(value: String) { if(value.length<=4000)mutable.update { it.copy(notesDraft=value) } }
    fun saveNotes() {
        if(state.value.busy)return
        local { persist(state.value.memory.copy(notes=state.value.notesDraft));notice("Pinned notes saved for this conversation.") }
    }
    private fun local(block: suspend ()->Unit) {
        if(state.value.busy)return
        mutable.update { it.copy(busy=true) }
        val current=epoch
        job=scope.launch {
            try { restoreContext();block() }
            catch(e: Exception) { if(e is CancellationException)throw e;notice("Conversation could not be saved. Your previous data is retained.") }
            finally { if(current==epoch)mutable.update { it.copy(busy=false) } }
        }
    }
    fun selectConversation(id: String) {
        if(state.value.busy || index.entries.none { it.id==id })return
        local {
            val bound=loadedScope
            val memory=withContext(Dispatchers.IO) { secrets.get("conversation:$bound:$id")?.let { Json.decodeFromString<AssistantMemory>(it.toString(Charsets.UTF_8)) } }
                ?: throw ContractException("Conversation missing")
            persist(memory,bound);clearAnswer();mutable.update { it.copy(draft="",preview="",tokens=0,contextWindow=null,status="Conversation selected.") }
        }
    }
    private suspend fun ensure(): CodexClient {
        restoreContext()
        client?.let { return it }
        val ssh=transport() ?: throw ContractException("Connect your SSH host first.")
        var path=state.value.binary.trim()
        if(path=="codex") {
            // Only standard install locations; remote user strings never become shell fragments.
            val found=ssh.exec("command -v codex || { for p in /opt/homebrew/bin/codex /usr/local/bin/codex \"\u0024HOME/.local/bin/codex\" \"\u0024HOME/.npm-global/bin/codex\"; do if [ -x \"\u0024p\" ]; then printf '%s\\n' \"\u0024p\"; break; fi; done; }")
            found.stdout.trim().takeIf { it.isNotBlank() && !it.contains('\n') }?.let { path=it }
        }
        val version=ssh.exec(HerdrCommandBuilder.quote(path)+" --version")
        if(version.exitCode!=0 || !version.stdout.startsWith("codex-cli "))throw ContractException("Codex CLI was not found. Enter its executable path in Codex account.")
        val current=CodexClient(ssh.open(CodexClient.command(path)))
        client=current
        current.initialize()
        val account=current.account()
        mutable.update { it.copy(binary=path,version=version.stdout.trim().take(80),account=account,connected=true,
            status=if(account.subscribed)"ChatGPT subscription connected."else "Sign in with ChatGPT to use your Codex subscription.") }
        persistBinary(path)
        return current
    }
    private fun run(block: suspend (CodexClient)->Unit) {
        if(state.value.busy)return
        val currentEpoch=epoch
        mutable.update { it.copy(busy=true,status="Connecting to Codex…") }
        job=scope.launch {
            try { block(ensure()) }
            catch(e: Exception) {
                if(e is CancellationException && e !is TimeoutCancellationException)throw e
                if(currentEpoch==epoch) {
                    val old=client;client=null;old?.close()
                    mutable.update { it.copy(connected=false,login=null,status=if(e is ContractException)e.message.orEmpty()else "Codex connection failed or timed out. Check the host, CLI and account; then retry.") }
                }
            } finally { if(currentEpoch==epoch)mutable.update { it.copy(busy=false) } }
        }
    }
    fun connect() = run { current ->
        val account=current.account()
        mutable.update { it.copy(account=account,status=if(account.subscribed)"ChatGPT subscription connected."else "No ChatGPT subscription sign-in on this host.") }
    }
    fun signIn() = run { current ->
        // Never replace an existing subscription account or sign a shared host out implicitly.
        val account=current.account()
        if(account.subscribed) { mutable.update { it.copy(account=account,status="Using the host's existing ChatGPT sign-in.") };return@run }
        val login=current.login()
        mutable.update { it.copy(login=login,status="Enter the code in your browser. Sign-in is stored by Codex on your SSH host.") }
        current.awaitLogin(login.id)
        val updated=current.account()
        mutable.update { it.copy(account=updated,login=null,status="ChatGPT sign-in completed.") }
    }
    fun newConversation() {
        local {
            if(index.entries.size>=100)throw ContractException("Conversation limit reached")
            persist(AssistantMemory(createdAt=System.currentTimeMillis()))
            clearAnswer();mutable.update { it.copy(draft="",preview="",tokens=0,contextWindow=null,status="New conversation. Earlier conversations remain in the picker.") }
        }
    }
    fun cancelTurn() {
        val current=client
        if(current==null) { stop();return }
        val cancelling=job
        scope.launch {
            runCatching { withTimeout(3000) { current.interrupt() } }
            if(client!==current || job!==cancelling)return@launch
            stop("Stopped. The conversation is retained; nothing is replayed.")
        }
    }
    fun compact() {
        val id=state.value.memory.threadId;if(id.isBlank())return
        run { current ->
            current.resume(id)
            notice("Compacting conversation on the host…")
            current.compact(id)
            notice("Context compacted. Pinned notes and local conversation history are retained.")
        }
    }
    fun fork() {
        val memory=state.value.memory;if(memory.threadId.isBlank() || index.entries.size>=100)return
        run { current ->
            val id=current.fork(memory.threadId)
            persist(memory.copy(id=java.util.UUID.randomUUID().toString(),threadId=id,createdAt=System.currentTimeMillis(),title=(memory.title.take(54)+" · branch")))
            clearAnswer();mutable.update { it.copy(preview="",tokens=0,contextWindow=null,status="Branch created. The original conversation is retained.") }
        }
    }
    fun ask(context: AssistantContext) {
        val draft=state.value.draft.trim();if(draft.isBlank() || state.value.busy)return
        clearAnswer()
        run { current ->
            if(!state.value.account.subscribed)throw ContractException("Connect a ChatGPT subscription in Codex account first. API-key billing is not used here.")
            mutable.update { it.copy(status="Thinking · nothing is being applied…",preview="") }
            val boundScope=loadedScope
            val answer=current.ask(AssistantContract.prompt(draft,context,state.value.memory.notes),state.value.memory.threadId,onThread={ id ->
                if(state.value.memory.threadId!=id)persist(state.value.memory.copy(threadId=id,createdAt=System.currentTimeMillis()),boundScope)
            },onProgress={ preview -> mutable.update { it.copy(preview=preview) } },onUsage={ tokens,window -> mutable.update { it.copy(tokens=tokens,contextWindow=window) } })
            require(answer.actions.all { AssistantContract.valid(it,context) })
            val memory=state.value.memory.append(draft,answer.answer)
            persist(memory,boundScope)
            mutable.update { it.copy(memory=memory,answer=answer,context=context,draft="",preview="",status="Choose an action to continue. Messages open a separate send review.") }
        }
    }
}
