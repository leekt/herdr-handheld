package dev.herdr.handheld.ui

import android.app.Activity
import android.os.Build
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.herdr.handheld.connection.*
import dev.herdr.handheld.assistant.*
import dev.herdr.handheld.voice.*
import dev.herdr.handheld.herdr.*
import dev.herdr.handheld.input.LogicalAction
import dev.herdr.handheld.input.ControllerCommands
import dev.herdr.handheld.launcher.SystemAccess
import dev.herdr.handheld.terminal.XtermWebView
import dev.herdr.handheld.terminal.TerminalText
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val Ink=Color(0xFF0A0C10)
internal val Panel=Color(0xFF12161D)
internal val FocusPanel=Color(0xFF1A2029)
internal val Outline=Color(0xFF303947)
private val Accent=Color(0xFF8DBFFF)
internal val Muted=Color(0xFFADB7C7)
internal val Amber=Color(0xFFFFB020)
internal val White=Color(0xFFF2F4F7)
internal val Green=Color(0xFF74DAA0)

@Composable
fun PdxApp(model: ConnectionCoordinator) {
    val state by model.state.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme=darkColorScheme(primary=White,onPrimary=Ink,background=Ink,surface=Ink,
        onSurface=White,surfaceVariant=Panel,onSurfaceVariant=Muted,outline=Outline,secondary=Green),typography=HandheldTypography) {
        CompositionLocalProvider(LocalContentColor provides White) {
        BackHandler { model.back() }
        Box(Modifier.fillMaxSize().background(Ink).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.fillMaxSize()) {
                if(state.selected!=null) TerminalScreen(state,model) else HomeScreen(state,model)
                if(state.screen !in setOf(Screen.HOME,Screen.TERMINAL)) {
                    ModalSheet(when(state.screen) {
                        Screen.SETTINGS->"Settings";Screen.DIAGNOSTICS->"Diagnostics";Screen.APPS->"All apps"
                        Screen.CONVERSATION->"Conversation";Screen.ASSISTANT->"Assistant";Screen.CODEX->"Codex account";Screen.VOICE->if(state.voice.phase==VoicePhase.REVIEW)"Review voice"else "Voice"
                        Screen.SYSTEM->"System";Screen.CONTROL->"Input";Screen.COMPOSE->"Compose";else->"Actions"
                    },model::back) {
                        when(state.screen) {
                            Screen.CONVERSATION->ConversationScreen(state,model)
                            Screen.ASSISTANT->AssistantScreen(state,model)
                            Screen.CODEX->CodexScreen(state,model)
                            Screen.VOICE->VoiceScreen(state,model)
                            Screen.SETTINGS->SettingsScreen(state,model)
                            Screen.DIAGNOSTICS->DiagnosticsScreen(state,model)
                            Screen.APPS->AppsScreen(state,model)
                            Screen.SYSTEM->SystemScreen(state,model)
                            Screen.CONTROL->ControlScreen(state,model)
                            Screen.COMPOSE->ComposeScreen(state,model)
                            else->ActionsScreen(state,model)
                        }
                    }
                }
                if(state.hud) ModalSheet("Button map",{model.setHud(false)}) { HudScreen(state,model) }
            }
            if(state.hintsVisible && !state.hud)Box(Modifier.align(Alignment.BottomCenter)) { Footer(state,model) }
        }
        state.hostKey?.let { challenge ->
            AlertDialog(onDismissRequest=model::dismissHostKey,title={ Text(if(challenge.changed) "Host key changed" else "Verify SSH host") },
                text={ Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("${challenge.host}:${challenge.port}")
                    Text(challenge.fingerprint,fontFamily=ReaderFont,fontSize=15.sp)
                    Text("Compare this fingerprint with the server using a trusted connection before continuing.")
                } },
                confirmButton={ TextButton(onClick=model::trustHostKey) { Text(if(challenge.changed) "Replace verified key" else "Trust verified key") } },
                dismissButton={ TextButton(onClick=model::dismissHostKey) { Text("Cancel") } })
        }
        }
    }
}

