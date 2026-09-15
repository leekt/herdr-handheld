package dev.herdr.handheld

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.herdr.handheld.connection.ConnectionCoordinator
import dev.herdr.handheld.herdr.*
import dev.herdr.handheld.ui.PdxApp
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Constrained Compose canvases on the real device; this is not a second device certification. */
@RunWith(AndroidJUnit4::class)
class ResponsiveLayoutTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun narrowAndWideCanvasesKeepHintLegendsOnOneLine() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runRealReadOnly")=="true")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: ConnectionCoordinator
            scenario.onActivity { model=ViewModelProvider(it)[ConnectionCoordinator::class.java] }
            compose.waitUntil(30000) { model.state.value.phase==ConnectionPhase.READY && model.state.value.agents.isNotEmpty() }
            for((width,height) in listOf(320 to 440,440 to 280)) {
                scenario.onActivity { activity -> activity.setContent { Box(Modifier.requiredSize(width.dp,height.dp)) { PdxApp(model) } } }
                for(screen in listOf(Screen.HOME,Screen.TERMINAL,Screen.COMPOSE,Screen.ASSISTANT,Screen.CONVERSATION,Screen.SETTINGS,Screen.DIAGNOSTICS,Screen.SYSTEM,Screen.CODEX)) {
                    scenario.onActivity {
                        if(screen==Screen.HOME)model.home()
                        else if(screen==Screen.TERMINAL)model.open(model.state.value.agents.first())
                        else if(screen==Screen.COMPOSE)model.openCompose()
                        else model.navigate(screen)
                        model.showHints()
                    }
                    val strip=compose.onNodeWithContentDescription("Controller hints").assertIsDisplayed().fetchSemanticsNode()
                    val nodes=compose.onAllNodes(hasAnyAncestor(hasContentDescription("Controller hints")),useUnmergedTree=true).fetchSemanticsNodes()
                    for(node in nodes) {
                        val layouts=mutableListOf<TextLayoutResult>()
                        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                        assertTrue("Wrapped hint on $screen at ${width}x$height",layouts.all { it.lineCount==1 && it.getLineRight(0)<=it.size.width+1 })
                        assertTrue(node.boundsInRoot.right<=strip.boundsInRoot.right+1)
                    }
                    assertEquals(TerminalAccess.NONE,model.state.value.access)
                    if(screen==Screen.TERMINAL && AgentChat.supported(model.state.value.selected?.ref)) {
                        for(view in listOf(AgentView.CHAT,AgentView.TERMINAL)) {
                            scenario.onActivity { model.setAgentView(view) }
                            val group=compose.onNodeWithContentDescription("Agent view mode").assertIsDisplayed().fetchSemanticsNode()
                            val labels=compose.onAllNodes(hasAnyAncestor(hasContentDescription("Agent view mode")),useUnmergedTree=true).fetchSemanticsNodes()
                            for(label in labels) {
                                val layouts=mutableListOf<TextLayoutResult>()
                                label.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                                assertTrue(layouts.all { it.lineCount==1 && it.getLineRight(0)<=it.size.width+1 })
                                assertTrue(label.boundsInRoot.left>=group.boundsInRoot.left-1 && label.boundsInRoot.right<=group.boundsInRoot.right+1)
                            }
                        }
                    }
                }
            }
            scenario.onActivity { model.home() }
        }
    }
}
