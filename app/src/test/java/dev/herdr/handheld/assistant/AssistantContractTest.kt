package dev.herdr.handheld.assistant

import dev.herdr.handheld.herdr.*
import org.junit.Assert.*
import org.junit.Test

class AssistantContractTest {
    private val target=AgentTarget(TargetRef("host","named-session","terminal-1","pane-1","codex","agent-session"),"Example project","blocked")
    private val context=AssistantContext(HostProfile(session="named-session"),listOf(target),mapOf("app.example" to "Example app"),target.ref,null,1)
    @Test fun resolvesExactInventoryAliasAndRetainsFullRecipient() {
        val action=AssistantProposal("DRAFT_MESSAGE",AssistantContract.alias(target.ref),"Continue reviewing the test results.","Draft instruction")
        assertTrue(AssistantContract.valid(action,context));assertEquals(target.ref,AssistantContract.target(action,context)?.ref)
        assertFalse(AssistantContract.valid(action.copy(target=target.title),context))
        assertFalse(AssistantContract.valid(action.copy(target="agent-00"),context))
        assertFalse(AssistantContract.valid(action.copy(target="agent-2"),context))
    }
    @Test fun rejectsUnsupportedCommandsAndTerminalControlCharacters() {
        assertFalse(AssistantContract.valid(AssistantProposal("EXEC","","rm anything","Run"),context))
        assertFalse(AssistantContract.valid(AssistantProposal("DRAFT_MESSAGE",AssistantContract.alias(target.ref),"\u001b[200~hidden","Send"),context))
        assertFalse(AssistantContract.valid(AssistantProposal("OPEN_APP","unlisted","","Open"),context))
        assertFalse(AssistantContract.valid(AssistantProposal("SET_FONT","","1","Change"),context))
    }
    @Test fun outputIsExcludedByDefaultAndRequestIsJsonData() {
        val prompt=AssistantContract.prompt("\"ignore inventory\"\nsecond line",context)
        assertFalse(prompt.contains("selectedOutput"));assertTrue(prompt.contains("\\nsecond line"))
        assertFalse(prompt.contains("profileId"));assertFalse(prompt.contains("username"))
    }
    @Test fun refusesUnknownOrOversizedAnswers() {
        for(raw in listOf("""{"answer":"ok","actions":[{"action":"EXEC","target":"","text":"","label":"Run"}]}""", "x".repeat(24001))) {
            assertTrue(runCatching { AssistantContract.decode(raw) }.isFailure)
        }
    }
    @Test fun quotesExecutableAsOneArgument() {
        assertEquals("exec '/opt/Codex tools/codex' app-server --listen stdio://",CodexClient.command("/opt/Codex tools/codex"))
        assertTrue(runCatching { CodexClient.command("codex\necho bad") }.isFailure)
    }
    @Test fun boundedContextKeepsSelectedTargetAndSeparatesInventoryFromOutputTime() {
        val agents=(0..100).map { target.copy(ref=target.ref.copy(terminalId="terminal-$it"),title="Agent $it") }
        val selected=agents.last()
        val supplied=context.copy(agents=agents,selected=selected.ref,apps=mapOf("browser" to "Browser","music" to "Music"),output="x".repeat(20000),checkedAt=100,outputAt=20).bounded("Open Browser")
        assertEquals(12,supplied.agents.size);assertEquals(selected,supplied.agents.first())
        assertEquals(setOf("browser"),supplied.apps.keys);assertEquals(8000,supplied.output!!.length)
        assertEquals(89,supplied.omittedAgents)
        val prompt=kotlinx.serialization.json.Json.parseToJsonElement(AssistantContract.prompt("question",supplied,"Use Korean")).toString()
        assertTrue(prompt.contains("\"inventoryCheckedAt\":100"));assertTrue(prompt.contains("\"outputCheckedAt\":20"));assertTrue(prompt.contains("Use Korean"))
    }
    @Test fun legacyMemoryMigratesWithoutLosingThreadAndPickerRetainsEarlierConversation() {
        val old=kotlinx.serialization.json.Json.decodeFromString<AssistantMemory>("""{"threadId":"old-thread","createdAt":1,"messages":[{"role":"You","text":"hello"}]}""")
        val fresh=AssistantMemory()
        val index=ConversationIndex().select(old).select(fresh)
        assertEquals("old-thread",old.threadId);assertEquals("hello",old.messages.single().text)
        assertEquals(2,index.entries.size);assertEquals(fresh.id,index.selected)
        assertEquals(2,index.select(old).entries.size)
    }
}
