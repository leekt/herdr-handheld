package dev.herdr.handheld.voice

import dev.herdr.handheld.herdr.TargetRef

enum class VoicePhase { IDLE, PERMISSION, STARTING, LISTENING, TRANSCRIBING, REVIEW, ERROR }
data class VoiceState(val id: Long=0,val phase: VoicePhase=VoicePhase.IDLE,val target: TargetRef?=null,val recipient: String="Assistant",val transcript: String="",val message: String="",val level: Float=0f)
/** A result belongs to one explicit recording and one recipient, even if an Android callback arrives late. */
class VoiceSession {
    var state=VoiceState();private set
    fun begin(target: TargetRef?,recipient: String) { state=VoiceState(state.id+1,VoicePhase.STARTING,target,recipient) }
    fun update(id: Long,phase: VoicePhase,message: String="",text: String?=null,level: Float?=null): Boolean {
        if(id!=state.id || state.phase in setOf(VoicePhase.IDLE,VoicePhase.ERROR,VoicePhase.REVIEW))return false
        state=state.copy(phase=phase,message=message,transcript=text?.take(12000) ?: state.transcript,level=level ?: state.level);return true
    }
    fun edit(text: String) { if(state.phase==VoicePhase.REVIEW && text.length<=12000)state=state.copy(transcript=text) }
    fun cancel() { state=VoiceState(id=state.id+1) }
}
