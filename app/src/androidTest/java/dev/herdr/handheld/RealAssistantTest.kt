package dev.herdr.handheld

import android.speech.SpeechRecognizer
import android.media.AudioManager
import android.media.AudioDeviceInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import dev.herdr.handheld.assistant.AssistantContract
import dev.herdr.handheld.connection.ConnectionCoordinator
import dev.herdr.handheld.herdr.*
import dev.herdr.handheld.input.LogicalAction
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in: uses the paired host's actual ChatGPT subscription, with no terminal writes. */
@RunWith(AndroidJUnit4::class)
class RealAssistantTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun subscriptionProducesARealReviewedLauncherAction() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runRealAssistant")=="true")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: ConnectionCoordinator
            scenario.onActivity { activity ->
                model=ViewModelProvider(activity)[ConnectionCoordinator::class.java];model.home()
                assertTrue(SpeechRecognizer.isRecognitionAvailable(activity))
                assertTrue(activity.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_INPUTS).any { it.type==AudioDeviceInfo.TYPE_BUILTIN_MIC })
            }
            compose.waitUntil(30000) { model.state.value.phase==ConnectionPhase.READY && model.state.value.agents.isNotEmpty() }
            scenario.onActivity { model.dispatch(LogicalAction.COMPOSE);model.assistant.connect() }
            assertEquals(Screen.ASSISTANT,model.state.value.screen)
            compose.waitUntil(45000) { !model.assistant.state.value.busy }
            assertTrue(model.assistant.state.value.status,model.assistant.state.value.connected)
            assertTrue(model.assistant.state.value.account.subscribed)
            scenario.onActivity { model.showHints() }
            compose.onNodeWithContentDescription("Controller hints").assertIsDisplayed()
            scenario.onActivity {
                model.assistant.edit("How many agents are reported blocked in this current inventory? Answer with the count, without naming agents, and propose only SHOW_ATTENTION to show that filter. Do not use tools.")
                model.askAssistant()
            }
            compose.waitUntil(210000) { !model.assistant.state.value.busy }
            val assistant=model.assistant.state.value
            assertNotNull(assistant.status,assistant.answer)
            val answer=assistant.answer!!
            assertTrue(answer.actions.isNotEmpty())
            assertTrue(answer.actions.all { AssistantContract.valid(it,assistant.context!!) })
            assertFalse("Proposals must not execute themselves",model.state.value.attentionOnly)
            assertEquals(TerminalAccess.NONE,model.state.value.access)
            assertEquals(Screen.ASSISTANT,model.state.value.screen)
            val thread=assistant.memory.threadId
            assertTrue(thread.isNotBlank())
            val turns=assistant.memory.messages.size
            // Close just this stdio connection, then prove the same dedicated thread resumes.
            scenario.onActivity { model.assistant.stop();model.assistant.connect() }
            compose.waitUntil(45000) { !model.assistant.state.value.busy }
            scenario.onActivity {
                model.assistant.edit("What blocked-agent count did you report in the previous turn? Use our conversation context. Reply briefly with no actions.")
                model.askAssistant()
            }
            compose.waitUntil(210000) { !model.assistant.state.value.busy }
            assertNotNull(model.assistant.state.value.status,model.assistant.state.value.answer)
            assertEquals(thread,model.assistant.state.value.memory.threadId)
            assertEquals((turns+2).coerceAtMost(20),model.assistant.state.value.memory.messages.size)
            // The first proposal was invalidated when the channel closed; no old proposal replay.
            assertFalse(model.state.value.attentionOnly)
            scenario.onActivity { model.home() }
            return@use
        }
    }
}
