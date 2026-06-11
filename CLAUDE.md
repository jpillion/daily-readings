# CLAUDE.md — Daily Readings

Context for any Claude Code session working in this repo. This file is the quick orientation +
handoff. The planning docs (written by the product/engineering team) live in `docs/`:

- [docs/SPEC.md](docs/SPEC.md) — product/build spec (concept, scope, roadmap).
- [docs/PRD.md](docs/PRD.md) — Product Requirements Document (Maya, PM): users, goals,
  scope by release, user stories, functional requirements, success metrics.
- [docs/ENGINEERING_SPEC.md](docs/ENGINEERING_SPEC.md) — Engineering Requirements &
  Architecture (Diego, staff architect): stack, module layout, data design, NFRs, decisions.
- [docs/EXECUTION_PLAN.md](docs/EXECUTION_PLAN.md) — V1 execution plan (Morgan, EM): up-front
  decisions, sprint sequence, ticketed first two sprints. **Start here to build.**

The agentic team lives at `.claude/agents/` (symlinked to the shared `../agents/android-team`).

## What this is

An Android app for the Christadelphian **"Bible Companion"** daily reading plan (Robert
Roberts). Three scripture readings per day, **date-anchored** (Jan 1 is always Genesis 1–2 /
Psalm 1–2 / Matthew 1–2), so all readers worldwide stay in sync.

**V1 is a digital reading planner**, not a Bible reader: show today's three readings, mark them
done, and tap any reading to open its chapter on Blue Letter Bible (KJV) in the browser. No
scripture text is bundled or rendered in-app in V1 — that's V3. The planner/tracker core works
offline; the text link needs network.

This is a **standalone repo**, deliberately separate from the unrelated `strikelog` project.
Do not reference or depend on strikelog.

## Current status (as of 2026-06-10)

- Repo on `main`, pushed to private GitHub remote `https://github.com/jpillion/daily-readings`.
- ✅ **Sprint 1 (Phase 0) is DONE** (commits `803ad3a`, `e22a123`): 365-day plan data (Feb = 28,
  no Feb 29), 66-book BLB catalog (live-verified abbrevs), independent second-source fixture,
  7-test verification gate incl. day-by-day equality vs. second source.
  Handoff: [docs/sprints/sprint-0001-trusted-plan-data.md](docs/sprints/sprint-0001-trusted-plan-data.md);
  reconciliation log: [docs/data/README.md](docs/data/README.md).
- ✅ **Sprint 2 (scaffold + CI + DI + theme) is DONE.** Installable `:app` (single-activity
  Compose, Hilt live, M3 light/dark/system theme + dynamic color on API 31+, StrictMode in
  debug), Gradle version catalog, GitHub Actions CI (`.github/workflows/ci.yml`). The Sprint 1
  gate is re-homed: canonical plan now lives at `app/src/main/assets/reading_plan.json`,
  fixtures in `app/src/test/resources/` (the old `data/` dir and standalone `verification/`
  module are gone); gate runs under `./gradlew testDebugUnitTest` (7 tests, mutation-verified).
  Full local quality pipeline:
  `./gradlew spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug koverVerifyAppDebug`.
  Toolchain: AGP 9.2.1 / Gradle 9.5.1 / Kotlin 2.3.21 / compileSdk 37 / Compose BOM 2026.05.01 /
  Hilt 2.59.2 / Kover 0.9.8 (70% floor on domain/data classes only).
  Handoff: [docs/sprints/sprint-0002-scaffold-ci-di-theme.md](docs/sprints/sprint-0002-scaffold-ci-di-theme.md).