@Composable private fun ModalSheet(title: String,close: ()->Unit,content: @Composable ()->Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha=.72f)).clickable(onClick=close))
        Surface(Modifier.align(Alignment.Center).padding(horizontal=16.dp,vertical=12.dp).fillMaxWidth().fillMaxHeight(.94f),
            color=Panel,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Outline),shadowElevation=16.dp) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(start=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(title,fontSize=23.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    TextButton(onClick=close) { Text("Close",fontSize=12.sp,color=Muted) }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            }
        }
    }
}

@Composable private fun HomeScreen(s: UiState,model: ConnectionCoordinator) {
    val targets=s.agents.filter { !s.attentionOnly || it.needsResponse }
    val list=rememberLazyListState()
    LaunchedEffect(s.selectedKey,targets.size) {
        val index=targets.indexOfFirst { it.ref.key==s.selectedKey }
        if(index>=0) list.animateScrollToItem(index)
    }
    Box(Modifier.fillMaxSize()) {
        if(targets.isEmpty()) Column(Modifier.align(Alignment.Center).padding(32.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(if(s.attentionOnly)"No reported requests"else "Connect to Herdr",fontSize=26.sp,fontWeight=FontWeight.Bold)
            Text(if(s.attentionOnly)"Check all agents when in doubt."else "Your existing agents will appear here.",color=Muted)
            Button(onClick={if(s.attentionOnly)model.toggleAttention()else model.navigate(Screen.SETTINGS)}) { Text(if(s.attentionOnly)"All agents"else "Set up connection") }
        } else LazyColumn(state=list,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(start=32.dp,end=32.dp,top=if(s.phase==ConnectionPhase.READY)28.dp else 54.dp,bottom=28.dp),
            verticalArrangement=Arrangement.spacedBy(5.dp,Alignment.CenterVertically)) {
            itemsIndexed(targets,key={_,a->a.ref.key}) { _,agent ->
                val focused=agent.ref.key==s.selectedKey
                val color=when(agent.status) { "blocked"->Amber;"working","running"->Green;else->Muted }
                Surface(onClick={model.select(agent.ref.key);model.open(agent)},modifier=Modifier.fillMaxWidth().semantics { selected=focused },
                    color=if(focused)FocusPanel else Color.Transparent,shape=RoundedCornerShape(12.dp),
                    border=BorderStroke(2.dp,if(focused)White else Color.Transparent)) {
                    Row(Modifier.heightIn(min=62.dp).padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(9.dp).background(color,RoundedCornerShape(5.dp)))
                        Text(agent.title,fontSize=21.sp,fontWeight=if(focused)FontWeight.Bold else FontWeight.SemiBold,
                            color=if(focused)White else Muted,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
                        Text(agent.status,fontFamily=ReaderFont,fontSize=10.sp,color=color)
                    }
                }
            }
        }
        if(s.phase!=ConnectionPhase.READY) Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Ink).padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("${s.phase.name.lowercase()} · last ${time(s.lastChecked)}",fontSize=11.sp,color=Amber,modifier=Modifier.weight(1f))
            TextButton(onClick=model::connect) { Text("Reconnect",fontSize=12.sp,color=Amber) }
        }
        else if(s.attentionOnly)Text("Needs response · ${targets.size}",Modifier.align(Alignment.TopStart).padding(24.dp,6.dp),fontSize=11.sp,color=Amber)
    }
}

