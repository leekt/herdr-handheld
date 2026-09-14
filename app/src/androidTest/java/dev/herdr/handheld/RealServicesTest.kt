package dev.herdr.handheld

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import dev.herdr.handheld.connection.ConnectionCoordinator
import dev.herdr.handheld.herdr.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealServicesTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun assistantCanAnswerWithHerdrUnavailable() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runRealAssistant")=="true")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: ConnectionCoordinator
            scenario.onActivity { model=ViewModelProvider(it)[ConnectionCoordinator::class.java] }
            compose.waitUntil(30000) { model.state.value.phase==ConnectionPhase.READY }
            val profile=model.state.value.profile
            try {
                scenario.onActivity { model.saveProfile(profile.copy(binary="/pdx-validation-missing-herdr"));model.connect();model.openAssistant() }
                compose.waitUntil(30000) { model.state.value.phase==ConnectionPhase.OFFLINE && model.state.value.sshPhase==ConnectionPhase.READY }
                compose.waitUntil(45000) { !model.assistant.state.value.busy }
                scenario.onActivity {
                    model.assistant.edit("Is Herdr available in the current request? Answer briefly with no actions and no tools.")
                    model.askAssistant()
                }
                compose.waitUntil(210000) { !model.assistant.state.value.busy }
                assertNotNull(model.assistant.state.value.status,model.assistant.state.value.answer)
                assertFalse(model.assistant.state.value.context!!.herdrAvailable)
                assertTrue(model.assistant.state.value.context!!.agents.isEmpty())
                assertEquals(ConnectionPhase.READY,model.state.value.sshPhase)
                assertEquals(TerminalAccess.NONE,model.state.value.access)
            } finally {
                scenario.onActivity { model.saveProfile(profile);model.connect();model.home() }
                compose.waitUntil(30000) { model.state.value.phase==ConnectionPhase.READY }
                assertEquals(profile,model.state.value.profile)
            }
        }
    }
}
