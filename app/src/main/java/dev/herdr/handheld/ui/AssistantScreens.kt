package dev.herdr.handheld.ui

import androidx.compose.foundation.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.herdr.handheld.connection.*
import dev.herdr.handheld.assistant.*
import dev.herdr.handheld.herdr.Screen
import dev.herdr.handheld.voice.*

@Composable internal fun AssistantScreen(s: UiState,model: ConnectionCoordinator) {
    val assistant by model.assistant.state.collectAsStateWithLifecycle()
    val scroll=rememberScrollState()
    val scope=rememberCoroutineScope()
    LaunchedEffect(assistant.memory.id,assistant.memory.messages.size,assistant.busy) { scroll.scrollTo(scroll.maxValue) }
    LaunchedEffect(assistant.preview) { if(scroll.maxValue-scroll.value<180)scroll.scrollTo(scroll.maxValue) }
    var showNotes by remember { mutableStateOf(false) }
    val proposals=assistant.answer?.actions.orEmpty()
    val entries=proposals.map { proposal -> { model.applyProposal(proposal) } }
    SideEffect {
        model.menuActions=entries
        model.assistantScroll={ delta -> scope.launch { scroll.scrollTo((scroll.value+delta).coerceIn(0,scroll.maxValue)) } }
    }
    DisposableEffect(model) { onDispose { model.assistantScroll=null } }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(assistant.memory.title,modifier=Modifier.weight(1f),fontSize=15.sp,color=Muted,maxLines=1)
            TextButton(onClick={model.navigate(Screen.CONVERSATION)}) { Text("Chats") }
            TextButton(onClick={showNotes=!showNotes}) { Text("Notes") }
        }
        if(showNotes) {
            OutlinedTextField(assistant.notesDraft,model.assistant::notes,label={Text("Pinned notes")},modifier=Modifier.fillMaxWidth(),enabled=!assistant.busy)
            Text("Included in future requests in this conversation.",fontSize=12.sp,color=Muted)
            TextButton(onClick=model.assistant::saveNotes,enabled=!assistant.busy) { Text("Save notes") }
        }
        if(assistant.memory.messages.isEmpty())Text("Ask about an agent or your handheld. Suggested actions appear here for review.",fontSize=18.sp,lineHeight=26.sp,color=Muted)
        assistant.memory.messages.forEach { message ->
            Column(Modifier.fillMaxWidth().background(if(message.role=="You")FocusPanel else Panel,RoundedCornerShape(12.dp)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(message.role,fontSize=11.sp,color=Amber)
                SelectionContainer { Text(message.text,fontSize=18.sp,lineHeight=26.sp) }
            }
        }
        if(assistant.busy) {
            if(assistant.draft.isNotBlank())Text(assistant.draft,fontSize=17.sp,color=Muted)
            if(assistant.preview.isNotEmpty())Text(assistant.preview,fontSize=18.sp,lineHeight=26.sp)
            LinearProgressIndicator(Modifier.fillMaxWidth(),color=White,trackColor=Outline)
        }
        if(proposals.isNotEmpty()) {
            Text("PROPOSED · NOTHING SENT",fontSize=11.sp,color=Amber)
            proposals.forEachIndexed { index,proposal ->
                Surface(onClick={model.applyProposal(proposal)},color=if(index==s.menuIndex)FocusPanel else Ink,
                    border=BorderStroke(1.dp,if(index==s.menuIndex)Amber else Outline),shape=RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(proposal.label,fontSize=17.sp)
                        if(proposal.action=="DRAFT_MESSAGE")Text(proposal.text,fontSize=14.sp,color=Muted)
                    }
                }
            }
        }
        OutlinedTextField(assistant.draft,model.assistant::edit,modifier=Modifier.fillMaxWidth().heightIn(min=90.dp),
            placeholder={Text("Ask about your agents or device…",fontSize=18.sp)},textStyle=LocalTextStyle.current.copy(fontSize=19.sp,lineHeight=27.sp),enabled=!assistant.busy)
        if(s.selected!=null)Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(assistant.includeOutput,model.assistant::includeOutput,enabled=!assistant.busy)
            Text("Include selected output",fontSize=14.sp)
        }
        Text("Uses available agents and apps named in your request.${if(assistant.includeOutput)" Includes selected output."else " No terminal output."}",fontSize=12.sp,color=Muted)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick=model::askAssistant,enabled=assistant.draft.isNotBlank() && !assistant.busy) { Text("Ask") }
            OutlinedButton(onClick=model::beginVoice,enabled=!assistant.busy) { Text("Use mic") }
            if(assistant.busy)TextButton(onClick=model.assistant::cancelTurn) { Text("Stop") }
        }
        Text(assistant.status,fontSize=13.sp,color=Amber)
        if(assistant.tokens>0)Text("Last turn: ${assistant.tokens} tokens${assistant.contextWindow?.let { " · window $it" }.orEmpty()}",fontSize=12.sp,color=Muted)
        TextButton(onClick={model.navigate(Screen.CODEX)}) { Text("Codex account") }
    }
}

