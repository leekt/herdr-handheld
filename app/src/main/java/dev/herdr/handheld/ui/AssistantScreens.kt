package dev.herdr.handheld.ui

import androidx.compose.foundation.*
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
    val answer=assistant.answer
    if(answer!=null) {
        val context=assistant.context
        val entries=answer.actions.map { proposal ->
            val target=context?.let { AssistantContract.target(proposal,it) }
            MenuEntry(proposal.label,when(proposal.action) {
                "DRAFT_MESSAGE"->"Prepare message · ${target?.title.orEmpty()}\n${proposal.text}"
                "OPEN_AGENT"->"Read · ${target?.title.orEmpty()}"
                else->"Apply this launcher action"
            },action={model.applyProposal(proposal)})
        }+MenuEntry("Ask something else",action={model.assistant.clearAnswer()})
        Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("PROPOSED · NOTHING SENT",color=Amber,fontSize=11.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=8.dp))
            MenuList(s,model,entries,answer.answer)
        }
        return
    }
    SideEffect { model.menuActions=emptyList() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(if(assistant.account.subscribed && assistant.connected)"Codex · ${assistant.account.plan} · ${assistant.memory.messages.size/2} recent turns"else "Uses your Codex subscription on the SSH host",color=Muted,fontSize=13.sp)
        OutlinedTextField(assistant.draft,model.assistant::edit,modifier=Modifier.fillMaxWidth().heightIn(min=96.dp),
            placeholder={ Text("Ask about your agents or device…",fontSize=18.sp) },textStyle=LocalTextStyle.current.copy(fontSize=19.sp,lineHeight=27.sp),enabled=!assistant.busy)
        if(s.selected!=null)Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(assistant.includeOutput,{model.assistant.includeOutput(it)},enabled=s.selected!=null && !assistant.busy)
            Text("Include selected terminal output",fontSize=14.sp)
        }
        Text("Sends your request + agent/app inventory.${if(assistant.includeOutput && s.selected!=null)" Includes selected output."else " No terminal output."}",fontSize=12.sp,lineHeight=17.sp,color=Muted)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick=model::askAssistant,enabled=assistant.draft.isNotBlank() && !assistant.busy) { Text("Ask") }
            OutlinedButton(onClick=model::beginVoice,enabled=!assistant.busy) { Text("Use mic") }
            TextButton(onClick={model.navigate(Screen.CONVERSATION)}) { Text("Context") }
        }
        TextButton(onClick={model.navigate(Screen.CODEX)}) { Text("Codex account") }
        if(assistant.busy)LinearProgressIndicator(Modifier.fillMaxWidth(),color=White,trackColor=Outline)
        Text(assistant.status,fontSize=13.sp,color=if(assistant.busy)Muted else Amber)
        if(assistant.busy)TextButton(onClick={model.assistant.stop()}) { Text("Stop") }
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
    SideEffect { model.menuActions=emptyList() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),verticalArrangement=Arrangement.spacedBy(15.dp)) {
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
    val memory=assistant.memory
    val entries=listOf(
        MenuEntry("Continue conversation","${memory.messages.size/2} recent turns on this launcher",action={model.openAssistant()}),
        MenuEntry("New conversation","Start fresh; the previous conversation remains in Codex on the host",enabled=!assistant.busy,action={model.assistant.newConversation();model.openAssistant()}),
        MenuEntry("Codex account",assistant.status,action={model.navigate(Screen.CODEX)})
    )+memory.messages.map { MenuEntry(it.role,it.text,enabled=false,action={}) }
    MenuList(s,model,entries,if(memory.threadId.isBlank())"Your assistant gets its own Codex conversation with the first request."else "Dedicated Codex conversation · ${memory.threadId.take(8)}. Context survives reconnects. Each request refreshes the agent inventory; actions still need your review.")
}
