package dev.herdr.handheld.voice

import dev.herdr.handheld.herdr.TargetRef
import org.junit.Assert.*
import org.junit.Test

class VoiceSessionTest {
    @Test fun ignoresLateResultsAfterCancelOrNewRecipient() {
        val session=VoiceSession();val target=TargetRef("p","s","t","pane","codex","identity")
        session.begin(target,"First");val id=session.state.id
        session.cancel();session.begin(null,"Assistant")
        assertFalse(session.update(id,VoicePhase.REVIEW,text="late input"))
        assertEquals("",session.state.transcript);assertNull(session.state.target)
    }
    @Test fun finalTranscriptCannotBeOverwrittenByLatePartialOrError() {
        val session=VoiceSession();session.begin(null,"Assistant");val id=session.state.id
        assertTrue(session.update(id,VoicePhase.REVIEW,text="한글 and English"))
        assertFalse(session.update(id,VoicePhase.LISTENING,text="partial"))
        assertFalse(session.update(id,VoicePhase.ERROR,"late error"))
        assertEquals("한글 and English",session.state.transcript)
    }
}
