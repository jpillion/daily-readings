# Sprint 0017 — Year-strip progress visualization (owner-approved design)

**Status: GOAL MET.** Closed 2026-06-11. (Owner-redirected again from `v2-release-prep`,
which rolls forward to Sprint 18. CLI sub-agent dispatch still down — EM executed tickets
directly under per-ticket verification discipline. Working tree handed over **uncommitted**;
version deliberately untouched at **1.3.0/10300** — these changes need a bump to ship. No
tags touched.)

## Goal outcome

**Met (JVM-provable parts).** The stats panel's plain progress bars are replaced by year
strips: a reader can now see, at a glance, the *shape* of their year per stream — green
where a stream was read, red where a past tracked day wasn't, quiet neutral everywhere
else — and the year section shows the three strips stacked as a compact heat-strip. The
visual texture (~1dp segments) and today-tick visibility need the device pass.

## Current capability

- **Per-stream strips:** under each "n of 365" stream row, a 10dp-tall strip with one
  contiguous segment per calendar day of the current year. Green = that stream marked
  that day (including pre-tracking-start marked days — earned-green parity); red = past,
  post-start, unmarked; neutral = Feb 29, pre-start, today-in-grace, future. Single
  `Canvas`, no per-day composables, no touch interaction.
- **Stacked year view:** the year section keeps its "% · n of 1,095 readings" stat text;
  its bar is now the three stream strips stacked (3 × 6dp rows, 2dp gaps).
- **Today tick:** a thin (1dp) onSurface vertical marker at today's position on every
  strip — the eye's anchor (owner-approved).
- **Spoken summaries (color-only surface):** each stream strip speaks
  "Law & History: 120 read, 3 not read, 242 upcoming"; the stacked view speaks one
  combined "All streams: …" line.
- Streak toggle (D-S15-5) unaffected: strips show regardless; only streak rows hide.
- Verified: **321/321 tests** (net +17; 7-test Sprint 1 gate untouched at 7/7), full
  pipeline green (`spotlessCheck lintDebug assembleDebug testDebugUnitTest
  koverXmlReportAppDebug koverVerifyAppDebug`), **Kover 96.2%** on domain/data (floor
  70%). **5 mutations killed, restored in place:** (1) grace — classify against
  `today.plusDays(1)` → exactly the today-grace test fails; (2) pre-start gate — null
  trackingStart → exactly the pre-start test fails; (3) **re-derivation drift** — replace
  the classifier call with a local truth table lacking the resolver → exactly the Feb-29
  test fails (the literal R-STREAK-5 guard); (4) `List(dayCount - 1)` → day-365 +
  whole-year-size tests fail; (5) `index + 1` date shift → day-1 boundary test fails
  (plus collateral, as expected for a whole-array shift).

## Decisions & rationale (do not relitigate)

- **D-S17-1 — Red is allowed on the stats surface, strips only (AMENDS the S11
  "no red in stats" pin; owner decision).** Red segments are information, not commentary.
  The no-guilt **copy** ban stays fully in force and was deliberately *extended*: no
  missed-day wording in any on-screen text OR contentDescription
  (`StatsContentTest.assertNoGuiltCopy` now scans both).
- **D-S17-2 — Per-stream day state THROUGH the shared classifier, never re-derived.**
  `GetYearStripsUseCase` classifies each (day, stream) by feeding
  `DayCompletionClassifier` a synthetic count — `STREAM_COUNT` when that one stream is
  marked, 0 otherwise — so the pinned truth-table ORDER (Feb 29 → marked/COMPLETE →
  start gate → past/MISSED → NONE) is inherited verbatim. Mapping: COMPLETE→READ,
  MISSED→MISSED, NONE→NEUTRAL. Mutation 3 pins this against local re-derivation.
- **D-S17-3 — A11y wording is "not read", never "missed" (decided with Priya).** The
  copy ban is kept absolute including speech: "not read" is equally informative with no
  guilt vocabulary, and it keeps the ban-list test simple and total. "Upcoming" counts
  all neutral days — pre-start neutrals are folded in (deliberate simplification; the
  numbers stay honest because pre-start days are also excluded from "not read").
- **D-S17-4 — Segments = `lengthOfYear()`; palette = picker tokens behind one seam.**
  366 segments in a leap year (Feb 29 renders neutral, visually indistinguishable —
  calendar-honest, no index gymnastics). Colors reuse `IndicatorGreen/RedLight/Dark`
  (the S8 picker-dot tokens) via `StripColors` + `defaultStripColors()` — strips and
  picker dots cannot disagree visually, and the future colorblind-friendly option
  (owner-deferred, queued below) is a single-provider swap.

