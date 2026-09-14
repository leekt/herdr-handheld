package dev.herdr.handheld

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import dev.herdr.handheld.voice.AndroidDictation
import dev.herdr.handheld.voice.VoicePhase
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.herdr.handheld.connection.ConnectionCoordinator
import dev.herdr.handheld.input.AndroidInputRouter
import dev.herdr.handheld.herdr.Screen
import dev.herdr.handheld.ui.PdxApp

class MainActivity : ComponentActivity() {
    private val model: ConnectionCoordinator by viewModels()
    private lateinit var input: AndroidInputRouter
    private lateinit var dictation: AndroidDictation
    private var permissionRecording=0L
    private val microphonePermission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.voiceUpdate(permissionRecording,VoicePhase.ERROR,
            if(granted)"Microphone enabled. Hold Y again to speak."else "Microphone permission denied. You can still use the keyboard.",null,null)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        WindowCompat.getInsetsController(window,window.decorView).apply {
            systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        input=AndroidInputRouter(model)
        dictation=AndroidDictation(this,model::voiceUpdate)
        model.voiceStart={ id,language ->
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
                permissionRecording=id
                model.voiceUpdate(id,VoicePhase.PERMISSION,"Allow microphone access in Android to use dictation.",null,null)
                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            } else dictation.start(id,language)
        }
        model.voiceStop=dictation::stop
        model.voiceCancel=dictation::cancel
        // Android 12 otherwise consumes the first D-pad down after touch to assign focus,
        // before Activity.dispatchKeyEvent. Route owned controller keys at the pre-IME stage.
        // OS keys and text editing continue through the normal Android path.
        val controllerRoot=object: FrameLayout(this) {
            init { isFocusableInTouchMode=true }
            override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean =
                if(input.key(event)) true else super.dispatchKeyEventPreIme(event)
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                // Retain a native focus owner during browsing, so pre-IME dispatch also
                // reaches this root immediately after a touch gesture. Editors own focus.
                if(event.actionMasked==MotionEvent.ACTION_DOWN && model.state.value.screen in setOf(Screen.HOME,Screen.TERMINAL)) requestFocus()
                return super.dispatchTouchEvent(event)
            }
        }
        controllerRoot.addView(ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by model.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.screen) {
                    val browsing=state.screen !in setOf(Screen.COMPOSE,Screen.SETTINGS,Screen.ASSISTANT,Screen.CODEX,Screen.VOICE)
                    controllerRoot.descendantFocusability=if(browsing)FrameLayout.FOCUS_BLOCK_DESCENDANTS else FrameLayout.FOCUS_AFTER_DESCENDANTS
                    if(browsing)controllerRoot.requestFocus()
                }
                PdxApp(model)
            }
        },FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(controllerRoot)
    }
    override fun onDestroy() { if(::dictation.isInitialized)dictation.cancel();model.voiceStart=null;model.voiceStop=null;model.voiceCancel=null;super.onDestroy() }
    override fun onStart() { super.onStart();input.start();model.setVisible(true) }
    override fun onStop() { input.stop();model.setVisible(false);super.onStop() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus);if(!hasFocus && ::input.isInitialized) { input.reset();model.focusLost() } }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);setIntent(intent);model.home();input.reset() }
    // Public Activity input callback; AndroidX's inherited compatibility implementation
    // is annotated with a library restriction, but must still receive unhandled OS/IME keys.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = if(::input.isInitialized && input.key(event)) true else super.dispatchKeyEvent(event)
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean = if(::input.isInitialized && input.motion(event)) true else super.dispatchGenericMotionEvent(event)
}
