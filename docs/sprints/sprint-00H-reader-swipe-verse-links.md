# V3 Sprint H — Reader chapter-swipe + per-verse external links + schedule cleanup

> **EM:** Morgan · **Status:** DONE (uncommitted; main session verifies + commits) ·
> **Date:** 2026-06-15 · **Next:** `sprint-0021-v2x-release-prep`

## Goal outcome — MET

Five owner-requested UI changes landed as working software:
1. Reader navigation is continuous chapter SWIPE (Prev/Next buttons gone; space reclaimed for text).
2. The Schedule whole-day button is gone (checkboxes are the only mark affordance).
3. The Schedule "All readings done" badge is gone (checkboxes are the only completion cue).
4. Tapping any verse in the reader opens that exact book+chapter+verse in the user's external Bible app.
5. (Verse-link verification) Every verse URL form was live-checked before shipping.

## Current capability (working software)

- Open the reader and **swipe left/right between chapters** — continuously across the whole Bible:
  past Genesis 50 lands on Exodus 1, back past chapter 1 of a book lands on the last chapter of the
  previous one; the ends are bounded at Genesis 1 and Revelation 22. Each page is the full
  verse-keyed reader (markup, superscriptions, opens at the top).
- **Tap any verse** → it opens at the exact (book, chapter, verse) in the user's chosen external
  Bible app/site (BLB / Bible Gateway / YouVersion in a Custom Tab; MySword via its intent with a
  BLB-verse fallback). If the user reads IN_APP, a tap-out falls back to BLB at that verse.
- The **Schedule day screen** is just the three reading cards with checkboxes — no whole-day button,
  no completion badge. Marking is per-reading; the widget still has `MarkWholeDayUseCase`.

## Tickets (administrative record)

| Ticket | Owner | Status |
|---|---|---|
| H1 `GlobalChapterIndex` (index↔book/chapter, pinned vs `ChapterNavigator`) | Diego | ✅ |
| H2 chapter `HorizontalPager` (ReaderScreen/Route/VM), remove ChapterNavBar | Priya | ✅ |
| H3 remove whole-day button (DayContent/Screen) | Sam | ✅ |
| H4 remove "All readings done" badge | Sam | ✅ |
| H5 `ProviderUrlBuilder.buildVerse` (4 providers, offline-pinned) | Diego | ✅ |
| H6 `OpenVerseUseCase` (stored provider, IN_APP→BLB fallback) | Diego | ✅ |
| H7 verse tap in ReaderScreen (clickable, role/cd, ≥48dp, launch) | Priya | ✅ |
| H8 live verse-URL verification + record | Riley | ✅ |
| H9 a11y gate + mutation-verify | Riley | ✅ |
| H10 docs | Morgan | ✅ |

517 tests (net +19), all three data/Room gates UNTOUCHED (plan 7, BibleTextVerificationTest 18,
BibleDatabaseRoomOpenTest 5), full pipeline green, Kover 95.2% on domain/data, 5 mutations killed.

## Decisions & rationale

