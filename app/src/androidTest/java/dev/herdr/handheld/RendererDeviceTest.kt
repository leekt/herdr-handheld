package dev.herdr.handheld

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.herdr.handheld.herdr.TerminalFrame
import dev.herdr.handheld.terminal.XtermWebView
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.resume

@RunWith(AndroidJUnit4::class)
class RendererDeviceTest {
    @Test fun acknowledgedWritesTargetResetAndNavigationIsolation()=runBlocking {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var view: XtermWebView
            var fault: String?=null
            scenario.onActivity { activity ->
                view=XtermWebView(activity,{_,_,_->},{fault=it})
                activity.setContentView(view)
            }
            suspend fun evaluate(script: String): String=withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation -> view.evaluateJavascript(script) { if(continuation.isActive)continuation.resume(it) } }
            }
            try {
                view.reset(1,17)
                val start=SystemClock.elapsedRealtime()
                repeat(40) { i -> view.render(TerminalFrame(i.toLong()+1,44,18,true,"\u001b[2J\u001b[HFrame $i · 한글 👋 e\u0301".toByteArray()),1) }
                assertTrue("Bounded sequential render writes stalled",SystemClock.elapsedRealtime()-start<15000)
                view.reset(2,17)
                view.render(TerminalFrame(1,44,18,true,"\u001b[2J\u001b[HNEW TARGET".toByteArray()),2)
                view.render(TerminalFrame(42,44,18,true,"OLD TARGET".toByteArray()),1)
                // xterm's write callback acknowledges parsing; DOM paint happens on a later frame.
                val text=withTimeout(3000) {
                    var painted=""
                    while(!painted.contains("NEW TARGET")) {
                        painted=Json.parseToJsonElement(evaluate("document.querySelector('.xterm-rows').textContent")).jsonPrimitive.content
                        if(!painted.contains("NEW TARGET"))delay(20)
                    }
                    painted
                }
                assertFalse("A previous target's frame reached the new renderer: $text",text.contains("OLD TARGET"))
                evaluate("var a=document.createElement('a');a.href='https://example.com/';document.body.appendChild(a);a.click();a.remove();")
                delay(200)
                val location=Json.parseToJsonElement(evaluate("location.href")).jsonPrimitive.content
                assertEquals("https://appassets.androidplatform.net/assets/terminal/index.html",location)
                assertNull(fault)
            } finally { withContext(Dispatchers.Main) { view.destroy() } }
        }
    }
}
