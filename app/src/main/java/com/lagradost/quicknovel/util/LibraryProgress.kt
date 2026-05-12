package com.lagradost.quicknovel.util

import com.lagradost.quicknovel.BaseApplication.Companion.getKeys
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_READ_AT

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
        return maxOf(cached.lastChapterRead, readCountForNovelName(cached.name)).coerceAtLeast(0)
    }

    fun readCount(cached: ResultCached, readCounts: Map<String, Int>): Int {
        return maxOf(cached.lastChapterRead, readCounts[cached.name] ?: 0).coerceAtLeast(0)
    }

    fun readCountForNovelName(name: String): Int {
        val prefix = "$EPUB_CURRENT_POSITION_READ_AT/$name/"
        return getKeys(EPUB_CURRENT_POSITION_READ_AT)
            ?.count { it.startsWith(prefix) }
            ?: 0
    }

    fun readCountSnapshot(keys: Collection<String>): Map<String, Int> {
        val prefix = "$EPUB_CURRENT_POSITION_READ_AT/"
        return keys.asSequence()
            .filter { key -> key.startsWith(prefix) }
            .mapNotNull { key ->
                val path = key.removePrefix(prefix)
                val novelName = path.substringBeforeLast('/', missingDelimiterValue = "")
                val chapterIndex = path.substringAfterLast('/', missingDelimiterValue = "")
                novelName.takeIf { it.isNotBlank() && chapterIndex.toIntOrNull() != null }
            }
            .groupingBy { it }
            .eachCount()
    }

    fun readCountSnapshot(): Map<String, Int> {
        return readCountSnapshot(getKeys(EPUB_CURRENT_POSITION_READ_AT) ?: emptyList())
    }

    fun unreadCount(cached: ResultCached): Int {
        return unreadCount(cached.currentTotalChapters, readCount(cached))
    }

    fun unreadCount(cached: ResultCached, readCounts: Map<String, Int>): Int {
        return unreadCount(cached.currentTotalChapters, readCount(cached, readCounts))
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

    fun matchesFilters(
        totalChapters: Int,
        readCount: Int,
        status: String?,
        lastChapterName: String?,
        unreadOnly: Boolean,
        notStartedOnly: Boolean,
        completedOnly: Boolean
    ): Boolean {
        if (unreadOnly && !hasUnread(totalChapters, readCount)) return false
        if (notStartedOnly && !isNotStarted(readCount)) return false
        if (completedOnly && !isCompletedLike(status, lastChapterName)) return false
        return true
    }

    fun matchesFilters(
        cached: ResultCached,
        unreadOnly: Boolean,
        notStartedOnly: Boolean,
        completedOnly: Boolean
    ): Boolean {
        return matchesFilters(
            totalChapters = cached.currentTotalChapters,
            readCount = readCount(cached),
            status = cached.status,
            lastChapterName = cached.lastChapterName,
            unreadOnly = unreadOnly,
            notStartedOnly = notStartedOnly,
            completedOnly = completedOnly
        )
    }

    fun matchesFilters(
        cached: ResultCached,
        readCounts: Map<String, Int>,
        unreadOnly: Boolean,
        notStartedOnly: Boolean,
        completedOnly: Boolean
    ): Boolean {
        return matchesFilters(
            totalChapters = cached.currentTotalChapters,
            readCount = readCount(cached, readCounts),
            status = cached.status,
            lastChapterName = cached.lastChapterName,
            unreadOnly = unreadOnly,
            notStartedOnly = notStartedOnly,
            completedOnly = completedOnly
        )
    }
}
