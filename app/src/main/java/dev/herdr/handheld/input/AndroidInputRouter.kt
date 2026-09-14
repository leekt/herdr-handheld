package dev.herdr.handheld.input

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import dev.herdr.handheld.connection.ConnectionCoordinator

class AndroidInputRouter(private val model: ConnectionCoordinator) {
    private val router=InputRouter({ model.dispatch(if(it==LogicalAction.HOME)LogicalAction.SYSTEM else it) }) { action,held ->
        if(action==LogicalAction.INPUT)model.setHud(held)
        if(action==LogicalAction.HOME && held)model.home()
        if(action==LogicalAction.COMPOSE) { if(held)model.beginVoice() else model.endVoice() }
    }
    private val handler=Handler(Looper.getMainLooper())
    private var epoch=-1L
    private var device=-999
    private val hats=mutableMapOf<String,Boolean>()
    private val captured=mutableSetOf<String>()
    private var running=false
    private val repeat=object: Runnable {
        override fun run() { if(!running)return;sync();router.tick(SystemClock.uptimeMillis());handler.postDelayed(this,25) }
    }
    fun start() { if(running)return;running=true;handler.post(repeat) }
    fun stop() { running=false;handler.removeCallbacks(repeat);reset() }
    fun reset() { router.reset();hats.clear();captured.clear();epoch=model.state.value.inputEpoch }
    private fun sync() { if(epoch!=model.state.value.inputEpoch) reset() }
    private fun action(code: Int,descriptor: String): LogicalAction? {
        val configured=model.state.value.mappings.entries.find { it.key.startsWith("$descriptor:") && it.value==code }
        if(configured!=null) return runCatching { LogicalAction.valueOf(configured.key.substringAfterLast(':')) }.getOrNull()
        return when(code) {
            KeyEvent.KEYCODE_DPAD_UP->LogicalAction.UP;KeyEvent.KEYCODE_DPAD_DOWN->LogicalAction.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT->LogicalAction.LEFT;KeyEvent.KEYCODE_DPAD_RIGHT->LogicalAction.RIGHT
            KeyEvent.KEYCODE_BUTTON_A,KeyEvent.KEYCODE_DPAD_CENTER->LogicalAction.CONFIRM
            KeyEvent.KEYCODE_BUTTON_B->LogicalAction.BACK
            KeyEvent.KEYCODE_BUTTON_X->LogicalAction.ACTIONS;KeyEvent.KEYCODE_BUTTON_Y->LogicalAction.COMPOSE
            KeyEvent.KEYCODE_BUTTON_L1->LogicalAction.PREVIOUS;KeyEvent.KEYCODE_BUTTON_R1->LogicalAction.NEXT
            KeyEvent.KEYCODE_BUTTON_L2->LogicalAction.FONT_SMALL;KeyEvent.KEYCODE_BUTTON_R2->LogicalAction.FONT_LARGE
            KeyEvent.KEYCODE_BUTTON_SELECT->LogicalAction.INPUT;KeyEvent.KEYCODE_BUTTON_START->LogicalAction.HOME
            else->null
        }
    }
    fun key(event: KeyEvent): Boolean {
        // Android Home/Back, keyboard text, and IME input remain separate from gamepad logical buttons.
        if(event.keyCode in setOf(KeyEvent.KEYCODE_HOME,KeyEvent.KEYCODE_BACK,KeyEvent.KEYCODE_VOLUME_UP,KeyEvent.KEYCODE_VOLUME_DOWN,KeyEvent.KEYCODE_POWER)) return false
        if(model.state.value.hostKey!=null) return false
        val mapped=action(event.keyCode,event.device?.descriptor.orEmpty())
        val gamepad=event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK)
        if(mapped==null && !gamepad) return false
        if(model.state.value.screen in setOf(dev.herdr.handheld.herdr.Screen.COMPOSE,dev.herdr.handheld.herdr.Screen.ASSISTANT,dev.herdr.handheld.herdr.Screen.VOICE,dev.herdr.handheld.herdr.Screen.CODEX) && !gamepad && event.keyCode in 19..22) return false
        sync()
        if(device!=event.deviceId) { reset();device=event.deviceId }
        val token="${event.deviceId}:key:${event.keyCode}"
        model.diagnostic("KEY ${event.keyCode} scan ${event.scanCode} ${if(event.action==KeyEvent.ACTION_DOWN) "down" else "up"} r${event.repeatCount} dev ${event.deviceId} src ${event.source.toString(16)}")
        if(event.action==KeyEvent.ACTION_DOWN && event.repeatCount==0 && model.captureButton(event.device?.descriptor.orEmpty(),event.keyCode)) {
            captured.add(token);return true
        }
        if(token in captured) { if(event.action==KeyEvent.ACTION_UP) captured.remove(token);return true }
        if(mapped==null) return true
        router.edge(token,mapped,event.action==KeyEvent.ACTION_DOWN,event.eventTime,event.repeatCount)
        return true
    }
    fun motion(event: MotionEvent): Boolean {
        if(!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.action!=MotionEvent.ACTION_MOVE) return false
        sync()
        if(device!=event.deviceId) { reset();device=event.deviceId }
        val x=event.getAxisValue(MotionEvent.AXIS_HAT_X);val y=event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        for((action,down) in listOf(LogicalAction.LEFT to (x< -0.5),LogicalAction.RIGHT to (x>0.5),LogicalAction.UP to (y< -0.5),LogicalAction.DOWN to (y>0.5))) {
            val token="${event.deviceId}:hat:${action.name}"
            if((hats[token] ?: false)!=down) {
                hats[token]=down;model.diagnostic("HAT x=$x y=$y ${action.name} ${if(down) "down" else "up"} dev ${event.deviceId}")
                if(model.state.value.calibrating==null) router.edge(token,action,down,event.eventTime)
            }
        }
        return true
    }
}
