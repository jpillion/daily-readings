# Sprint 0010 — Tracking start date

**Status: GOAL MET.** Closed 2026-06-11. (Commit to `main` performed by the main session per
protocol — working tree handed over uncommitted by request. Unattended overnight run, sprint 1 of 4.)

> Note: the Sprint 9 handoff recorded `next: sprint-0010-v1-release`. The owner redirected this
> sprint to the tracking-start-date feature (docs/features/tracking-start-date.md); V1 release
> support remains owner-blocked on the device pass / upload key (Sprint 9 checklists still stand).

## Goal outcome

**Met.** A user who adopts the app mid-year no longer faces a wall of red: a tracking start
date — defaulted on a fresh install, fully editable and clearable in Settings — makes every
day strictly before it *neutral*. No red "missed" dot in the date-picker grid, excluded from
missed classification, yet still navigable and markable, and a fully-read pre-start day keeps
its earned green dot. Clearing the date reverts to exact pre-S10 behavior, live, with the
picker open.

## Current capability

- **Settings → Tracking → "Start tracking from":** shows the current value ("Not set" when
  unset), opens a stock M3 full-calendar (year-navigable) date picker, and offers a Clear
  button when set. Helper text explains the semantics. All authored controls meet the 48dp
  a11y gate; the row speaks label + value, Clear speaks its purpose.
- **The single missed-day predicate honors the start date** (`GetMonthCompletionUseCase.classify`,
  truth-table order: Feb 29 → COMPLETE → start-date gate → MISSED → NONE). The month flow is a
  `combine` of marks + start date, so an open picker re-renders live on either change.
- **First-run default (D-S10-1):** a genuinely fresh install is defaulted to its first-launch
  date; an install with existing marks is left unset; a deliberate clear is never re-defaulted.
- Verified: **183/183 tests** (23 new; the 7-test Sprint 1 plan gate untouched and passing),
  **3 mutations killed** (boundary `<`→`<=`, gate-before-COMPLETE reorder, initializer
  ignores-existing-progress — each killed by exactly its intended test, in-place restores),
  full pipeline green, **Kover 95.4%** on domain/data (floor 70%). No Room schema change
  (`hasAnyMarks` is a query; `app/schemas/` untouched). Version stays 1.0.0/10000 (V2 WIP).

## Decisions & rationale (do not relitigate)

- **D-S10-1 — Default = first-run date (spec §3 option B), gated.** Written only when the
  one-time marker pref has never been set AND `ProgressRepository.hasAnyMarks()` is false.
  Rationale: the app is unreleased, so every future user is a new install and gets the fix
  automatically; upgraders with history keep `null` (= track everything = pre-S10 behavior);
  the separate `tracking_start_initialized` marker guarantees a user who clears the date is
  never re-defaulted. All three pinned in `InitializeTrackingStartUseCaseTest`.
- **D-S10-2 — `ThemeRepository` renamed to `SettingsRepository`** (spec §4 recommendation):
  it now holds theme + fontScale + tracking start. DataStore file name ("settings") and keys
  (`theme_mode`, `font_scale`) unchanged — only Kotlin types renamed; no data migration.
  New keys: `tracking_start_epoch_day` (Long, absent = null), `tracking_start_initialized`
  (Boolean).
- **D-S10-3 — Start date semantics** (spec §2, settled by the owner, re-pinned in tests):
  start-date-*inclusive* (the start date itself IS tracked); only MISSED is suppressed,
  COMPLETE always wins (ordering test); future start dates allowed; reset-progress and the
  start date are strictly independent (neither touches the other — tested).
- **The predicate is the V2 streaks contract (R-STREAK-5):** streaks/stats MUST consume
  `GetMonthCompletionUseCase` classifications (or at minimum inject the same
  `SettingsRepository.trackingStartDate` and apply `date.isBefore(start)` identically) —
  one source of truth, no drift between picker dots and stats.

## State of the codebase

- **Data:** `data/prefs/SettingsRepository(.Impl).kt` (renamed + 2 new prefs);
  `data/progress/ReadingProgressDao.hasAnyRows()` → `ProgressRepository.hasAnyMarks()`.
- **Domain:** `domain/GetMonthCompletionUseCase.kt` (combine + gate — THE predicate);
  `domain/InitializeTrackingStartUseCase.kt` (one-time default), fired from
  `MainActivity.onCreate` via `lifecycleScope`.
- **UI:** `ui/settings/SettingsScreen.kt` — `TrackingStartRow` + `TrackingStartDatePickerDialog`
  (stock M3, full date — deliberately NOT the pinned-year `DayDatePickerDialog`); tags:
  `tracking-start-row/-value/-clear/-dialog/-confirm/-cancel`. `SettingsViewModel.trackingStartDate`
  + `onTrackingStartChanged`. No picker/widget/nav changes (pre-start days simply arrive as
  NONE; the widget has no past-day surface — spec §7).
- **Tests (23 new):** predicate suite + liveness in `GetMonthCompletionUseCaseTest` (the
  pre-S10 tests run with `null` start = regression guard), `SettingsRepositoryImplTest`
  (round-trips incl. leap day, clear, marker), `InitializeTrackingStartUseCaseTest`,
  `ProgressRepositoryTest.hasAnyMarks`, `SettingsViewModelTest` (incl. reset-independence),
  `SettingsScreenTest` (row/dialog/clear), `AccessibilityGateTest` additions.
  `testing/FakeSettingsRepository` (renamed) and `domain/Fakes.kt` extended.
- Process note: this sprint ran unattended; the `claude` CLI sub-agent dispatch failed on
  expired credentials (interactive `/login` required), so the EM executed tickets directly
  under the same per-ticket verification discipline.

## Carryover & next goal

- **Next goal (Sprint 11): V2 streaks & stats** — PRD §13.1 (FR-15…FR-18, R-STREAK-1…6): a
  Stats screen (current streak, longest streak, year %, per-stream %) reachable from the
  readings top bar; no guilt mechanics. Consume the S10 predicate per R-STREAK-5 (above).
  Note R-STREAK-3 (today is in grace) and R-STREAK-2 (Feb 29 neutral in the walk).
- **Queued/deferred (unchanged from S9):** V1 ship itself (owner checklists in the Sprint 9
  handoff: upload key, Play listing, device pass — now also smoke the tracking-start row +
  M3 dialog on device), toggle-from-widget (V2), Psalm 119 verse-ranges, deprecation
  housekeeping, API 26–28 scrim check.
- **Scope protected out this sprint:** end-of-tracking date, per-stream start dates, any
  streak computation (next sprint), widget past-day surfaces.

## Next sprint

`next: sprint-0011-streaks-stats`

## Open questions & risks

- The `InitializeTrackingStartUseCase` *hook* in `MainActivity.onCreate` is JVM-untested
  (same standing debt as the edge-to-edge wiring and `AppNavHost`); the use case itself is
  fully tested. Add to the owner device pass: fresh install → Settings shows today's date.
- The M3 `DatePickerDialog` content is rendered by the stock component; our tests pin
  open/confirm/cancel/clear and the confirm-returns-selected-date path, not in-dialog
  calendar navigation (stock behavior, trusted as with the M3 Slider precedent, D-S9-5).
- CLI agent credentials expired — owner should run `claude /login` before the next
  unattended run.
- Known debt carried: Robolectric pinned `@Config(sdk = [34])`; widget ignores in-app font
  scale (by design, D-S8-5).
