package dev.herdr.handheld.storage

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*

class DraftStoreTest {
    @Test fun switchingRecipientsRetainsBothDraftsAndLatestEditWins()=runTest {
        val stored=java.util.concurrent.ConcurrentHashMap<String,String>()
        val store=DraftStore(this,{stored[it]},{key,text->stored[key]=text},{_,_->},{error("save failed")})
        store.save("host/session-a/pane","old",true)
        store.save("host/session-b/pane","other",true)
        store.save("host/session-a/pane","latest",false)
        testScheduler.advanceUntilIdle()
        // IO writes finish as children of the test, without relying on wall-clock sleeps.
        coroutineContext.job.children.toList().joinAll()
        assertEquals("latest",stored["host/session-a/pane"])
        assertEquals("other",stored["host/session-b/pane"])
    }
}
