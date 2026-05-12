package com.lagradost.quicknovel.ui.download

import android.content.DialogInterface
import androidx.annotation.StringRes
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.getKeys
import com.lagradost.quicknovel.BaseApplication.Companion.removeKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader2
import com.lagradost.quicknovel.BookDownloader2.currentDownloads
import com.lagradost.quicknovel.BookDownloader2.currentDownloadsMutex
import com.lagradost.quicknovel.BookDownloader2.downloadDataRefreshed
import com.lagradost.quicknovel.BookDownloader2.downloadInfoMutex
import com.lagradost.quicknovel.BookDownloader2.downloadProgress
import com.lagradost.quicknovel.BookDownloader2.downloadProgressChanged
import com.lagradost.quicknovel.BookDownloader2.downloadRemoved
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.CURRENT_TAB
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.DOWNLOAD_COMPLETED_ONLY_FILTER
import com.lagradost.quicknovel.DOWNLOAD_EPUB_LAST_ACCESS
import com.lagradost.quicknovel.DOWNLOAD_NOT_STARTED_ONLY_FILTER
import com.lagradost.quicknovel.DOWNLOAD_NORMAL_SORTING_METHOD
import com.lagradost.quicknovel.DOWNLOAD_SETTINGS
import com.lagradost.quicknovel.DOWNLOAD_SORTING_METHOD
import com.lagradost.quicknovel.DOWNLOAD_UNREAD_ONLY_FILTER
import com.lagradost.quicknovel.DownloadActionType
import com.lagradost.quicknovel.DownloadFileWorkManager
import com.lagradost.quicknovel.DownloadFileWorkManager.Companion.viewModel
import com.lagradost.quicknovel.DownloadProgressState
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.LIBRARY_LAST_READ_ID
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.PreferenceDelegate
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.RESULT_BOOKMARK
import com.lagradost.quicknovel.RESULT_BOOKMARK_STATE
import com.lagradost.quicknovel.StreamResponse
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.launchSafe
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.ui.UiImage
import com.lagradost.quicknovel.util.Apis.Companion.getApiFromNameOrNull
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.LibraryProgress
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import me.xdrop.fuzzywuzzy.FuzzySearch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.collections.set
import kotlin.coroutines.cancellation.CancellationException

const val DEFAULT_SORT = 0
const val ALPHA_SORT = 1
const val REVERSE_ALPHA_SORT = 2
const val DOWNLOADSIZE_SORT = 3
const val REVERSE_DOWNLOADSIZE_SORT = 4
const val DOWNLOADPRECENTAGE_SORT = 5
const val REVERSE_DOWNLOADPRECENTAGE_SORT = 6
const val LAST_ACCES_SORT = 7
const val REVERSE_LAST_ACCES_SORT = 8
const val LAST_UPDATED_SORT = 9
const val REVERSE_LAST_UPDATED_SORT = 10

const val CHAPTER_SORT = 11
const val REVERSE_CHAPTER_SORT = 12
const val UNREAD_CHAPTER_SORT = 13
const val REVERSE_UNREAD_CHAPTER_SORT = 14

data class SortingMethod(@StringRes val name: Int, val id: Int, val inverse: Int = id)

private data class LibrarySortState(
    val sortingMethod: Int,
    val unreadOnly: Boolean,
    val notStartedOnly: Boolean,
    val completedOnly: Boolean,
    val readCounts: Map<String, Int>
) {
    val hasFilters: Boolean = unreadOnly || notStartedOnly || completedOnly
}

class DownloadViewModel : ViewModel() {

