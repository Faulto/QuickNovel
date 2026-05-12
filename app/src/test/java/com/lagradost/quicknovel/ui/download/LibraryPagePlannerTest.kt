package com.lagradost.quicknovel.ui.download

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryPagePlannerTest {
    @Test
    fun activeLibraryPageIsImmediateAndOtherLibraryPagesAreDeferred() {
        val plan = LibraryPagePlanner.plan(pageCount = 7, selectedPage = 2)

        assertEquals(2, plan.immediatePage)
        assertEquals(setOf(1, 3, 4, 5, 6), plan.deferredLibraryPages)
    }

    @Test
    fun downloadsPageDefersEveryLibraryPage() {
        val plan = LibraryPagePlanner.plan(pageCount = 7, selectedPage = 0)

        assertEquals(0, plan.immediatePage)
        assertEquals(setOf(1, 2, 3, 4, 5, 6), plan.deferredLibraryPages)
    }
}
