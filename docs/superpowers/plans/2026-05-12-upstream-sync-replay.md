# QuickNovel Upstream Sync Replay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the fork-specific QuickNovel features on top of `upstream/master` without preserving the old merge conflicts.

**Architecture:** Start from `upstream/master`, then replay features as focused changes: data model and helpers first, then cached/offline result behavior, then library filters/badges, then visual styling. Use shared helpers for read/unread calculations so Library, Result, and card UI agree.

**Tech Stack:** Android XML layouts, Kotlin ViewModels/adapters, WorkManager, app resource styles/strings, Gradle Android project.

---

## File Structure

- Modify `app/src/main/java/com/lagradost/quicknovel/DataStore.kt`: add fork preference/storage keys.
- Modify `app/src/main/java/com/lagradost/quicknovel/ui/ReadType.kt`: add `TRASH`.
- Modify `app/src/main/java/com/lagradost/quicknovel/util/ResultCached.kt`: persist status and last chapter metadata.
- Create `app/src/main/java/com/lagradost/quicknovel/util/LibraryProgress.kt`: shared read/unread/count helpers.
- Modify `app/src/main/java/com/lagradost/quicknovel/BookDownloader2.kt`: add cached chapter count fallback and empty chapter URL guard, preserving upstream PDF/import behavior.
- Modify `app/src/main/java/com/lagradost/quicknovel/DownloadFileWorkManager.kt`: keep upstream worker refresh and extend metadata refresh if needed.
- Modify `app/src/main/java/com/lagradost/quicknovel/ui/result/ResultViewModel.kt`: cached-first result loading, persisted status metadata, read count, cached chapter progress.
- Modify `app/src/main/java/com/lagradost/quicknovel/ui/result/ResultFragment.kt`: result read/download labels, progress bars, chip style, button enabled states.
- Modify `app/src/main/java/com/lagradost/quicknovel/ui/result/ChapterAdapter.kt`: chapter zebra striping.
- Modify `app/src/main/java/com/lagradost/quicknovel/ui/download/DownloadViewModel.kt`: Trash tab, unread/not-started/completed filters, unread sort, refresh integration.
- Modify `app/src/main/java/com/lagradost/quicknovel/ui/download/DownloadFragment.kt`: toolbar import button, last-read background, filter switches.
- Modify `app/src/main/java/com/lagradost/quicknovel/ui/download/AnyAdapter.kt`: Library unread badges only.
- Modify `app/src/main/java/com/lagradost/quicknovel/ui/search/SearchFragment.kt`: toolbar import button only, no search/home unread badges.
- Modify XML resources under `app/src/main/res/layout`, `app/src/main/res/values`, `app/src/main/res/color`, and `app/src/main/res/drawable`: default fork styling, Library badge, result progress UI, sort toggles, strings.
- Drop `.idea` changes by starting from upstream and not replaying those files.

## Task 1: Branch From Upstream And Preserve Docs

- [ ] Create branch `codex/upstream-sync-replay` from `upstream/master`.
- [ ] Keep `docs/fork-upstream-sync-audit.md` and this plan on that branch.
- [ ] Run `git status --short --branch`.
- [ ] Commit docs with `docs: audit fork upstream replay`.

## Task 2: Data Model And Shared Progress Helpers

- [ ] Add storage keys in `DataStore.kt`:
  - `DOWNLOAD_UNREAD_ONLY_FILTER`
  - `DOWNLOAD_NOT_STARTED_ONLY_FILTER`
  - `DOWNLOAD_COMPLETED_ONLY_FILTER`
  - `LIBRARY_LAST_READ_ID`
- [ ] Add `ReadType.TRASH(6, R.string.type_trash)`.
- [ ] Add nullable `status` and `lastChapterName` to `ResultCached`.
- [ ] Add `LibraryProgress.kt` with helpers:
  - `readCountForNovelName(name: String): Int`
  - `unreadCount(cached: ResultCached): Int`
  - `isNotStarted(cached: ResultCached): Boolean`
  - `isCompletedLike(cached: ResultCached): Boolean`
- [ ] Build with `./gradlew assembleDebug` and fix compile errors before continuing.
- [ ] Commit with `feat: add library progress metadata`.

## Task 3: Downloader Cache Counting

- [ ] Add `BookDownloader2Helper.countCachedChapters(...)` while preserving upstream PDF/import behavior in `downloadInfo()`.
- [ ] Add the empty `ChapterData.url` guard in `downloadIndividualChapter()`.
- [ ] Run `./gradlew assembleDebug`.
- [ ] Commit with `fix: count cached streaming chapters`.

## Task 4: Cached-First Result Page

- [ ] Update `ResultViewModel` so direct result loads show cached metadata from bookmarks/history first.
- [ ] Do not create fake placeholder chapters with empty URLs.
- [ ] Preserve cached total chapter count for progress display.
- [ ] Persist `status` and `lastChapterName` when adding history/bookmark data.
- [ ] Update read-count state from `LibraryProgress`.
- [ ] Run `./gradlew assembleDebug`.
- [ ] Commit with `feat: load result metadata from cache first`.

## Task 5: Result Page UI And Layout

- [ ] Add result read/download progress labels and read progress bar in `fragment_result.xml`.
- [ ] Keep upstream `TextButton` and `DropdownButton`; add fork result-outline style separately.
- [ ] Apply transparent/outlined tag and action-button styling.
- [ ] Add chapter zebra striping in `ChapterAdapter.kt`.
- [ ] Update `ResultFragment.kt` to animate read/download progress and use enabled state instead of only clickable state.
- [ ] Run `./gradlew assembleDebug`.
- [ ] Commit with `feat: restore fork result page layout`.

## Task 6: Library Filters, Sort, And Trash

- [ ] Add Trash to upstream `DownloadViewModel.readList` and page mapping.
- [ ] Add `UNREAD_CHAPTER_SORT` and reverse sort for non-download tabs.
- [ ] Add filter switches for `Unread only`, `Not started`, and `Completed novels only`.
- [ ] Implement filter semantics with AND combination:
  - unread: `readCount < total`
  - not started: `readCount == 0`
  - completed: provider completed OR final/end title heuristic
- [ ] Keep pull-to-refresh behavior aligned with upstream WorkManager refresh.
- [ ] Run `./gradlew assembleDebug`.
- [ ] Commit with `feat: restore fork library filters`.

## Task 7: Library Badges, Import Buttons, And Last-Read Background

- [ ] Add Library unread badges in `AnyAdapter.kt` and Library card layouts only.
- [ ] Do not port search/home unread badge adapter logic.
- [ ] Add toolbar import buttons to Search and Library.
- [ ] Track true last-read novel and load it as the Library blurred background.
- [ ] Keep import footer/card behavior from upstream.
- [ ] Run `./gradlew assembleDebug`.
- [ ] Commit with `feat: restore fork library affordances`.

## Task 8: Fork Default Theme Polish

- [ ] Replace direct old hard-coded styling with named resources/attrs where practical.
- [ ] Keep fork styling as default.
- [ ] Preserve upstream layout improvements that landed after the fork.
- [ ] Run `./gradlew assembleDebug`.
- [ ] Commit with `style: restore fork default theme`.

## Task 9: Final Verification

- [ ] Run `./gradlew assembleDebug`.
- [ ] Run available unit tests with `./gradlew testDebugUnitTest` if the project supports them.
- [ ] Run `git status --short`.
- [ ] Record any known limitations in the final response.
