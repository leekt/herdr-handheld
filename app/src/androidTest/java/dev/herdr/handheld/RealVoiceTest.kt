package dev.herdr.handheld

import android.Manifest
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import dev.herdr.handheld.connection.ConnectionCoordinator
import dev.herdr.handheld.herdr.*
import dev.herdr.handheld.voice.VoicePhase
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in actual microphone smoke test. No audio file is created and no recognized text is sent. */
@RunWith(AndroidJUnit4::class)
class RealVoiceTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun nativeRecognitionStartsAndCancellationRejectsLateInput() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runRealVoice")=="true")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: ConnectionCoordinator
            scenario.onActivity { activity -> model=ViewModelProvider(activity)[ConnectionCoordinator::class.java];model.home() }
            compose.waitUntil(30000) { model.state.value.phase==ConnectionPhase.READY }
            scenario.onActivity { model.beginVoice() }
            if(model.state.value.voice.phase==VoicePhase.PERMISSION) {
                // Keep the real Android permission dialog visible for the operator to approve.
                compose.waitUntil(60000) { model.state.value.voice.phase!=VoicePhase.PERMISSION }
                scenario.onActivity { activity ->
                    assertEquals(PackageManager.PERMISSION_GRANTED,activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
                    model.beginVoice()
                }
            }
            compose.waitUntil(15000) { model.state.value.voice.phase in setOf(VoicePhase.LISTENING,VoicePhase.ERROR) }
            assertEquals(model.state.value.voice.message,VoicePhase.LISTENING,model.state.value.voice.phase)
            val id=model.state.value.voice.id
            scenario.onActivity { model.cancelVoice();model.voiceUpdate(id,VoicePhase.REVIEW,"", "late test callback",null) }
            assertEquals(VoicePhase.IDLE,model.state.value.voice.phase)
            assertEquals("",model.state.value.voice.transcript)
            assertEquals(TerminalAccess.NONE,model.state.value.access)
            assertEquals(Screen.HOME,model.state.value.screen)
        }
    }
}
