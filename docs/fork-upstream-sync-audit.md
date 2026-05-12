# Fork Upstream Sync Audit

Date: 2026-05-12

## Purpose

This document audits the local fork changes before rebasing or replaying them onto `upstream/master`. The goal is to keep the fork's behavior and default look, but avoid dragging old conflicts forward unchanged.

The recommended migration shape is a fresh branch from `upstream/master`, followed by small topic commits that reimplement the fork features against the current upstream code.

## Repository State

- Fork repo: `Faulto/QuickNovel`
- Upstream repo: `LagradOst/QuickNovel`
- Local branch: `master`, tracking `origin/master`
- Upstream branch: `upstream/master`
- Fork point: `07a445d` (`v3.4.1`, 2025-11-26)
- Fork-only commits after the fork point: 6
- Upstream-only commits after the fork point: 33

Current test merge conflicts are in:

- `app/src/main/java/com/lagradost/quicknovel/ui/download/DownloadFragment.kt`
- `app/src/main/java/com/lagradost/quicknovel/ui/download/DownloadViewModel.kt`
- `app/src/main/java/com/lagradost/quicknovel/ui/result/ChapterAdapter.kt`
- `app/src/main/java/com/lagradost/quicknovel/ui/result/ResultViewModel.kt`
- `app/src/main/java/com/lagradost/quicknovel/ui/search/HomeChildItemAdapter2.kt`
- `app/src/main/res/layout/fragment_downloads.xml`
- `app/src/main/res/layout/fragment_result.xml`
- `app/src/main/res/layout/fragment_search.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/styles.xml`

## Fork-Only Commits

### `2b6c48c` - `Read description`

This is the main fork feature commit. It includes library behavior, unread/read progress, cached novel loading, UI styling, and local `.idea` files.

### `7fd1d57` - `change library bg to last read novel, other theme changes etc`

This adds the blurred last-read library background and transparent/darker card styling.

### `cc278ad` - `fix cache issue`

This changes cached result loading to avoid placeholder chapters and skips empty chapter URLs during downloads.

### README commits

`bf65a1b` and `2c8735c` document fork features and replace upstream screenshots. README changes are optional and low priority for this migration.

### Merge commit

`20362ee` only merges local fork history and does not need to be replayed.

## User Decisions Captured

- Fork visual style remains the default.
- `Unread only` should mean entries with at least one unread chapter.
- Add a separate `Not started` toggle for entries with zero chapters read.
- Completed filtering should use provider completed status OR final/end chapter-title heuristics.
- Unread badges should be limited to the Library page, not search/home surfaces.
- The library background should be based on the last read novel, not metadata refresh time.
- Toolbar import buttons should be kept.
- `.idea` files should be dropped.
- Result-page layout/spacing improvements should be kept.
- README fork notes are low priority and can be updated or skipped after the migration.

## Feature Audit

### 1. Fork Visual Style As Default

Observed changes:

- Library/root pages use darker transparent overlays (`#59000000`) and blurred poster backgrounds.
- Result page uses a larger blurred poster background.
- Result download/read buttons switch from filled white/black buttons to outlined buttons.
- Tags use transparent outlined chip styling.
- Chapter rows use alternating subtle backgrounds.
- Bottom navigation and library cards are made translucent.

Keep: yes.

How to keep it:

- Keep the fork look as the default app theme.
- Do not overwrite upstream defaults ad hoc in each layout where a theme attribute can express the intent.
- Add fork-specific resources such as `ForkResultOutlineButton`, fork surface colors, and fork card overlay colors.
- Prefer theme attributes for fork surfaces so future upstream layout edits do not conflict on literal colors.

Risk:

- The current fork applies style changes directly to many upstream layouts. Replaying that literally will keep causing conflicts.
- Some colors are hard-coded as `#59000000`; these should become named color resources or theme attrs.

### 2. Library Background From Last Read Novel

Observed changes:

- `fragment_downloads.xml` root becomes a `FrameLayout`.
- A full-screen `ImageView` named `download_background_blur` is added.
- `DownloadFragment.loadBackgroundFromLastRead()` scans `HISTORY_FOLDER`, finds the most recent `ResultCached`, and loads its poster as a blurred background.

