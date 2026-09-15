package dev.herdr.handheld

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import dev.herdr.handheld.connection.ConnectionCoordinator
import dev.herdr.handheld.connection.ProblemCode
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
            val target=model.state.value.agents.first()
            scenario.onActivity { model.open(target);model.setAgentView(AgentView.TERMINAL) }
            compose.waitUntil(15000) { model.state.value.reading.updatedAt>0 }
            // Exercise the actual controller router in INPUT, including its 600ms hold threshold.
            scenario.onActivity { model.requestInput() }
            compose.waitUntil(15000) { model.state.value.mode==InputMode.REMOTE_KEYS || (!model.state.value.acquiring && model.state.value.problem!=ProblemCode.NONE) }
            assertEquals("Control acquisition must succeed without takeover",InputMode.REMOTE_KEYS,model.state.value.mode)
            val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
            val down=SystemClock.uptimeMillis()
            automation.injectInputEvent(KeyEvent(down,down,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_Y,0,0,-1,0,0,InputDevice.SOURCE_GAMEPAD),true)
            compose.waitUntil(15000) { model.state.value.screen==Screen.VOICE && model.state.value.voice.phase in setOf(VoicePhase.LISTENING,VoicePhase.ERROR) }
            assertEquals(model.state.value.voice.message,VoicePhase.LISTENING,model.state.value.voice.phase)
            assertEquals(target.ref,model.state.value.voice.target)
            assertEquals(InputMode.COMPOSE,model.state.value.mode)
            val recording=model.state.value.voice.id
            automation.injectInputEvent(KeyEvent(down,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_Y,0,0,-1,0,0,InputDevice.SOURCE_GAMEPAD),true)
            compose.waitUntil(5000) { model.state.value.voice.phase!=VoicePhase.LISTENING }
            assertEquals(Screen.VOICE,model.state.value.screen)
            assertFalse(model.state.value.sending)
            val back=SystemClock.uptimeMillis()
            for(action in listOf(KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP))automation.injectInputEvent(KeyEvent(back,SystemClock.uptimeMillis(),action,KeyEvent.KEYCODE_BUTTON_B,0,0,-1,0,0,InputDevice.SOURCE_GAMEPAD),true)
            compose.waitUntil(5000) { model.state.value.screen==Screen.TERMINAL && model.state.value.mode==InputMode.NAVIGATION }
            scenario.onActivity { model.voiceUpdate(recording,VoicePhase.REVIEW,"","late terminal transcript",null) }
            assertEquals(VoicePhase.IDLE,model.state.value.voice.phase)
            assertEquals("",model.state.value.voice.transcript)
            assertEquals(TerminalAccess.NONE,model.state.value.access)
            scenario.onActivity {
                model.beginTerminalVoice()
                assertEquals(target.ref,model.state.value.voice.target)
                model.dispatch(dev.herdr.handheld.input.LogicalAction.SYSTEM)
                assertEquals(Screen.SYSTEM,model.state.value.screen)
                model.back()
                assertEquals(Screen.TERMINAL,model.state.value.screen)
                assertEquals(VoicePhase.IDLE,model.state.value.voice.phase)
            }
            scenario.onActivity { model.home() }
        }
    }
}
