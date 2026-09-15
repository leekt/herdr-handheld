package dev.herdr.handheld.herdr

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AgentChatTest {
    private fun fixture()=Json.parseToJsonElement(javaClass.classLoader!!.getResource("codex/0.153.4/agent-chat.json")!!.readText()).jsonObject
    @Test fun realRolesAndChronologyComeFromStructuredItemsOnly() {
        val page=AgentChat.decode(fixture())
        assertEquals(listOf("You","Agent","You","Agent"),page.messages.map { it.role })
        assertEquals(listOf("첫 질문","First reply","Continue","Second reply"),page.messages.map { it.text })
        assertEquals("older-page",page.older)
    }
    @Test fun updatesStayPendingWhileReadingAndUnknownProvidersDoNotPretendToHaveChat() {
        val first=AgentChat.decode(fixture());val next=first.copy(messages=first.messages+AgentMessage("new","Agent","New output"))
        val paused=AgentChatState().receive(first,1).follow(false).receive(next,2)
        assertEquals(first,paused.page);assertEquals(next,paused.pending);assertEquals(next,paused.follow(true).page)
        assertFalse(AgentChat.supported(TargetRef("p","s","t","pane","claude","id")))
        assertFalse(AgentChat.supported(TargetRef("p","s","t","pane","codex",null)))
    }
    @Test fun malformedHistoryIsRejected() {
        assertThrows(ContractException::class.java) { AgentChat.decode(buildJsonObject {}) }
        val duplicate=fixture().getValue("data").jsonArray.first()
        assertThrows(ContractException::class.java) { AgentChat.decode(buildJsonObject { put("data",JsonArray(listOf(duplicate,duplicate))) }) }
    }
}
