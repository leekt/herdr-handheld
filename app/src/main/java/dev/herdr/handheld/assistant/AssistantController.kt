package dev.herdr.handheld.assistant

import dev.herdr.handheld.herdr.ContractException
import dev.herdr.handheld.herdr.HerdrCommandBuilder
import dev.herdr.handheld.ssh.SshTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import dev.herdr.handheld.storage.SecretStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

data class AssistantState(
    val binary: String="codex",val version: String="",val account: CodexAccount=CodexAccount(),
    val connected: Boolean=false,val busy: Boolean=false,val draft: String="",val includeOutput: Boolean=false,
    val status: String="Connect Codex using the account on your SSH host.",val login: DeviceLogin?=null,
    val answer: AssistantAnswer?=null,val context: AssistantContext?=null,val memory: AssistantMemory=AssistantMemory(),
)
class AssistantController(private val scope: CoroutineScope,private val transport: ()->SshTransport?,private val memoryScope: ()->String,private val secrets: SecretStore,private val persistBinary: (String)->Unit) {
    private val mutable=MutableStateFlow(AssistantState())
    val state=mutable.asStateFlow()
    private var client: CodexClient?=null
    private var job: Job?=null
    private var epoch=0L
    private var loadedScope=""
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
        val currentScope=memoryScope()
        if(loadedScope==currentScope)return
        val memory=withContext(Dispatchers.IO) { runCatching { secrets.get("assistant:$currentScope")?.let { Json.decodeFromString<AssistantMemory>(it.toString(Charsets.UTF_8)) } }.getOrNull() } ?: AssistantMemory()
        if(memoryScope()!=currentScope)return
        loadedScope=currentScope
        mutable.update { it.copy(memory=memory,answer=null,context=null) }
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
        if(state.value.busy)return
        stop("New conversation. Previous Codex conversation remains on your host.")
        val boundScope=memoryScope();loadedScope=boundScope
        mutable.update { it.copy(memory=AssistantMemory(),draft="") }
        scope.launch(Dispatchers.IO) { secrets.put("assistant:$boundScope",Json.encodeToString(AssistantMemory()).toByteArray()) }
    }
    fun ask(context: AssistantContext) {
        val draft=state.value.draft.trim();if(draft.isBlank() || state.value.busy)return
        clearAnswer()
        run { current ->
            if(!state.value.account.subscribed)throw ContractException("Connect a ChatGPT subscription in Codex account first. API-key billing is not used here.")
            mutable.update { it.copy(status="Thinking · nothing is being applied…") }
            val boundScope=loadedScope
            val answer=current.ask(AssistantContract.prompt(draft,context),state.value.memory.threadId) { id ->
                if(state.value.memory.threadId!=id) {
                    val memory=AssistantMemory(id,System.currentTimeMillis())
                    withContext(Dispatchers.IO) { secrets.put("assistant:$boundScope",Json.encodeToString(memory).toByteArray()) }
                    mutable.update { it.copy(memory=memory) }
                }
            }
            require(answer.actions.all { AssistantContract.valid(it,context) })
            val memory=state.value.memory.append(draft,answer.answer)
            withContext(Dispatchers.IO) { secrets.put("assistant:$boundScope",Json.encodeToString(memory).toByteArray()) }
            mutable.update { it.copy(memory=memory,answer=answer,context=context,status="Choose an action to continue. Messages open a separate send review.") }
        }
    }
}
