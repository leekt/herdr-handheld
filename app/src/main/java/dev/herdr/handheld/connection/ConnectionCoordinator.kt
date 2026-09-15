package dev.herdr.handheld.connection

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.herdr.handheld.herdr.*
import dev.herdr.handheld.input.*
import dev.herdr.handheld.ssh.*
import dev.herdr.handheld.storage.*
import dev.herdr.handheld.terminal.*
import dev.herdr.handheld.assistant.*
import dev.herdr.handheld.voice.*
import dev.herdr.handheld.launcher.SystemAccess
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

data class UiState(
    val screen: Screen = Screen.HOME,
    val phase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val sshPhase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val problem: ProblemCode = ProblemCode.NONE,
    val profile: HostProfile = HostProfile(),
    val agents: List<AgentTarget> = emptyList(),
    val selectedKey: String? = null,
    val attentionOnly: Boolean = false,
    val selected: AgentTarget? = null,
    val access: TerminalAccess = TerminalAccess.NONE,
    val mode: InputMode = InputMode.NAVIGATION,
    val acquiring: Boolean = false,
    val generation: Long = 0,
    val inputEpoch: Long = 0,
    val message: String = "Connect to your Herdr host to get started.",
    val lastChecked: Long = 0,
    val lastFrame: Long = 0,
    val capabilities: Capabilities? = null,
    val fontSize: Int = 17,
    val cols: Int = 44,
    val rows: Int = 20,
    val draft: String = "",
    val draftSaved: Boolean = false,
    val reviewDraft: Boolean = false,
    val sending: Boolean = false,
    val deliveryUncertain: Boolean = false,
    val menuIndex: Int = 0,
    val reading: ReadingBuffer = ReadingBuffer(),
    val agentView: AgentView = AgentView.TERMINAL,
    val chat: AgentChatState = AgentChatState(),
    val recentScroll: Int = 0,
    val recentMaxScroll: Int = 0,
    val readScrollTarget: Int = 0,
    val readScrollRequest: Long = 0,
    val hostKey: HostKeyChallenge? = null,
    val hasKey: Boolean = false,
    val publicKey: String = "",
    val generatingKey: Boolean = false,
    val diagnostics: List<String> = emptyList(),
    val calibrating: LogicalAction? = null,
    val mappings: Map<String,Int> = emptyMap(),
    val loaded: Boolean = false,
    val applicationCursor: Boolean = false,
    val hud: Boolean = false,
    val hintsVisible: Boolean = false,
    val voice: VoiceState = VoiceState(),
    val speechLanguage: String = "",
) {
    val recent: String? get()=reading.output
}

class ConnectionCoordinator(application: Application) : AndroidViewModel(application) {
    val settings=SettingsStore(application)
    val secrets=SecretStore(application)
    private val mutable=MutableStateFlow(UiState())
    val state: StateFlow<UiState> = mutable.asStateFlow()
    val safety=InputSafety()
    private var client: HerdrClient?=null
    private var sshTransport: SshTransport?=null
    val assistant=AssistantController(viewModelScope,{sshTransport},{state.value.profile.id+":"+state.value.profile.session},secrets) { path -> viewModelScope.launch { settings.saveCodexBinary(path) } }
    private val voiceSession=VoiceSession()
    var voiceStart: ((Long,String)->Unit)?=null
    var voiceStop: (() -> Unit)?=null
    var voiceCancel: (() -> Unit)?=null
    private var voiceParent=Screen.HOME
    private var connectionJob: Job?=null
    private var readJob: Job?=null
    private var chatJob: Job?=null
    private var historyClient: CodexClient?=null
    private var historyBinary: String?=null
    var chatScroll: ((Int)->Unit)?=null
    private var hintsJob: Job?=null
    private val terminalSession=dev.herdr.handheld.terminal.TerminalSession(viewModelScope)
    private val stream get()=terminalSession.stream
    private var renderer: TerminalRenderer?
        get()=terminalSession.renderer
        set(value) { terminalSession.renderer=value }
    private var visible=false
    private val inputLock=Mutex()
    private val drafts=DraftStore(viewModelScope,
        { key -> secrets.get("draft:$key")?.toString(Charsets.UTF_8) },
        { key,text -> secrets.put("draft:$key",text.toByteArray()) },
        { key,text -> if(state.value.selected?.ref?.key==key && state.value.draft==text)mutable.update { it.copy(draftSaved=true) } },
        { message("Draft could not be saved. Keep this screen open and copy your text.",ProblemCode.STORAGE) })
    private var afterAcquire: (() -> Unit)?=null
    private var checkedMonotonic=0L
    var hudScroll: ((Int)->Unit)?=null
    var assistantScroll: ((Int)->Unit)?=null
    var voiceScroll: ((Int)->Unit)?=null
    var menuActions: List<() -> Unit> = emptyList()
    private val modalParents=mutableListOf<Screen>()
    private val calibrationOrder=ControllerCommands.calibration.map { it.first }