- ✅ **Sprint 3 (data + domain layer) is DONE.** The engine works behind Hilt-bound
  interfaces (no UI yet): `ScheduleDateResolver` (sealed `ResolvedDate`; Feb 29 =
  `NoScheduledReadings`, unrepresentable in `ReadingDate`), `ReadingPlanRepository`
  (asset parsed/validated once, single-flight), production `BookCatalog` (test-pinned
  field-by-field to the Sprint 1 CSV) + `BlbUrlBuilder`, Room `ProgressRepository`
  (PK `(dateEpochDay, stream)`, year isolation proven), DataStore `ThemeRepository`,
  and use cases (`GetDayReadingsUseCase: Flow<DayReadings>`, `ToggleReadingUseCase`,
  `MarkWholeDayUseCase`, `OpenReferenceUseCase` → URL only). Multi-book portions handled
  (Jun 19 / Dec 19 = 2 John + 3 John in ONE portion). 45/45 tests (38 new + the untouched
  7-test Sprint 1 gate), mutation-verified; Kover 93.9% on domain/data vs the 70% floor
  (generated DI/Room code excluded by annotation). Known debt: `exportSchema = false` on
  `ProgressDatabase` (revisit at V2 streak schema); Robolectric pinned `@Config(sdk = [34])`.
  Handoff: [docs/sprints/sprint-0003-data-domain-layer.md](docs/sprints/sprint-0003-data-domain-layer.md).
- ✅ **Sprint 4 (Today screen) is DONE.** The primary user value is live: the Today screen
  shows today's three readings (stream title + collapsed reference via `ReadingFormatter` —
  "Genesis 1–2"; Jun 19/Dec 19 renders "2 John 1; 3 John 1"), per-reading checkbox + one-tap
  whole-day mark/unmark persist via the Sprint 3 use cases, tapping a card opens the BLB URL
  in a Chrome Custom Tab (`ui/browser/CustomTabLauncher.kt`, androidx.browser 1.10.0, plain
  ACTION_VIEW fallback). Feb 29 → "No scheduled readings for Feb 29th"; asset load failure →
  retryable error state (no crash). Pattern: stateless `TodayScreen` over sealed
  `TodayUiState` + stateful `TodayRoute` owning side-effects; one-shot URL events via
  Channel. Compose UI tests run under Robolectric in `testDebugUnitTest`
  (`isIncludeAndroidResources = true`, `@Config(sdk = [34])`). 68/68 tests (23 new + all 45
  prior incl. the 7-test Sprint 1 gate), mutation-verified; Kover 93.9% on domain/data.
  Handoff: [docs/sprints/sprint-0004-today-screen.md](docs/sprints/sprint-0004-today-screen.md).
- ✅ **Sprint 5 (Date picker + day swiping) is DONE.** The readings screen is now "a day's
  readings for any date": `ui/today/` was generalized into `ui/day/` — date-parameterized
  `DayReadingsViewModel` (`uiStateFor(date): StateFlow<DayUiState>`, per-date cache) under a
  `HorizontalPager` (`DayReadingsPagerScreen`, ±10,000-day window, today at the center page),
  so the user swipes left/right through real calendar days. M3 date-picker **dialog**
  (`ui/datepicker/DayDatePickerDialog`, year pinned to the current year — recorded ESpec
  deviation: no pushed picker route) plus a "Today" jump affordance in the top bar.
  **Year semantics decided (D-S5-3, ESpec §6.1):** progress always keys to the displayed
  *full* date; the picker targets the current year's occurrence; swipe steps real dates so
  Dec 31 → Jan 1 crosses into the adjacent year (test-pinned). Feb 29 reachable by swipe and
  picker, shows the no-readings state. Marks + BLB taps work on whatever day is displayed.
  New dep: `material-icons-core` 1.7.8 (frozen artifact, outside the BOM). 89/89 tests
  (37 new/adapted UI tests incl. real swipe gestures under Robolectric; 7-test Sprint 1 gate
  untouched), mutation-verified; Kover 93.9% on domain/data.
  Handoff: [docs/sprints/sprint-0005-date-picker.md](docs/sprints/sprint-0005-date-picker.md).
