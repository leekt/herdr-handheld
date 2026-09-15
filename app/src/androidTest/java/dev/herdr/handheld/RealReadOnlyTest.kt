package dev.herdr.handheld

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import dev.herdr.handheld.connection.ConnectionCoordinator
import dev.herdr.handheld.herdr.*
import dev.herdr.handheld.input.LogicalAction
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

/** Opt-in read-only integration using the paired device; never changes credentials or sends input. */
@RunWith(AndroidJUnit4::class)
class RealReadOnlyTest {
    @get:Rule val compose=createEmptyComposeRule()
    private fun button(code: Int,held: Long=0) {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val start=SystemClock.uptimeMillis()
        automation.injectInputEvent(KeyEvent(start,start,KeyEvent.ACTION_DOWN,code,0,0,-1,0,0,InputDevice.SOURCE_GAMEPAD),true)
        if(held>0)SystemClock.sleep(held)
        automation.injectInputEvent(KeyEvent(start,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,code,0,0,-1,0,0,InputDevice.SOURCE_GAMEPAD),true)
    }
    private fun hintsVisible(): Boolean {
        val strip=compose.onNodeWithContentDescription("Controller hints").assertIsDisplayed().fetchSemanticsNode()
        val nodes=compose.onAllNodes(hasAnyAncestor(hasContentDescription("Controller hints")),useUnmergedTree=true).fetchSemanticsNodes()
        for(node in nodes) {
            val layouts=mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            // Compose rounds intrinsic glyph widths to whole physical pixels. Allow that one-pixel
            // rounding while rejecting wrapping or a visibly clipped legend.
            check(layouts.all { it.lineCount==1 && it.getLineRight(0)<=it.size.width+1f && it.getLineBottom(0)<=it.size.height+1f }) {
                "Hint layout: ${layouts.map { listOf(it.layoutInput.text.text,it.size,it.getLineRight(0),it.getLineBottom(0)) }}"
            }
            check(node.boundsInRoot.left>=strip.boundsInRoot.left-1 && node.boundsInRoot.right<=strip.boundsInRoot.right+1) { "A hint escaped the strip" }
        }
        return true
    }
    private fun eventually(check: ()->Boolean) {
        compose.waitUntil(timeoutMillis=15000) { check() }
    }
    @Test fun pairedHostOpensScrollableReadingByDefault() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runRealReadOnly")=="true")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: ConnectionCoordinator
            scenario.onActivity { model=ViewModelProvider(it)[ConnectionCoordinator::class.java];model.home() }
            eventually { model.state.value.loaded && model.state.value.phase==ConnectionPhase.READY && model.state.value.agents.size>=2 }
            assertTrue("This test requires an already paired real host",model.state.value.hasKey)
            val targets=model.state.value.agents.take(2)
            for(target in targets) {
                scenario.onActivity { model.open(target);model.setAgentView(AgentView.TERMINAL) }
                eventually { model.state.value.access==TerminalAccess.NONE && model.state.value.reading.updatedAt>0 }
                scenario.onActivity { activity ->
                    fun webViews(view: android.view.View): Int = (if(view is android.webkit.WebView)1 else 0) +
                        if(view is android.view.ViewGroup)(0 until view.childCount).sumOf { webViews(view.getChildAt(it)) }else 0
                    assertEquals("READ must not initialize a hidden renderer",0,webViews(activity.window.decorView))
                }
                assertEquals(0L,model.state.value.lastFrame)
                assertEquals(target.ref,model.state.value.selected?.ref)
                assertEquals(InputMode.NAVIGATION,model.state.value.mode)
                assertTrue("Styled snapshot must contain real colors",model.state.value.reading.formatted!!.spanStyles.any { it.item.color!=dev.herdr.handheld.terminal.TerminalText.foreground })
            }
            // No menu or explicit recent-output action: opening a real target must load it.
            eventually { val s=model.state.value;s.recent!=null && s.recentMaxScroll>0 && s.recentScroll==s.recentMaxScroll }
            assertEquals("Snapshot must initially show the latest output",model.state.value.recentMaxScroll,model.state.value.recentScroll)
            val end=model.state.value.recentMaxScroll
            val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
            val down=SystemClock.uptimeMillis()
            for(action in listOf(KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP))
                automation.injectInputEvent(KeyEvent(down,SystemClock.uptimeMillis(),action,KeyEvent.KEYCODE_DPAD_UP,0,0,-1,0,0,InputDevice.SOURCE_GAMEPAD),true)
            eventually { model.state.value.recentScroll in 0 until end }
            assertFalse(model.state.value.reading.following)
            scenario.onActivity { model.resumeReading() }
            eventually { model.state.value.reading.following && model.state.value.recentScroll==model.state.value.recentMaxScroll }
            // Drag down inside the reader to reveal older output, using actual touch events.
            val touchStart=SystemClock.uptimeMillis()
            for(step in 0..10) {
                val action=when(step) { 0->MotionEvent.ACTION_DOWN;10->MotionEvent.ACTION_UP;else->MotionEvent.ACTION_MOVE }
                val event=MotionEvent.obtain(touchStart,SystemClock.uptimeMillis(),action,360f,300f+step*20f,0).apply { source=InputDevice.SOURCE_TOUCHSCREEN }
                automation.injectInputEvent(event,true);event.recycle();SystemClock.sleep(25)
            }
            eventually { val s=model.state.value;!s.reading.following && s.recentScroll<s.recentMaxScroll }
            SystemClock.sleep(600)
            val afterTouch=model.state.value.recentScroll
            assertTrue("Touch test must leave older output to scroll; position=$afterTouch max=${model.state.value.recentMaxScroll}",afterTouch>0)
            val beforeRequest=model.state.value.readScrollRequest
            val directionStart=SystemClock.uptimeMillis()
            for(action in listOf(KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP))
                automation.injectInputEvent(KeyEvent(directionStart,SystemClock.uptimeMillis(),action,KeyEvent.KEYCODE_DPAD_UP,0,0,-1,0,0,InputDevice.SOURCE_GAMEPAD),true)
            eventually { model.state.value.readScrollRequest>beforeRequest }
            eventually { model.state.value.recentScroll<afterTouch }
            assertEquals(TerminalAccess.NONE,model.state.value.access)
            assertEquals(InputMode.NAVIGATION,model.state.value.mode)
            assertFalse(model.state.value.hintsVisible)
            button(KeyEvent.KEYCODE_BUTTON_SELECT)
            eventually { model.state.value.hintsVisible && hintsVisible() }
            eventually { !model.state.value.hintsVisible }
            val selectDown=SystemClock.uptimeMillis()
            automation.injectInputEvent(KeyEvent(selectDown,selectDown,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_SELECT,0,0,-1,0,0,InputDevice.SOURCE_GAMEPAD),true)
            eventually { model.state.value.hud }
            assertEquals(TerminalAccess.NONE,model.state.value.access)
            automation.injectInputEvent(KeyEvent(selectDown,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_SELECT,0,0,-1,0,0,InputDevice.SOURCE_GAMEPAD),true)
            eventually { !model.state.value.hud }
            assertEquals(Screen.TERMINAL,model.state.value.screen)
            button(KeyEvent.KEYCODE_BUTTON_START)
            eventually { model.state.value.screen==Screen.SYSTEM }
            button(KeyEvent.KEYCODE_BUTTON_SELECT)
            eventually { model.state.value.hintsVisible && hintsVisible() }
            scenario.onActivity { model.navigate(Screen.SETTINGS) }
            eventually { model.state.value.screen==Screen.SETTINGS }
            button(KeyEvent.KEYCODE_BUTTON_B)
            eventually { model.state.value.screen==Screen.SYSTEM }
            button(KeyEvent.KEYCODE_BUTTON_B)
            eventually { model.state.value.screen==Screen.TERMINAL }
            button(KeyEvent.KEYCODE_BUTTON_A)
            eventually { model.state.value.screen==Screen.CONTROL }
            assertEquals(TerminalAccess.NONE,model.state.value.access)
            button(KeyEvent.KEYCODE_BUTTON_B)
            eventually { model.state.value.screen==Screen.TERMINAL }
            // Tap the transient A/Control hint; this only opens the local confirmation.
            button(KeyEvent.KEYCODE_BUTTON_SELECT)
            eventually { model.state.value.hintsVisible && hintsVisible() }
            val touch=SystemClock.uptimeMillis()
            for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                val event=MotionEvent.obtain(touch,SystemClock.uptimeMillis(),action,60f,680f,0).apply { source=InputDevice.SOURCE_TOUCHSCREEN }
                automation.injectInputEvent(event,true);event.recycle();SystemClock.sleep(60)
            }
            eventually { model.state.value.screen==Screen.CONTROL }
            button(KeyEvent.KEYCODE_BUTTON_B)
            button(KeyEvent.KEYCODE_BUTTON_B)
            eventually { model.state.value.screen==Screen.HOME }
            assertEquals(Screen.HOME,model.state.value.screen)
            assertEquals(TerminalAccess.NONE,model.state.value.access)
        }
    }
}
