package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpillion.dailyreadingplanner.bible.domain.GetChapterUseCase
import com.jpillion.dailyreadingplanner.bible.domain.GetPortionTextUseCase
import com.jpillion.dailyreadingplanner.bible.domain.model.BibleVersion
import com.jpillion.dailyreadingplanner.bible.domain.model.ChapterContent
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText
import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.data.reference.Book
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.domain.OpenVerseUseCase
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestination
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.ui.day.ReadingFormatter
import com.jpillion.dailyreadingplanner.ui.navigation.ReaderHandoff
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * I2 (D-I-1 / D-I-2) — drives the two-context reader.
 *
 * **Browse** (the Bible tab; the picker): one chapter per page over [GlobalChapterIndex.TOTAL_CHAPTERS]
 * pages, page == global chapter index — the Sprint-H single-chapter swipe, unchanged.
 *
 * **Reading** (a Schedule reading tap, IN_APP): a portion-anchored pager over a [ReadingPagerIndex],
 * where the whole multi-chapter portion collapses to ONE atomic page rendered via
 * [GetPortionTextUseCase] (revives the multi-block render for this context only — D-I-3). Swiping out
 * to the next single chapter and back returns to the SAME combined portion page (the consistency
 * guarantee falls out of the portion being one fixed page index in [ReadingPagerIndex]).
 *
 * The two contexts are SEPARATE pager configurations ([context]); they never share a page-index
 * space, so the same chapters are "one combined page" in Reading and "individual pages" in Browse
 * without contradiction. The route rebuilds the pager (page count + initial page) whenever [context]
 * changes; the per-page state cache is cleared on every context switch (the page→content mapping is
 * context-specific).
 *
 * In-session last-read (D-V3-13, D-I-4): the last-shown *single* global chapter is held in
 * [SavedStateHandle], so a Browse session restores its chapter and re-entering Browse from a Reading
 * session lands on a sensible chapter. The collapsed portion page never overwrites it.
 *
 * **Tab-reset (OQ-A, D-I-2):** tapping the Bible tab — or jumping via the picker — forces Browse at
 * the last-read chapter ([resetToBrowse]); only a Schedule reading tap enters Reading.
 */