- ✅ **Sprint 6 (Settings — theme) is DONE.** The user controls appearance: a gear action in
  the readings top bar pushes the first real route (`Routes.SETTINGS`, ESpec §7;
  `ui/settings/SettingsRoute`/`SettingsScreen` — stateless screen over a Light/Dark/System
  radio group). Selection persists via the Sprint 3 DataStore `ThemeRepository` and drives
  the app live: activity-scoped `ui/theme/ThemeViewModel` + `ThemeMode.resolveDarkTheme()`
  feed `DailyReadingPlannerTheme(darkTheme = …)` in `MainActivity`, and edge-to-edge is
  re-issued via `DisposableEffect(darkTheme)` with `SystemBarStyle.auto` so system-bar icon
  contrast follows the *app* theme, not the device theme. No ESpec deviations. 99/99 tests
  (10 new: SettingsViewModel, SettingsScreen, resolveDarkTheme under Robolectric
  night/notnight, gear-action wiring; 7-test Sprint 1 gate untouched), mutation-verified;
  Kover 93.9% on domain/data. Known debt: `SystemBarStyle`/nav-bar scrim on API 26–28 needs
  the Sprint 8 device pass (JVM-only pipeline); `AppNavHost` push/pop untested on JVM.
  Handoff: [docs/sprints/sprint-0006-settings-theme.md](docs/sprints/sprint-0006-settings-theme.md).
- ✅ **Sprint 7 (Glance widget) is DONE.** Today's readings live on the launcher (PRD FR-8,
  ESpec §7): a resizable 3x2 widget (`widget/TodayWidget` + `TodayWidgetReceiver` +
  stateless `WidgetContent` over sealed `TodayWidgetState`) shows date, three stream rows
  (same `ReadingFormatter`), read/unread marks and an "All readings done" badge; Feb 29 and
  load-failure states degrade gracefully; the whole surface is one tap into the app. The
  widget reads the *same* `GetDayReadingsUseCase`/`Clock` via a Hilt `@EntryPoint` (snapshot
  per update, D-S7-1). Freshness (D9, no midnight alarm): `WidgetRefresher` seam (D-S7-2)
  fired from `MainActivity.onResume` and after progress mutations in `DayReadingsViewModel`,
  plus the 30-min `updatePeriodMillis` backstop (`res/xml/today_widget_info.xml`). Widget
  follows the *system* theme, not the in-app ThemeMode (D-S7-3); read-only — marking happens
  in-app (D-S7-4). New deps: `glance-material3`, `glance-appwidget-testing` (test). 110/110
  tests (11 new: 8 Glance unit-rig + 3 refresh-hook; 7-test Sprint 1 gate untouched),
  mutation-verified; Kover 93.9% on domain/data. Known debt: no widget `previewImage`; no
  on-device run yet (Sprint 8).
  Handoff: [docs/sprints/sprint-0007-glance-widget.md](docs/sprints/sprint-0007-glance-widget.md).
- ✅ **Sprint 8 (owner-feedback features) is DONE.** Four owner requests after a partial
  device pass (which also retired three release checks: widget works on a real launcher ✅,
  66-book live BLB link check ✅ = G-LINKS signed off, performance good ✅ = G-PERF).
  (1) **Responsive widget** (D-S8-1): one widget, `SizeMode.Responsive` with three
  width-keyed breakpoints in `widget/WidgetContent.kt` — LARGE ~3x2 (full rows), MEDIUM
  ~2x2 (marks + references, no stream titles), SMALL ~1x2 (date + "n/3" completion);
  `minResizeWidth=40dp` in `today_widget_info.xml`; every size keeps Feb-29/error states,
  the single tap target, and spoken state. (2) **Date-picker completion indicators**
  (D-S8-2): M3 `DatePicker` has no per-day-cell slot, so `ui/datepicker/DayDatePickerDialog`
  is now a custom calendar grid (same dialog contract/tags, pinned year per D-S5-3, Feb 29
  selectable) with a dot per day — green = all three read (past/today/future), red = past
  day missed, none = incomplete today/future or Feb 29 — plus contentDescription (never
  color alone). Domain seam: `ProgressRepository.readCounts(start,end)` (one grouped Room
  query) → `GetMonthCompletionUseCase` → `DayReadingsViewModel.monthCompletionFor(YearMonth)`
  (cached, live). (3) **Settings → Reset progress**: confirm-gated, clears the *current
  year only* (`ProgressRepository.clearYear` ranged delete, `ResetYearProgressUseCase`),
  refreshes the widget. (4) **Settings → text-size slider**: 0.85x–1.5x in 0.05 steps,
  default 1.0, persisted as `fontScale` in the DataStore `ThemeRepository`, applied live
  app-wide by multiplying `LocalDensity.fontScale` in `DailyReadingPlannerTheme` (composes
  with system font scaling; does NOT affect the widget — D-S8-5/D-S7-3). 147/147 tests
  (37 new/adapted; 7-test Sprint 1 gate untouched), 4 mutations killed (past-only guard,
  Feb-29 guard, reset end-bound, breakpoint bound); Kover 95.1% on domain/data.
  Handoff: [docs/sprints/sprint-0008-owner-feedback-features.md](docs/sprints/sprint-0008-owner-feedback-features.md).
