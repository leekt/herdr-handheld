package dev.herdr.handheld.assistant

import kotlinx.serialization.Serializable

@Serializable data class AssistantMessage(val role: String,val text: String)
@Serializable data class AssistantMemory(val threadId: String="",val createdAt: Long=0,val messages: List<AssistantMessage> = emptyList()) {
    fun append(request: String,answer: String)=copy(messages=(messages+listOf(AssistantMessage("You",request),AssistantMessage("Codex",answer))).takeLast(10))
}