@HiltViewModel
class ReaderViewModel
    @Inject
    constructor(
        private val getChapter: GetChapterUseCase,
        private val getPortionText: GetPortionTextUseCase,
        private val openVerse: OpenVerseUseCase,
        private val savedStateHandle: SavedStateHandle,
        private val readerHandoff: ReaderHandoff,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private var pageStates = mutableMapOf<Int, MutableStateFlow<ReaderUiState>>()

        /**
         * Sprint K (reader footer hint) — the user's chosen external Bible app, surfaced reactively
         * so the reader's footer hint ("Tap a verse to open it on <app>") reflects the current
         * setting and updates live when the user changes it in Settings. This is the SAME setting the
         * verse-tap-out resolves at tap time (via [OpenVerseUseCase]); here it is display copy only.
         * It is shown REGARDLESS of the reading destination — most useful when reading IN_APP (the
         * read-here / study-there bridge). Seeds with the default so the hint never flickers wrong.
         */
        val externalApp: StateFlow<ExternalBibleApp> =
            settingsRepository.externalBibleApp
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExternalBibleApp.DEFAULT)

        /** The active reader context (Browse or Reading); the route reads this to build the pager. */
        private val _context = MutableStateFlow<ReaderContext>(ReaderContext.Browse)
        val context: StateFlow<ReaderContext> = _context.asStateFlow()

        /** The pager page the reader should open on for the CURRENT [context]. */
        private val _initialPage = MutableStateFlow(restoredBrowsePage())
        val initialPage: StateFlow<Int> = _initialPage.asStateFlow()

        /**
         * D-N-3 — the text versions on offer and the selected one, for the reader top-bar version
         * control. With three versions ([BibleVersion]) this now takes D-N-3's **dropdown** branch,
         * built in Sprint 00N and unexercised until now.
         *
         * **Sprint 00R supersedes D-N-1's "sourced from the asset's `translation` table"** for this
         * control: NKJV and NASB are served from the proxy and are not in the bundled artifact, so
         * the app's version catalog is necessarily code. `BibleTextSource.translations()` remains
         * the asset-integrity seam (pinned by `RoomBibleTextSourceTranslationsTest`); it is simply
         * no longer what populates the selector.
         *
         * Written ONLY by the settings collector in [init], so the label can never show a version
         * that is not actually stored.
         */
        private val _versionState = MutableStateFlow(ReaderVersionState())
        val versionState: StateFlow<ReaderVersionState> = _versionState.asStateFlow()

        /**
         * The reader's multi-select state (Q2 Ticket 1). Hoisted here (unlike the ephemeral verse
         * menu, D-Q-2) because it must survive recomposition, drive the contextual action bar, and
         * feed the copy action. The reducer itself is the pure [VerseSelection] (Q1).
         *
         * **MUST be declared ABOVE [init] — this ordering is load-bearing, not cosmetic.** Kotlin
         * initializes properties and init blocks in declaration order. [init] collects
         * `readerHandoff.pending`, and a Schedule reading tap populates that handoff BEFORE this
         * ViewModel is constructed, so the collect fires *during* construction → [enterReading] →
         * [switchContext], which writes `_selection.value`. Declared below [init] (where Q2
         * originally put it) the field is still null at that moment and every reading tap dies with
         * `NullPointerException: … StateFlowImpl.setValue(Object) on a null object reference`.
         *
         * That was the 1.7.0 production P0. It reproduced ONLY in the R8-minified release build —
         * debug dispatched the collect late enough to miss the window — so the JVM suite and a debug
         * device pass both showed green. Pinned by `ReaderViewModelHandoffInitTest`; if another field
         * is ever written from [init] or [switchContext], it belongs above [init] too.
         */
        private val _selection = MutableStateFlow(VerseSelection.NONE)
        val selection: StateFlow<VerseSelection> = _selection.asStateFlow()

        init {
            // VD-T5 (D-D-1) / I2 (D-I-1): a Schedule reading tap hands off a portion to read in-app —
            // enter the Reading context anchored on that portion and open on the combined portion
            // page. consume() is single-shot, so a config change or a manual Bible-tab visit does not
            // re-enter Reading.
            viewModelScope.launch {
                readerHandoff.pending.filterNotNull().collect { portion ->
                    readerHandoff.consume()
                    enterReading(portion)
                }
            }
            // I2 (D-I-2, OQ-A): the nav bar tapped the Bible tab — reset to single-chapter Browse.
            viewModelScope.launch {
                readerHandoff.browseRequested.filter { it }.collect {
                    readerHandoff.consumeBrowseRequest()
                    resetToBrowse()
                }
            }
            // Sprint 00R step 4 — the version control now offers every version the app can show
            // (bundled KJV + the online ones), driven by the stored selection so the top bar and the
            // text can never disagree. Collected rather than read once: [selectVersion] persists and
            // lets this collector be the single writer of _versionState.
            viewModelScope.launch {
                settingsRepository.selectedBibleVersion.collect { version ->
                    val previous = _versionState.value.selected
                    _versionState.value =
                        ReaderVersionState(
                            available = BibleVersion.entries.map { it.toTranslation() },
                            selected = version.toTranslation(),
                        )
                    // Only reload on an actual change — the first emission is the initial load,
                    // which uiStateForPage is already performing.
                    if (previous != null && previous.code != version.code) reloadCachedPages()
                }
            }
        }

        /**
         * The [StateFlow] for one pager page in the CURRENT context. The first request triggers the
         * load; later swipes back to the page reuse the cached flow. Records a single-chapter page as
         * the in-session last-read (the collapsed portion page does not — D-I-4).
         */
        fun uiStateForPage(page: Int): StateFlow<ReaderUiState> =
            pageStates.getOrPut(page) {
                MutableStateFlow<ReaderUiState>(ReaderUiState.Loading).also { flow ->
                    recordLastReadFor(page)
                    loadPage(page, flow)
                }
            }

        private fun loadPage(
            page: Int,
            flow: MutableStateFlow<ReaderUiState>,
        ) {
            flow.value = ReaderUiState.Loading
            val ctx = _context.value
            viewModelScope.launch {
                flow.value =
                    try {
                        if (ctx is ReaderContext.Reading && ctx.index.isPortionPage(page)) {
                            // The atomic portion page: render the WHOLE portion as ordered blocks.
                            val portionContent = getPortionText(ctx.portion)
                            ReaderUiState.Content(
                                blocks = portionContent.blocks,
                                title = portionTitle(portionContent.blocks),
                                // D-OT-2: any block that fell back to KJV banners the whole page —
                                // the user must never be shown a mixed page with no warning.
                                degraded = portionContent.blocks.any { it.degraded },
                            )
                        } else {
                            val (book, chapter) = chapterForPage(ctx, page)
                            val content = getChapter(book, chapter)
                            ReaderUiState.Content(
                                blocks = listOf(content),
                                title = "${ReadingFormatter.singularizeBookName(
                                    book.canonicalName,
                                    singleChapter = true,
                                )} $chapter",
                                degraded = content.degraded,
                            )
                        }
                    } catch (e: Exception) {
                        ReaderUiState.Error()
                    }
            }
        }

        private fun chapterForPage(
            ctx: ReaderContext,
            page: Int,
        ): Pair<Book, Int> =
            when (ctx) {
                ReaderContext.Browse -> GlobalChapterIndex.chapterAt(page)
                is ReaderContext.Reading -> ctx.index.chapterAt(page)
            }

        private val openDestinationChannel = Channel<ReadingDestination>(Channel.BUFFERED)

        /** One-shot resolved verse-tap destinations (BACKLOG #5, D-23-1); collect exactly once from the UI. */
        val openDestinationEvents: Flow<ReadingDestination> = openDestinationChannel.receiveAsFlow()

        /**
         * H7 — open [verseId] in the user's external Bible app at the exact (book, chapter, verse)
         * decoded from it (D-H-3, canonical coords, NOT the display label). Works UNCHANGED inside
         * the combined portion page — each verse keeps its canonical id. The launch side-effect
         * runs in the UI.
         *
         * **Resolution is D-23-1 (Sprint K), not the superseded H4 shim.** A verse tap is always
         * external by definition — the reader IS the in-app destination — so [OpenVerseUseCase]
         * resolves from the chosen [ExternalBibleApp] ALONE, independent of the
         * [com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode]: an in-app-mode
         * user keeps their remembered external app rather than being mapped to BLB. MySword
         * resolves to the app intent with a BLB-verse fallback; the persisted choice is never
         * rewritten.
         *
         * **Q2 renamed this from `onVerseTapped`, body unchanged.** A tap no longer opens anything
         * directly — it opens the [VerseActionMenu], and only its "Open in `<app>`" item lands here.
         * The old name would now be a lie; [OpenVerseUseCase] itself is deliberately untouched.
         */
        fun openVerseExternally(verseId: Long) {
            val bookNo = VerseId.book(verseId)
            val chapter = VerseId.chapter(verseId)
            val verse = VerseId.verse(verseId)
            val book = BookCatalog.books.firstOrNull { it.order == bookNo } ?: return
            viewModelScope.launch {
                openDestinationChannel.send(openVerse(Reference(book, chapter), verse))
            }
        }

        /**
         * Sprint 00R step 4 (supersedes the D-N-3 no-op placeholder) — switch the reader's text
         * version and persist it, so the choice survives leaving the screen.
         *
         * Deliberately does NOT write `_versionState` itself: the settings collector in [init] is
         * the single writer, so the visible label can only ever show a version that is actually
         * stored. That also means the reload happens in exactly one place.
         */
        fun selectVersion(translation: com.jpillion.dailyreadingplanner.bible.domain.model.BibleTranslation) {
            viewModelScope.launch {
                settingsRepository.setSelectedBibleVersion(BibleVersion.fromCode(translation.code))
            }
        }

        /**
         * Re-runs every page the user has already visited so a version switch is visible immediately
         * rather than only on the next swipe. The page *flows* are kept (the pager holds them), only
         * their content is reloaded — replacing the map would detach the pager's live subscriptions.
         */
        private fun reloadCachedPages() {
            pageStates.forEach { (page, flow) -> loadPage(page, flow) }
        }

        /** Retry a failed page load in the current context. */
        fun retry(page: Int) {
            val flow = pageStates[page] ?: return
            loadPage(page, flow)
        }

        /**
         * Records the displayed page as the in-session last-read (called as the pager settles), and
         * — **D-Q-3** — applies the page change to the verse selection: a selection lives only on
         * the page it was made on, so swiping away clears it rather than leaving an invisible
         * off-screen selection alive. Idempotent for the page it is already on.
         */
        fun onPageSettled(page: Int) {
            recordLastReadFor(page)
            _selection.value = _selection.value.onPageChanged(page)
        }

        // --- Q2: verse selection (Ticket 1) and copy (Ticket 2 / D-Q-4). ---

        /** Long-press (or the menu's "Select verses"): enter selection with exactly this verse. */
        fun onVerseLongPressed(
            page: Int,
            verseId: Long,
        ) {
            _selection.value = VerseSelection.start(page, verseId)
        }

        /** A tap while selecting: add or remove [verseId]; removing the last one exits the mode. */
        fun onVerseSelectionToggled(
            page: Int,
            verseId: Long,
        ) {
            _selection.value = _selection.value.toggle(page, verseId)
        }

        /** Explicit exit — the action bar's X and system back. */
        fun onSelectionCleared() {
            _selection.value = _selection.value.cleared()
        }

        private val copyChannel = Channel<String>(Channel.BUFFERED)

        /**
         * **D-Q-4** — one-shot clipboard payloads. The ViewModel BUILDS the string (it owns the
         * page-content cache and the translation code); the route PERFORMS the side effect
         * (`copyVerseTextToClipboard`), mirroring the [openDestinationEvents] →
         * `launchReadingDestination` idiom exactly. Collect exactly once from the UI.
         */
        val copyEvents: Flow<String> = copyChannel.receiveAsFlow()

        /**
         * The action bar's Copy: formats the whole current selection, then **exits selection mode**.
         *
         * **P-Q-1 (owner-decided 2026-07-25): Copy IS an exit.** The sprint shipped the
         * spec-literal reading — the spec listed only X, system back and deselecting the last verse
         * as exits — which left the selection standing after a copy. The owner chose the
         * note-taking / mail idiom instead: Copy completes the task and returns you to reading, one
         * tap fewer. The other three exits are unchanged.
         *
         * Order matters: the copy is emitted from the selection captured BEFORE clearing, so the
         * clipboard content is unaffected by the exit (mutation-pinned).
         */
        fun copySelection() {
            val current = _selection.value
            val page = current.page ?: return
            emitCopy(page, current.verseIds)
            _selection.value = current.cleared()
        }

        /**
         * The menu's "Copy this verse" — the SAME path as a one-verse selection, so the two can
         * never produce different strings (a spec requirement).
         */
        fun copyVerse(
            page: Int,
            verseId: Long,
        ) {
            emitCopy(page, setOf(verseId))
        }

        private fun emitCopy(
            page: Int,
            verseIds: Set<Long>,
        ) {
            val text = VerseClipboardFormatter.format(versesOn(page, verseIds), servedCodeOn(page, verseIds))
            if (text.isEmpty()) return
            viewModelScope.launch { copyChannel.send(text) }
        }

        /**
         * The loaded [VerseText]s for [verseIds] on [page]. Reads the already-rendered page content
         * (a portion page's several blocks flatten into one verse list), so a copy never re-queries
         * the database and can only ever copy what the user is actually looking at.
         */
        private fun versesOn(
            page: Int,
            verseIds: Set<Long>,
        ): List<VerseText> =
            (pageStates[page]?.value as? ReaderUiState.Content)
                ?.blocks
                ?.flatMap { it.verses }
                ?.filter { it.canonicalId in verseIds }
                .orEmpty()

        /**
         * The translation code for the citation ("KJV"), read from the version the copied verses
         * were **actually served in** — NOT the user's selection.
         *
         * Sprint 00R: those two can differ. If the user picked NKJV and the fetch degraded to
         * bundled KJV (D-OT-2 case 3), citing "(NKJV)" over KJV text would put a false attribution
         * on someone's clipboard, which is exactly the silent-swap failure the banner exists to
         * prevent. Reads per block, so a mixed page cites each copy correctly.
         */
        private fun servedCodeOn(
            page: Int,
            verseIds: Set<Long>,
        ): String? =
            (pageStates[page]?.value as? ReaderUiState.Content)
                ?.blocks
                ?.firstOrNull { block -> block.verses.any { it.canonicalId in verseIds } }
                ?.servedVersion
                ?.code

        /**
         * D-I-2 (OQ-A) — entering the Bible tab (or a picker jump) forces the Browse context at the
         * last-read single chapter. Idempotent: a no-op if already Browsing (so re-selecting the tab
         * doesn't reset an in-progress Browse). The picker-jump page is handled by the route after.
         */
        fun resetToBrowse() {
            if (_context.value is ReaderContext.Browse) return
            switchContext(ReaderContext.Browse, restoredBrowsePage())
        }

        /**
         * Enters the Reading context for a tapped Schedule reading.
         *
         * **D-SEG-7 — never fall back to Genesis 1.** [ReadingPagerIndex] requires the portion's refs
         * to be a contiguous ascending global-chapter run. If that invariant is ever violated (the
         * Ticket-1 bug: Chronological 07/25 = `Isaiah 37, Isaiah 38, Isaiah 39, Psalms 76`, where
         * Psalms precedes Isaiah in canon order), its `init` throws. The previous fallback was a
         * silent `return`, which left the reader in the Browse context at its DEFAULT page — Genesis
         * 1, the wrong end of the Bible, with no hint that anything failed.
         *
         * So a failed index now degrades to the portion's **first ref's chapter** in Browse: always
         * the right neighbourhood, and the reader is one swipe from the rest of the passage. Only if
         * even that resolution fails (an unresolvable ref) do we do nothing — no path may crash.
         *
         * Belt-and-braces only: segmentation (D-SEG-1/2) guarantees every card is a contiguous run,
         * gate-proven across all bundled plans, so the fallback is unreachable for today's data.
         */
        private fun enterReading(portion: Portion) {
            val index = runCatching { ReadingPagerIndex(portion) }.getOrNull()
            if (index != null) {
                switchContext(ReaderContext.Reading(portion, index), index.portionPage)
                return
            }
            val firstRefPage =
                runCatching {
                    val first = portion.refs.first()
                    GlobalChapterIndex.indexOf(first.book, first.chapter)
                }.getOrNull() ?: return
            switchContext(ReaderContext.Browse, firstRefPage)
        }

        private fun switchContext(
            ctx: ReaderContext,
            initialPage: Int,
        ) {
            // The page→content mapping is context-specific; a stale cache would serve the wrong
            // chapter for a reused page index, so drop it on every switch.
            pageStates = mutableMapOf()
            // Q2 (D-Q-3, taken to its conclusion): a Browse <-> Reading switch is a whole new
            // page-index space, so a selection made in the old one cannot survive it — the same
            // page number means a different chapter.
            _selection.value = VerseSelection.NONE
            _context.value = ctx
            _initialPage.value = initialPage
        }

        /** Records [page] as the in-session last-read GLOBAL chapter — only for single-chapter pages. */
        private fun recordLastReadFor(page: Int) {
            val global = globalChapterFor(_context.value, page) ?: return
            savedStateHandle[KEY_PAGE] = global
        }

        /** The underlying global chapter index for [page], or null if it is the collapsed portion page. */
        private fun globalChapterFor(
            ctx: ReaderContext,
            page: Int,
        ): Int? =
            when (ctx) {
                ReaderContext.Browse -> page.takeIf { it in 0 until GlobalChapterIndex.TOTAL_CHAPTERS }
                is ReaderContext.Reading ->
                    if (ctx.index.isPortionPage(page)) {
                        null
                    } else {
                        val (book, chapter) = runCatching { ctx.index.chapterAt(page) }.getOrNull() ?: return null
                        GlobalChapterIndex.indexOf(book, chapter)
                    }
            }

        private fun restoredBrowsePage(): Int {
            val page = savedStateHandle.get<Int>(KEY_PAGE)
            if (page != null && page in 0 until GlobalChapterIndex.TOTAL_CHAPTERS) return page
            return GENESIS_1_PAGE
        }

        private fun portionTitle(blocks: List<ChapterContent>): String {
            if (blocks.isEmpty()) return ""
            val first = blocks.first()
            val last = blocks.last()
            return if (first.bookNo == last.bookNo) {
                if (first.chapter == last.chapter) {
                    // One chapter (incl. a verse-windowed Psalm 119 day): singular -> "Psalm 119".
                    "${ReadingFormatter.singularizeBookName(first.bookName, singleChapter = true)} ${first.chapter}"
                } else {
                    // Spans chapters within one book: plural -> "Psalms 1–2" (en dash).
                    "${ReadingFormatter.singularizeBookName(
                        first.bookName,
                        singleChapter = false,
                    )} ${first.chapter}–${last.chapter}"
                }
            } else {
                // Two-book portion (Jun 19 / Dec 19): "2 John 1; 3 John 1". Each block is one chapter.
                blocks.joinToString("; ") {
                    "${ReadingFormatter.singularizeBookName(it.bookName, singleChapter = true)} ${it.chapter}"
                }
            }
        }

        private companion object {
            const val KEY_PAGE = "reader_page"
            val GENESIS_1_PAGE = GlobalChapterIndex.indexOf(BookCatalog.requireByName("Genesis"), 1)
        }
    }
