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
}
