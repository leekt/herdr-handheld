package dev.herdr.handheld.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.herdr.handheld.connection.*
import dev.herdr.handheld.herdr.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable internal fun AgentViewSwitch(s: UiState,model: ConnectionCoordinator) {
    Row(Modifier.selectableGroup().semantics { contentDescription="Agent view mode" },horizontalArrangement=Arrangement.spacedBy(2.dp)) {
        for((view,label) in listOf(AgentView.CHAT to "Messages",AgentView.TERMINAL to "Terminal")) {
            val active=s.agentView==view
            TextButton(onClick={if(!active)model.setAgentView(view)},
                modifier=Modifier.height(38.dp).semantics { selected=active;role=Role.Tab;contentDescription="Show ${label.lowercase(Locale.ROOT)}" },
                contentPadding=PaddingValues(horizontal=9.dp),shape=RoundedCornerShape(9.dp),
                colors=ButtonDefaults.textButtonColors(contentColor=if(active)Amber else Muted,containerColor=if(active)FocusPanel else androidx.compose.ui.graphics.Color.Transparent)) {
                Text(label,fontSize=12.sp,maxLines=1,softWrap=false)
            }
        }
    }
}

@Composable internal fun AgentChatScreen(s: UiState,model: ConnectionCoordinator) {
    val chat=s.chat
    Column(Modifier.fillMaxSize().background(Ink).semantics { contentDescription="Agent conversation" }) {
        Row(Modifier.fillMaxWidth().padding(start=18.dp,end=6.dp,top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.selected?.title.orEmpty(),fontSize=19.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                val status=s.agents.find { it.ref==s.selected?.ref }?.status ?: "unknown"
                val label=when {
                    s.phase!=ConnectionPhase.READY->"Offline · cached messages"
                    chat.cursor!=null->"Earlier messages"
                    !chat.following->"Reading paused · $status"
                    else->"Saved messages · $status"
                }
                Text(label,fontSize=11.sp,color=if(s.phase==ConnectionPhase.READY)Muted else Amber,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            AgentViewSwitch(s,model)
        }
        HorizontalDivider(color=Outline)
        key(s.selected?.ref?.key,chat.cursor) {
            val scroll=rememberScrollState()
            val dragging by scroll.interactionSource.collectIsDraggedAsState()
            val scope=rememberCoroutineScope()
            SideEffect { model.chatScroll={ delta ->
                model.followChat(false)
                scope.launch {
                    scroll.scrollTo((scroll.value+delta).coerceIn(0,scroll.maxValue))
                    if(scroll.value==scroll.maxValue && chat.cursor==null)model.followChat(true)
                }
            } }
            DisposableEffect(model) { onDispose { model.chatScroll=null } }
            LaunchedEffect(scroll.maxValue,chat.following) {
                if(chat.following && scroll.maxValue!=Int.MAX_VALUE)scroll.scrollTo(scroll.maxValue)
            }
            LaunchedEffect(dragging) { if(dragging)model.followChat(false) }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal=18.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                if(chat.page?.older!=null)OutlinedButton(onClick=model::olderChat,enabled=!chat.loading,modifier=Modifier.align(Alignment.CenterHorizontally)) { Text("Earlier messages") }
                if(chat.page==null)Text(if(chat.loading)"Reading this conversation…"else chat.error.ifBlank { "No saved messages yet. Terminal shows the current screen." },fontSize=19.sp,lineHeight=27.sp,color=Muted)
                if(chat.page?.messages?.isEmpty()==true)Text("No saved messages yet. Open Terminal for live output.",fontSize=19.sp,lineHeight=27.sp,color=Muted)
                for(message in chat.page?.messages.orEmpty())key(message.id) {
                    val user=message.role=="You"
                    Column(Modifier.fillMaxWidth().padding(start=if(user)28.dp else 0.dp)
                        .background(if(user)FocusPanel else Ink,RoundedCornerShape(12.dp)).padding(if(user)14.dp else 0.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(message.role.uppercase(Locale.ROOT),fontSize=10.sp,color=if(user)Amber else Muted,fontWeight=FontWeight.Bold)
                        val formatted=remember(message.text,message.role) { if(user)androidx.compose.ui.text.AnnotatedString(message.text)else MessageMarkdown.parse(message.text) }
                        Text(formatted,fontSize=s.fontSize.sp,lineHeight=(s.fontSize*1.5).sp)
                    }
                }
                if(chat.updatedAt>0)Text("Saved history · ${SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(Date(chat.updatedAt))}\nTool details and live choices are in Terminal.",fontSize=11.sp,lineHeight=16.sp,color=Muted)
                if(chat.page!=null && chat.error.isNotBlank())Text(chat.error,fontSize=13.sp,color=Amber)
            }
        }
        if(!chat.following || chat.cursor!=null || chat.error.isNotBlank())TextButton(onClick=model::latestChat,modifier=Modifier.align(Alignment.End)) { Text(if(chat.error.isNotBlank())"Retry messages"else "Latest messages ↓",color=Amber) }
        if(s.deliveryUncertain || s.problem!=ProblemCode.NONE)Text(s.message,Modifier.fillMaxWidth().background(Panel).padding(12.dp,8.dp),fontSize=13.sp,color=Amber,maxLines=3)
        HorizontalDivider(color=Outline)
        Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal=10.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={model.openCompose()},modifier=Modifier.weight(1f)) { Text("Write a reply…",Modifier.fillMaxWidth(),fontSize=17.sp,color=Muted) }
            TextButton(onClick=model::beginTerminalVoice) { Text("Mic",fontSize=16.sp,color=Amber) }
        }
    }
}
