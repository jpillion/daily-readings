# Sprint 0011 — Streaks & stats

**Status: GOAL MET.** Closed 2026-06-11. (Unattended overnight run, sprint 2 of 4; working
tree handed over uncommitted by request. CLI sub-agent dispatch still failing on expired
credentials — EM executed tickets directly under per-ticket verification discipline.)

## Goal outcome

**Met.** A reader can open a read-only Stats screen from the readings top bar and see —
soberly, in plain M3 typography — their current streak (today in grace), longest streak
(all-time, crossing year boundaries), year progress (n of 1,095, full-year denominator),
and per-stream progress (n of 365 each), all derived live from stored marks and provably
consistent with the date-picker indicators. No gamification, no guilt: no red, no
missed-day copy anywhere on the surface (pinned by test).

## Current capability

- **Stats screen (PRD §13.1, FR-15…18):** bar-chart action in the readings top bar (tag
  `open-stats`) pushes `Routes.STATS`. Exactly four stat groups; read-only (only action is
  back, tag `stats-back`). Honors theme + in-app text size automatically (it's ordinary
  themed Compose). Fully offline.
- **Streak engine:** `GetReadingStatsUseCase` — current/longest streak + year and
  per-stream totals from nothing but stored marks (R-STREAK-6), re-emitting on any mark or
  tracking-start change (FR-17). All R-STREAK rules implemented and mutation-verified.
- Verified: **214/214 tests** (31 new; the 7-test Sprint 1 plan gate untouched), **4
  mutations killed** — each by exactly its intended test, in-place restores: (1) walk
  NONE-neutrality → reset (kills Feb-29 skip), (2) classifier today-grace `isBefore` →
  `!isAfter`, (3) classifier pre-start gate dropped, (4) walk floored at Jan 1 of the
  current year (kills year-boundary + all-time-longest). Full pipeline green; **Kover
  96.2%** on domain/data (floor 70%). No Room schema change (`app/schemas/` untouched at
  schema 1). Version stays 1.0.0/10000.

## Decisions & rationale (do not relitigate)

- **D-S11-1 — R-STREAK-5 by extraction.** The S10 truth table moved verbatim from a private
  method into injectable `domain/DayCompletionClassifier`; `GetMonthCompletionUseCase` and
  `GetReadingStatsUseCase` both inject it. One predicate object — picker dots and stats
  cannot drift. The 183 pre-existing tests passed with assertions byte-identical (only
  test-construction lines adapted).
- **D-S11-2 — Streak walk = one forward pass.** From the earliest stored mark to today:
  COMPLETE → run+1 (longest = max), MISSED → run = 0, NONE → neutral skip. Current streak =
  the run still open at today. Rules fall out of the classifier: Feb 29 = NONE (R-STREAK-2),
  incomplete today = NONE (R-STREAK-3 grace), real-date stepping crosses years (R-STREAK-4),
  pre-start incomplete = NONE while pre-start COMPLETE still extends (R-STREAK-5,
  earned-green parity with the picker). Flooring at the earliest mark is exact (no earlier
  day can be COMPLETE). Future-dated marks never enter the walk (walk ends at today) but DO
  count in year/stream totals.
