package dev.herdr.handheld

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import dev.herdr.handheld.connection.ConnectionCoordinator
import dev.herdr.handheld.herdr.ConnectionPhase
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealConversationTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun branchCompactAndPickerPreserveOriginalConversation() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runRealAssistant")=="true")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: ConnectionCoordinator
            scenario.onActivity { model=ViewModelProvider(it)[ConnectionCoordinator::class.java];model.openAssistant() }
            compose.waitUntil(30000) { model.state.value.sshPhase==ConnectionPhase.READY && !model.assistant.state.value.busy }
            val original=model.assistant.state.value.memory
            assumeTrue(original.threadId.isNotBlank())
            try {
                scenario.onActivity { model.assistant.fork() }
                compose.waitUntil(45000) { !model.assistant.state.value.busy }
                val branch=model.assistant.state.value.memory
                assertNotEquals(model.assistant.state.value.status,original.threadId,branch.threadId)
                assertEquals(original.messages,branch.messages)
                scenario.onActivity { model.assistant.notes("Prefer concise answers. Validation note.");model.assistant.saveNotes() }
                compose.waitUntil(10000) { !model.assistant.state.value.busy }
                assertEquals("Prefer concise answers. Validation note.",model.assistant.state.value.memory.notes)
                scenario.onActivity { model.assistant.compact() }
                compose.waitUntil(210000) { !model.assistant.state.value.busy }
                assertTrue(model.assistant.state.value.status,model.assistant.state.value.status.startsWith("Context compacted"))
                assertEquals(branch.threadId,model.assistant.state.value.memory.threadId)
                scenario.onActivity { model.assistant.newConversation() }
                compose.waitUntil(10000) { !model.assistant.state.value.busy }
                assertTrue(model.assistant.state.value.memory.threadId.isBlank())
                assertTrue(model.assistant.state.value.conversations.any { it.id==original.id })
                assertTrue(model.assistant.state.value.conversations.any { it.id==branch.id })
            } finally {
                scenario.onActivity { model.assistant.stop();model.assistant.selectConversation(original.id) }
                compose.waitUntil(10000) { !model.assistant.state.value.busy }
                assertEquals(original.threadId,model.assistant.state.value.memory.threadId)
                assertEquals(original.notes,model.assistant.state.value.memory.notes)
                scenario.onActivity { model.home() }
            }
        }
    }
}
