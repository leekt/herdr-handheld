package dev.herdr.handheld.terminal

import org.junit.Assert.*
import org.junit.Test

class ReadingBufferTest {
    @Test fun newerOutputDoesNotMoveContentWhileBrowsingOlderLines() {
        val reading=ReadingBuffer().receive("First response",1).follow(false)
            .receive("Second response",2).receive("Latest response",3)
        assertEquals("First response",reading.output)
        assertEquals("Latest response",reading.pending)
        assertEquals(1L,reading.updatedAt)
        val resumed=reading.follow(true)
        assertEquals("Latest response",resumed.output)
        assertEquals(3L,resumed.updatedAt)
        assertNull(resumed.pending)
        assertTrue(resumed.following)
    }
    @Test fun followingUpdatesImmediatelyAndSameOutputDoesNotClaimNewContent() {
        val current=ReadingBuffer().receive("One",1).receive("Two",2)
        assertEquals("Two",current.output)
        assertNull(current.pending)
        val reading=current.follow(false).receive("Two",3)
        assertFalse(reading.following)
        assertNull(reading.pending)
        assertEquals(3L,reading.updatedAt)
    }
}