@Composable private fun TerminalScreen(s: UiState,model: ConnectionCoordinator) {
    val reading=s.mode!=InputMode.REMOTE_KEYS && !s.acquiring
    if(reading && s.access!=TerminalAccess.CONTROLLER && s.agentView==AgentView.CHAT) { AgentChatScreen(s,model);return }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if(!reading || s.access==TerminalAccess.CONTROLLER) AndroidView(factory={ context -> XtermWebView(context,model::viewport) { model.leaveInput(it) }.also { model.bindRenderer(it) } },
            modifier=Modifier.fillMaxSize().padding(start=5.dp,top=26.dp),onRelease={model.bindRenderer(null);it.destroy()})
        if(reading) key(s.selected?.ref?.key) { ReadingPane(s,model) }
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(5.dp).background(if(s.mode==InputMode.REMOTE_KEYS)Amber else Color(0xFF4A5464)))
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black.copy(alpha=.95f)).padding(start=12.dp,end=6.dp),verticalAlignment=Alignment.CenterVertically) {
            val label=when {
                s.acquiring->"REQUESTING";s.mode==InputMode.REMOTE_KEYS->"INPUT · ${s.selected?.title}"
                s.phase!=ConnectionPhase.READY || s.problem==ProblemCode.OUTPUT->"READ · cached ${time(s.reading.updatedAt)}"
                !s.reading.following->"READ · paused";else->"READ · ${s.selected?.title}"
            }
            Text(label,Modifier.weight(1f).clickable { model.setHud(true) }.padding(vertical=7.dp),fontSize=11.sp,
                color=if(s.mode==InputMode.REMOTE_KEYS || s.phase!=ConnectionPhase.READY)Amber else Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(reading && !s.reading.following) TextButton(onClick=model::resumeReading,contentPadding=PaddingValues(horizontal=10.dp),modifier=Modifier.height(32.dp)) { Text("Latest ↓",fontSize=11.sp,color=Amber) }
            if(reading && AgentChat.supported(s.selected?.ref))AgentViewSwitch(s,model)
            if(!reading && !s.acquiring)TextButton(onClick=model::beginTerminalVoice,contentPadding=PaddingValues(horizontal=12.dp),modifier=Modifier.height(36.dp)) { Text("Mic",fontSize=12.sp,color=Amber) }
        }
        if(s.deliveryUncertain || s.phase!=ConnectionPhase.READY || s.problem!=ProblemCode.NONE)
            Text(s.message,Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Panel).padding(12.dp,8.dp),fontSize=12.sp,color=Amber,maxLines=3)
    }
}

private data class ReadPosition(val value: Int,val maximum: Int,val moving: Boolean,val dragging: Boolean)

@Composable private fun ReadingPane(s: UiState,model: ConnectionCoordinator) {
    val scroll=rememberScrollState()
    val dragging by scroll.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(scroll.maxValue,s.reading.following) {
        if(s.reading.following && scroll.maxValue!=Int.MAX_VALUE) scroll.scrollTo(scroll.maxValue)
    }
    LaunchedEffect(s.readScrollRequest) {
        if(!s.reading.following)scroll.scrollTo(s.readScrollTarget.coerceAtMost(scroll.maxValue))
    }
    LaunchedEffect(scroll) {
        var touchScrolling=false
        snapshotFlow { ReadPosition(scroll.value,scroll.maxValue,scroll.isScrollInProgress,dragging) }.distinctUntilChanged().collect { position ->
            if(position.dragging)touchScrolling=true
            val settled=!position.moving && !position.dragging
            model.recentScrollPosition(position.value,position.maximum,touchScrolling,settled)
            if(settled)touchScrolling=false
        }
    }
    Text(s.reading.formatted ?: AnnotatedString(if(s.phase==ConnectionPhase.READY)"Loading output…"else "Reconnect to read this agent's output."),
        Modifier.fillMaxSize().background(Color.Black).verticalScroll(scroll).padding(start=21.dp,end=16.dp,top=38.dp,bottom=10.dp),color=TerminalText.foreground,fontFamily=ReaderFont,fontSize=s.fontSize.sp,lineHeight=(s.fontSize*1.45).sp)
}

internal data class MenuEntry(val title: String,val detail: String="",val enabled: Boolean=true,val action: ()->Unit)

