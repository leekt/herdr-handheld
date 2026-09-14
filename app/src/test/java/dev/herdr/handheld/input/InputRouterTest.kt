package dev.herdr.handheld.input

import org.junit.Assert.*
import org.junit.Test

class InputRouterTest {
    @Test fun selectHoldShowsMapUntilReleaseWithoutRequestingControl() {
        val taps=mutableListOf<LogicalAction>();val holds=mutableListOf<Pair<LogicalAction,Boolean>>()
        val router=InputRouter(taps::add) { action,held -> holds.add(action to held) }
        router.edge("select",LogicalAction.INPUT,true,0);router.tick(599)
        assertTrue(holds.isEmpty());router.tick(600);router.tick(900)
        router.edge("select",LogicalAction.INPUT,false,1000)
        assertTrue(taps.isEmpty())
        assertEquals(listOf(LogicalAction.INPUT to true,LogicalAction.INPUT to false),holds)
    }
    @Test fun startHoldDoesNotAlsoTriggerItsTapAndResetClosesMap() {
        val taps=mutableListOf<LogicalAction>();val holds=mutableListOf<Pair<LogicalAction,Boolean>>()
        val router=InputRouter(taps::add) { action,held -> holds.add(action to held) }
        router.edge("start",LogicalAction.HOME,true,0);router.tick(600);router.reset();router.edge("start",LogicalAction.HOME,false,700)
        assertTrue(taps.isEmpty());assertEquals(2,holds.size)
        router.edge("select",LogicalAction.INPUT,true,800);router.edge("select",LogicalAction.INPUT,false,900)
        assertEquals(listOf(LogicalAction.INPUT),taps)
    }
    @Test fun heldAEmitsExactlyOneEnterOnRelease() {
        val actions=mutableListOf<LogicalAction>();val router=InputRouter(actions::add)
        router.edge("A",LogicalAction.CONFIRM,true,0)
        repeat(30) { router.edge("A",LogicalAction.CONFIRM,true,500L+it*100,1);router.tick(500L+it*100) }
        assertTrue(actions.isEmpty())
        router.edge("A",LogicalAction.CONFIRM,false,4000)
        router.edge("A",LogicalAction.CONFIRM,false,4001)
        assertEquals(listOf(LogicalAction.CONFIRM),actions)
    }
    @Test fun overlappingDpadAndHatOnlyMoveOnce() {
        val actions=mutableListOf<LogicalAction>();val r=InputRouter(actions::add)
        r.edge("key",LogicalAction.DOWN,true,100)
        r.edge("hat",LogicalAction.DOWN,true,105)
        r.edge("key",LogicalAction.DOWN,false,150)
        r.edge("hat",LogicalAction.DOWN,false,155)
        assertEquals(listOf(LogicalAction.DOWN),actions)
    }
    @Test fun adjacentDuplicateSourcesOnlyMoveOnce() {
        val a=mutableListOf<LogicalAction>();val r=InputRouter(a::add)
        r.edge("key",LogicalAction.DOWN,true,100);r.edge("key",LogicalAction.DOWN,false,102)
        r.edge("hat",LogicalAction.DOWN,true,110);r.edge("hat",LogicalAction.DOWN,false,112)
        assertEquals(1,a.size)
    }
    @Test fun modeChangeDiscardsHeldButton() {
        val a=mutableListOf<LogicalAction>();val r=InputRouter(a::add)
        r.edge("A",LogicalAction.CONFIRM,true,0);r.reset();r.edge("A",LogicalAction.CONFIRM,false,100)
        assertTrue(a.isEmpty())
    }
    @Test fun onlyDirectionsRepeatAfterDelay() {
        val a=mutableListOf<LogicalAction>();val r=InputRouter(a::add)
        r.edge("down",LogicalAction.DOWN,true,0);r.tick(359);assertEquals(1,a.size)
        r.tick(360);r.tick(459);assertEquals(2,a.size)
        r.tick(460);assertEquals(3,a.size)
        r.reset();r.tick(900);assertEquals(3,a.size)
    }
}
