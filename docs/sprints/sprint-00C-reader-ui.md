# V3 Sprint C — The reader UI

> **EM:** Morgan · **Status:** DONE (uncommitted; main session verifies + commits) ·
> **Date:** 2026-06-14 · **Next:** `sprint-00D-nav-integration`

## Goal outcome — MET

A working in-app KJV reader the user can open and read, **fully offline**, over the Sprint B
spine. The user browses any book/chapter, sees faithfully-formatted verses (italic added words,
de-emphasized native verse numbers, superscriptions as unnumbered headings), navigates
chapter-to-chapter across book boundaries, and the whole-portion path (incl. the two-book
Jun 19/Dec 19 portion) renders top-to-bottom. No networking added.

## Current capability (working software)

- Open the reader (temporary top-bar action on the readings screen → `Routes.READER`) and read
  Genesis 1 immediately; pick any (book, chapter) from a two-step bottom-sheet picker (66 books
  grouped OT/NT → chapter grid) and read it.
- Each verse renders as flowing prose with a de-emphasized native verse number; `<a>` translator-
  added words are italic; a Psalm superscription appears as an unnumbered italic heading before
  verse 1. TalkBack reads the plain (stripped) text of every verse and title, never markup tags.
- Step chapters with visible Prev/Next — the walk crosses book boundaries (Genesis 50 → Exodus 1)
  and stops cleanly at Genesis 1 / Revelation 22.
- The reader survives configuration change / process death within a session (last (book, chapter)
  restored from `SavedStateHandle`).
- A whole reading-plan `Portion` (incl. the two-book portion) renders as ordered chapter blocks
  via `ReaderViewModel.openPortion` — exercised by tests; the tap that feeds it is Sprint D.

## Tickets (administrative record)

| Ticket | Status | Note |
|---|---|---|
| VC-T1 `ReaderUiState` + `ReaderViewModel` (chapter/portion load, SavedStateHandle) | ✅ | Loading/Content/Error; in-session last-read |
| VC-T2 `VerseRenderer` (markup → AnnotatedString) | ✅ | `<a>`→italic (mutation-pinned); `<w>`/`<l/>` recognized |
| VC-T3 `ReaderScreen` (verse-id-keyed LazyColumn, headers, superscription heading) | ✅ | D-V3-12/D-V3-7/D-V3-4 all mutation-pinned |
| VC-T4 `BookChapterPicker` + `BookChapterPickerSheet` (two-step, OT/NT groups, chapter grid) | ✅ | ≥48dp rows/cells |
| VC-T5 `ChapterNavigator` + Prev/Next | ✅ | crosses book bounds; ends null→disabled |
| VC-T6 `ReaderRoute` (stateful) + temporary `Routes.READER` entry | ✅ | comment-marked SPRINT C TEMPORARY |
| VC-T8 `ReaderAudioSlot` empty audio seam (D-V3-14) | ✅ | bottomBar renders nothing; activeVerseId always null |
| VC-T7 `AccessibilityGateTest` extension | ✅ | reader Prev/Next + picker cells ≥48dp; heading + stripped speech |

453/453 tests (net +32; **both data gates untouched — Sprint-1 plan gate = 7,
`BibleTextVerificationTest` = 18**), full standing pipeline green, Kover 96.2% on domain/data
(≥70% floor), **4 load-bearing mutations killed** (added-word italic span, superscription
isTitle branch, nativeLabel-not-derived, keyed-by-canonicalId), each restored in place.

## Decisions & rationale (this sprint)

- **Temporary reader entry, not D's nav.** The reader is reached via a top-bar book-list action
  (tag `open-reader-dev`) that pushes a plain `Routes.READER` in the EXISTING single `NavHost`.
  This makes the reader reachable + test-drivable now WITHOUT pulling forward D's bottom-nav
  restructure (D-V3-16) or the reading-tap handoff. Every touch point is comment-marked
  `SPRINT C TEMPORARY`. `DayReadingsRoute`/`DayReadingsPagerScreen` gained an `onOpenReader: () -> Unit = {}`
  param (defaulted, so all existing call sites/tests are unchanged).
