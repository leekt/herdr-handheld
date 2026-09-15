package dev.herdr.handheld.herdr

import dev.herdr.handheld.assistant.CodexClient
import dev.herdr.handheld.assistant.text
import kotlinx.serialization.json.*

enum class AgentView { CHAT, TERMINAL }
data class AgentMessage(val id: String,val role: String,val text: String)
data class AgentChatPage(val messages: List<AgentMessage>,val older: String?)
data class AgentChatState(
    val page: AgentChatPage?=null,val updatedAt: Long=0,val pending: AgentChatPage?=null,val pendingAt: Long=0,
    val following: Boolean=true,val cursor: String?=null,val loading: Boolean=false,val error: String="",
) {
    fun receive(value: AgentChatPage,at: Long): AgentChatState = when {
        following || page==null -> copy(page=value,updatedAt=at,pending=null,pendingAt=0,loading=false,error="")
        value==page -> copy(updatedAt=at,pending=null,pendingAt=0,loading=false,error="")
        else -> copy(pending=value,pendingAt=at,loading=false,error="")
    }
    fun follow(value: Boolean): AgentChatState =
        if(value && pending!=null)copy(page=pending,updatedAt=pendingAt,pending=null,pendingAt=0,following=true)
        else copy(following=value)
}

/** Persisted Codex display history only. Never resume, subscribe, start a turn or send input. */
object AgentChat {
    fun supported(target: TargetRef?)=target?.agentKind=="codex" && !target.agentSessionId.isNullOrBlank()
    fun defaultView(target: TargetRef?)=if(supported(target))AgentView.CHAT else AgentView.TERMINAL
    suspend fun read(client: CodexClient,target: TargetRef,cursor: String?): AgentChatPage {
        require(supported(target))
        val threadId=target.agentSessionId!!
        val thread=client.call("thread/read",buildJsonObject { put("threadId",threadId);put("includeTurns",false) })["thread"] as? JsonObject
        if(thread?.text("id")!=threadId)throw ContractException("Conversation identity changed")
        return decode(client.call("thread/turns/list",buildJsonObject {
            put("threadId",threadId);put("limit",6);put("sortDirection","desc");put("itemsView","summary")
            if(cursor!=null)put("cursor",cursor)
        }))
    }
    fun decode(result: JsonObject): AgentChatPage {
        val turns=result["data"] as? JsonArray ?: throw ContractException("Missing conversation turns")
        if(turns.size>6)throw ContractException("Conversation page exceeded limit")
        val messages=mutableListOf<AgentMessage>()
        for(turn in turns.reversed()) {
            val t=turn.jsonObject;val id=t.text("id")
            if(id.isBlank())throw ContractException("Missing turn identity")
            val items=t["items"] as? JsonArray ?: throw ContractException("Missing conversation items")
            for(entry in items) {
                val item=entry.jsonObject
                val role=when(item.text("type")) { "userMessage"->"You";"agentMessage"->"Agent";else->continue }
                val text=if(role=="Agent")item.text("text") else (item["content"] as? JsonArray).orEmpty().joinToString("\n") { part ->
                    val content=part.jsonObject
                    when(content.text("type")) { "text"->content.text("text");"image","localImage"->"[Image]";else->"[Attachment]" }
                }
                if(text.isBlank())continue
                val itemId=item.text("id");if(itemId.isBlank())throw ContractException("Missing message identity")
                messages+=AgentMessage("$id/$itemId",role,if(text.length>24000)text.take(24000)+"\n[Message shortened. Open Terminal for current output.]"else text)
                if(messages.size>48)throw ContractException("Too many conversation messages")
            }
        }
        if(messages.map { it.id }.distinct().size!=messages.size)throw ContractException("Duplicate conversation identity")
        return AgentChatPage(messages,result.text("nextCursor").takeIf { it.isNotBlank() })
    }
}