    init {
        viewModelScope.launch {
            val profile=settings.profile()
            assistant.binary(settings.codexBinary())
            mutable.update { it.copy(profile=profile,fontSize=settings.fontSize(),
                mappings=settings.mappings(),agents=settings.cachedAgents(),lastChecked=settings.cachedAt(),
                selectedKey=settings.lastTarget(),hasKey=secrets.exists("ssh:${profile.id}"),
                publicKey=secrets.get("publickey:${profile.id}")?.toString(Charsets.UTF_8).orEmpty(),loaded=true,applicationCursor=settings.applicationCursor(),speechLanguage=settings.speechLanguage()) }
            runCatching { assistant.restoreContext() }.onFailure { assistant.notice("Saved conversations could not be read. Existing encrypted data has been retained.") }
            if(visible) connect()
        }
    }
    private fun sync() { mutable.update { it.copy(access=safety.access,mode=safety.mode,acquiring=safety.acquiring,generation=safety.generation) } }
    private fun transition(screen: Screen) { hintsJob?.cancel();if(state.value.screen==Screen.VOICE && screen!=Screen.VOICE)cancelVoice(false);mutable.update { it.copy(screen=screen,hintsVisible=false,inputEpoch=it.inputEpoch+1,menuIndex=0) } }
    fun message(value: String,problem: ProblemCode=ProblemCode.NONE) { mutable.update { it.copy(message=value,problem=problem) } }
    fun setVisible(value: Boolean) {
        if(visible==value) return
        visible=value
        if(!state.value.loaded) return
        if(value) connect() else {
            cancelVoice()
            if(state.value.screen==Screen.COMPOSE) saveDraft()
            safety.connection(false);sync();afterAcquire=null
            mutable.update { it.copy(phase=ConnectionPhase.DISCONNECTED,sshPhase=ConnectionPhase.DISCONNECTED,inputEpoch=it.inputEpoch+1,
                message=if(it.sending)"Delivery unknown. Check the output when you return."else "Paused · reconnects in read mode when you return.",
                deliveryUncertain=it.deliveryUncertain || it.sending,sending=false) }
            shutdown()
        }
    }
    fun focusLost() { if(state.value.voice.phase in setOf(VoicePhase.STARTING,VoicePhase.LISTENING,VoicePhase.TRANSCRIBING))cancelVoice();if(safety.access==TerminalAccess.CONTROLLER || safety.acquiring) leaveInput("Input closed because the app lost focus.") }
    fun connect() {
        if(!visible || !state.value.loaded)return
        if(sshTransport!=null && state.value.sshPhase==ConnectionPhase.READY) { startHerdr(sshTransport!!);return }
        shutdown();safety.connection(false);sync()
        val profile=state.value.profile
        if(profile.host.isBlank() || profile.username.isBlank() || !state.value.hasKey) {
            mutable.update { it.copy(phase=ConnectionPhase.AUTH_REQUIRED,sshPhase=ConnectionPhase.AUTH_REQUIRED,problem=ProblemCode.AUTHENTICATION,message="Add your SSH connection and device key in Settings.",inputEpoch=it.inputEpoch+1) }
            return
        }
        mutable.update { it.copy(phase=ConnectionPhase.CONNECTING,sshPhase=ConnectionPhase.CONNECTING,hostKey=null,problem=ProblemCode.NONE,message="Connecting to SSH…",inputEpoch=it.inputEpoch+1) }
        val transport=SshjTransport(secrets);sshTransport=transport
        connectionJob=viewModelScope.launch {
            try {
                transport.connect(profile)
                if(sshTransport!==transport || !visible)return@launch
                mutable.update { it.copy(sshPhase=ConnectionPhase.READY) }
                if(state.value.screen in setOf(Screen.ASSISTANT,Screen.CODEX,Screen.CONVERSATION))assistant.connect()
                // This job hands ownership to the Herdr poller after SSH authentication succeeds.
                connectionJob=null;startHerdr(transport)
            } catch(e: Exception) {
                if(e is CancellationException && e !is TimeoutCancellationException)throw e
                if(sshTransport===transport)fail(e)
            }
        }
    }
    private fun startHerdr(transport: SshTransport) {
        connectionJob?.cancel();stopStream();safety.connection(false);sync()
        val current=SshHerdrClient(state.value.profile,transport,ownsTransport=false);client=current
        mutable.update { it.copy(phase=ConnectionPhase.CONNECTING,capabilities=null) }
        connectionJob=viewModelScope.launch {
            var verified=false
            while(isActive && visible && client===current) {
                try {
                    if(!verified) {
                        val capabilities=current.connect()
                        if(client!==current)return@launch
                        verified=true;safety.connection(true);sync()
                        mutable.update { it.copy(phase=ConnectionPhase.READY,capabilities=capabilities,problem=ProblemCode.NONE,message=capabilities.notes) }
                    }
                    poll(current)
                    delay(2000)
                } catch(e: Exception) {
                    if(e is CancellationException && e !is TimeoutCancellationException)throw e
                    if(client!==current)return@launch
                    if(e is HerdrUnavailable || (e is SshFailure && e.stage in setOf(SshStage.COMMAND,SshStage.OUTPUT)) || e is ContractException || e is kotlinx.serialization.SerializationException) {
                        verified=false;stopStream();safety.connection(false);sync();afterAcquire=null
                        mutable.update { it.copy(phase=ConnectionPhase.OFFLINE,problem=ProblemCode.HERDR,capabilities=null,
                            message=if(e is HerdrUnavailable)e.message.orEmpty()else "Herdr response is incompatible. SSH and Codex remain available.",inputEpoch=it.inputEpoch+1) }
                        delay(5000)
                    } else { fail(e);return@launch }
                }
            }
        }
    }
    private suspend fun poll(current: HerdrClient) {
        val fresh=current.agents()
        if(client!==current || !visible) return
        val now=System.currentTimeMillis();checkedMonotonic=SystemClock.elapsedRealtime()
        val old=state.value.agents
        // Preserve order while browsing; new rows append. Initial order prioritizes blocked.
        val sorted=if(old.isEmpty() || old.firstOrNull()?.ref?.profileId!=fresh.firstOrNull()?.ref?.profileId)
            fresh.sortedByDescending { it.needsResponse }
        else old.mapNotNull { previous -> fresh.find { it.ref.key==previous.ref.key } } + fresh.filter { a->old.none { it.ref.key==a.ref.key } }
        val selected=state.value.selected
        if(selected!=null && fresh.none { it.ref==selected.ref }) {
            invalidateTarget(null)
            mutable.update { it.copy(selected=null,message="The agent changed or exited. Choose a target again.") }
            transition(Screen.HOME)
        }
        mutable.update { it.copy(agents=sorted,lastChecked=now,selectedKey=it.selectedKey?.takeIf { key->fresh.any { a->a.ref.key==key } } ?: sorted.firstOrNull()?.ref?.key) }
        settings.cacheAgents(sorted,now)
        if(state.value.access!=TerminalAccess.CONTROLLER) recentOutput()
        refreshChat()
    }
    private fun fail(e: Exception) {
        safety.connection(false);sync();afterAcquire=null
        val ssh=e as? SshFailure
        mutable.update { it.copy(phase=if(ssh?.stage in setOf(SshStage.AUTHENTICATION,SshStage.HOST_KEY)) ConnectionPhase.AUTH_REQUIRED else ConnectionPhase.OFFLINE,
            sshPhase=if(ssh?.stage in setOf(SshStage.AUTHENTICATION,SshStage.HOST_KEY))ConnectionPhase.AUTH_REQUIRED else ConnectionPhase.OFFLINE,problem=if(ssh?.challenge!=null)ProblemCode.HOST_KEY else ProblemCode.CONNECTION,hostKey=ssh?.challenge,message=ssh?.message ?: "Connection interrupted. Reconnect to refresh the target.",inputEpoch=it.inputEpoch+1) }
        shutdown()
    }
    private fun shutdown() {
        closeHistory()
        assistant.stop("Assistant disconnected. Reconnect using the account screen.")
        val oldTransport=sshTransport;sshTransport=null
        if(oldTransport!=null)viewModelScope.launch(Dispatchers.IO) { runCatching { oldTransport.disconnect() } }
        cancelVoice(false)
        connectionJob?.cancel();connectionJob=null
        readJob?.cancel();readJob=null
        stopStream()
        val previous=client;client=null
        if(previous!=null) viewModelScope.launch(Dispatchers.IO) { runCatching { previous.disconnect() } }
    }
    fun disconnect() {
        shutdown();safety.connection(false);sync();afterAcquire=null
        mutable.update { it.copy(phase=ConnectionPhase.DISCONNECTED,sshPhase=ConnectionPhase.DISCONNECTED,message="Disconnected. Remote work continues.",inputEpoch=it.inputEpoch+1) }
    }
    private fun stopStream() = terminalSession.close()
    private fun invalidateTarget(ref: TargetRef?) {
        chatJob?.cancel();chatJob=null
        if(state.value.voice.target!=null && state.value.voice.target!=ref)cancelVoice(false)
        readJob?.cancel();readJob=null
        stopStream();safety.invalidate(ref);afterAcquire=null;sync()
        mutable.update { it.copy(inputEpoch=it.inputEpoch+1,lastFrame=0,reading=ReadingBuffer(),recentScroll=0,recentMaxScroll=0) }
    }
    fun select(key: String) { mutable.update { it.copy(selectedKey=key) } }
    fun openSelected() { state.value.agents.find { it.ref.key==state.value.selectedKey }?.let(::open) }
    fun open(target: AgentTarget) {
        modalParents.clear()
        invalidateTarget(target.ref)
        mutable.update { it.copy(selected=target,selectedKey=target.ref.key,message="Read mode · your buttons stay local.",problem=ProblemCode.NONE,deliveryUncertain=false,draft="",reviewDraft=false,
            agentView=if(AgentChat.supported(target.ref))AgentView.CHAT else AgentView.TERMINAL,chat=AgentChatState()) }
        viewModelScope.launch { settings.saveLastTarget(target.ref.key) }
        transition(Screen.TERMINAL)
        if(state.value.phase==ConnectionPhase.READY) recentOutput()
        refreshChat()
    }
    fun bindRenderer(value: TerminalRenderer?) {
        renderer=value
        if(value!=null && safety.acquiring)startController()
    }
    private fun startController() {
        val target=state.value.selected ?: return
        val current=client ?: return
        if(renderer==null)return
        if(state.value.capabilities?.liveTerminal!=true) { recentOutput();return }
        val callback=afterAcquire
        readJob?.cancel();readJob=null
        stopStream()
        val generation=safety.invalidate(target.ref)
        val ticket=safety.request()
        afterAcquire=callback
        sync();mutable.update { it.copy(inputEpoch=it.inputEpoch+1,lastFrame=0) }
        val cols=state.value.cols;val rows=state.value.rows
        terminalSession.open(current,target.ref,generation,cols,rows,state.value.fontSize,
            current={generation==safety.generation && current===client},onFrame={ first ->
                if(first) {
                    if(ticket==null || !safety.acquired(ticket))throw CancellationException()
                    sync();mutable.update { it.copy(inputEpoch=it.inputEpoch+1,problem=ProblemCode.NONE,message="Input mode · target locked. B returns to reading.") }
                    val next=afterAcquire;afterAcquire=null;next?.invoke()
                }
                mutable.update { it.copy(lastFrame=System.currentTimeMillis()) }
            },onError={ first ->
                val wasSending=state.value.sending
                safety.invalidate(target.ref);sync();afterAcquire=null
                mutable.update { it.copy(inputEpoch=it.inputEpoch+1,sending=false,deliveryUncertain=it.deliveryUncertain || wasSending,problem=ProblemCode.TERMINAL,
                    message=if(first)"Control unavailable; another client may own it. Reading only."else "Terminal stream interrupted. Input is disabled.") }
                recentOutput()
            })
    }
    fun viewport(cols: Int,rows: Int,generation: Long) {
        if(generation!=safety.generation || (cols==state.value.cols && rows==state.value.rows))return
        mutable.update { it.copy(cols=cols,rows=rows) }
        terminalSession.resize(cols,rows,{generation==safety.generation && safety.access==TerminalAccess.CONTROLLER}) { leaveInput("Resize failed. Returned to reading.") }
    }
    fun requestInput() {
        if(state.value.phase!=ConnectionPhase.READY || state.value.capabilities?.control!=true || state.value.selected==null) { message("Connect to a supported Herdr session first.");return }
        if(safety.acquiring) return
        message("Requesting control… B cancels.")
        if(renderer!=null)startController()else { safety.request();sync() }
    }
    fun leaveInput(note: String="Read mode · control released.") {
        val selected=state.value.selected
        invalidateTarget(selected?.ref)
        mutable.update { it.copy(message=note,sending=false) }
        if(selected!=null && state.value.phase==ConnectionPhase.READY) recentOutput()
    }
    fun home() {
        cancelVoice(false)
        if(state.value.screen==Screen.COMPOSE) saveDraft()
        invalidateTarget(null)
        modalParents.clear()
        mutable.update { it.copy(selected=null,reviewDraft=false,calibrating=null,hud=false,message="Choose an agent to read its output.",problem=ProblemCode.NONE) }
        transition(Screen.HOME)
    }
    fun navigate(screen: Screen) {
        if(screen in setOf(Screen.SETTINGS,Screen.DIAGNOSTICS,Screen.APPS,Screen.SYSTEM,Screen.ACTIONS,Screen.CONTROL,Screen.ASSISTANT,Screen.CODEX,Screen.CONVERSATION) && screen!=state.value.screen)
            modalParents.add(state.value.screen)
        if(screen in setOf(Screen.SETTINGS,Screen.DIAGNOSTICS,Screen.APPS,Screen.SYSTEM,Screen.ASSISTANT,Screen.CODEX) && (safety.access==TerminalAccess.CONTROLLER || safety.acquiring)) leaveInput()
        transition(screen)
    }
    fun showActions() { navigate(Screen.ACTIONS) }
    fun showSystem() { if(state.value.screen==Screen.SYSTEM)back() else navigate(Screen.SYSTEM) }
    fun showHints() {
        if(state.value.hud)return
        hintsJob?.cancel();mutable.update { it.copy(hintsVisible=true) }
        hintsJob=viewModelScope.launch { delay(3000);mutable.update { it.copy(hintsVisible=false) } }
    }
    fun setHud(shown: Boolean) {
        val s=state.value
        if(s.hostKey!=null)return
        if(s.hud==shown)return
        mutable.update { it.copy(hud=shown,hintsVisible=false,inputEpoch=it.inputEpoch+if(shown)0 else 1) }
    }
    fun showControl() {
        if(state.value.phase!=ConnectionPhase.READY || state.value.capabilities?.control!=true) { message("Connect to a supported Herdr session first.");return }
        navigate(Screen.CONTROL)
    }
    fun back() {
        val s=state.value
        if(s.hud) { setHud(false);return }
        if(s.screen==Screen.VOICE) { cancelVoice();return }
        if(s.screen==Screen.ASSISTANT && assistant.state.value.busy)assistant.cancelTurn()
        if(s.screen==Screen.CODEX && assistant.state.value.login!=null)assistant.stop("Sign-in closed. Reconnect to check account status.")
        if(s.calibrating!=null)mutable.update { it.copy(calibrating=null,inputEpoch=it.inputEpoch+1) }
        when(s.screen) {
            Screen.ACTIONS -> { safety.navigation();sync(); if(s.access==TerminalAccess.CONTROLLER) leaveInput();modalParents.clear();transition(if(s.selected!=null) Screen.TERMINAL else Screen.HOME);recentOutput() }
            Screen.COMPOSE -> { saveDraft();leaveInput();modalParents.clear();transition(Screen.TERMINAL) }
            Screen.TERMINAL -> if(s.access==TerminalAccess.CONTROLLER || s.acquiring) { leaveInput();transition(Screen.TERMINAL) } else home()
            Screen.HOME -> home()
            else -> { transition(modalParents.removeLastOrNull() ?: if(s.selected!=null)Screen.TERMINAL else Screen.HOME);recentOutput() }
        }
    }
    fun toggleAttention() { mutable.update { s ->
        val only=!s.attentionOnly
        s.copy(attentionOnly=only,selectedKey=if(only) s.agents.firstOrNull { it.needsResponse }?.ref?.key else s.selectedKey ?: s.agents.firstOrNull()?.ref?.key)
    } }
    fun visibleAgents()=state.value.agents.filter { !state.value.attentionOnly || it.needsResponse }
    fun dispatch(action: LogicalAction) {
        val s=state.value
        if(action==LogicalAction.HOME) { home();return }
        if(action==LogicalAction.BACK) { back();return }
        if(s.hud) { if(action in setOf(LogicalAction.UP,LogicalAction.DOWN))hudScroll?.invoke(if(action==LogicalAction.UP)-100 else 100);return }
        if(action==LogicalAction.INPUT) { showHints();return }
        if(action==LogicalAction.FONT_SMALL || action==LogicalAction.FONT_LARGE) { setFont(s.fontSize+if(action==LogicalAction.FONT_LARGE)1 else -1);return }
        if(action==LogicalAction.SYSTEM) { cancelVoice();showSystem();return }
        if(s.screen==Screen.VOICE) {
            if(action.directional)voiceScroll?.invoke(if(action in setOf(LogicalAction.UP,LogicalAction.LEFT))-120 else 120)
            if(action==LogicalAction.CONFIRM)when(s.voice.phase) {
                VoicePhase.REVIEW->useVoice()
                VoicePhase.ERROR->beginVoice()
                else->endVoice()
            }
            if(action==LogicalAction.COMPOSE)voiceKeyboard()
            return
        }
        if(ControllerCommands.forScreen(s.screen,s.mode,s.acquiring,s.reviewDraft).any { it.action==action && !it.enabled })return
        if(s.screen==Screen.ASSISTANT) {
            if(action==LogicalAction.ACTIONS) { navigate(Screen.CONVERSATION);return }
            if(action==LogicalAction.COMPOSE) { assistant.clearAnswer();mutable.update { it.copy(inputEpoch=it.inputEpoch+1) };return }
            if(action in setOf(LogicalAction.UP,LogicalAction.DOWN)) { assistantScroll?.invoke(if(action==LogicalAction.UP)-120 else 120);return }
            if(action==LogicalAction.CONFIRM && assistant.state.value.draft.isNotBlank()) { askAssistant();return }
            if(assistant.state.value.answer==null) return
        }
        if(s.screen in setOf(Screen.ACTIONS,Screen.SETTINGS,Screen.DIAGNOSTICS,Screen.APPS,Screen.SYSTEM,Screen.CONTROL,Screen.ASSISTANT,Screen.CODEX,Screen.CONVERSATION)) {
            when(action) {
                LogicalAction.UP,LogicalAction.LEFT -> mutable.update { it.copy(menuIndex=(it.menuIndex-1).coerceAtLeast(0)) }
                LogicalAction.DOWN,LogicalAction.RIGHT -> mutable.update { it.copy(menuIndex=(it.menuIndex+1).coerceAtMost((menuActions.size-1).coerceAtLeast(0))) }
                LogicalAction.CONFIRM -> menuActions.getOrNull(s.menuIndex)?.invoke()
                else -> {}
            };return
        }
        if(s.screen==Screen.COMPOSE) {
            when(action) {
                LogicalAction.CONFIRM -> if(s.reviewDraft) confirmDraft(true) else reviewDraft()
                LogicalAction.ACTIONS -> { saveDraft();message("Draft saved on this device.") }
                else -> {}
            };return
        }
        when(action) {
            LogicalAction.ACTIONS -> showActions()
            LogicalAction.COMPOSE -> if(s.selected!=null) openCompose() else openAssistant()
            LogicalAction.INPUT -> if(s.access==TerminalAccess.CONTROLLER || s.acquiring) leaveInput() else setHud(true)
            LogicalAction.PREVIOUS,LogicalAction.NEXT -> if(s.mode!=InputMode.REMOTE_KEYS && !s.acquiring) moveAgent(if(action==LogicalAction.NEXT) 1 else -1,s.screen==Screen.TERMINAL)
            LogicalAction.CONFIRM -> if(s.screen==Screen.HOME) openSelected() else if(s.mode==InputMode.REMOTE_KEYS) sendKey("Enter") else if(s.screen==Screen.TERMINAL)showControl()
            LogicalAction.UP,LogicalAction.DOWN,LogicalAction.LEFT,LogicalAction.RIGHT -> {
                val delta=if(action==LogicalAction.UP || action==LogicalAction.LEFT) -1 else 1
                if(s.screen==Screen.HOME) moveAgent(delta,false)
                else if(s.mode==InputMode.REMOTE_KEYS) sendKey(action.name)
                else scrollReading(delta*if(action in setOf(LogicalAction.LEFT,LogicalAction.RIGHT)) 270 else 90)
            }
            else -> {}
        }
    }
    private fun moveAgent(delta: Int,open: Boolean) {
        val targets=visibleAgents();if(targets.isEmpty())return
        val current=targets.indexOfFirst { it.ref.key==state.value.selectedKey }.coerceAtLeast(0)
        val next=targets[(current+delta).coerceIn(0,targets.lastIndex)]
        if(open) open(next) else select(next.ref.key)
    }
    fun sendKey(name: String) {
        // Rendered Herdr frames do not export the remote keyboard modes. Use an explicit profile.
        val prefix=if(state.value.applicationCursor) "\u001bO" else "\u001b["
        val bytes=when(name) { "UP"->prefix+"A";"DOWN"->prefix+"B";"RIGHT"->prefix+"C";"LEFT"->prefix+"D"
            "Enter"->"\r";"Esc"->"\u001b";"Tab"->"\t";"Shift+Tab"->"\u001b[Z";"Ctrl+C"->"\u0003";else->return }.toByteArray()
        val ticket=safety.ticket() ?: return
        transmit(bytes,ticket)
    }
    private fun transmit(bytes: ByteArray,ticket: InputTicket,followWithEnter: Boolean=false,done: (() -> Unit)?=null) {
        if(!safety.canSend(ticket) || state.value.deliveryUncertain || !inputLock.tryLock()) return
        val channel=stream ?: run { inputLock.unlock();return }
        mutable.update { it.copy(sending=true) }
        viewModelScope.launch {
            var writeStarted=false
            try {
                if(SystemClock.elapsedRealtime()-checkedMonotonic>4000) throw ContractException("Target status is stale")
                val targets=client?.agents() ?: throw ContractException("Disconnected")
                if(targets.none { it.ref==ticket.target } || !safety.canSend(ticket) || channel!==stream) throw ContractException("Target changed")
                writeStarted=true
                channel.input(bytes)
                if(followWithEnter) {
                    // Separate complete paste and Enter commands, ordered on the same channel.
                    // A partial transaction is uncertain and is never retried.
                    if(!safety.canSend(ticket) || channel!==stream) throw ContractException("Target changed after paste")
                    channel.input(byteArrayOf(13))
                }
                if(safety.current(ticket)) { message("Sent to terminal transport. Check the agent's output.");done?.invoke() }
            } catch(e: Exception) {
                if(safety.current(ticket)) {
                    leaveInput(if(writeStarted) "Delivery unknown. Check output before sending again." else "Target could not be verified. Input closed.")
                    mutable.update { it.copy(deliveryUncertain=writeStarted) }
                }
            } finally { mutable.update { it.copy(sending=false) };inputLock.unlock() }
        }
    }
    fun openCompose(prefill: String?=null) {
        val target=state.value.selected ?: return
        leaveInput("Draft stays on this device until you confirm sending.")
        safety.compose();sync();transition(Screen.COMPOSE)
        viewModelScope.launch {
            val draft=runCatching { drafts.load(target.ref.key) }.getOrElse { message("Saved draft could not be read.",ProblemCode.STORAGE);"" }
            if(state.value.selected?.ref==target.ref && state.value.screen==Screen.COMPOSE)
                mutable.update { it.copy(draft=prefill ?: draft,draftSaved=prefill==null && draft.isNotEmpty(),reviewDraft=false) }
        }
    }
    fun askNextSteps() { openAssistant("Suggest up to three next steps for the selected agent. State missing context and wait for me to choose.") }
    fun editDraft(value: String) {
        if(value.toByteArray().size>24*1024)return
        val target=state.value.selected?.ref ?: return
        mutable.update { it.copy(draft=value,draftSaved=false,reviewDraft=false) }
        drafts.save(target.key,value,debounce=true)
    }
    fun saveDraft() {
        val s=state.value;val target=s.selected ?: return
        drafts.save(target.ref.key,s.draft)
    }
    fun reviewDraft() { if(state.value.draft.isNotBlank()) { saveDraft();mutable.update { it.copy(reviewDraft=true,inputEpoch=it.inputEpoch+1) } } }
    fun confirmDraft(submit: Boolean) {
        val s=state.value;val target=s.selected ?: return
        if(!s.reviewDraft || s.draft.isBlank() || s.sending || s.acquiring) return
        val text=s.draft
        if(text.any { it=='\u001b' || it=='\u0000' || (it<' ' && it!='\n' && it!='\t') }) { message("Remove control characters before sending.",ProblemCode.INPUT);return }
        afterAcquire={
            if(state.value.selected?.ref==target.ref && state.value.draft==text && state.value.screen==Screen.COMPOSE) {
                safety.compose();sync()
                // 0.9.0 recognizes a COMPLETE bracketed paste input command and adapts it to
                // the remote terminal's actual mode. Do not append Enter inside that command.
                run {
                    val payload="\u001b[200~$text\u001b[201~"
                    val ticket=safety.ticket()
                    if(ticket!=null) transmit(payload.toByteArray(),ticket,submit) {
                        // Keep the draft until explicitly cleared; transport success is not task success.
                        transition(Screen.TERMINAL);leaveInput("Sent to transport. Returned to reading; draft kept.")
                    }
                }
            }
        }
        requestInput()
    }
    fun clearDraft() { editDraft("");saveDraft() }
    fun openAssistant(prefill: String?=null) {
        if(prefill!=null) { assistant.edit(prefill);assistant.clearAnswer() }
        navigate(Screen.ASSISTANT)
        if(state.value.sshPhase==ConnectionPhase.READY && !assistant.state.value.connected && !assistant.state.value.busy)assistant.connect()
    }
    fun askAssistant() {
        val s=state.value
        if(s.sshPhase!=ConnectionPhase.READY) {
            assistant.notice("Connect SSH before asking. Herdr is optional for device questions.");return
        }
        val apps=SystemAccess.apps(getApplication()).associate { it.packageName to it.label }
        val fresh=s.phase==ConnectionPhase.READY && SystemClock.elapsedRealtime()-checkedMonotonic<=4000
        val context=AssistantContext(s.profile,if(fresh)s.agents.toList()else emptyList(),apps,s.selected?.ref,
            if(assistant.state.value.includeOutput)s.reading.output else null,s.lastChecked,s.reading.updatedAt,herdrAvailable=fresh)
            .bounded(assistant.state.value.draft)
        assistant.includeOutput(false)
        assistant.ask(context)
    }
    fun applyProposal(proposal: AssistantProposal) {
        val context=assistant.state.value.context ?: return
        val s=state.value
        if(context.profile!=s.profile || !AssistantContract.valid(proposal,context)) {
            assistant.notice("Context changed. Ask again before applying an action.");return
        }
        val target=AssistantContract.target(proposal,context)
        if(target!=null && (s.phase!=ConnectionPhase.READY || SystemClock.elapsedRealtime()-checkedMonotonic>4000 || s.agents.none { it.ref==target.ref })) { assistant.notice("That agent changed or exited. Refresh and ask again.");return }
        when(AssistantAction.valueOf(proposal.action)) {
            AssistantAction.OPEN_AGENT -> open(target!!)
            AssistantAction.DRAFT_MESSAGE -> { open(target!!);openCompose(proposal.text) }
            AssistantAction.SHOW_ATTENTION -> { if(!s.attentionOnly)toggleAttention();home() }
            AssistantAction.SHOW_ALL -> { if(s.attentionOnly)toggleAttention();home() }
            AssistantAction.REFRESH -> { home();connect() }
            AssistantAction.HOME -> home()
            AssistantAction.OPEN_SETTINGS -> SystemAccess.settings(getApplication())
            AssistantAction.WIFI_SETTINGS -> SystemAccess.deviceSettings(getApplication(),true)
            AssistantAction.DISPLAY_SETTINGS -> SystemAccess.deviceSettings(getApplication(),false)
            AssistantAction.OPEN_APPS -> navigate(Screen.APPS)
            AssistantAction.OPEN_APP -> SystemAccess.apps(getApplication()).find { it.packageName==proposal.target }?.let { SystemAccess.open(getApplication(),it) }
            AssistantAction.SET_FONT -> { setFont(proposal.text.toInt());assistant.notice("Launcher text size changed to ${state.value.fontSize}.") }
        }
        assistant.clearAnswer()
    }
    fun beginVoice() = startVoice(false)
    fun beginTerminalVoice() = startVoice(true)
    private fun startVoice(terminal: Boolean) {
        val s=state.value
        if(s.screen !in setOf(Screen.HOME,Screen.TERMINAL,Screen.COMPOSE,Screen.ASSISTANT,Screen.VOICE,Screen.CONTROL,Screen.ACTIONS))return
        if(!terminal && s.screen in setOf(Screen.CONTROL,Screen.ACTIONS))return
        if(s.hostKey!=null || s.calibrating!=null || s.hud || s.acquiring || s.sending)return
        val dictating=terminal || s.screen==Screen.COMPOSE || s.screen==Screen.TERMINAL || (s.screen==Screen.VOICE && s.voice.target!=null)
        val target=if(dictating)s.selected else null
        if(dictating && target==null) { message("Choose an agent before recording a message.");return }
        if(s.screen!=Screen.VOICE)voiceParent=if(s.screen in setOf(Screen.CONTROL,Screen.ACTIONS))Screen.TERMINAL else s.screen
        if(dictating) { safety.compose();sync() }
        voiceCancel?.invoke()
        voiceSession.begin(target?.ref,target?.let { "${it.title} · ${s.profile.session} / ${it.ref.paneId}" } ?: "Codex assistant")
        // Retain the initiating Y press until release; this modal consumes every other key.
        mutable.update { it.copy(screen=Screen.VOICE,voice=voiceSession.state) }
        voiceStart?.invoke(voiceSession.state.id,s.speechLanguage)
            ?: voiceUpdate(voiceSession.state.id,VoicePhase.ERROR,"Microphone is not ready. Reopen the app.",null,null)
    }
    fun endVoice() {
        if(state.value.screen==Screen.VOICE && state.value.voice.phase in setOf(VoicePhase.STARTING,VoicePhase.LISTENING)) {
            voiceUpdate(state.value.voice.id,VoicePhase.TRANSCRIBING,"Finishing transcript…",null,null);voiceStop?.invoke()
        }
    }
    fun voiceUpdate(id: Long,phase: VoicePhase,message: String,text: String?,level: Float?) {
        if(voiceSession.update(id,phase,message,text,level))mutable.update { it.copy(voice=voiceSession.state) }
    }
    fun editVoice(value: String) { voiceSession.edit(value);mutable.update { it.copy(voice=voiceSession.state) } }
    fun voiceKeyboard() {
        val voice=state.value.voice
        if(voice.target!=null && voice.target!=state.value.selected?.ref) { cancelVoice();message("Voice recipient changed. Record again.");return }
        val text=voice.transcript.takeIf { it.isNotBlank() }
        cancelVoice()
        if(voice.target!=null)openCompose(text)else openAssistant(text)
    }
    fun cancelVoice(returnToParent: Boolean=true) {
        val active=state.value.voice.phase!=VoicePhase.IDLE
        voiceCancel?.invoke();voiceSession.cancel()
        mutable.update { it.copy(voice=voiceSession.state,inputEpoch=it.inputEpoch+if(active)1 else 0,screen=if(returnToParent && it.screen==Screen.VOICE)voiceParent else it.screen) }
        if(active && returnToParent && safety.access==TerminalAccess.CONTROLLER)leaveInput()
        else if(active && returnToParent) { if(voiceParent!=Screen.COMPOSE)safety.navigation();sync();recentOutput() }
    }
    fun useVoice() {
        val voice=state.value.voice
        if(voice.phase!=VoicePhase.REVIEW || voice.transcript.isBlank())return
        if(voice.target!=null) {
            if(state.value.selected?.ref!=voice.target) { cancelVoice();message("Voice recipient changed. Record again.");return }
            val text=voice.transcript
            cancelVoice();openCompose(text)
        } else {
            val text=voice.transcript;cancelVoice();openAssistant(text)
        }
    }
    fun cycleSpeechLanguage() {
        val next=when(state.value.speechLanguage) { ""->"ko-KR";"ko-KR"->"en-US";else->"" }
        mutable.update { it.copy(speechLanguage=next) };viewModelScope.launch { settings.saveSpeechLanguage(next) }
    }
    private fun closeHistory() {
        chatJob?.cancel();chatJob=null
        val old=historyClient;historyClient=null;historyBinary=null
        if(old!=null)viewModelScope.launch { old.close() }
    }
    fun setAgentView(view: AgentView) {
        if(state.value.mode==InputMode.REMOTE_KEYS || state.value.acquiring)leaveInput()
        chatJob?.cancel();chatJob=null
        mutable.update { it.copy(agentView=view,chat=it.chat.copy(loading=false,error="")) }
        if(view==AgentView.CHAT)refreshChat(true)else recentOutput()
    }
    fun followChat(follow: Boolean) { mutable.update { it.copy(chat=it.chat.follow(follow)) } }
    fun latestChat() {
        chatJob?.cancel();chatJob=null
        mutable.update { it.copy(chat=if(it.chat.cursor!=null)AgentChatState()else it.chat.follow(true).copy(error="",loading=false)) }
        refreshChat(true)
    }
    fun olderChat() {
        val cursor=state.value.chat.page?.older ?: return
        if(state.value.chat.loading)return
        chatJob?.cancel();chatJob=null
        mutable.update { it.copy(chat=AgentChatState(cursor=cursor,following=false)) }
        refreshChat(true)
    }
    private fun refreshChat(force: Boolean=false) {
        val s=state.value
        if(!visible || s.screen!=Screen.TERMINAL || s.phase!=ConnectionPhase.READY || s.agentView!=AgentView.CHAT ||
            s.access==TerminalAccess.CONTROLLER || s.acquiring || chatJob?.isActive==true)return
        if(!force && (s.chat.error.isNotBlank() || s.chat.cursor!=null))return
        val target=s.selected?.ref ?: return
        if(!AgentChat.supported(target))return
        val transport=sshTransport ?: return
        val generation=safety.generation;val cursor=s.chat.cursor
        val binary=assistant.state.value.binary
        if(historyBinary!=null && historyBinary!=binary)closeHistory()
        mutable.update { it.copy(chat=it.chat.copy(loading=true)) }
        chatJob=viewModelScope.launch {
            var opening: CodexClient?=null
            fun current()=isActive && visible && transport===sshTransport && generation==safety.generation && state.value.selected?.ref==target && state.value.chat.cursor==cursor
            try {
                if(historyClient==null) {
                    val version=transport.exec(HerdrCommandBuilder.quote(binary)+" --version")
                    if(version.exitCode!=0 || version.stdout.trim()!="codex-cli 0.153.4")throw ContractException("Unsupported Codex history version")
                    opening=CodexClient(transport.open(CodexClient.command(binary)))
                    opening.initialize()
                    if(!current())return@launch
                    historyClient=opening;historyBinary=binary;opening=null
                }
                val page=AgentChat.read(historyClient!!,target,cursor)
                if(current())mutable.update { it.copy(chat=it.chat.receive(page,System.currentTimeMillis())) }
            } catch(e: Exception) {
                if(e is CancellationException && e !is TimeoutCancellationException)throw e
                if(current()) {
                    val old=historyClient;historyClient=null;historyBinary=null
                    old?.close()
                    mutable.update { it.copy(chat=it.chat.copy(loading=false,error="Chat history is unavailable. Use Terminal, or retry Chat."),
                        agentView=if(it.chat.page==null)AgentView.TERMINAL else it.agentView) }
                }
            } finally { opening?.close() }
        }
    }
    fun recentOutput() {
        val s=state.value
        if(!visible || s.screen!=Screen.TERMINAL || s.phase!=ConnectionPhase.READY || s.access==TerminalAccess.CONTROLLER || s.acquiring || readJob?.isActive==true)return
        val target=state.value.selected ?: return;val current=client ?: return
        val ref=target.ref;val g=safety.generation
        readJob=viewModelScope.launch {
            runCatching { withContext(Dispatchers.Default) { TerminalText.parse(current.recent(ref)) } }.onSuccess { output ->
                if(g==safety.generation && client===current && state.value.selected?.ref==ref)
                    mutable.update { it.copy(reading=it.reading.receive(output,System.currentTimeMillis()),problem=if(it.problem==ProblemCode.OUTPUT)ProblemCode.NONE else it.problem) }
            }.onFailure {
                if(it is CancellationException)throw it
                if(g==safety.generation && client===current) message("Could not refresh output. X opens reconnect actions.",ProblemCode.OUTPUT)
            }
        }
    }
    private fun scrollReading(delta: Int) {
        if(state.value.agentView==AgentView.CHAT && state.value.mode!=InputMode.REMOTE_KEYS) { chatScroll?.invoke(delta);return }
        mutable.update {
            val target=(it.recentScroll+delta).coerceIn(0,it.recentMaxScroll)
            it.copy(readScrollTarget=target,readScrollRequest=it.readScrollRequest+1,reading=it.reading.follow(target>=it.recentMaxScroll))
        }
    }
    fun resumeReading() { mutable.update { it.copy(reading=it.reading.follow(true)) };recentOutput() }
    fun recentScrollPosition(position: Int,maximum: Int,userScrolling: Boolean=false,settled: Boolean=true) {
        if(maximum==Int.MAX_VALUE)return
        mutable.update {
            val follow=if(userScrolling)settled && position>=maximum else it.reading.following
            it.copy(recentScroll=position.coerceIn(0,maximum),recentMaxScroll=maximum,reading=it.reading.follow(follow))
        }
    }
    fun saveProfile(profile: HostProfile) {
        val old=state.value.profile
        val changedHost=old.host!=profile.host || old.port!=profile.port || old.username!=profile.username
        val saved=if(changedHost)profile.copy(id=java.util.UUID.randomUUID().toString())else profile
        disconnect();home();mutable.update { it.copy(profile=saved,hasKey=secrets.exists("ssh:${saved.id}"),
            publicKey=secrets.get("publickey:${saved.id}")?.toString(Charsets.UTF_8).orEmpty(),agents=emptyList(),lastChecked=0) }
        transition(Screen.SETTINGS)
        viewModelScope.launch { settings.saveProfile(saved);assistant.restoreContext();message("Connection profile saved.") }
    }
    fun importKey(bytes: ByteArray,passphrase: String) {
        if(bytes.size>65536 || !bytes.toString(Charsets.UTF_8).contains("PRIVATE KEY")) { message("Choose a PEM or OpenSSH private key, up to 64 KiB.");return }
        val id=state.value.profile.id
        viewModelScope.launch {
            withContext(Dispatchers.IO) { secrets.put("ssh:$id",bytes);secrets.put("passphrase:$id",passphrase.toByteArray());secrets.remove("publickey:$id");bytes.fill(0) }
            if(state.value.profile.id==id) mutable.update { it.copy(hasKey=true,publicKey="",message="SSH key encrypted on this device.") }
        }
    }
    fun generateKey() {
        val s=state.value
        if(s.hasKey || s.generatingKey)return
        val id=s.profile.id
        mutable.update { it.copy(generatingKey=true) }
        viewModelScope.launch {
            try {
                val public=withContext(Dispatchers.IO) {
                    val pair=DeviceKeyPair.generate()
                    try {
                        secrets.put("ssh:$id",pair.privateKey)
                        secrets.remove("passphrase:$id")
                        secrets.put("publickey:$id",pair.publicKey.toByteArray())
                        pair.publicKey
                    } finally { pair.privateKey.fill(0) }
                }
                if(state.value.profile.id==id) mutable.update { it.copy(hasKey=true,publicKey=public,message="Device key created. Authorize its public key on your host.") }
            } catch(e: Exception) {
                if(e is CancellationException)throw e
                message("Could not create the device key. Try again or import an existing key.")
            } finally { mutable.update { it.copy(generatingKey=false) } }
        }
    }
    fun forgetKey() {
        disconnect();val id=state.value.profile.id
        viewModelScope.launch(Dispatchers.IO) {
            secrets.remove("ssh:$id");secrets.remove("passphrase:$id");secrets.remove("publickey:$id")
            if(state.value.profile.id==id) mutable.update { it.copy(hasKey=false,publicKey="",message="SSH key removed from this device.") }
        }
    }
    fun trustHostKey() {
        val challenge=state.value.hostKey ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { secrets.put("hostkey:${challenge.host.lowercase()}:${challenge.port}",challenge.fingerprint.toByteArray()) }
            mutable.update { it.copy(hostKey=null) };connect()
        }
    }
    fun dismissHostKey() { mutable.update { it.copy(hostKey=null) } }
    fun setFont(size: Int) { val actual=size.coerceIn(12,26);mutable.update { it.copy(fontSize=actual) };renderer?.font(actual,safety.generation);viewModelScope.launch { settings.saveFontSize(actual) } }
    fun toggleCursorProtocol() { val value=!state.value.applicationCursor;leaveInput();mutable.update { it.copy(applicationCursor=value) };viewModelScope.launch { settings.saveApplicationCursor(value) } }
    fun diagnostic(event: String) { if(state.value.screen==Screen.DIAGNOSTICS) mutable.update { it.copy(diagnostics=(listOf(event)+it.diagnostics).take(24)) } }
    fun calibrate() { invalidateTarget(null);mutable.update { it.copy(calibrating=calibrationOrder.first(),diagnostics=emptyList(),inputEpoch=it.inputEpoch+1) } }
    fun captureButton(descriptor: String,code: Int): Boolean {
        val action=state.value.calibrating ?: return false
        val key="$descriptor:${action.name}"
        val mappings=state.value.mappings.toMutableMap().apply {
            entries.removeAll { it.key.startsWith("$descriptor:") && it.value==code };put(key,code)
        }
        val next=calibrationOrder.getOrNull(calibrationOrder.indexOf(action)+1)
        mutable.update { it.copy(mappings=mappings,calibrating=next,message=if(next==null) "Button profile saved." else "Button recorded.",inputEpoch=it.inputEpoch+1) }
        viewModelScope.launch { settings.saveMappings(mappings) };return true
    }
    fun resetCalibration() { mutable.update { it.copy(mappings=emptyMap(),calibrating=null,inputEpoch=it.inputEpoch+1) };viewModelScope.launch { settings.saveMappings(emptyMap()) } }
}

enum class ProblemCode { NONE, AUTHENTICATION, HOST_KEY, CONNECTION, HERDR, TERMINAL, OUTPUT, INPUT, STORAGE }
