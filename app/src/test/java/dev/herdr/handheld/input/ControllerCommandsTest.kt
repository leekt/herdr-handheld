package dev.herdr.handheld.input

import dev.herdr.handheld.herdr.*
import org.junit.Assert.*
import org.junit.Test

class ControllerCommandsTest {
    @Test fun helpAndCalibrationRetainLegacyButtonIdentitiesWithoutRemoteEscape() {
        assertEquals("Select",ControllerCommands.calibration.toMap()[LogicalAction.INPUT])
        assertEquals("Start",ControllerCommands.calibration.toMap()[LogicalAction.HOME])
        val commands=ControllerCommands.forScreen(Screen.TERMINAL,InputMode.REMOTE_KEYS)
        assertEquals("Exit input",commands.single { it.action==LogicalAction.BACK }.label)
        assertFalse(commands.single { it.action==LogicalAction.NEXT }.enabled)
        assertTrue(commands.filter { it.hint }.all { it.action !in setOf(LogicalAction.BACK,LogicalAction.UP) })
    }
}
