package com.lagradost.quicknovel.util

object LibraryProgress {
    private val completedChapterMarkers = listOf(
        "(end)",
        "[end]",
        " end",
        "(finale)",
        "[finale]",
        "finale",
        "(final)",
        "[final]",
        " final",
        "(完)",
        "完"
    )

    fun unreadCount(totalChapters: Int, readCount: Int): Int {
        return (totalChapters - readCount).coerceAtLeast(0)
    }

    fun hasUnread(totalChapters: Int, readCount: Int): Boolean {
        return totalChapters > 0 && readCount < totalChapters
    }

    fun isNotStarted(readCount: Int): Boolean {
        return readCount <= 0
    }

    fun isCompletedLike(status: String?, lastChapterName: String?): Boolean {
        if (status.equals("Completed", ignoreCase = true)) return true

        val normalizedChapter = lastChapterName
            ?.lowercase()
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?: return false

        return completedChapterMarkers.any { marker ->
            normalizedChapter == marker.trim() || normalizedChapter.contains(marker)
        }
    }

    fun readCount(cached: ResultCached): Int {
        return cached.lastChapterRead.coerceAtLeast(0)
    }

    fun unreadCount(cached: ResultCached): Int {
        return unreadCount(cached.currentTotalChapters, readCount(cached))
    }

    fun hasUnread(cached: ResultCached): Boolean {
        return hasUnread(cached.currentTotalChapters, readCount(cached))
    }

    fun isNotStarted(cached: ResultCached): Boolean {
        return isNotStarted(readCount(cached))
    }

    fun isCompletedLike(cached: ResultCached): Boolean {
        return isCompletedLike(cached.status, cached.lastChapterName)
    }
}