- **D-S11-3 — Pure queries, no schema change, no caching.** Two new grouped DAO queries:
  `allReadCounts()` (all-time per-day counts — the walk's input; ~1,095 rows/year, trivially
  cheap) and `streamCountsInRange()` (per-stream year counts; the year total is their sum).
  Stats derive on collection; Room invalidation provides liveness. Revisit caching only if a
  device pass ever shows jank (it won't at this data size).
- **D-S11-4 — Percent rounds DOWN.** `n * 100 / 1095` integer division: 1,094/1,095 shows
  99%, 100% only at completion. Honest numbers per §13.0.
- **D-S11-5 — Custom stats icon.** `material-icons-core` has no chart glyph; the extended
  artifact is unjustified for one icon → hand-authored `res/drawable/ic_stats.xml` (three
  bars), tinted normally.

## User-visible strings — OWNER TONE SIGN-OFF NEEDED (PRD M8)

New strings introduced this sprint (`app/src/main/res/values/strings.xml`):

| id | string |
|---|---|
| `stats_title` | "Stats" |
| `open_stats` (a11y) | "Open stats" |
| `stats_current_streak` | "Current streak" |
| `stats_longest_streak` | "Longest streak" |
| `stats_streak_days` (plural) | "%d day" / "%d days" |
| `stats_year_section` | "This year" |
| `stats_percent` | "%1$d%%" (e.g. "40%") |
| `stats_year_count` | "%1$s of %2$s readings" (e.g. "438 of 1,095 readings") |
| `stats_stream_section` | "By stream" |
| `stats_stream_count` | "%1$d of %2$d" (e.g. "150 of 365") |

Stream row titles reuse the existing `ReadingFormatter.streamTitle` ("Law & History",
"Psalms & Prophecy", "New Testament"). No celebration/guilt vocabulary anywhere;
`StatsScreenTest.assertNoGuiltCopy` pins the ban list (miss/broke/fail/behind).

## State of the codebase

- **Domain:** `domain/DayCompletionClassifier.kt` (THE predicate, extracted),
  `domain/GetReadingStatsUseCase.kt` (walk + totals), `domain/model/ReadingStats.kt`
  (incl. `YEAR_TOTAL_READINGS = 1095`, `STREAM_TOTAL_DAYS = 365`).
- **Data:** `ReadingProgressDao.allReadCounts()/streamCountsInRange()` (+ `StreamReadCount`
  row type); `ProgressRepository(.Impl)` `allReadCounts()/streamCounts()`.
- **UI:** `ui/stats/` (Route/Screen/ViewModel — stateless screen over nullable
  `ReadingStats`, null = first frame renders nothing); `Routes.STATS` in `AppNavHost`;
  `DayReadingsRoute/PagerScreen` gained `onOpenStats`; `res/drawable/ic_stats.xml`.
  Tags: `open-stats`, `stats-back`, `stats-current-streak`, `stats-longest-streak`,
  `stats-year`, `stats-stream-1..3` (each group is one merged semantics node).
- **Tests (31 new):** `GetReadingStatsUseCaseTest` (17 — the rule suite),
  `ProgressRepositoryTest` (+3 Room query tests), `StatsViewModelTest` (2),
  `StatsScreenTest` (8 incl. sober-copy ban + floor rounding + singular "1 day"),
  `DayReadingsPagerScreenTest` (+1 wiring), `AccessibilityGateTest` (+1 stats block).
- Convention note: when testing flows built by `combine` over multiple fake-backed sources,
  do NOT assert exact emission sequences (conflation makes counts nondeterministic) —
  assert re-collection (`first()`) or a single awaited transition.

## Carryover & next goal

- **Next goal (Sprint 12): V2 reading reminders** — PRD §13.2: optional daily notification
  at a user-chosen time (off by default; time set in Settings — owner constraint verbatim in
  §13.0). Notification copy will need the same owner tone sign-off as this sprint's strings.
  Relevant prior art: `WidgetRefresher` seam (D9 — we deliberately have NO midnight alarm
  yet), `InitializeTrackingStartUseCase` hook pattern in `MainActivity.onCreate`. Expect:
  notification permission (API 33+), exact-vs-inexact alarm decision (API 26 floor),
  reschedule-on-boot receiver, Settings time row.
- **Queued/deferred (unchanged):** V1 ship (owner: Sprint 9 checklists + tracking-start
  device smoke + now the stats-screen device pass), toggle-from-widget, Psalm 119
  verse-ranges, bible-app-links feature (specced, unscheduled), API 26–28 scrim check,
  deprecation housekeeping.
- **Scope protected out this sprint:** stats on the widget (S9 owner veto), "behind by N
  days" affordance, milestone celebrations (banned), week/month goals, streak
  freeze/repair, any caching layer.

## Next sprint

`next: sprint-0012-reminders`

## Open questions & risks

- **Owner tone sign-off pending** on the strings table above — cheap to change, all in
  `strings.xml` (plus `ReadingFormatter.streamTitle` if stream titles need renaming).
- Stats percent/counts use `NumberFormat.getIntegerInstance()` ("1,095") — locale-correct,
  but V1 is English-only; no action.
- `StatsViewModel` exposes nullable `ReadingStats` (no loading/error state): the derivation
  cannot fail and completes in one frame in practice. If a device pass shows a flash, add a
  placeholder — not before.
- Standing debt unchanged: Robolectric pinned `@Config(sdk = [34])`; JVM-untested
  `MainActivity` hooks (`AppNavHost` push/pop incl. the new STATS route is pinned only at
  the wiring-callback level); widget ignores in-app font scale (by design); CLI agent
  credentials still expired — owner should run `claude /login` before the next unattended
  run.
- The CI `release-bundle` job was not exercised this session (no commit per instructions);
  nothing in this sprint touches release packaging.
