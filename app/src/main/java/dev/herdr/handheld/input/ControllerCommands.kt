package dev.herdr.handheld.input

import dev.herdr.handheld.herdr.*

data class ControllerCommand(val action: LogicalAction,val key: String,val label: String,val detail: String=label,val hint: Boolean=false,val enabled: Boolean=true)

/** Persisted INPUT/HOME names remain compatible with existing Select/Start calibrations. */
object ControllerCommands {
    val calibration=listOf(
        LogicalAction.CONFIRM to "A",LogicalAction.BACK to "B",LogicalAction.ACTIONS to "X",LogicalAction.COMPOSE to "Y",
        LogicalAction.PREVIOUS to "L1",LogicalAction.NEXT to "R1",LogicalAction.INPUT to "Select",LogicalAction.HOME to "Start",
    )
    fun forScreen(screen: Screen,mode: InputMode,acquiring: Boolean=false,review: Boolean=false): List<ControllerCommand> {
        val remote=mode==InputMode.REMOTE_KEYS
        val assistant=screen==Screen.ASSISTANT
        val home=screen==Screen.HOME
        val terminal=screen==Screen.TERMINAL
        val compose=screen==Screen.COMPOSE
        val confirm=when { acquiring->"Wait";compose->if(review)"Send + Enter"else "Review";assistant->"Ask / choose";home->"Open";terminal->if(remote)"Enter once"else "Control";screen==Screen.VOICE->"Use text";else->"Select" }
        val x=when { compose->"Save draft";assistant->"Chats";remote->"Exact keys";else->"Actions" }
        val y=when { home->"Assistant";assistant->"Write";terminal || compose->"Write";else->"—" }
        return listOf(
            ControllerCommand(LogicalAction.CONFIRM,"A",confirm,hint=true,enabled=!acquiring),
            ControllerCommand(LogicalAction.BACK,"B",if(remote || acquiring)"Exit input"else "Back / close"),
            ControllerCommand(LogicalAction.UP,"D-pad",if(remote)"Remote arrows"else "Scroll / navigate",if(assistant)"Up/down: scroll · left/right: proposals"else if(remote)"Remote arrows"else "Scroll / navigate"),
            ControllerCommand(LogicalAction.ACTIONS,"X",x,hint=home || terminal || compose || assistant,enabled=home || terminal || compose || assistant),
            ControllerCommand(LogicalAction.COMPOSE,"Y",y,"Tap: $y · hold: ${if(remote || compose)"dictation"else "assistant mic"}",hint=home || terminal || assistant,enabled=home || terminal || compose || assistant),
            ControllerCommand(LogicalAction.PREVIOUS,"L1","Previous agent",enabled=(home || terminal) && !remote && !acquiring),
            ControllerCommand(LogicalAction.NEXT,"R1","Next agent",enabled=(home || terminal) && !remote && !acquiring),
            ControllerCommand(LogicalAction.FONT_SMALL,"L2","Smaller text"),ControllerCommand(LogicalAction.FONT_LARGE,"R2","Larger text"),
            ControllerCommand(LogicalAction.INPUT,"Select","Hints","Tap: hints for 3s · hold: full map"),
            ControllerCommand(LogicalAction.SYSTEM,"Start","System","Tap: System · hold: Home")
        )
    }
}