- ✅ **Sprint 9 (widget sizing refinement + hardening & release readiness) is DONE.**
  (1) **Widget feedback (owner):** the widget lists the **three readings at every size
  down to 1x1** — completion is never the focus. `WidgetLayout` gained `TINY` (1x1,
  57x48dp; `minResizeHeight=40dp`); the chooser is width-first then height-split
  (SMALL ~1x2 = date + abbreviated rows, TINY ~1x1 = abbreviated rows only); the old
  "n/3" layout is gone. Abbreviations are *derived* from the live-verified BLB tokens —
  `Book.displayAbbrev` ("gen"→"Gen", "2jo"→"2Jo") + `ReadingFormatter.formatAbbreviated`
  ("Gen 1–2"; Jun 19/Dec 19 = "2Jo 1; 3Jo 1") per **D-S9-1** (no second 66-row table; no
  drift). A11y still speaks full canonical names; Feb-29/error states + single tap target
  hold at all sizes; static `res/drawable/widget_preview.xml` is the picker preview.
  (2) **Release readiness:** `./gradlew bundleRelease` builds an R8-minified,
  resource-shrunk `.aab` (debug-signed until the owner provisions the upload key — signing
  reads gitignored `keystore.properties` or `DRP_UPLOAD_*` env, D-S9-5); CI gained a
  `release-bundle` job; versioning is `1.0.0`/`10000` (D-S9-3: MAJOR*10000+MINOR*100+PATCH);
  Room schema exported + checked in at `app/schemas/` (D-S9-4, Sprint 3 debt retired);
  `ui/AccessibilityGateTest.kt` pins the JVM-provable a11y gate (48dp touch bounds on all
  authored controls, picker-grid spoken dates, slider range semantics; stock M3 Slider
  accepted at its 44dp handle token). StrictMode review: all I/O off main on JVM-provable
  paths. 160/160 tests (13 new; 7-test Sprint 1 gate untouched), 3 mutations killed;
  Kover 95.2% on domain/data.
  Handoff (incl. the owner's keystore/Play-listing/device-pass checklists):
  [docs/sprints/sprint-0009-hardening-release.md](docs/sprints/sprint-0009-hardening-release.md).
- ✅ **Sprint 10 (tracking start date) is DONE** (owner-redirected from the planned
  `v1-release` sprint; V1 ship stays owner-blocked on the Sprint 9 checklists). The
  mid-year-adopter fix (docs/features/tracking-start-date.md) is live: Settings → Tracking →
  "Start tracking from" (row + stock M3 *full-calendar* year-navigable `DatePickerDialog` —
  deliberately not the pinned-year `DayDatePickerDialog` — plus Clear + helper text; tags
  `tracking-start-*`). Days strictly before the start date are neutral: never red/MISSED in
  the picker grid, excluded from missed/streak classification, but still navigable/markable,
  and a fully-read pre-start day keeps its green COMPLETE dot. THE missed-day predicate is
  `GetMonthCompletionUseCase.classify` (order: Feb29 → COMPLETE → start-date gate → MISSED);
  month flow is now a `combine`(readCounts, trackingStartDate) so an open picker updates
  live — **V2 streaks MUST consume this seam (R-STREAK-5), never re-derive it.**
  **D-S10-1:** default = first-run date, written only if never-initialized AND zero existing
  marks (upgraders keep null = pre-S10 behavior; a deliberate clear is never re-defaulted —
  separate `tracking_start_initialized` marker; `InitializeTrackingStartUseCase` fired from
  `MainActivity.onCreate`). **D-S10-2:** `ThemeRepository` → `SettingsRepository` rename
  (DataStore file + old keys unchanged; new keys `tracking_start_epoch_day`,
  `tracking_start_initialized`). `ProgressRepository.hasAnyMarks()` added (query only — NO
  Room schema change). Reset-progress and start date are independent (tested). 183/183 tests
  (23 new; 7-test Sprint 1 gate untouched), 3 mutations killed (boundary, gate-vs-COMPLETE
  ordering, initializer gate); Kover 95.4% on domain/data. Version stays 1.0.0/10000 (V2 WIP).
  Handoff: [docs/sprints/sprint-0010-tracking-start-date.md](docs/sprints/sprint-0010-tracking-start-date.md).
- ✅ **Sprint 11 (streaks & stats) is DONE.** The sober V2 Stats screen (PRD §13.1,
  FR-15…18) is live: a bar-chart action in the readings top bar (custom
  `res/drawable/ic_stats.xml`, D-S11-5 — icons-core has no chart glyph) pushes `Routes.STATS`
  (`ui/stats/StatsRoute`/`StatsScreen`/`StatsViewModel`) — read-only, exactly four stat
  groups: current streak, longest streak (all-time), year progress (n of 1,095, full-year
  denominator, **floor** rounding so 100% only at completion — D-S11-4), per-stream
  progress (n of 365). No red, no missed-day copy anywhere (pinned by test). **D-S11-1:**
  the S10 truth table was extracted verbatim into `domain/DayCompletionClassifier` and BOTH
  `GetMonthCompletionUseCase` and the new `GetReadingStatsUseCase` inject it (R-STREAK-5
  satisfied literally — one predicate, no drift). **D-S11-2:** streak walk = one forward
  pass from the earliest stored mark to today: COMPLETE extends, MISSED resets, NONE is
  neutral/skipped — so Feb 29 (R-STREAK-2), today-in-grace (R-STREAK-3), year-boundary
  crossing (R-STREAK-4) and pre-start exclusion (R-STREAK-5; pre-start COMPLETE days still
  extend, earned-green parity) all fall out of the classifier; future-dated marks never
  enter the walk but count in year totals. **D-S11-3:** NO Room schema change and no
  caching — two new grouped DAO queries (`allReadCounts`, `streamCountsInRange`) +
  `ProgressRepository.allReadCounts()/streamCounts()`; stats are derived per R-STREAK-6
  (Room invalidation = FR-17 liveness). 214/214 tests (31 new; 7-test Sprint 1 gate
  untouched), 4 mutations killed (NONE-neutrality/Feb-29 skip, today-grace, pre-start gate,
  year-boundary walk floor); Kover 96.2% on domain/data. New user-visible strings await
  owner tone sign-off (PRD M8) — listed in the handoff. Version stays 1.0.0/10000 (V2 WIP).
  Handoff: [docs/sprints/sprint-0011-streaks-stats.md](docs/sprints/sprint-0011-streaks-stats.md).
- Next up: **Sprint 12 — V2 reading reminders** (`sprint-0012-reminders`): PRD §13.2 — an
  optional, user-scheduled daily notification with the day's readings; off by default;
  owner constraint: time configurable in Settings, or not at all. Mind exact-alarm vs
  inexact (API 26+), reuse the `WidgetRefresher`-style seam thinking, notification copy
  needs owner tone sign-off. V1 ship still runs in parallel on the owner's side (Sprint 9
  checklists; add: fresh install defaults the tracking start to today).