@Composable internal fun MenuList(s: UiState,model: ConnectionCoordinator,entries: List<MenuEntry>,intro: String?=null) {
    SideEffect { model.menuActions=entries.map { entry -> { if(entry.enabled)entry.action() } } }
    val list=rememberLazyListState()
    LaunchedEffect(s.menuIndex) { if(entries.isNotEmpty())list.animateScrollToItem(if(s.menuIndex==0)0 else s.menuIndex.coerceIn(0,entries.lastIndex)+(if(intro!=null)1 else 0)) }
    LazyColumn(state=list,modifier=Modifier.fillMaxSize().background(Panel),contentPadding=PaddingValues(4.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        if(intro!=null)item { Text(intro,color=White,fontSize=17.sp,lineHeight=25.sp,modifier=Modifier.padding(8.dp)) }
        itemsIndexed(entries) { index,entry ->
            val focused=index==s.menuIndex
            Surface(onClick={if(entry.enabled)entry.action()},modifier=Modifier.fillMaxWidth().semantics { selected=focused },enabled=entry.enabled,
                color=FocusPanel,shape=RoundedCornerShape(10.dp),border=BorderStroke(2.dp,if(focused)White else Color.Transparent)) {
                Column(Modifier.padding(horizontal=14.dp,vertical=12.dp)) {
                    Text(entry.title,fontSize=18.sp,color=if(entry.enabled)White else Muted)
                    if(entry.detail.isNotBlank())Text(entry.detail,fontSize=12.sp,lineHeight=17.sp,color=Muted,modifier=Modifier.padding(top=3.dp))
                }
            }
        }
    }
}

@Composable private fun ActionsScreen(s: UiState,model: ConnectionCoordinator) {
    val entries=buildList {
        if(AgentChat.supported(s.selected?.ref)) {
            val showMessages=s.agentView==AgentView.TERMINAL || s.access==TerminalAccess.CONTROLLER || s.acquiring
            add(MenuEntry(if(showMessages)"Switch to Messages"else "Switch to Terminal",
                if(showMessages)"Read the conversation · default view"else "Colored output and interactive prompts",action=model::toggleAgentView))
        }
        add(MenuEntry("Assistant","Ask Codex using voice or keyboard",action={model.openAssistant()}))
        if(s.selected!=null) {
            if(AgentChat.supported(s.selected.ref)) {
                if(s.agentView==AgentView.CHAT) {
                    add(MenuEntry("Earlier messages",enabled=s.chat.page?.older!=null && !s.chat.loading,action={model.navigate(Screen.TERMINAL);model.olderChat()}))
                    add(MenuEntry("Latest messages",action={model.navigate(Screen.TERMINAL);model.latestChat()}))
                }
            }
            if(s.access==TerminalAccess.NONE) add(MenuEntry("Reopen terminal","Fetch a new initial screen",action={model.open(s.selected)}))
            add(MenuEntry("Ask for next steps","Ask Codex about the selected agent.",action=model::askNextSteps))
            add(MenuEntry("Write a message","Native editor · drafts stay on this device",action={model.openCompose()}))
            add(MenuEntry("Voice message","Dictate to this agent, then review",action=model::beginTerminalVoice))
            if(s.access==TerminalAccess.CONTROLLER) {
                for(key in listOf("Esc","Tab","Shift+Tab","Ctrl+C")) add(MenuEntry(key,if(key=="Ctrl+C")"Interrupt the current input or process"else "Send this exact key",action={model.sendKey(key)}))
                add(MenuEntry("Exit input mode","Release control and return to reading",action={model.leaveInput();model.navigate(Screen.TERMINAL)}))
            } else add(MenuEntry("Enter input mode","Review the target, then request control",enabled=s.phase==ConnectionPhase.READY && s.capabilities?.control==true,action=model::showControl))
        } else add(MenuEntry(if(s.attentionOnly)"Show all agents"else "Show agents needing response","Based on Herdr's reported blocked state",action={model.toggleAttention();model.home()}))
        add(MenuEntry("Button map","Host, target, connection and controls",action={model.setHud(true)}))
        add(MenuEntry("Larger text","Current size ${s.fontSize}",action={model.setFont(s.fontSize+1)}))
        add(MenuEntry("Smaller text","Current size ${s.fontSize}",action={model.setFont(s.fontSize-1)}))
        add(MenuEntry("System",action=model::showSystem))
        add(MenuEntry("Settings",action={model.navigate(Screen.SETTINGS)}))
        add(MenuEntry("Controller lab","Raw events and button calibration",action={model.navigate(Screen.DIAGNOSTICS)}))
        add(MenuEntry("Disconnect","Remote agents keep running",action={model.disconnect();model.home()}))
    }
    MenuList(s,model,entries)
}

@Composable private fun ComposeScreen(s: UiState,model: ConnectionCoordinator) {
    Column(Modifier.fillMaxSize().background(Panel).padding(8.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text(if(s.reviewDraft) "Review before sending" else "Write to ${s.selected?.ref?.agentKind ?: "agent"}",fontSize=19.sp,fontWeight=FontWeight.SemiBold)
        Text("${s.profile.host} / ${s.profile.session} / ${s.selected?.title}",fontSize=12.sp,color=Amber)
        if(s.reviewDraft) {
            Text(s.draft,Modifier.weight(1f).fillMaxWidth().background(Panel,RoundedCornerShape(8.dp)).padding(12.dp).verticalScroll(rememberScrollState()),fontSize=17.sp)
            Text(if(s.acquiring) "Requesting control… B cancels." else "A sends text + Enter. No automatic retry.",fontSize=12.sp,color=Muted)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={model.confirmDraft(true)},enabled=!s.acquiring && !s.sending && s.phase==ConnectionPhase.READY) { Text("Send + Enter") }
                OutlinedButton(onClick={model.confirmDraft(false)},enabled=!s.acquiring && !s.sending && s.phase==ConnectionPhase.READY) { Text("Text only") }
            }
        } else {
            OutlinedTextField(value=s.draft,onValueChange=model::editDraft,modifier=Modifier.weight(1f).fillMaxWidth(),
                placeholder={Text("Short instructions, in your own words")},textStyle=LocalTextStyle.current.copy(fontSize=17.sp),minLines=3)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(s.draftSaved) "Draft saved locally" else "Unsent draft",fontSize=12.sp,color=Muted,modifier=Modifier.weight(1f))
                TextButton(onClick=model::clearDraft) { Text("Clear") }
                TextButton(onClick=model::beginTerminalVoice) { Text("Mic") }
                Button(onClick=model::reviewDraft,enabled=s.draft.isNotBlank()) { Text("Review") }
            }
        }
        if(s.problem==ProblemCode.INPUT) Text(s.message,fontSize=12.sp,color=Amber)
    }
}