    companion object {
        val sortingMethods = arrayOf(
            SortingMethod(R.string.default_sort, DEFAULT_SORT),
            SortingMethod(R.string.recently_sort, LAST_ACCES_SORT, REVERSE_LAST_ACCES_SORT),
            SortingMethod(
                R.string.recently_updated_sort,
                LAST_UPDATED_SORT,
                REVERSE_LAST_UPDATED_SORT
            ),
            SortingMethod(R.string.alpha_sort, ALPHA_SORT, REVERSE_ALPHA_SORT),
            SortingMethod(R.string.download_sort, DOWNLOADSIZE_SORT, REVERSE_DOWNLOADSIZE_SORT),
            SortingMethod(
                R.string.download_perc, DOWNLOADPRECENTAGE_SORT,
                REVERSE_DOWNLOADPRECENTAGE_SORT
            ),
        )

        val normalSortingMethods = arrayOf(
            SortingMethod(R.string.default_sort, DEFAULT_SORT),
            SortingMethod(R.string.recently_sort, LAST_ACCES_SORT, REVERSE_LAST_ACCES_SORT),
            SortingMethod(R.string.alpha_sort, ALPHA_SORT, REVERSE_ALPHA_SORT),
            SortingMethod(
                R.string.unread_chapter_sort,
                UNREAD_CHAPTER_SORT,
                REVERSE_UNREAD_CHAPTER_SORT
            ),
        )

        var unreadOnlyFilter by PreferenceDelegate(
            DOWNLOAD_UNREAD_ONLY_FILTER,
            false,
            Boolean::class
        )
        var notStartedOnlyFilter by PreferenceDelegate(
            DOWNLOAD_NOT_STARTED_ONLY_FILTER,
            false,
            Boolean::class
        )
        var completedOnlyFilter by PreferenceDelegate(
            DOWNLOAD_COMPLETED_ONLY_FILTER,
            false,
            Boolean::class
        )
    }

    val readList = arrayListOf(
        ReadType.READING,
        ReadType.ON_HOLD,
        ReadType.PLAN_TO_READ,
        ReadType.COMPLETED,
        ReadType.DROPPED,
        ReadType.TRASH,
    )

    var activeQuery: String = ""
    val _pages: MutableLiveData<List<Page>> = MutableLiveData(null)
    val pages: LiveData<List<Page>> = _pages

    private var selectedTab: Int = getKey(DOWNLOAD_SETTINGS, CURRENT_TAB, 0) ?: 0
    private var downloadsDirty: Boolean = false
    private val dirtyLibraryPages = mutableSetOf<Int>()
    private val loadedLibraryPages = mutableSetOf<Int>()
    private val libraryBookmarkKeysByPage = mutableMapOf<Int, List<String>>()
    private val libraryPageJobs = mutableMapOf<Int, Job>()

    var currentTab: MutableLiveData<Int> = MutableLiveData<Int>(selectedTab)

    val libraryBackground: MutableLiveData<UiImage?> = MutableLiveData(null)

    fun switchPage(position: Int) {
        selectedTab = position
        setKey(DOWNLOAD_SETTINGS, CURRENT_TAB, position)
        currentTab.value = position
        if (position == 0) {
            sortDownloadsPageIfDirty()
        } else {
            sortOrLoadLibraryPage(position)
        }
    }

    fun refreshCard(card: DownloadFragment.DownloadDataLoaded) {
        DownloadFileWorkManager.download(card, context ?: return)
    }

    fun pause(card: DownloadFragment.DownloadDataLoaded) {
        BookDownloader2.addPendingAction(card.id, DownloadActionType.Pause)
    }

    fun resume(card: DownloadFragment.DownloadDataLoaded) {
        BookDownloader2.addPendingAction(card.id, DownloadActionType.Resume)
    }

    fun load(card: ResultCached) {
        loadResult(card.source, card.apiName)
    }

    fun stream(card: ResultCached) {
        setKey(LIBRARY_LAST_READ_ID, card.id)
        libraryBackground.postValue(card.image)
        BookDownloader2.stream(card)
    }

    fun search(query: String) {
        activeQuery = query.lowercase()
        resortAllData()
    }

    fun readEpub(card: DownloadFragment.DownloadDataLoaded) = ioSafe {
        try {
            cardsDataMutex.withLock {
                cardsData[card.id] = cardsData[card.id]?.copy(generating = true) ?: return@withLock
            }
            postCards()
            BookDownloader2.readEpub(
                card.id,
                card.downloadedCount.toInt(),
                card.author,
                card.name,
                card.apiName,
                card.synopsis
            )
        } finally {
            setKey(DOWNLOAD_EPUB_LAST_ACCESS, card.id.toString(), System.currentTimeMillis())
            setKey(LIBRARY_LAST_READ_ID, card.id)
            libraryBackground.postValue(card.image)
            cardsDataMutex.withLock {
                cardsData[card.id] = cardsData[card.id]?.copy(generating = false) ?: return@withLock
            }
            postCards()
        }
    }

