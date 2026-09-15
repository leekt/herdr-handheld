package dev.herdr.handheld

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
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

/** Real paired host, no agent prompts. A temporary in-memory binary path tests history failure. */
@RunWith(AndroidJUnit4::class)
class RealMessageModeTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun messageDefaultSurvivesControllerExitRecreationAndHistoryFailure() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runRealReadOnly")=="true")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: ConnectionCoordinator
            scenario.onActivity { model=ViewModelProvider(it)[ConnectionCoordinator::class.java];model.home() }
            compose.waitUntil(30000) { model.state.value.phase==ConnectionPhase.READY && model.state.value.agents.any { AgentChat.supported(it.ref) } }
            val target=model.state.value.agents.first { AgentChat.supported(it.ref) }
            val originalBinary=model.assistant.state.value.binary
            try {
                scenario.onActivity { model.open(target) }
                compose.waitUntil(30000) { model.state.value.chat.page!=null }
                compose.onNodeWithContentDescription("Show messages").assertIsDisplayed().assertIsSelected()
                compose.onNodeWithContentDescription("Show terminal").assertIsDisplayed().performClick()
                compose.onNodeWithContentDescription("Show terminal").assertIsSelected()
                assertEquals(AgentView.TERMINAL,model.state.value.agentView)
                // The switch is the first action: X then A, without navigating menu rows.
                scenario.onActivity { model.dispatch(LogicalAction.ACTIONS) }
                compose.onNodeWithText("Switch to Messages").assertIsDisplayed()
                assertEquals(0,model.state.value.menuIndex)
                scenario.onActivity { model.dispatch(LogicalAction.CONFIRM) }
                compose.waitUntil(5000) { model.state.value.screen==Screen.TERMINAL && model.state.value.agentView==AgentView.CHAT }
                compose.onNodeWithContentDescription("Show terminal").performClick()
                scenario.onActivity { model.requestInput() }
                compose.waitUntil(15000) { model.state.value.mode==InputMode.REMOTE_KEYS }
                scenario.onActivity { model.dispatch(LogicalAction.BACK) }
                compose.waitUntil(5000) { model.state.value.agentView==AgentView.CHAT && model.state.value.access==TerminalAccess.NONE }
                compose.onNodeWithContentDescription("Show terminal").performClick()
                scenario.recreate()
                compose.waitUntil(30000) { model.state.value.phase==ConnectionPhase.READY && model.state.value.agentView==AgentView.CHAT }
                scenario.onActivity { model.assistant.binary("/pdx-missing-codex");model.open(target) }
                compose.waitUntil(15000) { model.state.value.chat.error.isNotBlank() }
                assertEquals("History failure must not silently select Terminal",AgentView.CHAT,model.state.value.agentView)
                compose.onNodeWithContentDescription("Show messages").assertIsSelected()
                compose.onNodeWithText("Retry messages").assertIsDisplayed()
                compose.onNodeWithContentDescription("Show terminal").performClick()
                assertEquals(AgentView.TERMINAL,model.state.value.agentView)
                scenario.onActivity { model.assistant.binary(originalBinary) }
                compose.onNodeWithContentDescription("Show messages").performClick()
                compose.waitUntil(30000) { model.state.value.chat.page!=null && model.state.value.chat.error.isBlank() }
                assertEquals(target.ref,model.state.value.selected?.ref)
                assertFalse(model.state.value.sending)
            } finally { scenario.onActivity { model.assistant.binary(originalBinary);model.home() } }
        }
    }
}