## The reading plan

Three parallel streams through scripture, one portion each per day:
1. **Stream 1 — Law & History:** Genesis → Job
2. **Stream 2 — Psalms & Prophecy:** Psalms → Malachi
3. **Stream 3 — New Testament:** Matthew → Revelation

Over a year: **Old Testament once, New Testament twice.** ~3–4 chapters/day.

## Phase 0 (data foundation) — ✅ DONE (Sprint 1, 2026-06-10)

The reading plan schedule is the project's real IP and the **only** V1 data asset (no Bible
text is bundled in V1). It now exists and is gate-verified (paths below reflect the
Sprint 2 re-home; the original `data/` dir and standalone `verification/` module are gone):

- `app/src/main/assets/reading_plan.json` — canonical plan, bundled in the APK. 365 days
  (Feb = 28, **no Feb 29 entry** per D1), structured `{book, chapter}` refs (per D3),
  `schemaVersion: 1`.
- `app/src/test/resources/book_catalog.csv` — 66 books
  `(order, canonicalName, chapterCount, blbAbbrev)`, all abbrevs verified live against BLB
  (D2 resolved).
- `app/src/test/resources/reading_plan_verify.json` — independent second-source fixture
  (antipas booklet; the pricejh PDF turned out byte-identical to the primary, so it was
  substituted).