- **Picker = stateless content + sheet wrapper.** `BookChapterPicker` (stateless, two-step state
  internal) is wrapped by `BookChapterPickerSheet` (`ModalBottomSheet`). The stateless content is
  what the tests + the accessibility gate drive (the project's established idiom); the sheet is
  the thin host. Books grouped OT/NT from `Book.order <= 39` (no second table). Chapter grid is a
  `LazyVerticalGrid(GridCells.Adaptive(56dp))` sized to `book.chapterCount`.
- **Picker action icon = `Icons.AutoMirrored.Filled.List`** and the temp reader action reuses it.
  `MenuBook` is NOT in the frozen `material-icons-core` artifact (it lives in icons-extended,
  which the project does not depend on); `List` is in core and reads as "book/chapter list". If
  Priya wants a book glyph at polish, it becomes a custom drawable (the `ic_stats.xml` pattern) —
  noted for E/owner, no code blocker.
- **`ReaderViewModel` opens Genesis 1 by default** when there is no in-session last-read, so the
  reader always lands on real content. In-session last-read is `SavedStateHandle` only (D-V3-13);
  durable cross-session is explicitly V3.x and is NEVER written to any DB here.
- **`VerseRenderer` is a small hand parser** over the closed tag set (no XML lib — mirrors
  `MarkupStripper`’s discipline). It recognizes all three tags from day one even though V3.0
  emits only `<a>`, so enabling `<w>`/`<l/>` later is artifact-only. Output text never drops or
  duplicates inner words (`render(m).text == MarkupStripper.strip(m)` for added-word input).

## State of the codebase

New code (under `app/src/main/kotlin/.../bible/ui/`):
- `reader/ReaderUiState.kt` — sealed `Loading | Content(blocks, title, activeVerseId) | Error(canRetry)`.
- `reader/ReaderViewModel.kt` — `@HiltViewModel`; `uiState: StateFlow<ReaderUiState>`;
  `openChapter(book, chapter)`, `openPortion(portion)`, `lastReadChapter(): Pair<Int,Int>?`.
  SavedStateHandle keys `reader_book_no`, `reader_chapter`.
- `reader/ReaderScreen.kt` — stateless; verse-id-keyed `LazyColumn`; `ChapterHeader` (`heading()`),
  `VerseItem` (title branch vs numbered branch; `contentDescription = strip(markup)`),
  `ChapterNavBar` (Prev/Next, tags `reader-prev-chapter`/`reader-next-chapter`). Top-bar action
  `reader-open-picker`. bottomBar = `ReaderAudioSlot`.
- `reader/VerseRenderer.kt` — `object VerseRenderer.render(markup): AnnotatedString` (pure).
- `reader/ReaderAudioSlot.kt` — empty composable (D-V3-14).
- `reader/ChapterNavigator.kt` — `previous`/`next` over `BookCatalog`, null at the ends.
- `reader/ReaderRoute.kt` — stateful; owns the VM, picker visibility, browse cursor (drives
  Prev/Next enablement via `ChapterNavigator`), and the `BookChapterPickerSheet`.
- `picker/BookChapterPicker.kt` (stateless two-step) + `picker/BookChapterPickerSheet.kt` (M3 sheet).

Edited: `ui/navigation/AppNavHost.kt` (+`Routes.READER`, temp composable, `onOpenReader` wiring),
`ui/day/DayReadingsScreen.kt` (+`onOpenReader` param on Route + PagerScreen, temp `open-reader-dev`
top-bar action), `app/src/main/res/values/strings.xml` (reader/picker strings), the extended
`ui/AccessibilityGateTest.kt`.

Tests (new): `bible/ui/reader/VerseRendererTest` (6), `ReaderViewModelTest` (5, over
`FakeBibleTextSource`), `ReaderScreenTest` (9, Robolectric `@Config(sdk=[34])`),
`ChapterNavigatorTest` (6), `bible/ui/picker/BookChapterPickerTest` (4, Robolectric); plus 2 new
methods in `AccessibilityGateTest`.

Test tags introduced (Sprint D / device pass reference): `open-reader-dev`, `reader-title`,
`reader-open-picker`, `reader-loading`, `reader-error`, `reader-retry`, `reader-list`,
`reader-header-{bookNo}-{ch}`, `reader-verse-{canonicalId}`, `reader-title-{canonicalId}`,
`reader-prev-chapter`, `reader-next-chapter`, `book-chapter-picker-sheet`, `picker-book-list`,
`picker-testament-ot`/`-nt`, `picker-book-{order}`, `picker-chapter-grid`,
`picker-chapter-{n}`, `picker-chapter-title`, `picker-chapter-back`.

## What Sprint D needs to know

- **Replace the temporary entry, don't duplicate it.** Delete the `SPRINT C TEMPORARY` blocks:
  the `Routes.READER` push, the `open-reader-dev` action, and the `onOpenReader` params — fold
  the reader into the Bible graph under `RootScaffold` (D-V3-16). The reader itself
  (`ReaderRoute`/`ReaderScreen`/VM) is nav-agnostic and moves as-is.
- **The tap-handoff target already exists.** `ReaderViewModel.openPortion(portion: Portion)`
  renders a whole portion (multi-block, incl. the two-book portion). VD-T5 routes
  `ReadingDestination.InApp(portion)` into the Bible graph and calls it. `ReaderScreen` already
  renders multi-block content; no screen change needed for the portion path.
- The nav-regression suite (VD-T3) must assert the reader + picker stay reachable after the
  restructure — the tags above are stable handles.

## Strings for owner tone sign-off (S-C)

| Key | Current value |
|---|---|
| `reader_open` | "Open the Bible reader" |
| `reader_pick_chapter` | "Choose a book and chapter" |
| `reader_prev_chapter` | "Previous chapter" |
| `reader_next_chapter` | "Next chapter" |
| `reader_load_failed` | "Couldn't load this chapter" |
| `reader_retry` | "Retry" |
| `picker_testament_ot` | "Old Testament" |
| `picker_testament_nt` | "New Testament" |
| `picker_back_to_books` | "All books" |

OQ-3 (nav labels "Bible"/"Schedule") is a Sprint D/E string call — unaffected here.

## Device-pass items (collected for E; NOT JVM/Robolectric-provable)

- Reading feel (U15): the verse-keyed list reads as calm prose, not a numbered list, at real
  density; de-emphasized verse numbers sit right.
- Markup look: italic added words and the italic superscription heading read faithfully in
  light + dark (M-V3-2; titled Ps 3/51, untitled Ps 1, Genesis 1, the Jun 19 two-book portion).
- Picker on-glass: bottom sheet height, OT/NT grouping legibility, chapter-grid tap accuracy.
- Reader at large font (`fontScale` inherited): no clipping; Prev/Next still reachable.
- Instant load / no spinner on a real device read (U13).
- Picker action glyph: confirm `List` reads acceptably, or swap to a custom book drawable.

## Open questions & risks / tech debt

- The temp `Routes.READER` push touches `AppNavHost`, which remains JVM-untested for push/pop
  (the long-standing Sprint-6 debt) — D's Robolectric nav-regression suite (D-V3-17) retires
  this; do NOT add more nav surface before that suite lands.
- No `INTERNET`, zero new runtime deps; one already-present pattern reused (HorizontalPager is
  used by the day pager — the reader uses Prev/Next + a plain LazyColumn, no pager needed for
  V3.0 single-chapter browse; chapter swipe via pager is a contained V3.x add if owner wants it).
- `hiltViewModel` import carries the same pre-existing deprecation warning as `DayReadingsScreen`
  (matched intentionally for consistency; not new debt).

## Next sprint

`next: sprint-00D-nav-integration`