@Composable private fun SettingsScreen(s: UiState,model: ConnectionCoordinator) {
    val context=LocalContext.current
    var panel by remember { mutableStateOf("menu") }
    var passphrase by remember { mutableStateOf("") }
    val scope=rememberCoroutineScope()
    val keyPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            val bytes=withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.use { input ->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
                while(output.size()<=65536) {
                    val count=input.read(buffer,0,minOf(buffer.size,65537-output.size()))
                    if(count<0)break
                    output.write(buffer,0,count)
                }
                output.toByteArray()
            } }.getOrNull() }
            if(bytes!=null)model.importKey(bytes,passphrase)else model.message("Could not read the selected key.")
            passphrase=""
        }
    }
    // Form editing is local; never leave actions from the hidden settings menu active.
    if(panel!="menu") SideEffect { model.menuActions=emptyList() }
    if(panel=="connection") {
        var host by remember { mutableStateOf(s.profile.host) }
        var user by remember { mutableStateOf(s.profile.username) }
        var port by remember { mutableStateOf(s.profile.port.toString()) }
        var session by remember { mutableStateOf(s.profile.session) }
        var binary by remember { mutableStateOf(s.profile.binary) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text("SSH connection",fontSize=20.sp)
            Text("Use an existing SSH server and Herdr session.",fontSize=13.sp,color=Muted)
            OutlinedTextField(host,{host=it},label={Text("Host or IP")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(user,{user=it},label={Text("SSH user")},singleLine=true,modifier=Modifier.weight(2f))
                OutlinedTextField(port,{port=it.filter(Char::isDigit)},label={Text("Port")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f))
            }
            OutlinedTextField(session,{session=it},label={Text("Herdr session")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(binary,{binary=it},label={Text("Herdr executable path")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Button(onClick={model.saveProfile(s.profile.copy(host=host.trim(),username=user.trim(),port=port.toIntOrNull() ?: 22,session=session.trim(),binary=binary.trim(),name=host.trim()));panel="menu"},
                enabled=host.isNotBlank() && user.isNotBlank() && session.isNotBlank() && binary.isNotBlank() && (port.toIntOrNull() ?: 0) in 1..65535) { Text("Save connection") }
            TextButton(onClick={panel="menu"}) { Text("Back to settings") }
        };return
    }
    if(panel=="key") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("SSH device key",fontSize=20.sp)
            Text("Create a dedicated key here, then authorize its public key on your host. The private key stays encrypted on this device.",color=Muted,fontSize=14.sp)
            if(!s.hasKey) {
                Button(onClick=model::generateKey,enabled=!s.generatingKey && s.profile.host.isNotBlank()) { Text(if(s.generatingKey)"Creating key…"else "Create device key") }
                if(s.profile.host.isBlank())Text("Save your SSH connection first.",color=Amber,fontSize=13.sp)
            }
            if(s.publicKey.isNotBlank()) {
                Text("Public key · add to the host's authorized_keys",fontSize=13.sp,color=Amber)
                SelectionContainer { Text(s.publicKey,fontFamily=ReaderFont,fontSize=12.sp) }
                OutlinedButton(onClick={
                    context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("PDX device public key",s.publicKey))
                    model.message("Public key copied. The private key stays on this device.")
                }) { Text("Copy public key") }
            }
            HorizontalDivider(color=Outline)
            Text("Or import an existing private key",fontSize=14.sp)
            OutlinedTextField(passphrase,{passphrase=it},label={Text("Key passphrase, if any")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
            Button(onClick={keyPicker.launch(arrayOf("*/*"))}) { Text("Choose private key") }
            Text(if(s.hasKey)"A key is stored for this profile."else "No key imported.",color=if(s.hasKey)Green else Amber)
            if(s.hasKey)OutlinedButton(onClick=model::forgetKey) { Text("Remove stored key") }
            Text(s.message,fontSize=13.sp,color=Muted)
            TextButton(onClick={panel="menu";passphrase=""}) { Text("Back to settings") }
        };return
    }
    val entries=listOf(
        MenuEntry("Codex assistant","Subscription connection and voice language",action={model.navigate(Screen.CODEX)}),
        MenuEntry("SSH connection",if(s.profile.host.isBlank())"No host configured"else "${s.profile.username}@${s.profile.host} · ${s.profile.session}",action={panel="connection"}),
        MenuEntry("SSH key",if(s.hasKey)"Encrypted key stored"else "Create a device key or import one",action={panel="key"}),
        MenuEntry("Connect to host","Reconnect with your saved SSH profile",enabled=s.profile.host.isNotBlank() && s.hasKey,action={model.connect();model.home()}),
        MenuEntry("Controller lab","Inspect events and calibrate physical buttons",action={model.navigate(Screen.DIAGNOSTICS)}),
        MenuEntry("Larger terminal text","Current size ${s.fontSize} · maximum 26",action={model.setFont(s.fontSize+1)}),
        MenuEntry("Smaller terminal text","Current size ${s.fontSize} · minimum 12",action={model.setFont(s.fontSize-1)}),
        MenuEntry("Arrow key protocol: ${if(s.applicationCursor)"application"else "normal"}","Advanced · switch if the target ignores arrow keys",action=model::toggleCursorProtocol),
        MenuEntry("Use as home app","Android asks you to choose the default launcher",action={SystemAccess.requestHome(context as Activity)}),
        MenuEntry("Change home app","Return to your previous launcher",action={SystemAccess.settings(context,true)}),
        MenuEntry("Android settings","Wi-Fi, display, and system controls",action={SystemAccess.settings(context)}),
        MenuEntry("Open another app","Installed apps remain accessible",action={model.navigate(Screen.APPS)}),

    )
    MenuList(s,model,entries)
}

@Composable private fun DiagnosticsScreen(s: UiState,model: ConnectionCoordinator) {
    val context=LocalContext.current
    val metrics=context.resources.displayMetrics
    val webview=remember { WebView.getCurrentWebViewPackage()?.versionName ?: "Unavailable" }
    val raw=s.diagnostics.take(5).joinToString("\n").ifBlank { "Press a controller button to see its event." }
    val prompt=s.calibrating?.let { action -> "Press ${ControllerCommands.calibration.firstOrNull { it.first==action }?.second ?: action.name}" }
    val entries=listOf(
        MenuEntry(prompt ?: "Calibrate buttons",if(prompt!=null)"The next physical press is recorded locally."else "Map physical A/B/X/Y, L1/R1, Select/Start",action=model::calibrate),
        MenuEntry("Reset button profile","Restore Android defaults",action=model::resetCalibration),
        MenuEntry("Return home",action=model::home)
    )
    Column(Modifier.fillMaxSize()) {
        Text("PDX ${dev.herdr.handheld.BuildConfig.VERSION_NAME} · ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\n${metrics.widthPixels} × ${metrics.heightPixels} · ${metrics.densityDpi} dpi · WebView $webview\nSSH ${s.sshPhase.name} · Herdr ${s.phase.name}\n${s.capabilities?.notes.orEmpty()}",
            Modifier.padding(horizontal=16.dp,vertical=9.dp),fontSize=12.sp,color=Muted)
        Text(raw,Modifier.fillMaxWidth().background(Panel).padding(horizontal=16.dp,vertical=9.dp),fontSize=12.sp,lineHeight=17.sp,fontFamily=ReaderFont,color=Green)
        Box(Modifier.weight(1f)) { MenuList(s,model,entries) }
    }
}

@Composable private fun AppsScreen(s: UiState,model: ConnectionCoordinator) {
    val context=LocalContext.current
    val apps=remember { SystemAccess.apps(context) }
    MenuList(s,model,apps.map { app -> MenuEntry(app.label,action={if(!SystemAccess.open(context,app))model.message("Could not open this app. Use Android settings.")}) })
}

@Composable private fun ControlScreen(s: UiState,model: ConnectionCoordinator) {
    Column(Modifier.fillMaxSize().padding(6.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(s.selected?.title.orEmpty(),fontSize=25.sp,fontWeight=FontWeight.Bold,color=Amber)
        Text("${s.profile.host} / ${s.profile.session} / ${s.selected?.ref?.paneId.orEmpty()}",fontFamily=ReaderFont,fontSize=12.sp,color=Muted)
        Text("Speak or write a message, or use controller keys for terminal choices.",fontSize=17.sp,lineHeight=24.sp)
        Box(Modifier.weight(1f)) { MenuList(s,model,listOf(
            MenuEntry("Request control","No automatic takeover",action={model.navigate(Screen.TERMINAL);model.requestInput()}),
            MenuEntry("Voice message","Hold Y from the terminal, or start here",action=model::beginTerminalVoice),
            MenuEntry("Write a message","Review the recipient and text before sending",action={model.openCompose()}),
            MenuEntry("Keep reading",action={model.navigate(Screen.TERMINAL)})
        )) }
    }
}

@Composable private fun SystemScreen(s: UiState,model: ConnectionCoordinator) {
    val context=LocalContext.current
    val apps=remember { SystemAccess.apps(context) }
    MenuList(s,model,listOf(
        MenuEntry("All apps","${apps.size} installed apps",action={model.navigate(Screen.APPS)}),
        MenuEntry("Android settings","Network, display and device settings",action={SystemAccess.settings(context)}),
        MenuEntry("Home app","Choose or restore your launcher",action={SystemAccess.settings(context,true)}),
        MenuEntry("Launcher settings","SSH, keys, text and controller",action={model.navigate(Screen.SETTINGS)}),
        MenuEntry("Diagnostics","Actual device events and connection details",action={model.navigate(Screen.DIAGNOSTICS)}),
        MenuEntry("Disconnect","Remote agents keep running",action={model.disconnect();model.home()}),
        MenuEntry("Agent home",action=model::home)
    ),"${s.profile.name} / ${s.profile.session} · ${s.phase.name.lowercase()}")
}

@Composable private fun HudScreen(s: UiState,model: ConnectionCoordinator) {
    val scroll=rememberScrollState()
    val scope=rememberCoroutineScope()
    SideEffect { model.hudScroll={ delta -> scope.launch { scroll.scrollTo((scroll.value+delta).coerceIn(0,scroll.maxValue)) } } }
    DisposableEffect(model) { onDispose { model.hudScroll=null } }
    val target=s.selected ?: s.agents.find { it.ref.key==s.selectedKey }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(target?.title ?: "Agent home",fontSize=25.sp,fontWeight=FontWeight.Bold)
        Text("${s.profile.name} / ${s.profile.session}\n${s.phase.name} · ${s.access.name} · ${target?.ref?.paneId ?: "no target"}",fontSize=13.sp,color=Muted,fontFamily=ReaderFont)
        Text("Last checked ${time(s.lastChecked)} · output ${time(s.reading.updatedAt)}",fontSize=12.sp,color=Muted)
        for(command in ControllerCommands.forScreen(s.screen,s.mode,s.acquiring,s.reviewDraft,s.voice.phase)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                KeyPill(command.key,false)
                Text(command.detail+if(command.enabled)""else " · unavailable here",fontSize=16.sp,color=if(command.enabled)White else Muted,modifier=Modifier.weight(1f))
            }
        }
        Text("${s.agents.count { it.needsResponse }} need response · ${s.agents.size} agents",fontSize=13.sp,color=Amber)
        Text("Release Select to return. Buttons stay local while the map is open.",fontSize=13.sp,color=Muted)
    }
}

internal data class ButtonHint(val key: String,val label: String,val action: LogicalAction?=null)

@Composable private fun KeyPill(key: String,input: Boolean) {
    Surface(color=if(input)Amber else White,shape=RoundedCornerShape(12.dp)) {
        Text(key,Modifier.padding(horizontal=6.dp,vertical=2.dp),fontSize=10.sp,lineHeight=16.sp,fontFamily=ReaderFont,fontWeight=FontWeight.Bold,color=Ink,maxLines=1,softWrap=false)
    }
}

@Composable private fun Footer(s: UiState,model: ConnectionCoordinator) {
    val hints=when {
        s.hostKey!=null->listOf(ButtonHint("Touch","Compare fingerprint"))
        s.calibrating!=null->listOf(ButtonHint("Any","Assign button"))
        else->ControllerCommands.forScreen(s.screen,s.mode,s.acquiring,s.reviewDraft,s.voice.phase).filter { it.hint && it.enabled }.take(3).map { ButtonHint(it.key,it.label,it.action) }
    }
    val input=s.mode==InputMode.REMOTE_KEYS && !s.hud
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(Panel).heightIn(min=44.dp).semantics { contentDescription="Controller hints" }.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.SpaceBetween) {
        for(hint in hints) Row(Modifier.heightIn(min=44.dp).then(if(hint.action!=null)Modifier.clickable(role=Role.Button) { model.dispatch(hint.action) }else Modifier),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            KeyPill(hint.key,input)
            Text(hint.label,fontSize=11.sp,lineHeight=14.sp,color=if(input)White else Muted,maxLines=1,softWrap=false)
        }
    }
}

private fun time(timestamp: Long): String = if(timestamp==0L) "not checked" else SimpleDateFormat("HH:mm:ss",Locale.US).format(Date(timestamp))
