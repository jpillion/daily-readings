# Sprint I — Reading-portion view (Phase 1: multi-chapter combined page)

> **EM:** Morgan · **Status:** DONE (uncommitted; main session verifies + commits) ·
> **Date:** 2026-06-15 · **Next:** `sprint-0021-v2x-release-prep`

## Goal outcome — MET

**Tapping a multi-chapter reading on the Schedule opens it as ONE combined page in the in-app
reader, and swiping out and back is consistent.** A reading like "James 4–5" opens both chapters
together on a single atomic page; swiping right goes to the next single chapter, swiping back
returns to the combined page (never the portion's last chapter alone). Tapping the Bible tab always
resets to plain single-chapter Browse.

**Phase 1 ONLY** per `docs/features/reader-portion-view.md`. Phase 2 (Psalm 119 verse ranges — a
trusted-plan-data change) is a SEPARATE track and was deliberately left untouched: the reading plan
JSON and its schema were not modified.

## Current capability (working software)

- **Tap any reading on the Schedule (when "Open readings in" = in-app)** → the reader opens on a
  single combined page showing the whole portion (chapter header + verses, next chapter header +
  verses) for all 319 multi-chapter readings, AND the 776 single-chapter ones. The two-book Jun 19/
  Dec 19 portion (2 John + 3 John) renders as one page.
- **Swipe right** off the portion → the next single chapter after the portion's last; **swipe back
  left** → the same combined portion page. The portion is one atomic page at a fixed index, so this
  is always consistent.
- **Tap the Bible tab** → the reader is plain single-chapter Browse at the last-read chapter (it does
  NOT preserve a Reading context). The book/chapter picker also lands you in Browse.
- **Tap any verse** inside the combined page → it still opens that exact book:chapter:verse in the
  user's external Bible app (Sprint H, unchanged — each verse keeps its canonical id).

## Tickets (administrative record)

| Ticket | Owner | Status |
|---|---|---|
| I1 `ReadingPagerIndex` — pure portion-anchored index (span-collapse, flanks, bounds, contiguity) | Diego | ✅ |
| I2 `ReaderViewModel` two contexts (Browse/Reading), combined-page render, tab-reset, per-context cache | Diego | ✅ |
| I3 `ReaderRoute`/`ReaderScreen` context-driven pager (page count + initial page rebuilt per context) | Priya | ✅ |
| I4 Tab-reset-to-Browse: `ReaderHandoff.requestBrowse` + `RootViewModel` + `RootScaffold` Bible-tab wiring | Diego/Priya | ✅ |
| I5 Tests: `ReadingPagerIndexTest`, VM context/consistency/reset tests, handoff + root tests; mutation-verify | Riley | ✅ |
| I6 a11y gate + full pipeline + docs | Riley/Morgan | ✅ |

568 tests (net +21), all three data/Room gates UNTOUCHED (plan 7, BibleTextVerificationTest 18,
BibleDatabaseRoomOpenTest 5), full pipeline green, Kover 95.3% on domain/data, a11y gate 7/7,
5 load-bearing mutations killed.

## Decisions & rationale

- **D-I-1 — two reader contexts, separate index spaces.** `ReaderContext` (sealed) = `Browse`
  (the Sprint-H `GlobalChapterIndex`, 1189 single-chapter pages, UNCHANGED) or
  `Reading(portion, ReadingPagerIndex)`. They never share a page-index space, which is exactly what
  makes the consistency guarantee clean: the same chapters are "one combined page" in Reading and
  "individual pages" in Browse without contradiction. The route rebuilds the `PagerState` on a
  context switch via `key(...)`.
  - `ReadingPagerIndex(portion)`: the portion's contiguous global span `[first…last]` collapses to
    ONE page at `portionPage == first`. Pages `0..first-1` map 1:1 to globals; pages `> first` map
    to global `page + collapsedSpan` (`collapsedSpan = last - first`), so the collapsed chapters
    never reappear as separate pages. `pageCount = TOTAL_CHAPTERS - collapsedSpan`; Gen-1/Rev-22
    bounds hold for free. A contiguity `require` asserts the refs ARE the consecutive globals
    `first..last` (every real plan portion satisfies this, incl. the two-book one). Single-chapter
    portions = `collapsedSpan 0` = Browse-equivalent.
- **D-I-2 — tab nav = Browse (OQ-A, owner-resolved).** Tapping the Bible tab always means
  single-chapter Browse at the last-read chapter; only a Schedule reading tap enters Reading; the
  picker forces Browse. Implemented as two mutually-exclusive signals on the shared
  `@ActivityRetainedScoped` `ReaderHandoff`: `request(portion)` (→ Reading, the existing D-D-1 path)
  vs the new `requestBrowse()` (→ Browse). A reading request clears any stale browse request, and
  `requestBrowse()` no-ops if a reading open is already pending — so the handoff path (which fires
  `request` then a tab switch) is never reset by its own switch. The nav bar's Bible-tab click goes
  through a new `RootViewModel.onBibleTabSelected()` (so the activity-retained-scoped handoff is
  reachable from a composable). `resetToBrowse()` is idempotent (no-op if already Browsing) so
  re-selecting the tab doesn't reset an in-progress Browse.
- **D-I-3 — combined page revives `GetPortionTextUseCase`.** The portion page is
  `Content(blocks = getPortionText(portion).blocks)` — the pre-Sprint-H multi-block render, alive
  again ONLY in the Reading context's portion page. `ReaderViewModel` re-injects `GetPortionTextUseCase`
  (Sprint B, previously unused by the reader after D-H-7). The `ReaderScreen` already renders
  multi-block `Content`, so no screen-render change was needed. Title: same-book multi-chapter →
  "James 4–5" (en dash); two-book → "2 John 1; 3 John 1".
- **D-I-4 — last-read tracks the underlying single chapter.** In-session last-read (`reader_page` in
  `SavedStateHandle`) always stores a GLOBAL chapter index, never the collapsed portion page. So
  reading a flank chapter inside a Reading session and then tabbing back to Browse lands on that
  chapter; the portion page itself never overwrites last-read. The per-page state cache is cleared
  on every context switch (the page→content mapping is context-specific).
- **Supersedes D-H-7** (reading-tap lands on the first chapter only) for the Reading context.

## State of the codebase

New:
- `bible/ui/reader/ReadingPagerIndex.kt` — the pure portion-anchored index (span-collapse + flanks +
  bounds + contiguity guard). The load-bearing model; JVM-unit-tested + mutation-pinned.
- `bible/ui/reader/ReaderContext.kt` — sealed `Browse | Reading(portion, index)` + a `pageCount`
  extension.
- `ui/navigation/RootViewModel.kt` — `@HiltViewModel`; `onBibleTabSelected()` → `ReaderHandoff.requestBrowse()`.

Changed:
- `bible/ui/reader/ReaderViewModel.kt` — now injects `GetPortionTextUseCase`; holds `context:
  StateFlow<ReaderContext>` + `initialPage`; `uiStateForPage(page)` renders the combined portion
  page (Reading + `isPortionPage`) or a single chapter (Browse / flanks); `resetToBrowse()`,
  `enterReading(portion)` (handoff `pending` collector), and a `browseRequested` collector;
  per-context page cache cleared on `switchContext`. `onPageSettled`/`uiStateForPage` record the
  underlying GLOBAL chapter as last-read (D-I-4).
- `bible/ui/reader/ReaderRoute.kt` — reads `viewModel.context`; builds the pager for the active
  context's `pageCount` + `initialPage`, wrapped in `key(keyForContext(...))` so a context switch
  discards the old `PagerState`; the picker calls `resetToBrowse()` before jumping.
- `ui/navigation/ReaderHandoff.kt` — added the `browseRequested` StateFlow + `requestBrowse()` /
  `consumeBrowseRequest()` (single-shot), with the reading-vs-browse precedence rules.
- `ui/navigation/AppNavHost.kt` — `RootScaffold` injects `RootViewModel`; the Bible-tab `onClick`
  calls `onBibleTabSelected()` before `switchTab(Graph.BIBLE)`.

Tests:
- `ReadingPagerIndexTest` (12) — span-collapse, both flanks (mid-book James 1–2 + end-of-book
  James 4–5 → 1 Peter 1), bounds, start/end portions, two-book portion, three-chapter portion,
  rejects.
- `ReaderViewModelTest` (rewritten, 12) — Browse pages, multi-chapter reading → combined page,
  swipe-out-and-back consistency, end-of-book flank, two-book combined render, tab-reset-to-Browse,
  reset-restores-last-single-chapter, verse-tap-out from the second chapter of the combined page.
- `ReaderHandoffTest` (4) — single-shot consume, browse-request single-shot, reading-supersedes-browse,
  browse-doesn't-override-pending-reading.
- `RootViewModelTest` (1) — Bible-tab raises the browse request.

Convention note: `hiltViewModel()` is imported from `androidx.hilt.navigation.compose` (deprecated
but the whole codebase uses it consistently; the build tolerates the warning).

## Test data gotcha (recorded so the next session doesn't repeat it)

The spec's canonical example "James 4–5" is the END of James (James has exactly 5 chapters), so the
chapter after that portion is **1 Peter 1**, NOT "James 6". For same-book flank assertions use a
mid-book multi-chapter portion (James 1–2 → James 3). Both forms are pinned.

## Carryover & next goal

Next: **`sprint-0021-v2x-release-prep`** (the long-queued release track) — version bump past
1.3.5/10305 (recommend 1.4.0/10400 per D-S9-3 now that V3 + this portion view have landed), the
consolidated owner device pass, string tone sign-offs, closed-track rollout.

Queued / deferred (protected OUT of this sprint):
- **Phase 2 — Psalm 119 verse ranges** (`docs/features/reader-portion-view.md` §6): extend the plan
  schema with optional `verseStart?/verseEnd?`, populate Psalm 119's four days (Mar 9–12) under the
  Sprint-1 trusted-data gate discipline, and filter the portion page to the in-range verses. A
  trusted-DATA task, not UI — affects exactly one reading. Keep it a separate sprint.
- Tier-2 install-detected providers (Logos/Olive Tree, BACKLOG); colorblind strip palette; removing
  the orphaned whole-day strings (`mark_whole_day_done`/`unmark_whole_day`/`day_progress`).

## Open questions & risks

- **Device-pass items (NOT JVM-provable):** the combined-page scroll feel for 3–5-chapter portions
  (the 2 four-chapter and 1 five-chapter readings); the swipe-out-and-back feel on glass; the
  tab-reset-to-Browse behaviour (confirm a reading tap → combined page, then a Bible-tab tap →
  single chapter at the last-read chapter).
- The `ReaderRoute`'s `key(...)`-based `PagerState` rebuild on context switch is a Compose
  interaction not provable on JVM — verify on glass that switching Browse ↔ Reading lands on the
  right page with no flash/stale page.
- No new strings this sprint (titles are computed: "James 4–5", "2 John 1; 3 John 1"); the en-dash
  multi-chapter title format is flagged for owner tone sign-off if he wants a different separator.

## Next sprint

`next: sprint-0021-v2x-release-prep`