    @WorkerThread
    suspend fun refreshInternal() {
        val allValues = cardsDataMutex.withLock {
            cardsData.values
        }

        val values = currentDownloadsMutex.withLock {
            allValues.filter { card ->
                val notImported = !card.isImported && card.apiName != IMPORT_SOURCE_PDF
                val canDownload =
                    card.downloadedTotal <= 0 || (card.downloadedCount * 100 / card.downloadedTotal) > 90
                val notDownloading = !currentDownloads.contains(
                    card.id
                )
                notImported && canDownload && notDownloading
            }
        }

        downloadInfoMutex.withLock {
            for (card in values) {
                downloadProgress[card.id]?.apply {
                    state = DownloadState.IsPending
                    lastUpdatedMs = System.currentTimeMillis()
                    downloadProgressChanged.invoke(card.id to this)
                }
            }
        }

        for (card in values) {
            if (card.downloadedTotal <= 0 || (card.downloadedCount * 100 / card.downloadedTotal) > 90) {
                BookDownloader2.downloadWorkThread(card)
            }
        }
    }

    fun refresh() {
        DownloadFileWorkManager.refreshAll(this@DownloadViewModel, context ?: return)
    }

    fun refreshReadingProgress(){
        DownloadFileWorkManager.refreshAllReadingProgress(this@DownloadViewModel, context ?: return, selectedTab)
    }

    fun showMetadata(card: DownloadFragment.DownloadDataLoaded) {
        MainActivity.loadPreviewPage(card)
    }

    fun importEpub() {
        MainActivity.importEpub()
    }

    fun showMetadata(card: ResultCached) {
        MainActivity.loadPreviewPage(card)
    }

    fun load(card: DownloadFragment.DownloadDataLoaded) {
        loadResult(card.source, card.apiName)
    }

    fun deleteAlert(card: ResultCached) {
        val dialogClickListener =
            DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        delete(card)
                    }

