package com.lagradost.quicknovel.ui.download

data class LibraryPagePlan(
    val immediatePage: Int,
    val deferredLibraryPages: Set<Int>
)

object LibraryPagePlanner {
    fun plan(pageCount: Int, selectedPage: Int): LibraryPagePlan {
        if (pageCount <= 0) return LibraryPagePlan(0, emptySet())

        val immediatePage = selectedPage.coerceIn(0, pageCount - 1)
        val deferredLibraryPages = (1 until pageCount)
            .filter { page -> page != immediatePage }
            .toSet()

        return LibraryPagePlan(immediatePage, deferredLibraryPages)
    }
}