Keep: yes.

How to keep it:

- Reimplement as a dedicated helper in the current upstream `DownloadFragment`, because upstream layout and refresh behavior changed after the fork.
- Keep it enabled by default for this fork.
- Base it on a real "last read" event rather than metadata refresh time. Prefer a stored last-read novel ID/source updated when a reader session opens or advances.
- Consider a setting later only if users complain about visual noise or performance.

Risk:

- It currently scans all history keys on the main fragment path and silently ignores errors. We should move the history lookup off the main thread or keep it clearly bounded.
- It currently keys "last read" by `cachedTime`, which also changes during refreshes. That behavior should be replaced so refreshes do not change the background.

### 3. Trash Library Category

Observed changes:

- `ReadType.TRASH(6, R.string.type_trash)` is added.
- `DownloadViewModel.readList` includes Trash after Dropped.
- Bookmark state mapping includes a Trash bucket.

Keep: yes.

How to keep it:

- Add `TRASH` to upstream's current `ReadType` and current library tab setup.
- Preserve the existing numeric value `6` to avoid changing any saved fork data.
- Add translations later; English string is enough for initial migration if non-English strings are missing.

Risk:

- Upstream now has Spanish resources and renamed some adapters. The enum itself is straightforward, but translations will need a pass.

### 4. Library Unread/Not Started/Completed Filters

Observed changes:

- Adds `DOWNLOAD_UNREAD_ONLY_FILTER` and `DOWNLOAD_COMPLETED_ONLY_FILTER`.
- Adds two switches to `sort_bottom_sheet.xml`.
- Filters non-download library categories by unread count and completed status.
- Completed detection uses `ResultCached.status == "Completed"` or last chapter name containing final/end markers.

Keep: yes, but reshape.

How to keep it:

- Keep the existing filter concept in the sort sheet for non-download tabs.
- Add a third stored filter for `Not started`.
- Define `Unread only` as "has at least one unread chapter": `readCount < totalChapters`.
- Define `Not started` as "no chapters read": `readCount == 0`.
- Define `Completed novels only` with OR behavior: provider completed status OR final/end chapter-title heuristics.
- Use upstream's current `ResultCached.currentTotalChapters` and `lastChapterRead` where possible.
- Keep `status` and `lastChapterName` fields on `ResultCached` if completed filtering still needs provider status and final chapter heuristics.

Risk:

- Current fork implementation filters unread-only with `(readMapSnapshot[cached.name] ?: 0) == 0`, which means "nothing read yet." That behavior should move to the new `Not started` toggle.
- Current read matching is by novel name. Upstream can migrate IDs when provider data changes, so the port should prefer ID/source matching where possible.

### 5. Sort By Unread Chapters

Observed changes:

- Adds `UNREAD_CHAPTER_SORT` and `REVERSE_UNREAD_CHAPTER_SORT`.
- Adds "Unread chapters" to non-download library sort options.
- Counts read chapters from `EPUB_CURRENT_POSITION_READ_AT`.

Keep: yes.

How to keep it:

- Implement on top of upstream's current `ResultCached` helpers.
- Compute unread as `currentTotalChapters - lastChapterRead` where that is valid.
- If per-chapter read keys are more accurate than `lastChapterRead`, add a small reusable read-progress helper rather than duplicating key scans in adapters and view models.

Risk:

- The fork duplicates read-count indexing in multiple classes.
- Current unread calculations may disagree between library cards, search cards, and result page because they use slightly different key matching.

### 6. Library Unread Badges

Observed changes:

- Adds `unread_badge_background.xml`.
- Adds `unreadBadge` views to library/history/search grid card layouts.
- `AnyAdapter` and `HomeChildItemAdapter2` asynchronously compute unread counts and show badges.

Keep: yes, but reshape.

How to keep it:

- Limit unread badges to the Library page.
- Do not port the search/home unread badge surface from `HomeChildItemAdapter2`.
- Keep badge resources, but adapt them to upstream's existing `progressReading` overlay in `download_result_grid.xml`.
- Avoid adding a second badge that duplicates upstream's existing `lastChapterRead/currentTotalChapters` display unless the UI clearly benefits.