                    DialogInterface.BUTTON_NEGATIVE -> {
                    }
                }
            }
        val act = activity ?: return
        val builder: AlertDialog.Builder = AlertDialog.Builder(act)
        builder.setMessage(act.getString(R.string.permanently_delete_format).format(card.name))
            .setTitle(R.string.delete)
            .setPositiveButton(R.string.delete, dialogClickListener)
            .setNegativeButton(R.string.cancel, dialogClickListener)
            .show()
    }

    fun delete(card: ResultCached) {
        removeKey(RESULT_BOOKMARK, card.id.toString())
        removeKey(RESULT_BOOKMARK_STATE, card.id.toString())
        loadAllData(false)
    }

    fun deleteAlert(card: DownloadFragment.DownloadDataLoaded) {
        val dialogClickListener =
            DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        delete(card)
                    }

                    DialogInterface.BUTTON_NEGATIVE -> {
                    }
                }
            }
        val act = activity ?: return
        val builder: AlertDialog.Builder = AlertDialog.Builder(act)
        builder.setMessage(act.getString(R.string.permanently_delete_format).format(card.name))
            .setTitle(R.string.delete)
            .setPositiveButton(R.string.delete, dialogClickListener)
            .setNegativeButton(R.string.cancel, dialogClickListener)
            .show()
    }

    fun delete(card: DownloadFragment.DownloadDataLoaded) {
        BookDownloader2.deleteNovel(card.author, card.name, card.apiName)
    }

    private fun matchesQuery(x: String): Boolean {
        return activeQuery.isBlank() || FuzzySearch.partialRatio(x.lowercase(), activeQuery) > 50
    }

    private fun sortArray(
        currentArray: ArrayList<DownloadFragment.DownloadDataLoaded>,
    ): List<DownloadFragment.DownloadDataLoaded> {
        val newSortingMethod = getKey(DOWNLOAD_SETTINGS, DOWNLOAD_SORTING_METHOD) ?: DEFAULT_SORT
        setKey(DOWNLOAD_SETTINGS, DOWNLOAD_SORTING_METHOD, newSortingMethod)

        return when (newSortingMethod) {
            ALPHA_SORT -> {
                currentArray.sortBy { t -> t.name }
                currentArray
            }

            REVERSE_ALPHA_SORT -> {
                currentArray.sortByDescending { t -> t.name }
                currentArray
            }

            DOWNLOADSIZE_SORT -> {
                currentArray.sortByDescending { t -> t.downloadedCount }
                currentArray
            }

            REVERSE_DOWNLOADSIZE_SORT -> {
                currentArray.sortBy { t -> t.downloadedCount }
                currentArray
            }

            DOWNLOADPRECENTAGE_SORT -> {
                currentArray.sortByDescending { t -> t.downloadedCount.toFloat() / t.downloadedTotal }
                currentArray
            }

            REVERSE_DOWNLOADPRECENTAGE_SORT -> {
                currentArray.sortBy { t -> t.downloadedCount.toFloat() / t.downloadedTotal }
                currentArray
            }

            REVERSE_LAST_ACCES_SORT -> {
                currentArray.sortBy { t ->
                    (getKey<Long>(
                        DOWNLOAD_EPUB_LAST_ACCESS,
                        t.id.toString(),
                        0
                    )!!)
                }
                currentArray
            }

            LAST_UPDATED_SORT -> {
                if (currentArray.any { it.lastDownloaded == null }) {
                    currentArray.sortByDescending { t ->
                        (getKey<Long>(
                            DOWNLOAD_EPUB_LAST_ACCESS,
                            t.id.toString(),
                            0
                        )!!)
                    }
                }
                currentArray.sortByDescending { it.lastDownloaded ?: 0L }
                currentArray
            }

            REVERSE_LAST_UPDATED_SORT -> {
                if (currentArray.any { it.lastDownloaded == null }) {
                    currentArray.sortByDescending { t ->
                        (getKey<Long>(
                            DOWNLOAD_EPUB_LAST_ACCESS,
                            t.id.toString(),
                            0
                        )!!)
                    }
                }
                currentArray.sortBy { it.lastDownloaded ?: 0L }
                currentArray
            }
            //DEFAULT_SORT, LAST_ACCES_SORT
            else -> {
                currentArray.sortByDescending { t ->
                    (getKey<Long>(
                        DOWNLOAD_EPUB_LAST_ACCESS,
                        t.id.toString(),
                        0
                    )!!)
                }
                currentArray
            }
        }.filter { matchesQuery(it.name) }
    }

    private fun createLibrarySortState(): LibrarySortState {
        val newSortingMethod =
            getKey(DOWNLOAD_SETTINGS, DOWNLOAD_NORMAL_SORTING_METHOD) ?: DEFAULT_SORT
        setKey(DOWNLOAD_SETTINGS, DOWNLOAD_NORMAL_SORTING_METHOD, newSortingMethod)
        val unreadOnly = unreadOnlyFilter
        val notStartedOnly = notStartedOnlyFilter
        val completedOnly = completedOnlyFilter
        val needsReadCounts = unreadOnly || notStartedOnly ||
                newSortingMethod == UNREAD_CHAPTER_SORT ||
                newSortingMethod == REVERSE_UNREAD_CHAPTER_SORT
        val readCounts = if (needsReadCounts) {
            LibraryProgress.readCountSnapshot()
        } else {
            emptyMap()
        }

        return LibrarySortState(
            sortingMethod = newSortingMethod,
            unreadOnly = unreadOnly,
            notStartedOnly = notStartedOnly,
            completedOnly = completedOnly,
            readCounts = readCounts
        )
    }

    private fun sortNormalArray(
        currentArray: ArrayList<ResultCached>,
        sortState: LibrarySortState = createLibrarySortState(),
    ): List<ResultCached> {
        fun readCount(card: ResultCached): Int {
            return LibraryProgress.readCount(card, sortState.readCounts)
        }

        fun matchesFilters(card: ResultCached): Boolean {
            if (sortState.unreadOnly || sortState.notStartedOnly) {
                val count = readCount(card)
                val total = card.currentTotalChapters
                if (sortState.unreadOnly && !LibraryProgress.hasUnread(total, count)) return false
                if (sortState.notStartedOnly && !LibraryProgress.isNotStarted(count)) return false
            }

            if (sortState.completedOnly && !LibraryProgress.isCompletedLike(card)) return false
            return true
        }

        return when (sortState.sortingMethod) {
            ALPHA_SORT -> {
                currentArray.sortBy { t -> t.name }
                currentArray
            }

            REVERSE_ALPHA_SORT -> {
                currentArray.sortByDescending { t -> t.name }
                currentArray
            }

            UNREAD_CHAPTER_SORT -> {
                currentArray.sortByDescending { t ->
                    LibraryProgress.unreadCount(t.currentTotalChapters, readCount(t))
                }
                currentArray
            }

            REVERSE_UNREAD_CHAPTER_SORT -> {
                currentArray.sortBy { t ->
                    LibraryProgress.unreadCount(t.currentTotalChapters, readCount(t))
                }
                currentArray
            }

            REVERSE_LAST_ACCES_SORT -> {
                currentArray.sortBy { t ->
                    (getKey<Long>(
                        DOWNLOAD_EPUB_LAST_ACCESS,
                        t.id.toString(),
                        0
                    )!!)
                }
                currentArray
            }
            // DEFAULT_SORT, LAST_ACCES_SORT
            else -> {
                currentArray.sortByDescending { t ->
                    (getKey<Long>(
                        DOWNLOAD_EPUB_LAST_ACCESS,
                        t.id.toString(),
                        0
                    )!!)
                }
                currentArray
            }
        }.filter {
            matchesQuery(it.name) && (!sortState.hasFilters || matchesFilters(it))
        }
    }

    private fun cancelLibraryPageJobs() {
        val jobs = libraryPageJobs.values.toList()
        libraryPageJobs.clear()
        jobs.forEach { job -> job.cancel() }
    }

    private fun sortDownloadsPageIfDirty() {
        if (!downloadsDirty) return
        val data = _pages.value ?: return
        if (data.isEmpty()) return

        val list = ArrayList(data)
        list[0] = data[0].copy(
            unsortedItems = data[0].unsortedItems,
            items = sortArray(ArrayList(data[0].unsortedItems.map { (it as DownloadFragment.DownloadDataLoaded).copy() }))
        )
        downloadsDirty = false
        _pages.postValue(list)
    }

    private suspend fun loadLibraryCardsForPage(position: Int): ArrayList<ResultCached> {
        val keys = libraryBookmarkKeysByPage[position].orEmpty()
        return withContext(Dispatchers.IO) {
            ArrayList(keys.mapNotNull { key -> getKey<ResultCached>(key) })
        }
    }

    private suspend fun sortLibraryCards(cards: ArrayList<ResultCached>): List<ResultCached> {
        return withContext(Dispatchers.Default) {
            sortNormalArray(cards, createLibrarySortState())
        }
    }

    private fun sortOrLoadLibraryPage(position: Int, force: Boolean = false) {
        if (position <= 0) return
        val data = _pages.value ?: return
        if (position !in data.indices) return

        val isLoaded = loadedLibraryPages.contains(position)
        val isDirty = dirtyLibraryPages.contains(position)
        if (!force && isLoaded && !isDirty) return

        val activeJob = libraryPageJobs[position]
        if (!force && activeJob?.isActive == true) return
        activeJob?.cancel()
        val job = viewModelScope.launch {
            val cards = if (isLoaded) {
                ArrayList(data[position].unsortedItems.filterIsInstance<ResultCached>().map { it.copy() })
            } else {
                loadLibraryCardsForPage(position)
            }
            val sorted = sortLibraryCards(ArrayList(cards))
            val current = _pages.value ?: return@launch
            if (position !in current.indices) return@launch

            val list = ArrayList(current)
            list[position] = current[position].copy(
                unsortedItems = cards,
                items = sorted
            )
            loadedLibraryPages.add(position)
            dirtyLibraryPages.remove(position)
            _pages.postValue(list)
        }
        libraryPageJobs[position] = job
        job.invokeOnCompletion {
            if (libraryPageJobs[position] == job) {
                libraryPageJobs.remove(position)
            }
        }
    }

    fun resortAllData() {
        val data = _pages.value ?: return
        if (data.isEmpty()) {
            return
        }

        cancelLibraryPageJobs()
        val plan = LibraryPagePlanner.plan(data.size, selectedTab)
        dirtyLibraryPages.clear()
        dirtyLibraryPages.addAll(plan.deferredLibraryPages)

        if (plan.immediatePage == 0) {
            val list = ArrayList(data)
            list[0] = data[0].copy(
                unsortedItems = data[0].unsortedItems,
                items = sortArray(ArrayList(data[0].unsortedItems.map { (it as DownloadFragment.DownloadDataLoaded).copy() }))
            )
            downloadsDirty = false
            _pages.postValue(list)
        } else {
            downloadsDirty = true
            dirtyLibraryPages.add(plan.immediatePage)
            sortOrLoadLibraryPage(plan.immediatePage, force = true)
        }
    }

    fun loadAllData(refreshAll: Boolean) = viewModelScope.launch {
        if (refreshAll) fetchAllData(false)
        cancelLibraryPageJobs()
        val bookmarkKeysByPage = readList
            .mapIndexed { index, read -> read.prefValue to (index + 1) }
            .toMap()
        val pageKeys: HashMap<Int, ArrayList<String>> = hashMapOf()
        for (position in 1..readList.size) {
            pageKeys[position] = arrayListOf()
        }

        withContext(Dispatchers.IO) {
            val keys = getKeys(RESULT_BOOKMARK_STATE)
            for (key in keys ?: emptyList()) {
                val type = getKey<Int>(key) ?: continue
                val id = key.replaceFirst(
                    RESULT_BOOKMARK_STATE,
                    RESULT_BOOKMARK
                )
                val position = bookmarkKeysByPage[type] ?: continue
                pageKeys[position]?.add(id)
            }
        }

        libraryBookmarkKeysByPage.clear()
        libraryBookmarkKeysByPage.putAll(pageKeys.mapValues { (_, keys) -> keys.toList() })
        loadedLibraryPages.clear()

        val plan = LibraryPagePlanner.plan(readList.size + 1, selectedTab)
        dirtyLibraryPages.clear()
        dirtyLibraryPages.addAll(plan.deferredLibraryPages)
        downloadsDirty = false

        val activeLibraryCards = if (plan.immediatePage > 0) {
            loadLibraryCardsForPage(plan.immediatePage)
        } else {
            arrayListOf()
        }
        val activeLibraryItems = if (plan.immediatePage > 0) {
            loadedLibraryPages.add(plan.immediatePage)
            sortLibraryCards(ArrayList(activeLibraryCards))
        } else {
            emptyList()
        }

        val pages = mutableListOf(
            getDownloadedCards(),
        )
        for ((index, read) in readList.withIndex()) {
            val position = index + 1
            val unsortedItems = if (position == plan.immediatePage) {
                activeLibraryCards
            } else {
                emptyList()
            }
            val items = if (position == plan.immediatePage) {
                activeLibraryItems
            } else {
                emptyList()
            }
            pages.add(
                Page(
                    read.name,
                    unsortedItems = unsortedItems,
                    items = items
                ),
            )
        }
        _pages.postValue(pages)
        updateLibraryBackground(activeLibraryCards)


    }

    private suspend fun getDownloadedCards(): Page = cardsDataMutex.withLock {
        Page(
            ReadType.NONE.name, unsortedItems = ArrayList(cardsData.values),
            items =
                sortArray(ArrayList(cardsData.values))
        )
    }


    private suspend fun postCards() {
        _pages.value?.let { data ->
            val list = CopyOnWriteArrayList(data)
            if (list.isEmpty()) {
                list.add(getDownloadedCards())
            } else {
                list[0] = getDownloadedCards()
            }
            _pages.postValue(list)
            updateLibraryBackground()
        }
    }

    private suspend fun updateLibraryBackground(bookmarks: Collection<ResultCached> = emptyList()) {
        val lastReadId = getKey<Int>(LIBRARY_LAST_READ_ID)
        if (lastReadId == null) {
            libraryBackground.postValue(null)
            return
        }

        val downloadedImage = cardsDataMutex.withLock {
            cardsData[lastReadId]?.image
        }
        val bookmarkedImage = bookmarks.firstOrNull { card -> card.id == lastReadId }?.image
            ?: withContext(Dispatchers.IO) {
                getKey<ResultCached>(RESULT_BOOKMARK, lastReadId.toString())?.image
            }
        val image = downloadedImage ?: bookmarkedImage
        libraryBackground.postValue(image)
    }

    init {
        BookDownloader2.downloadDataChanged += ::progressDataChanged
        BookDownloader2.downloadProgressChanged += ::progressChanged
        BookDownloader2.downloadDataRefreshed += ::downloadDataRefreshed
        BookDownloader2.downloadRemoved += ::downloadRemoved
    }

    override fun onCleared() {
        super.onCleared()
        BookDownloader2.downloadProgressChanged -= ::progressChanged
        BookDownloader2.downloadDataChanged -= ::progressDataChanged
        BookDownloader2.downloadDataRefreshed -= ::downloadDataRefreshed
        BookDownloader2.downloadRemoved -= ::downloadRemoved
    }

    val activeRefreshTabs = mutableSetOf<Int>()
    val isRefreshing = MutableLiveData(false)
    private val _refresh = MutableSharedFlow<Int>(
        extraBufferCapacity = 32
    )
    val refresh = _refresh.asSharedFlow()
    fun setIsLoading(isActive: Boolean, currentTab: Int){
        isRefreshing.postValue(isActive)
        synchronized(activeRefreshTabs){
            if(isActive && !activeRefreshTabs.contains(currentTab))
                activeRefreshTabs.add(currentTab)
            else{
                _refresh.tryEmit(currentTab)
                activeRefreshTabs.remove(currentTab)
            }
        }
    }

    private val cardsDataMutex = Mutex()
    private val cardsData: HashMap<Int, DownloadFragment.DownloadDataLoaded> = hashMapOf()

    private fun progressChanged(data: Pair<Int, DownloadProgressState>) =
        viewModelScope.launchSafe {
            cardsDataMutex.withLock {
                val (id, state) = data
                val newState = state.eta(context ?: return@launchSafe)
                cardsData[id] = cardsData[id]?.copy(
                    downloadedCount = state.progress,
                    downloadedTotal = state.total,
                    state = state.state,
                    ETA = newState,
                ) ?: return@launchSafe
            }
            postCards()
        }

    private fun downloadRemoved(id: Int) = viewModelScope.launchSafe {
        cardsDataMutex.withLock {
            cardsData -= id
        }
        postCards()
    }

    private fun progressDataChanged(data: Pair<Int, DownloadFragment.DownloadData>) =
        viewModelScope.launchSafe {
            cardsDataMutex.withLock {
                val (id, value) = data
                cardsData[id] = cardsData[id]?.copy(
                    source = value.source,
                    name = value.name,
                    author = value.author,
                    posterUrl = value.posterUrl,
                    rating = value.rating,
                    peopleVoted = value.peopleVoted,
                    views = value.views,
                    synopsis = value.synopsis,
                    tags = value.tags,
                    apiName = value.apiName,
                    lastUpdated = value.lastUpdated,
                    lastDownloaded = value.lastDownloaded
                ) ?: run {
                    DownloadFragment.DownloadDataLoaded(
                        source = value.source,
                        name = value.name,
                        author = value.author,
                        posterUrl = value.posterUrl,
                        rating = value.rating,
                        peopleVoted = value.peopleVoted,
                        views = value.views,
                        synopsis = value.synopsis,
                        tags = value.tags,
                        apiName = value.apiName,
                        downloadedCount = 0,
                        downloadedTotal = 0,
                        ETA = "",
                        state = DownloadState.Nothing,
                        id = id,
                        generating = false,
                        lastUpdated = value.lastUpdated,
                        lastDownloaded = value.lastDownloaded,
                    )
                }
            }
            postCards()
        }

    suspend fun fetchAllData(postCard: Boolean) {
        downloadInfoMutex.withLock {
            cardsDataMutex.withLock {
                BookDownloader2.downloadData.map { (key, value) ->
                    val info = downloadProgress[key] ?: return@map
                    cardsData[key] = DownloadFragment.DownloadDataLoaded(
                        source = value.source,
                        name = value.name,
                        author = value.author,
                        posterUrl = value.posterUrl,
                        rating = value.rating,
                        peopleVoted = value.peopleVoted,
                        views = value.views,
                        synopsis = value.synopsis,
                        tags = value.tags,
                        apiName = value.apiName,
                        downloadedCount = info.progress,
                        downloadedTotal = info.total,
                        ETA = context?.let { ctx -> info.eta(ctx) } ?: "",
                        state = info.state,
                        id = key,
                        generating = false,
                        lastUpdated = value.lastUpdated,
                        lastDownloaded = value.lastDownloaded,
                    )
                }
            }
            if (postCard) postCards()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun downloadDataRefreshed(_id: Int) = viewModelScope.launchSafe {
        fetchAllData(true)
    }
}