## User-visible strings — OWNER TONE SIGN-OFF NEEDED (PRD M8)

| id | string | where |
|---|---|---|
| `strip_stream_summary` | "%1$s: %2$d read, %3$d not read, %4$d upcoming" | spoken summary, each stream strip |
| `strip_year_summary` | "All streams: %1$d read, %2$d not read, %3$d upcoming" | spoken summary, stacked year view |

(Spoken-only — nothing visual changed in copy. S12–S16 string tables still await
sign-off — see those handoffs.)

## State of the codebase

- **Domain:** `domain/model/YearStrips.kt` (`StripDayState` READ/MISSED/NEUTRAL +
  `YearStrips(year, todayIndex, dayStates)`), `domain/GetYearStripsUseCase.kt`
  (classifier-fed derivation, live via Room invalidation + DataStore, same contract as
  `GetReadingStatsUseCase`).
- **Data:** `ReadingProgressDao.marksInRange()` (+ `DayStreamMark` row type — raw
  (day, stream) rows, ≤ ~1,098/year), `ProgressRepository(.Impl).streamMarks(start, end):
  Flow<Map<Stream, Set<LocalDate>>>`. Query only — **NO Room schema change**
  (`app/schemas/` untouched at schema 1).
- **UI:** `ui/stats/YearStrip.kt` (the Canvas strip + `StripColors`/`defaultStripColors()`
  — THE palette seam; no semantics of its own, callers attach the summary),
  `ui/stats/StatsContent.kt` (`StatsPanelUiState` gained `strips`; both
  `LinearProgressIndicator`s gone; tags `strip-stream-1..3`, `strip-year` — unmerged-tree
  tags inside the merged stat groups, summaries via `clearAndSetSemantics`),
  `ui/day/DayReadingsViewModel.kt` (statsPanel combine now includes `getYearStrips()`),
  `ui/day/DayReadingsScreen.kt` (passes strips through). Strings: 2 new (table above).
- **Tests (+17 net):** `GetYearStripsUseCaseTest` (13 — the rule suite incl. boundaries,
  leap year, other-year isolation, liveness), `ProgressRepositoryTest` (+1 streamMarks
  range/grouping), `StatsContentTest` (+2: strips-replace-bars w/ ProgressBarRangeInfo
  absence pin; exact spoken summaries), `DayReadingsViewModelTest` (+1 strips in panel).
  Adapted: pager screen test + `AccessibilityGateTest` fixtures (strips param).
- Layout cost: bars (4dp) → strips (10dp / 3×6dp+gaps) ≈ +36dp inside the
  45%-capped, internally-scrollable S15 panel — the S16 one-screen fit is structurally
  unaffected (readings column untouched), but eyeball it on the P7P.

## Carryover & next goal

- **Next goal (Sprint 18): V2.x release prep** — version bump past 1.3.0/10300, the
  consolidated device pass (S9 + S12 + S13 + S14 + S15 MySword gate + S16 one-screen fit
  + **S17: strip look at ~1dp/segment, today-tick visibility, dark-mode palette,
  TalkBack summaries**), string tone sign-offs (S12–S17), closed-track rollout via the
  tag-to-Play pipeline.
- **Queued candidate (NEW, owner-deferred):** colorblind-friendly strip palette option —
  structurally one seam (`defaultStripColors()`); would need a Settings row + persisted
  choice.
- **Queued/deferred (unchanged from S16):** second-wave web providers; Logos/Olive Tree
  behind install detection; toggle-from-widget; Psalm 119 verse-ranges; API 26–28 scrim
  check; TIME_SET/TIMEZONE_CHANGED receiver; deprecation housekeeping (incl.
  `createComposeRule` v2 migration); public requests channel; Priya's S16 polish notes.
- **Scope protected out this sprint:** strip touch interaction (tap-a-day navigation);
  per-day tooltips/legend; strips on the widget; colorblind palette (queued above); any
  stats-panel layout rework beyond swapping the bars.

## Next sprint

`next: sprint-0018-v2-release-prep`

## Open questions & risks

- Strip rendering is JVM-pinned for state/semantics only — segment legibility, the 1dp
  today tick, and dark-mode reds/greens need the device pass (top of the S17 items).
- "Upcoming" in the spoken summaries folds in pre-start neutral days (D-S17-3 note) —
  flag at tone sign-off if the owner wants finer wording.
- Owner tone sign-off pending on the 2 S17 strings plus the S12–S16 tables.
- Standing debt unchanged: Robolectric pinned `@Config(sdk = [34])`; JVM-untested
  MainActivity hooks; CLI agent credentials expired (owner: `claude /login`); CI
  unexercised until commit; D-S15-1 MySword numeric form still device-unverified.
