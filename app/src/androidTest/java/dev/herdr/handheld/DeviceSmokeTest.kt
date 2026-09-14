package dev.herdr.handheld

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.herdr.handheld.connection.ConnectionCoordinator
import dev.herdr.handheld.herdr.*
import dev.herdr.handheld.storage.SettingsStore
import dev.herdr.handheld.storage.SecretStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {
    private fun eventually(check: ()->Boolean) {
        val deadline=SystemClock.uptimeMillis()+10000
        while(SystemClock.uptimeMillis()<deadline) { if(check())return;SystemClock.sleep(40) }
        assertTrue("Timed out waiting for expected state",check())
    }
    @Test fun nativeControllerFlowAndResumeRemainReadOnly() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { SettingsStore(context).saveDemo(true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: ConnectionCoordinator
            scenario.onActivity { model=ViewModelProvider(it)[ConnectionCoordinator::class.java];model.home() }
            eventually { model.state.value.phase==ConnectionPhase.READY && model.state.value.agents.isNotEmpty() }
            fun press(code: Int) {
                scenario.onActivity { activity ->
                    val time=SystemClock.uptimeMillis()
                    activity.dispatchKeyEvent(KeyEvent(time,time,KeyEvent.ACTION_DOWN,code,0,0,-1,0,0,InputDevice.SOURCE_GAMEPAD))
                    activity.dispatchKeyEvent(KeyEvent(time,time+20,KeyEvent.ACTION_UP,code,0,0,-1,0,0,InputDevice.SOURCE_GAMEPAD))
                }
            }
            press(KeyEvent.KEYCODE_BUTTON_A)
            eventually { model.state.value.access==TerminalAccess.OBSERVER && model.state.value.lastFrame>0 }
            assertEquals(InputMode.NAVIGATION,model.state.value.mode)
            press(KeyEvent.KEYCODE_BUTTON_SELECT)
            eventually { model.state.value.access==TerminalAccess.CONTROLLER }
            assertEquals(InputMode.REMOTE_KEYS,model.state.value.mode)
            val target=model.state.value.selected?.ref
            press(KeyEvent.KEYCODE_BUTTON_R1)
            assertEquals(target,model.state.value.selected?.ref)
            press(KeyEvent.KEYCODE_BUTTON_B)
            eventually { model.state.value.access==TerminalAccess.OBSERVER }
            assertEquals(Screen.TERMINAL,model.state.value.screen)
            press(KeyEvent.KEYCODE_BUTTON_SELECT)
            eventually { model.state.value.access==TerminalAccess.CONTROLLER }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            eventually { model.state.value.access==TerminalAccess.NONE }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            eventually { model.state.value.access==TerminalAccess.OBSERVER }
            assertEquals(InputMode.NAVIGATION,model.state.value.mode)
            scenario.recreate()
            eventually { model.state.value.access==TerminalAccess.OBSERVER }
            press(KeyEvent.KEYCODE_BUTTON_START)
            eventually { model.state.value.screen==Screen.HOME }
            assertEquals(TerminalAccess.NONE,model.state.value.access)
        }
    }
    @Test fun secretBlobsUseAuthenticatedEncryption() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val vault=SecretStore(context)
        val value="fixture secret 한글".toByteArray()
        vault.put("instrumentation:secret",value)
        assertArrayEquals(value,vault.get("instrumentation:secret"))
        vault.remove("instrumentation:secret")
        assertNull(vault.get("instrumentation:secret"))
    }

    @Test fun immediateHomeSavesTheDraftForItsOriginalTarget() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { SettingsStore(context).saveDemo(true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: ConnectionCoordinator
            scenario.onActivity { model=ViewModelProvider(it)[ConnectionCoordinator::class.java];model.home() }
            eventually { model.state.value.phase==ConnectionPhase.READY && model.state.value.agents.isNotEmpty() }
            val first=model.state.value.agents.first()
            scenario.onActivity { model.open(first) }
            eventually { model.state.value.access==TerminalAccess.OBSERVER }
            scenario.onActivity { model.openCompose() }
            eventually { model.state.value.screen==Screen.COMPOSE && model.state.value.access==TerminalAccess.OBSERVER }
            val text="다음 단계를 설명해 주세요. Explain the next step."
            scenario.onActivity { model.editDraft(text);model.home() }
            eventually { model.secrets.get("draft:${first.ref.key}")?.toString(Charsets.UTF_8)==text }
            assertEquals(TerminalAccess.NONE,model.state.value.access)
            scenario.onActivity { model.open(first);model.openCompose() }
            eventually { model.state.value.draft==text && model.state.value.draftSaved }
            scenario.onActivity { model.reviewDraft() }
            assertEquals(Screen.COMPOSE,model.state.value.screen)
            assertNotEquals(TerminalAccess.CONTROLLER,model.state.value.access)
            scenario.onActivity { model.confirmDraft(true) }
            eventually { model.state.value.screen==Screen.TERMINAL && model.state.value.access==TerminalAccess.OBSERVER }
            assertFalse(model.state.value.deliveryUncertain)
            assertEquals(text,model.secrets.get("draft:${first.ref.key}")?.toString(Charsets.UTF_8))
            model.secrets.remove("draft:${first.ref.key}")
        }
    }
}
