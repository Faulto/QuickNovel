package com.lagradost.quicknovel.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryProgressTest {
    @Test
    fun unreadAndNotStartedUseSeparateSemantics() {
        assertEquals(7, LibraryProgress.unreadCount(totalChapters = 10, readCount = 3))
        assertTrue(LibraryProgress.hasUnread(totalChapters = 10, readCount = 3))
        assertFalse(LibraryProgress.isNotStarted(readCount = 3))

        assertEquals(10, LibraryProgress.unreadCount(totalChapters = 10, readCount = 0))
        assertTrue(LibraryProgress.hasUnread(totalChapters = 10, readCount = 0))
        assertTrue(LibraryProgress.isNotStarted(readCount = 0))

        assertEquals(0, LibraryProgress.unreadCount(totalChapters = 10, readCount = 12))
        assertFalse(LibraryProgress.hasUnread(totalChapters = 10, readCount = 12))
        assertFalse(LibraryProgress.isNotStarted(readCount = 12))
    }

    @Test
    fun completedUsesStatusOrFinalChapterHeuristic() {
        assertTrue(LibraryProgress.isCompletedLike(status = "Completed", lastChapterName = null))
        assertTrue(LibraryProgress.isCompletedLike(status = null, lastChapterName = "Chapter 100 (End)"))
        assertTrue(LibraryProgress.isCompletedLike(status = "Ongoing", lastChapterName = "Finale"))
        assertFalse(LibraryProgress.isCompletedLike(status = "Ongoing", lastChapterName = "Chapter 12"))
    }

    @Test
    fun libraryFiltersCombineWithAndSemantics() {
        assertTrue(
            LibraryProgress.matchesFilters(
                totalChapters = 10,
                readCount = 0,
                status = "Completed",
                lastChapterName = null,
                unreadOnly = true,
                notStartedOnly = true,
                completedOnly = true
            )
        )

        assertFalse(
            LibraryProgress.matchesFilters(
                totalChapters = 10,
                readCount = 4,
                status = "Completed",
                lastChapterName = null,
                unreadOnly = true,
                notStartedOnly = true,
                completedOnly = true
            )
        )

        assertFalse(
            LibraryProgress.matchesFilters(
                totalChapters = 10,
                readCount = 10,
                status = "Ongoing",
                lastChapterName = "Chapter 10",
                unreadOnly = true,
                notStartedOnly = false,
                completedOnly = false
            )
        )
    }
}