@Composable internal fun CodexScreen(s: UiState,model: ConnectionCoordinator) {
    val assistant by model.assistant.state.collectAsStateWithLifecycle()
    var editPath by remember { mutableStateOf(false) }
    val login=assistant.login
    if(login!=null) {
        SideEffect { model.menuActions=emptyList() }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Text("Sign in with ChatGPT",fontSize=25.sp,fontWeight=FontWeight.Bold)
            Text("On your phone or computer, open this address and enter the code.",fontSize=17.sp,lineHeight=24.sp,color=Muted)
            Surface(color=Ink,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()) {
                SelectionContainer { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(login.url,fontFamily=ReaderFont,fontSize=13.sp)
                    Text(login.code,fontFamily=ReaderFont,fontSize=30.sp,fontWeight=FontWeight.Bold)
                } }
            }
            Text(assistant.status,fontSize=14.sp,lineHeight=19.sp,color=Muted)
            TextButton(onClick={model.assistant.stop("Sign-in closed. Reconnect to check account status.")}) { Text("Cancel sign-in") }
        };return
    }
    if(editPath) {
        SideEffect { model.menuActions=emptyList() }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("Codex on your SSH host",fontSize=23.sp,fontWeight=FontWeight.Bold)
            OutlinedTextField(assistant.binary,model.assistant::binary,label={Text("Codex executable")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            Button(onClick={model.assistant.stop();model.assistant.connect();editPath=false},enabled=assistant.binary.isNotBlank()) { Text("Save and connect") }
            Text("Uses the existing Codex CLI. Nothing is installed or updated on your host.",fontSize=14.sp,lineHeight=19.sp,color=Muted)
        };return
    }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("ChatGPT",fontSize=24.sp,fontWeight=FontWeight.Bold)
            Text(if(assistant.connected && assistant.account.subscribed)assistant.account.planelse() else "Not connected",fontSize=13.sp,color=if(assistant.connected && assistant.account.subscribed)Green else Muted)
        }
        MenuList(s,model,listOf(
            MenuEntry(if(assistant.connected)"Refresh account"else "Connect subscription",assistant.status,enabled=!assistant.busy,action=model.assistant::connect),
            MenuEntry("Sign in with ChatGPT","Uses a device code when the host has no ChatGPT sign-in",enabled=!assistant.busy,action=model.assistant::signIn),
            MenuEntry("Codex executable",assistant.version.ifBlank { "Use the installed CLI on the SSH host" },enabled=!assistant.busy,action={editPath=true}),
            MenuEntry("Voice language",when(s.speechLanguage){"ko-KR"->"한국어";"en-US"->"English";else->"Device language"},action=model::cycleSpeechLanguage),
            MenuEntry("Assistant","Herdr navigation, message drafts, Android settings and apps",action={model.openAssistant()}),
            MenuEntry("Disconnect assistant","Leaves the host's ChatGPT account signed in",action={model.assistant.stop("Assistant disconnected. The host account remains signed in.")})
        ))
    }
}
private fun CodexAccount.planelse()=plan.ifBlank { "Connected" }

