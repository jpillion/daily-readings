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
- Next up: **Sprint 7 — Glance widget** (today's readings + completion at a glance; taps
  deep-link to the `today` route per ESpec §7; opportunistic refresh only — no midnight
  alarm in V1 per D9; Glance runs outside the activity so its theming is independent of
  `ThemeViewModel`; see [docs/EXECUTION_PLAN.md](docs/EXECUTION_PLAN.md) §3).

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