Risk:

- Upstream already added reading-progress labels on some library cards, so the fork badge should be reconciled with that instead of layered blindly on top.

### 7. Pull-To-Refresh Library Metadata

Observed changes:

- On download tab, pull-to-refresh calls existing download refresh.
- On bookmark/category tabs, fork loads each cached novel from its provider with `allowCache = false`, updates cached metadata, and posts progress notifications.
- Adds `LibraryRefreshNotifications`.

Keep: yes, but merge with upstream's newer refresh mechanism.

How to keep it:

- Upstream now has `refreshReadingProgress()` and `DownloadFileWorkManager.refreshAllReadingProgress()`.
- Preserve upstream's worker-based refresh path and expand it to update metadata fields the fork cares about: poster, tags, rating, synopsis, total chapters, status, last chapter name.
- Keep progress notification behavior if it does not duplicate WorkManager notification behavior.

Risk:

- The fork implementation updates cached data from inside `DownloadViewModel` directly, while upstream moved refresh work into `DownloadFileWorkManager` and `BookDownloader2.getOldDataReadingProgress`.
- Directly cherry-picking fork refresh code would regress upstream's current background-work structure.

### 8. Faster Cached Novel Info / Offline Result Page

Observed changes:

- `ResultViewModel.initState(apiName, url)` first searches `RESULT_BOOKMARK` and `HISTORY_FOLDER`.
- If cached data exists, it posts a `StreamResponse` immediately, then fetches fresh provider data in the background.
- If network fails and cached data already displayed, it suppresses the error.
- `cc278ad` changes cached responses to use an empty chapter list instead of placeholder chapters.

Keep: yes.

How to keep it:

- Keep instant cached metadata display.
- Keep the `cc278ad` behavior: do not create fake chapter entries with empty URLs.
- Preserve total chapter count separately via cached metadata so progress can still display without fake chapters.
- Make the offline/cached state explicit enough that the UI does not enable chapter actions that require real chapter URLs.

Risk:

- Fake placeholder chapters were removed because they caused cache/download problems. Do not reintroduce them.
- Empty chapter lists can make total progress display as zero unless the result UI reads cached totals separately.

### 9. Cached Chapter Counting

Observed changes:

- Adds `BookDownloader2Helper.countCachedChapters(context, author, name, apiName, total)`.
- Counts local chapter files even if `DOWNLOAD_TOTAL` was never set.
- Treats a full local epub as fully cached.
- `ResultViewModel.insertZeroData()` falls back to this count when `downloadInfo()` returns null.

Keep: yes.

How to keep it:

- Port as a focused helper or fold into upstream's current `downloadInfo()` carefully.
- Preserve the behavior where streaming-cached chapters still count toward download progress.
- Keep the empty chapter URL guard from `cc278ad`.

Risk:

- Upstream changed PDF/import handling in `downloadInfo()`. The fork helper must not remove PDF behavior or imported epub behavior.

### 10. Result Page Read/Download Progress

Observed changes:

- Adds separate "Downloaded" and "Read" labels.
- Adds `result_read_progress_text` and `result_read_progress_bar`.
- Colors downloaded progress with `colorPrimary` and read progress with text color.
- Updates z-order so the shorter bar remains visible.
- Adds `ResultViewModel.readCount`.

Keep: yes, but reconcile with upstream.

How to keep it:

- Port labels and separate read/download progress to upstream's current `fragment_result.xml`.
- Use a shared read-progress helper so the result page and library badges use the same count.
- Ensure progress text uses cached total when network data is unavailable.

Risk:

- Upstream already has two progress bars: `result_download_progress_bar` and `result_download_progress_bar_not_downloaded`. The fork renamed/reinterpreted the second bar. Porting needs a deliberate mapping, not a simple XML replacement.

### 11. Import Buttons In Search And Library Toolbar

Observed changes:

- Adds plus icon buttons in search and library search bars.
- Calls `MainActivity.importEpub()`.

Keep: yes.

How to keep it:

- Upstream already has import affordances in the downloads page adapter footer and PDF import support.
- Keep toolbar import buttons because they are a more direct import affordance.
- If kept, wire them through `DownloadViewModel.importEpub()` where possible rather than calling `MainActivity` directly from multiple fragments.

Risk:

- Toolbar space is tighter after upstream changes; search page also has provider filter controls.

### 12. Result Page UI Details

Observed changes:

- Result cards become transparent over the poster background.
- Stats card wrapper is removed.
- Tag chip style changes from filled to transparent outlined.
- Chapter rows get zebra striping.
- Result action buttons use `ResultOutlineButton`.

Keep: yes, as part of fork default look.

How to keep it:

- Keep the spacing/layout improvements; user confirmed these are important.
- Apply through named styles and attrs rather than direct layout rewrites where possible.
- Keep upstream's new `TextButton`/`DropdownButton` styles; do not overwrite them with the fork's `ResultOutlineButton`.

Risk:

- `styles.xml` conflict currently occurs because upstream added `TextButton` and `DropdownButton` in the same area where the fork added `ResultOutlineButton`.

### 13. Search/Home Unread Badges

Observed changes:

- `HomeChildItemAdapter2` builds cached indexes from bookmarks/history and displays unread badges on home/search result cards when matching by API+URL or API+normalized-name.

Keep: no as a search/home surface. Keep the underlying unread badge feature in the Library page only.

How to keep it:

- Do not port the search/home adapter badge code.
- Keep the shared read-progress helper so Library badges, Library filters, and result-page read progress agree.
- If search/home badges are reconsidered later, port to upstream's `HomeChildItemAdapter` and avoid storage scans inside adapter code.

Risk:

- Search/home cards are not necessarily library items; badges may be stale or confusing if matching by normalized title.
- The adapter-level key scan is heavier than it needs to be.

### 14. README Fork Notes

Observed changes:

- Adds a fork-specific feature summary and screenshots.
- Removes upstream local screenshot block.

Keep: optional.

How to keep it:

- Rewrite after the port is working only if a fork-specific README is useful.
- Avoid documenting implementation details that might change during the rebase.

Risk:

- Current README has typo `Screenshoots`.

### 15. IDE Files

Observed changes:

- Adds `.idea/AndroidProjectSystem.xml`, `.idea/deviceManager.xml`, `.idea/markdown.xml`, and `.idea/runConfigurations.xml`.

Drop: yes.

Reason:

- Upstream moved toward ignoring/removing `.idea` project files.
- These are local IDE state and should not be part of the replay.

## Proposed Replay Order

1. Start a new branch from `upstream/master`.
2. Add audit/spec docs only.
3. Port fork theme resources and make the fork look the default.
4. Port `ReadType.TRASH`.
5. Add shared library/read-progress helper APIs.
6. Port cached result/offline result-page loading.
7. Port cached chapter counting and empty URL guard.
8. Port result-page read/download progress UI.
9. Port library unread/not-started/completed filters and unread sort.
10. Port Library unread badges, reconciling with upstream's existing reading-progress labels.
11. Port last-read blurred library background.
12. Port toolbar import buttons.
13. Update README after implementation is verified, if still useful.

## Implementation Assumptions

- Use `Not started` as the UI label for the zero-read filter.
- Combine enabled filters with AND. If `Unread only` and `Not started` are both enabled, only not-started entries appear.
- Keep toolbar import buttons on both Search and Library, matching the fork behavior.
- Leave README close to upstream unless the implementation changes make a fork-specific README clearly useful.

## Initial Keep/Drop Summary

Keep:

- Fork visual style as default.
- Blurred library/result visual treatment.
- Trash category.
- Library unread/not-started/completed filters.
- Sort by unread chapters.
- Unread/read progress surfaces.
- Cached result/offline result loading.
- Cached chapter counting.
- Empty chapter URL guard.
- Category refresh behavior, merged into upstream's current worker refresh path.

Keep but reshape:

- Library unread badges.
- Toolbar import buttons.
- README feature notes.

Drop:

- `.idea` files.
- Merge commit.
- Placeholder fake chapter cache behavior from pre-`cc278ad`.
- Search/home unread badge surface.
- Literal replay of old layout diffs where upstream now has equivalent or newer structure.
