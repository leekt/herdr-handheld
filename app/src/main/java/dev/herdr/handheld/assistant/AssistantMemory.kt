package dev.herdr.handheld.assistant

import kotlinx.serialization.Serializable

@Serializable data class AssistantMessage(val role: String,val text: String)
@Serializable data class AssistantMemory(
    val threadId: String="",val createdAt: Long=0,val messages: List<AssistantMessage> = emptyList(),
    val id: String=java.util.UUID.randomUUID().toString(),val title: String="New conversation",val notes: String="",
) {
    fun append(request: String,answer: String): AssistantMemory {
        var recent=(messages+listOf(AssistantMessage("You",request),AssistantMessage("Codex",answer))).takeLast(20)
        while(recent.sumOf { it.text.toByteArray().size }>48000 && recent.size>2)recent=recent.drop(2)
        return copy(messages=recent,title=if(title=="New conversation")request.take(64)else title)
    }
}
@Serializable data class ConversationEntry(val id: String,val title: String,val createdAt: Long)
@Serializable data class ConversationIndex(val version: Int=1,val selected: String="",val entries: List<ConversationEntry> = emptyList()) {
    fun select(memory: AssistantMemory)=copy(selected=memory.id,entries=listOf(ConversationEntry(memory.id,memory.title,memory.createdAt))+entries.filter { it.id!=memory.id })
}
