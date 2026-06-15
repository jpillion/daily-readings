# Sprint 00G — Book/Chapter picker grid redesign (owner UI tweak)

## Goal outcome
Owner asked: "The chapter [book] picker — abbreviate the names, and put it in a grid, so you
can see all the books on a single screen. Chapters, follow the same grid (left to right, top
to bottom), though some books might need to scroll — like Psalms." **Met** (pending the
device-pass look check): the in-app reader's picker is now a dense grid of abbreviated book
labels with all 66 books reachable on one screen, and the chapter step uses the same grid.

## Current capability
- Reader → tap "Choose a book and chapter" → step 1 is a 5-column grid of abbreviated book
  names (OT then NT, with full-width testament headers). Tap a book → step 2, a 5-column grid
  of chapter numbers (Psalms 150 scrolls within the sheet; Genesis 50, Obadiah 1 render fine).
- Picker-only change. Reader loading, navigation, persistence all unaffected.

## Decisions & rationale
- **Reused `Book.displayAbbrev`** (the single live-verified catalog abbreviation from S9) for
  book labels — no second abbreviation table (one-catalog discipline, D-S9-1).
- **OT/NT grouping survived** the single-screen goal via full-width section headers
  (`GridItemSpan(maxLineSpan)`) inside ONE grid — not nested scrollers. A single 66-cell grid
  was the acceptable fallback; the headers cost only ~2 rows so grouping was kept.
- `GridCells.Fixed(5)` for both steps (consistent idiom). Book cells `surfaceVariant`,
  chapter cells `secondaryContainer` (matches prior chapter styling); rounded 8dp.
- **A11y:** cell label is abbreviated/numeric but the cell speaks the full name —
  `contentDescription` on the cell ("Genesis"; "Genesis chapter 3" via new string
  `picker_chapter_cd` = `%1$s chapter %2$d`), inner label `Text` `clearAndSetSemantics{}` so
  TalkBack speaks the full name once, not "Gen Genesis". ≥48dp via `heightIn(min=48.dp)`.

## State of the codebase
- `app/src/main/kotlin/.../bible/ui/picker/BookChapterPicker.kt` — rewritten: `BookGrid`
  (was `BookList`/`BookRow`) + `BookCell`; `ChapterGrid`/`ChapterCell` re-styled to match.
  `PICKER_COLUMNS = 5`. Same testTags preserved (`picker-book-list`, `picker-book-N`,
  `picker-chapter-grid`, `picker-chapter-N`, `picker-chapter-back`, testament headers).
- `BookChapterPickerSheet.kt` — unchanged (still the 0.9f-height modal sheet).
- `app/src/main/res/values/strings.xml` — added `picker_chapter_cd`.
- Tests: `app/src/test/.../bible/ui/picker/BookChapterPickerTest.kt` rewritten for the grid
  (adds book-cell full-name-speech + chapter-cell speech assertions; step-1 test scrolls the
  grid to reach NT/Matthew since off-screen cells aren't composed in Lazy grids).
  `ui/AccessibilityGateTest.kt` UNCHANGED and green (tags preserved).

## Verification
- Full pipeline green: `./gradlew spotlessCheck lintDebug assembleDebug testDebugUnitTest
  koverXmlReportAppDebug koverVerifyAppDebug`.
- 497 tests (net +2). The three data/Room gates intact: plan = 7,
  BibleTextVerificationTest = 18, BibleDatabaseRoomOpenTest = 5. Kover 94.9% on domain/data.

## Device-pass items (NOT JVM-provable)
- All 66 books actually fit one screen at default font (5 cols × ~16 rows incl. 2 headers).
- Abbreviation legibility / cell density on glass; dark mode.
- Psalms-150 chapter grid scrolls cleanly inside the sheet.

## Carryover & next goal
No carryover from this tweak. Next: unchanged — V3.0 release cut (owner device pass +
string/tone sign-offs from sprint-00E), then version bump + tag-to-Play. V2.x release prep
remains separately queued. Picker strings (`picker_chapter_cd`) fold into the pending V3
string tone sign-off.

next: release-cut (owner-scheduled; no new engineering sprint queued)