- The 7-test gate (incl. day-by-day equality vs. the second source) lives at
  `app/src/test/kotlin/.../data/plan/ReadingPlanVerificationTest.kt` and runs under
  `./gradlew testDebugUnitTest` on every CI run.
- Sources, normalization rules, and the 7-conflict reconciliation log:
  [docs/data/README.md](docs/data/README.md).

> The KJV **text** dataset is **not** a Phase 0 item — it's deferred to V3 (in-app text).
> V1 reaches scripture via Blue Letter Bible links:
> `https://www.blueletterbible.org/kjv/<book>/<chapter>/` (3-letter book abbrev, e.g.
> `gen`, → `/kjv/gen/1/`).

Reference sources (extraction is done; kept for reconciliation/notes-feature reference):
- christadelphia.org (Excel/PDF): https://christadelphia.org/readplan.php
- Bible Companion booklet (PDF): https://antipas.org/library/Robert%20Roberts/Booklets/The%20Bible%20Companion.pdf
- Daily readings + study notes (model for a future notes feature): https://dailyreadings.org.uk/
- Background — Wikipedia: https://en.wikipedia.org/wiki/Bible_Companion
- Prior art (existing app): https://apps.apple.com/us/app/daily-bible-readings/id536687049

## Planned stack

**V1:** Kotlin · Jetpack Compose · Material 3 · single-activity · plan JSON in memory ·
small progress store (DataStore or tiny Room table) · DataStore (theme) · Glance widget ·
outbound Blue Letter Bible links · Hilt · GitHub Actions CI (build + tests + Kover).

**Later:** AlarmManager reminders (V2); Room + bundled read-only **SQLite KJV asset** for
in-app text and richer progress/streak schema (V2/V3).

## Open decisions

None. All product/owner decisions are resolved — see below and `docs/EXECUTION_PLAN.md` §2.
(The BLB abbreviation table landed in Sprint 1: `data/book_catalog.csv`, all 66 link-checked.)

## Decisions already made

- New, separate repo (not inside strikelog). ✅
- Spec drafted first before code. ✅
- **V1 = digital reading planner**; no in-app scripture text (deferred to V3). ✅
- Mark-as-read is **per reading** (3/day) + one-tap "whole day done"; **not** per-chapter. ✅
- Scripture reached via **Blue Letter Bible** KJV links, not bundled text, in V1. ✅
- Schedule keyed by (month, day); **progress keyed by full date** so marks don't repeat
  across years. ✅
- KJV is the default/v1 translation; multi-translation schema is moot for V1 (no text stored). ✅
- **App name = "Daily Reading Planner"**; package id = **`com.jpillion.dailyreadingplanner`**. ✅
- **Feb 29 = no scheduled readings.** Plan covers 365 days (Feb = 28 entries, no Feb 29 entry);
  in leap years the date shows **"No scheduled readings for Feb 29th"** (no readings/marks/
  tracking); non-leap years skip it. No fold/double-day logic. ✅
- **minSdk = 26**; targetSdk/compileSdk = latest stable. ✅
- **No analytics/telemetry in V1** (no networking dep). ✅
- **Distribution = Play Store.** ✅