@Composable internal fun VoiceScreen(s: UiState,model: ConnectionCoordinator) {
    val voice=s.voice
    val scroll=rememberScrollState()
    val scope=rememberCoroutineScope()
    SideEffect { model.menuActions=emptyList();model.voiceScroll={ delta -> scope.launch { scroll.scrollTo((scroll.value+delta).coerceIn(0,scroll.maxValue)) } } }
    DisposableEffect(model) { onDispose { model.voiceScroll=null } }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(10.dp),verticalArrangement=Arrangement.spacedBy(15.dp)) {
        Text(voice.recipient,fontSize=20.sp,fontWeight=FontWeight.Bold,color=if(voice.target!=null)Amber else White)
        if(voice.phase==VoicePhase.REVIEW) {
            OutlinedTextField(voice.transcript,model::editVoice,modifier=Modifier.fillMaxWidth().heightIn(min=130.dp),textStyle=LocalTextStyle.current.copy(fontSize=20.sp,lineHeight=28.sp))
            Text("Nothing sent. ${if(voice.target!=null)"Continue to the message editor and review the recipient before sending."else "Use this text as an assistant request."}",fontSize=15.sp,color=Muted)
            Button(onClick=model::useVoice,enabled=voice.transcript.isNotBlank()) { Text("Use transcript") }
        } else {
            Text(when(voice.phase) { VoicePhase.LISTENING->"Listening";VoicePhase.TRANSCRIBING->"Transcribing…";VoicePhase.PERMISSION->"Microphone access";VoicePhase.ERROR->"Try again";else->"Getting ready…" },fontSize=28.sp,fontWeight=FontWeight.Bold)
            if(voice.phase==VoicePhase.LISTENING)LinearProgressIndicator(progress={voice.level},modifier=Modifier.fillMaxWidth().height(8.dp),color=if(voice.target!=null)Amber else White,trackColor=Outline)
            if(voice.transcript.isNotBlank())Text(voice.transcript,fontSize=22.sp,lineHeight=30.sp)
            Text(voice.message, fontSize=17.sp,lineHeight=25.sp,color=Muted)
            if(voice.phase in setOf(VoicePhase.LISTENING,VoicePhase.STARTING))Button(onClick=model::endVoice) { Text("Finish") }
            if(voice.phase==VoicePhase.ERROR)OutlinedButton(onClick=model::beginVoice) { Text("Try mic again") }
        }
        Text("Android speech recognition · the selected provider may use the network. Audio is not sent to Codex by this app.",fontSize=12.sp,lineHeight=17.sp,color=Muted)
    }
}

@Composable internal fun ConversationScreen(s: UiState,model: ConnectionCoordinator) {
    val assistant by model.assistant.state.collectAsStateWithLifecycle()
    var compactReview by remember { mutableStateOf(false) }
    val entries=listOf(
        MenuEntry("New conversation","Earlier chats remain available below",enabled=!assistant.busy,action={model.assistant.newConversation();model.openAssistant()}),
        MenuEntry("Branch this conversation","Continue from the same context in a separate thread",enabled=!assistant.busy && assistant.memory.threadId.isNotBlank(),action={model.assistant.fork();model.openAssistant()}),
        MenuEntry(if(compactReview)"Confirm compaction"else "Compact host context",if(compactReview)"Summarize older context on the host. Local history and pinned notes remain."else "Reduce context used by the current conversation",enabled=!assistant.busy && assistant.memory.threadId.isNotBlank(),action={if(compactReview) { model.assistant.compact();compactReview=false }else compactReview=true})
    )+assistant.conversations.map { entry ->
        MenuEntry(entry.title,if(entry.id==assistant.memory.id)"Current conversation"else "Open saved conversation",enabled=!assistant.busy,action={model.assistant.selectConversation(entry.id);model.openAssistant()})
    }
    MenuList(s,model,entries,assistant.status)
}
