package dev.herdr.handheld

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.herdr.handheld.connection.ConnectionCoordinator
import dev.herdr.handheld.herdr.*
import dev.herdr.handheld.input.LogicalAction
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Reads two existing Codex histories through the real SSH connection. No model turns or terminal input. */
@RunWith(AndroidJUnit4::class)
class RealAgentChatTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun existingAgentsHaveIndependentChatsAndColoredTerminalFallback() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runRealReadOnly")=="true")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: ConnectionCoordinator
            scenario.onActivity { model=ViewModelProvider(it)[ConnectionCoordinator::class.java];model.home() }
            compose.waitUntil(30000) { model.state.value.phase==ConnectionPhase.READY && model.state.value.agents.count { AgentChat.supported(it.ref) }>=2 }
            val targets=model.state.value.agents.filter { AgentChat.supported(it.ref) }.take(2)
            for(target in targets) {
                scenario.onActivity { model.open(target) }
                compose.waitUntil(30000) { model.state.value.chat.page!=null || model.state.value.chat.error.isNotBlank() }
                assertEquals("",model.state.value.chat.error)
                assertEquals(AgentView.CHAT,model.state.value.agentView)
                assertTrue(model.state.value.chat.page!!.messages.any { it.role=="You" })
                assertTrue(model.state.value.chat.page!!.messages.any { it.role=="Agent" })
                assertEquals(target.ref,model.state.value.selected?.ref)
                assertEquals(TerminalAccess.NONE,model.state.value.access)
                compose.onNodeWithContentDescription("Agent conversation").assertIsDisplayed()
                scenario.onActivity { model.dispatch(LogicalAction.UP) }
                compose.waitUntil(5000) { !model.state.value.chat.following }
                scenario.onActivity { model.latestChat() }
                compose.waitUntil(5000) { model.state.value.chat.following }
                scenario.onActivity { model.setAgentView(AgentView.TERMINAL) }
                compose.waitUntil(15000) { model.state.value.reading.formatted!=null }
                assertEquals(AgentView.TERMINAL,model.state.value.agentView)
                assertTrue(model.state.value.reading.formatted!!.spanStyles.size>1)
                scenario.onActivity { model.setAgentView(AgentView.CHAT) }
                compose.onNodeWithContentDescription("Agent conversation").assertIsDisplayed()
            }
            // A response from the previous target may not populate the newly selected chat.
            scenario.onActivity { model.open(targets[0]);model.open(targets[1]) }
            compose.waitUntil(30000) { model.state.value.chat.page!=null }
            assertEquals(targets[1].ref,model.state.value.selected?.ref)
            assertFalse(model.state.value.sending)
            scenario.onActivity { model.home() }
        }
    }
}