- **D-H-1 — `GlobalChapterIndex`** is the single chapter-identity model: a contiguous `0..1188`
  index over canon order (`flatMap` of each book's `1..chapterCount`). Adjacency is pinned
  field-by-field against the existing mutation-pinned `ChapterNavigator` at every index, so the two
  can never drift; the navigator is retained as the adjacency oracle (not deleted).
- **D-H-2 — bounded pager, not a large window.** Unlike the day pager (open-ended dates → ±10,000
  window), the Bible is finite (1189 chapters), so the pager page count == `TOTAL_CHAPTERS` and
  page == global index. No center-page offset, no clamping; the Gen-1/Rev-22 bounds fall out (the
  pager can't scroll past page 0 / last). Deliberate deviation from the day-pager idiom, recorded.
- **D-H-3 — verse coordinate = canonical decode.** The external link uses `VerseId.book/chapter/
  verse(canonicalId)` (the true location), NOT `nativeLabel` (a display string; D-V3-4 forbids
  trusting it for identity). Mutation-pinned (VM test taps a non-1 verse).
- **D-H-4 — IN_APP verse-tap fallback = BLB.** A verse tap-out has no external target when the user
  reads in-app; rather than no-op the explicit "open elsewhere" gesture, fall back to the always-web
  default (BLB) at that verse. The persisted IN_APP choice is never rewritten. Pinned.
- **D-H-5 — verse URL forms** (live-verified 2026-06-15): BLB `/kjv/{abbrev}/{ch}/{verse}/`,
  Bible Gateway `?search={Book} {ch}:{verse}&version=KJV`, YouVersion `{usfm}.{ch}.{verse}.KJV`,
  MySword `r={order}.{ch}.{verse}`. Reuses existing token columns — no new catalog. Verse 0
  (superscription) clamps to verse 1.
- **D-H-7 — portion handoff lands on the first chapter.** With a chapter-per-page pager, a Schedule
  reading tap (IN_APP) opens the pager on the portion's first chapter; the user swipes through the
  (contiguous) portion. This supersedes the prior multi-block `openPortion` render. `ReaderViewModel`
  no longer needs `GetPortionTextUseCase` (that use case + its own tests remain).

## State of the codebase

New:
- `bible/ui/reader/GlobalChapterIndex.kt` — `TOTAL_CHAPTERS`, `chapterAt(index)`, `indexOf(book,ch)`.
- `domain/OpenVerseUseCase.kt` — verse tap → `ReadingDestination` (IN_APP→BLB, D-H-4).
- `ProviderUrlBuilder.buildVerse(provider, reference, verse)` — verse-level URLs (D-H-5).

Changed:
- `bible/ui/reader/ReaderScreen.kt` — now hosts a `HorizontalPager`; `stateForPage:
  @Composable (Int) -> ReaderUiState`, `onVerseTapped(page, verseId)`, `onRetry(page)`. Tappable
  verses (`Role.Button`, ≥48dp, spoken "Open <Book> <ch>:<verse>. <text>"). `ChapterNavBar` removed.
  New tag `reader-pager`.
- `bible/ui/reader/ReaderViewModel.kt` — per-page `uiStateForPage(page)` (cache), `initialPage`,
  `onPageSettled`, `retry(page)`, `onVerseTapped(verseId)` + `openDestinationEvents` channel.
  SavedStateHandle key `reader_page`. `openChapter/openPortion/uiState/lastReadChapter` removed.
- `bible/ui/reader/ReaderRoute.kt` — owns `rememberPagerState`, picker-jump (animate to
  `indexOf(book,ch)`), and collects `openDestinationEvents` → `launchReadingDestination`.
- `ui/day/DayContent.kt` — whole-day button + complete badge removed; `onMarkWholeDay` param gone.
- `ui/day/DayReadingsScreen.kt` — `onMarkWholeDay` param + wiring removed.

Orphaned strings (unused in main, left in place — removable debt): `mark_whole_day_done`,
`unmark_whole_day`, `day_progress`. (`day_complete` is still used by the date picker — keep.)

Tests: `GlobalChapterIndexTest` (6), `OpenVerseUseCaseTest` (7), `ProviderUrlBuilderTest` (+5 verse
pins), `ReaderViewModelTest` (rewritten, 6), `ReaderScreenTest` (rewritten + verse-tap/a11y, 13),
`AccessibilityGateTest` (reader section rewritten to verse targets), `DayContentTest` /
`DayReadingsPagerScreenTest` (whole-day/badge pins → absence pins).

## Carryover & next goal

Next: **`sprint-0021-v2x-release-prep`** — version bump past 1.3.5/10305, consolidated device pass
(S9 + S12–S20 + the new Sprint-H items), string tone sign-offs, closed-track rollout.

Queued / deferred (protected OUT of this sprint): tier-2 install-detected providers (Logos/Olive
Tree, BACKLOG); colorblind strip palette; removing the orphaned whole-day strings.

## Open questions & risks

- Chapter-swipe feel, the reclaimed reader layout, and verse-tap on glass (target accuracy, correct
  external app + verse, MySword in-app) are NOT JVM-provable — top of the device-pass list.
- The verse-tap spoken label ("Open <Book> <ch>:<verse>. <text>") is an inline computed string
  (not a resource) — flagged for owner tone sign-off.
- MySword verse links resolve only in-app — the live 66-book pass remains the owner's device pass
  (S15 precedent); the numeric form is catalog-derived and offline-pinned.

## Next sprint

`next: sprint-0021-v2x-release-prep`
