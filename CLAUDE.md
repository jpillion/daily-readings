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

## Current status (as of 2026-06-11)

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
- ✅ **Sprint 12 (reading reminders) is DONE.** PRD §13.2 (FR-19…23): Settings → Reminders
  — "Daily reminder" switch (**off by default**, R-REM-1) + M3 time-picker row (default
  08:00, D-S12-5), persisted in the DataStore `SettingsRepository`
  (`reminder_enabled`/`reminder_minute_of_day`). At the chosen time one respectful
  notification ("Today's readings" + the day's collapsed references, e.g.
  "Genesis 1–2 · Psalms 1–2 · Matthew 1–2") opens the app on today; quietly suppressed
  when the day is already complete (R-REM-4) and on Feb 29 (R-REM-5), decided **at fire
  time** (D-S12-3) via the same `GetDayReadingsUseCase` the UI uses; never retroactive.
  **Inexact alarms** (`setAndAllowWhileIdle(RTC_WAKEUP)`, D-S12-1 — no exact-alarm
  permission/Play policy burden); the standing alarm is re-armed after each fire, on
  BOOT_COMPLETED, and on every app launch (`RescheduleAlarmsUseCase` in
  `MainActivity.onCreate`); a disable cancels it and a stale alarm no-ops.
  POST_NOTIFICATIONS is requested only when the user enables the toggle; denial keeps the
  setting off + explanation dialog with a system-settings path (R-REM-7;
  `NotificationPermissionChecker` seam, D-S12-6). **FR-23 landed:** an independent
  midnight alarm snaps the widget to the new day at date rollover (D-S12-2 — D9 risk R6
  retired; the 30-min `updatePeriodMillis` backstop remains for force-stop). New package
  `reminders/` (pure `AlarmTimes`, `ReminderScheduler`/`AlarmManagerReminderScheduler`,
  `ReminderNotifier`/`SystemReminderNotifier`, thin `ReminderAlarmReceiver`/`BootReceiver`);
  domain `DeliverDueReminderUseCase` + `RescheduleAlarmsUseCase`; manifest gains ONLY
  POST_NOTIFICATIONS + RECEIVE_BOOT_COMPLETED (receivers non-exported). 260/260 tests
  (46 new; 7-test Sprint 1 gate untouched), 4 mutations killed (complete-day skip, Feb-29
  skip, exactly-now no-refire boundary, disable-cancel); Kover 96.4% on domain/data.
  Notification/settings strings await owner tone sign-off (PRD M8 — table in the handoff,
  note "Psalms" vs the PRD's "Psalm"). Device-pass items (real fire time, reboot, permission
  prompt, midnight rollover) listed in the handoff. Version stays 1.0.0/10000 (V2 WIP).
  Handoff: [docs/sprints/sprint-0012-reminders.md](docs/sprints/sprint-0012-reminders.md).
- ✅ **Sprint 13 (Bible app links) is DONE.** The user chooses which KJV destination
  reading taps open — Settings → "Open readings in": **Blue Letter Bible (default,
  unchanged), Bible Gateway (website), YouVersion / Bible.com** — persisted in DataStore
  (`bible_provider`, unknown ids degrade to BLB), read at tap time (D-S13-4), still plain
  https through `CustomTabLauncher` (YouVersion app-links into its app when installed).
  Both new providers passed the spec §3 gate before shipping: **Bible Gateway 134/134,
  YouVersion 132/132** live HTTP checks (ch 1 + last ch of all 66 books + portion forms),
  recorded in docs/data/provider-link-checks.md; the committed suite stays offline. Model
  (D-S13-1/2, extends D-S9-1): `Book.usfmCode` column (hand-pinned — PHP/EZK/JOL/NAM/MRK/
  JUD defeat derivation; `UsfmCodeCatalogTest` is the gate), `data/reference/
  ProviderUrlBuilder` replaces `BlbUrlBuilder` as the ONLY URL home, `domain/model/
  BibleProvider` enum. Portion semantics (D-S13-3, pinned): BLB/YouVersion open the first
  ref; **Bible Gateway carries the whole portion in one URL** ("Genesis 1-2"; Jun 19/
  Dec 19 = "2 John 1,3 John 1"). "Request another app or site" row = mailto intent to
  jjpillion@gmail.com (no networking). **Owner's tier-2 apps all NO-GO** (spec §11 +
  handoff): Logos = anonymous login wall + bare links default to ESV; Olive Tree = custom
  scheme only; MySword = stub web page — all need install detection (queued candidate).
  279/279 tests (19 net-new; 7-test Sprint 1 gate untouched), 4 mutations killed; Kover
  96.5% on domain/data. S13 strings need owner tone sign-off (table in the handoff).
  Handoff: [docs/sprints/sprint-0013-bible-app-links.md](docs/sprints/sprint-0013-bible-app-links.md).
- ✅ **Sprint 14 (Settings UI tweaks + widget visual redesign — owner feedback) is DONE**
  (owner-redirected again from `v2-release-prep`; uncommitted in the working tree by
  request). (1) **Theme + "Open readings in" are compact dropdown rows** (shared
  `SettingsDropdownRow` idiom in `SettingsScreen.kt`: 56dp row showing the current value,
  `Role.DropdownList`, spoken "label, value", anchoring an M3 `DropdownMenu`; the old
  `theme-option-*`/`provider-option-*` tags carry over onto the menu items, new row tags
  `theme-dropdown`/`provider-dropdown`). The provider menu adds a **visible-but-disabled
  teaser** "Read in this app (coming soon)" (`provider-option-inapp`) — render-layer ONLY,
  deliberately not a `BibleProvider` value, so no tap can ever persist it; proper disabled
  semantics for TalkBack (pinned: disabled + never reports a selection). (2) **D-S14-1**
  (supersedes that part of D-S10-1): the tracking-start initializer defaults to **Jan 1 of
  the current year**, not the install date. Already-initialized devices keep their stored
  value — auto-set vs user-set is indistinguishable in the store, so no migration (owner
  changes his manually); pinned by a no-rewrite test. (3) **D-S14-2 — widget redesign**
  (owner: every size must look deliberate, not squeezed): reading rows now share the card
  height equally via `defaultWeight()` (vertically centered per row — no more dead zone
  below top-stacked content); per-tier type scale + insets (`WidgetScale`/`scaleFor`:
  LARGE 16sp date/17sp refs/16dp padding down to TINY 12sp/8dp); **LARGE requires BOTH
  axes** (≥203x102); new wide-short size points `MEDIUM_SHORT_SIZE` (130x48) +
  `WIDE_SHORT_SIZE` (203x48) in `SizeMode.Responsive` (`RESPONSIVE_SIZES`) so a wide 1-row
  widget gets full references instead of falling to TINY (the likely cause of the owner's
  "squeezed" 3x2 screenshot — Responsive only picks sizes fitting BOTH dims); the date
  header is a height decision (`showsHeader`: short tiers spend the room on readings);
  Feb-29/error states centered both axes; headerless complete day keeps a spoken badge;
  all S9 invariants hold (three readings at every size, marks, single tap target, TalkBack
  full names, system theme); `widget_preview.xml` refreshed. 285/285 tests (net +6; 7-test
  Sprint 1 gate untouched), 4 mutations killed (LARGE both-axes rule, header height gate,
  Jan-1 default, teaser enabled), each by exactly its intended test; Kover 96.5% on
  domain/data. **Version stays 1.1.1/10101 — these changes need a bump to ship.** S14
  strings need owner tone sign-off (table in the handoff). Weight-based distribution and
  per-size look are NOT JVM-provable — top of the device-pass list.
  Handoff: [docs/sprints/sprint-0014-settings-widget-polish.md](docs/sprints/sprint-0014-settings-widget-polish.md).
- ✅ **Sprint 15 (MySword provider + inline stats — owner feedback) is DONE**
  (owner-redirected again from `v2-release-prep`; uncommitted by request; version stays
  1.2.0/10200 while the v1.2.0 tag releases — needs a bump to ship).
  (1) **MySword as an install-detected provider** (spec §11 go-path taken):
  `BibleProvider.MYSWORD` + `requiresApp`; manifest `<queries>` + `data/apps/
  AppInstallChecker` seam (D-S15-2, the D-S12-6 pattern); the provider dropdown shows
  "MySword (app not installed)" disabled when absent (S14 teaser idiom), selectable when
  installed. **D-S15-1:** URL = the vendor-documented NUMERIC form
  `https://mysword.info/b?r={Book.order}.{chapter}` (vendor's `19.37.3-6` pins 19=Psalms=
  canon order; no abbreviation list exists, so numeric = zero guesses, derived from the
  pinned catalog — `MySwordTokenCatalogTest` is the 66-row gate). **D-S15-3:**
  `OpenReferenceUseCase` now returns sealed `ReadingDestination` (`Web` | `MySwordApp(url,
  fallbackUrl)`); the UI fires the explicit `com.riversoft.android.mysword/.MySwordLink`
  intent and falls back to the BLB URL on `ActivityNotFoundException` (never the
  mysword.info stub); the persisted choice is never rewritten. **Gate honesty:** MySword
  links resolve only in-app — the live 66-book check is the owner's on-device pass (adb
  list in the handoff); the committed suite pins tokens + URL construction offline.
  (2) **Stats moved onto the main screen** (D-S15-4): `ui/stats/StatsContent` renders the
  four S11 stat groups ONCE below the day pager (year-level = identical for every page),
  height-capped at 45% + internally scrollable so readings keep the majority; guilt-ban
  still pinned (`StatsContentTest`). The stats icon, `Routes.STATS`, route files, and
  `ic_stats.xml` are REMOVED. (3) **Settings → Stats → "Show streaks"** (D-S15-5,
  `show_streaks` default true): off hides only the streak rows; year + stream remain;
  display-only. 304/304 tests (net +19; 7-test Sprint 1 gate untouched), 5 mutations
  killed each by its intended test; Kover 95.8% on domain/data. S15 strings need owner
  tone sign-off (table in the handoff).
  Handoff: [docs/sprints/sprint-0015-mysword-inline-stats.md](docs/sprints/sprint-0015-mysword-inline-stats.md).
- ✅ **Sprint 16 (main-screen space cleanup — owner feedback) is DONE** (uncommitted in
  the working tree; version untouched at 1.3.0/10300 — needs a bump to ship). Goal: the
  whole main screen fits one screen at default font (the S15 stats panel had pushed it
  into a slight scroll). (1) **D-S16-1 — single-line title** replaces the two-line
  heading+date top bar: today = "Today – June 10" (`title_today_date`, en dash, no year);
  any other day = just the date, "Friday, June 13" — the year is appended ONLY when it
  differs from today's (Dec 31 → Jan 1 swipe, pinned), `maxLines = 1` + ellipsis
  guarantees one line. `today_title`/`readings_title` ("Today"/"Readings" headings) are
  REMOVED; formatting helpers `formatMonthDay`/`formatDayDate` in `DayReadingsScreen.kt`
  are test-pinned. (2) **D-S16-2 — progress line removed**: the "n of 3 readings done"
  count (`day_progress`) is gone at every state; "All readings done" (`day_complete`)
  stays — it costs no space in the not-done state and now renders *below* the whole-day
  button as a completion badge. (3) The whole-day button sits directly under the third
  card (dead Spacer removed, uniform 12dp rhythm); 48dp bounds still pinned by
  `AccessibilityGateTest`. **Priya's design review (required by owner): APPROVED** —
  hierarchy (readings first), uniform spacing, TalkBack still meaningful (title speaks
  the full date; checkbox states carry progress); noted ellipsis-at-extreme-font-scale
  as acceptable degradation per owner. 304/304 tests (count unchanged: progress pins
  rewritten as absence-pins, title pins are LITERAL strings — never computed via the
  prod formatter; 7-test Sprint 1 gate untouched), 3 mutations killed each by its
  intended test, pipeline green; Kover 95.8% on domain/data. Device-pass add: confirm one-screen fit on the
  P7P at default font.
  Handoff: [docs/sprints/sprint-0016-main-screen-cleanup.md](docs/sprints/sprint-0016-main-screen-cleanup.md).
- ✅ **Sprint 17 (year-strip progress visualization — owner-approved design) is DONE**
  (owner-redirected again from `v2-release-prep`; uncommitted in the working tree; version
  untouched at 1.3.0/10300 — needs a bump to ship). The stats panel's plain progress bars
  are now **year strips**: one single-Canvas strip per stream — 365 contiguous ~1dp
  segments, one per calendar day — green = that stream marked that day, red = past
  post-start unmarked, neutral = everything else (Feb 29, pre-start, today-in-grace,
  future); the year section keeps its "% · n of 1,095" text but its bar is the **three
  stream strips stacked** (3×6dp rows); a thin onSurface tick marks today on every strip.
  **D-S17-1 (amends the S11 pin):** red is now ALLOWED on the stats surface — strips only,
  information not commentary; the no-guilt **copy** ban stays absolute and was *extended*
  to contentDescriptions (pinned in `StatsContentTest`). **D-S17-2:** per-(day,stream)
  state goes THROUGH `DayCompletionClassifier` (R-STREAK-5, never re-derived): classify
  with a synthetic count — `STREAM_COUNT` if that one stream is marked, else 0 — so the
  truth-table order (Feb 29 → marked/green incl. pre-start earned green → start gate →
  past red → neutral) is inherited verbatim; mutation-pinned against local re-derivation.
  **D-S17-3:** strips are color-only, so each carries a spoken summary ("Law & History:
  120 read, 3 not read, 242 upcoming"; stacked view speaks one combined line) — wording is
  "not read", never "missed", even in speech. **D-S17-4:** segments = `lengthOfYear()`
  (366 in leap years, Feb 29 renders neutral); colors reuse the picker-dot
  `IndicatorGreen/Red` tokens via the **`StripColors` seam** (`defaultStripColors()` in
  `ui/stats/YearStrip.kt`) — a future colorblind palette (owner-deferred, queued) is a
  one-provider swap. New: `domain/model/YearStrips` + `StripDayState`,
  `GetYearStripsUseCase`, DAO `marksInRange` → `ProgressRepository.streamMarks()` (query
  only, NO schema change), strips ride the existing `StatsPanelUiState`. No touch
  interaction on strips; streak toggle (D-S15-5) independent — strips always show.
  321/321 tests (net +17; 7-test Sprint 1 gate untouched), **5 mutations killed**
  (grace-day today, pre-start gate, resolver-bypassing re-derivation → Feb-29, day-365
  truncation, day-1 shift), each restored in place; Kover 96.2% on domain/data; pipeline
  green. Strip look (~1dp texture, tick visibility, dark mode) is NOT JVM-provable —
  device-pass item. S17 strings need owner tone sign-off (table in the handoff).
  Handoff: [docs/sprints/sprint-0017-year-strips.md](docs/sprints/sprint-0017-year-strips.md).
- ✅ **Sprint 18 (stats tightening + streak clarity — owner feedback on v1.3.2) is DONE**
  (owner-redirected again from `v2-release-prep`; uncommitted in the working tree by
  request; version stays 1.3.2/10302, no tags touched — needs a bump to ship).
  (1) **One-screen stats (owner: "unused real estate… stats still have to scroll"):**
  the stats panel's intrinsic height dropped ~400dp → ~290dp with streaks shown
  (~222dp streaks off) — under the S15 45% cap (~360dp on a P7P), so the panel no longer
  scrolls at default font and readings + full stats fit one screen. How (D-S18-2 —
  density, not restructure; the cap stays as the large-font safety net): "This year"
  label + percent + count merged into ONE row (old label row + headlineMedium row ≈
  40dp saved); "By stream" header removed (names self-describe; absence pinned; string
  deleted); outer insets 12→10dp v / 24→16dp h (now matching the readings column); gaps
  12→10dp; streak values titleLarge→titleMedium. Budget table in the handoff.
  (2) **Streaks opt-in (D-S18-1, supersedes the D-S15-5 default):** `show_streaks`
  absent-key default flipped true→false; explicitly stored choices survive (pinned by a
  stored-true-survives test). (3) **Streak explainer:** `show_streaks_help` now states
  the D-S11-2 rule (all three readings; today-grace; pre-start/Feb-29 days skip, never
  break) — copy verified against the code first, pinned LITERALLY, awaiting owner tone
  sign-off. (4) **Continuous strips (D-S18-3):** `YearStrip` now coalesces consecutive
  same-state days into single rects (`coalesceRuns`, pure + fully pinned: partition,
  shared edges, maximality) — no per-day hairline seams, ~10x fewer draw calls; first
  run starts at exactly 0, last ends at exactly `size.width`. 328/328 tests (net +7;
  7-test Sprint 1 gate untouched), 4 mutations killed (default re-flip, non-maximal
  coalescing, header reintroduced, explainer corrupted), each by its intended test,
  restored in place; Kover 96.1% on domain/data. On-glass continuity + density feel =
  device-pass items.
  Handoff: [docs/sprints/sprint-0018-stats-tightening.md](docs/sprints/sprint-0018-stats-tightening.md).
- ✅ **Sprint 19 (first-run tracking-start prompt — owner request) is DONE** (uncommitted
  in the working tree by request; version untouched at 1.3.3/10303 — ships with the next
  bump). **D-S19-1** (supersedes the auto-write half of D-S14-1; Jan-1 stays as fallback):
  a fresh install gets ONE M3 dialog over the day screen (`ui/day/TrackingStartPromptDialog`,
  tags `tracking-start-prompt`, `tracking-prompt-jan1/-today/-custom`) — "Start from
  January 1, <year>" / "Start from today (<date>)" / "Pick a date…" (the shared S10
  full-calendar picker, extracted to `ui/settings/TrackingStartDatePickerDialog.kt`,
  tags unchanged; canceling it returns to the prompt — not an answer). One sentence of
  copy + "change anytime in Settings" (literally pinned). Dismiss = Jan-1 fallback applied
  silently + never re-shown; an unanswered prompt (process death) re-asks. NEVER shown to
  already-initialized devices or upgraders with marks (marked initialized, date left null).
  **D-S19-2:** gate lives in `DayReadingsViewModel` (`showTrackingStartPrompt` +
  chosen/dismissed handlers) — `InitializeTrackingStartUseCase` and its MainActivity hook
  are DELETED, replaced by `domain/ResolveTrackingStartPromptUseCase` (writes nothing on
  a fresh resolve) + `CompleteTrackingStartPromptUseCase` (date + marker). No widget,
  DataStore-key, Room, or manifest changes. 340/340 tests (net +12; 7-test Sprint 1 gate
  untouched), 4 mutations killed (initialized guard, marks guard, choice-writes-marker,
  dismiss-is-Jan-1), each by exactly its intended test; Kover 96.2% on domain/data.
  Prompt strings need owner tone sign-off (table in the handoff).
  Handoff: [docs/sprints/sprint-0019-first-run-prompt.md](docs/sprints/sprint-0019-first-run-prompt.md).
- ✅ **Sprint 20 (stats heading + strip legend — owner request) is DONE** (uncommitted in
  the working tree; version untouched at 1.3.4/10304 — the main session handles the
  release). The stats panel is self-explanatory: a compact **"Year at a glance"** heading
  (labelLarge, `heading()` semantics, tag `stats-heading`) tops the panel, and a legend
  row at the bottom (tag `stats-legend`) keys the strips — 10dp rounded swatches from the
  SAME `StripColors` seam (legend and strips can never disagree; the queued colorblind
  palette re-keys both), red = "Missed", green = "Completed", exactly two entries.
  **D-S20-1 (owner amendment, narrows D-S17-1/D-S17-3):** the owner explicitly chose
  "missed" for the legend — the exemption is EXACTLY the two literal labels
  "Missed"/"Completed" in the legend; all other guilt copy stays banned on screen and in
  speech (ban-scan exempts whole-node exact strings only; strip summaries keep "not
  read"; "Not read" flagged as the alternative at tone sign-off). A11y: legend is one
  merged row spoken after the strip summaries; swatches announce nothing (pinned).
  Budget: panel 290→346dp streaks-on (< ~360 cap), 222→278 off; whole screen ~671 of
  ~828dp — S18 one-screen fit holds. 342/342 tests (net +2; 7-test Sprint 1 gate
  untouched), 4 mutations killed (heading blanked, label reworded, guilt copy outside
  the legend, swatch given speech), each by its intended test; Kover 96.2% on
  domain/data; pipeline green. Heading/legend look = device-pass item; S20 strings need
  owner tone sign-off.
  Handoff: [docs/sprints/sprint-0020-stats-heading-legend.md](docs/sprints/sprint-0020-stats-heading-legend.md).
- ✅ **V3 Sprint A (Bible data foundation — the HARD GATE) is DONE** (uncommitted in the
  working tree; the main session verifies the gate and commits; version untouched at
  1.3.5/10305 — V3 WIP). **The project's second core-IP asset exists and is provably
  correct:** a committed, reproducible read-only KJV `app/src/main/assets/bible/bible.db`
  (66 books / 1,189 chapters / **31,102 verses** + **117 verse-0 superscriptions**;
  `<a>` added-word markup; SHA-256 `ce174e9…29da4909`; ~5.7 MB on disk, **~1.97 MB
  compressed** = well under the +6 MB D-V3-20 budget). Two **genuinely independent** PD KJV
  sources (R-V3-3, checksum-distinct, different lineage): primary = open-bibles
  `eng-kjv.osis.xml` (Haiola/eBible/SWORD; markup-bearing), second = scrollmapper
  `formats/csv/KJV.csv` (e-Sword/bible_databases). The initially-considered eBible USFX was
  **rejected as the same upstream** as the primary (the Sprint-1 re-mirror trap, avoided).
  Importer `tools/build_bible_db.py` (Python stdlib only, byte-deterministic) + the `book`
  table **generated** from `BookCatalog` via `tools/export_book_catalog.py` (never authored,
  USFM codes D-V3-5). **5 primary-corpus text defects** corrected via documented
  `TEXT_OVERRIDES` (1 Chr 11:2 + Ezek 17:24 doubling, Lev 17:8 of/or, Isa 47:11 + Matt 5:30
  if/it typos — all where the independent witness + authentic KJV agree); 2 legitimate
  inter-edition variants kept; full provenance + reconciliation in
  [docs/data/README.md](docs/data/README.md). **Hab 3 finding:** both corpora encode its
  prayer-heading as verse 1, not a verse-0 title — the asset follows both (overrides the
  spec's verse-0 expectation). Read-only `BibleDatabase` (`createFromAsset` +
  `fallbackToDestructiveMigration(false)`, D-V3-15 two-DB isolation) + `VerseEntity`/`VerseDao`
  + `BibleAssetVersion` re-copy-on-bump compare logic (D-V3-8, JVM-pinned) + `bible/` package
  skeleton + `di/BibleModule` Hilt wiring + closed `BibleMarkup`/`MarkupStripper` contract
  (D-V3-6, no `text_plain`). **`BibleTextVerificationTest` (offline `sqlite-jdbc`, 18
  assertions) is GREEN as the release gate** (M-V3-1) — structural invariants, `book`
  reconciliation, second-source verse-count equality, checksum-distinctness, superscriptions
  both-direction + Ps 3/51 text, strip-invariant + added-word floor + closed vocabulary,
  famous-verse pins incl. John 11:35 = "Jesus wept."; **4 mutations killed** (dropped
  superscription, stripped `<a>` floor, corrupted famous verse, dropped verse — asset
  restored byte-identically). New `data-rebuild` CI job re-derives from pinned-SHA sources and
  asserts a `cmp` byte-diff of zero (verified locally). New TEST dep only: `sqlite-jdbc`
  (catalog); **zero net-new runtime deps; no `INTERNET`**. 375/375 tests (33 new; 7-test
  Sprint 1 gate untouched), full pipeline green; Kover floor met. **No reader UI** (Sprint B+).
  Device-pass items: real `createFromAsset` copy + asset-version re-copy on a device.
  Handoff: [docs/sprints/sprint-00A-bible-data-foundation.md](docs/sprints/sprint-00A-bible-data-foundation.md).
- ✅ **V3 Sprint B (the spine: seam, resolver, Portion bridge) is DONE** (uncommitted in the
  working tree; the main session verifies and commits; version untouched). Pure-JVM, NO UI.
  "Given a `Portion` or a reference string, here are the verse_id ranges and the verse text" is
  now callable and exhaustively tested through ONE seam. (1) **The seam is bound and live:**
  `bible/domain/BibleTextSource` (`getVerses(VerseRange): List<VerseText>`) + `VerseText`
  (`canonicalId`/`nativeLabel`/`isTitle`/`markup` — reads `native_label` from the row, D-V3-4,
  never derives the display number); `bible/data/RoomBibleTextSource` maps `VerseDao.getVerses`
  onto it; `di/BibleModule` now binds `RoomBibleTextSource → BibleTextSource` (new abstract
  `BibleBindsModule`). The domain injects the seam ALONE; Room types stay in `bible/data`.
  (2) **`VerseId`** (`encode`/`book`/`chapter`/`verse`/`chapterRange`/`bookRange`, `Long` ids,
  `chapterRange` starts at verse 0 so titles are covered) + **`VerseRange`** (inclusive,
  rejects reversed). (3) **`VerseRef`** with the **verse ∈ [0,999]** invariant (D-V3-9) — the
  `require(verse >= 1)` Psalm-title trap is mutation-pinned (verse-0 title test goes red on the
  regression). (4) **`ReferenceResolver`** (clean-fail, D-V3-10): parses string + OSIS-dotted +
  same-chapter ranges + whole-chapter/whole-book; a 60+-entry alias table (lowercased canonical
  names, numbered-book forms "1john"/"i john", "Psalm"/"Ps", USFM/common abbrevs); **bare "John"
  is always the Gospel, never a numbered John**; ANY malformed input → `null`, never a
  plausible-but-wrong range. **Cross-chapter ranges are NOT supported in V3.0** (no consumer; a
  verse-count-aware end-of-chapter resolution is out of scope) — they clean-fail to `null`
  (pinned). `formatOsis` is a reserved V3.x stub. (5) **`ConsecutiveChapterRuns`** lifted to
  `bible/domain` (D-V3-10): `ProviderUrlBuilder` now delegates to it (its private
  `consecutiveRuns` deleted), so external egress and internal nav share ONE grouping and cannot
  drift; the same-book guard is mutation-pinned (Jude 1 + Rev 2 must not merge). (6)
  **`PortionVerseBridge.rangesFor`** maps each `Reference` INDEPENDENTLY (never assumes a shared
  book), so the Jun 19/Dec 19 two-book portion (2 John + 3 John) yields two ranges across two
  books for free; **`GetChapterUseCase`** ((book,chapter) → `ChapterContent`) and
  **`GetPortionTextUseCase`** (`Portion` → ordered `PortionContent` blocks, M-V3-4 whole-portion
  render incl. the two-book portion) consume the seam. 421/421 tests (46 new; **both data gates
  untouched — the 7-test Sprint-1 plan gate AND the 18-assertion `BibleTextVerificationTest`**),
  **5 mutations killed** (verse∈[0,999] trap, resolver reversed-range clean-fail, resolver
  out-of-range-chapter clean-fail, bridge shared-book assumption, consecutiveRuns cross-book
  merge), each by its intended test, restored in place; Kover 96.2% on domain/data; full
  pipeline green. No UI, no new runtime deps, no manifest/DataStore/Room-schema change. Known
  debt carried forward: the `BibleAssetVersion` startup hook (still deferred to Sprint E device
  pass) and the `exportBookCatalog` Gradle-task wrapping (Jordan follow-up).
  Handoff: [docs/sprints/sprint-00B-spine-resolver-bridge.md](docs/sprints/sprint-00B-spine-resolver-bridge.md).
- ✅ **V3 Sprint C (the reader UI) is DONE** (uncommitted in the working tree; the main session
  verifies and commits; version untouched). **The user can read faithfully-formatted KJV inside
  the app, fully offline, browse any book/chapter, and navigate chapter-to-chapter across book
  boundaries** — over the Sprint B spine, no networking. (1) **The reader screen:**
  `bible/ui/reader/ReaderViewModel` (`@HiltViewModel`; injects `GetChapterUseCase`/
  `GetPortionTextUseCase`; `openChapter(book,ch)`/`openPortion(portion)`; `StateFlow<ReaderUiState>`
  = `Loading | Content(blocks, title, activeVerseId) | Error`) + stateless `ReaderScreen` +
  stateful `ReaderRoute`. (2) **Verse-id-keyed `LazyColumn` (D-V3-12):** every verse a `LazyColumn`
  item keyed by `VerseText.canonicalId` — reads as prose, each verse individually addressable
  (mutation-pinned: same-label verses across chapters don’t collide). (3) **`VerseRenderer`**
  (pure, JVM-testable): closed-tag markup → `AnnotatedString` — `<a>`→italic span (P0,
  mutation-pinned), `<w>` recognized/no span (P1), `<l/>`→newline (P1); render text never drops
  inner words. (4) **Superscription (D-V3-7):** an `isTitle` verse renders as an unnumbered italic
  `heading()`-semantic block, not a numbered verse (mutation-pinned). Verse labels are the seam’s
  `nativeLabel`, **never derived from the id** (D-V3-4, mutation-pinned). (5) **TalkBack** speaks
  `MarkupStripper.strip(markup)`, never raw tags (NFR-V3-C). (6) **Picker (`bible/ui/picker/`):**
  stateless `BookChapterPicker` (66 books grouped OT/NT by `order<=39`, then a chapter grid sized
  to `chapterCount`) inside an M3 `ModalBottomSheet`; ≥48dp rows/cells. (7) **Chapter nav:**
  pure `ChapterNavigator` walks `BookCatalog` order — Prev/Next cross book boundaries
  (Gen 50→Exo 1), bounded at Gen 1 / Rev 22; visible Prev/Next controls. (8) **In-session
  last-read (D-V3-13):** `(bookNo,chapter)` in `SavedStateHandle` (`reader_book_no`/`reader_chapter`);
  restored on init, else Genesis 1. (9) **Empty audio seam (D-V3-14):** `ReaderAudioSlot` bottomBar
  (renders nothing) + `Content.activeVerseId` (always null). `fontScale` inherited free from the
  theme. **Temporary reader entry (Sprint-D-replaced):** a top-bar book-list action (tag
  `open-reader-dev`) pushes a plain `Routes.READER` in the existing single `NavHost` — comment-marked
  `SPRINT C TEMPORARY`; D’s bottom-nav (D-V3-16) + tap-handoff replace it. The two-book portion
  is exercised by `ReaderViewModelTest.openPortion`, not yet tap-wired. 453/453 tests (net +32;
  **both data gates untouched — Sprint-1 plan gate = 7, `BibleTextVerificationTest` = 18**),
  4 load-bearing mutations killed (added-word italic span, superscription isTitle branch,
  nativeLabel-not-derived, keyed-by-canonicalId), each restored in place; Kover 96.2% on
  domain/data; full pipeline green. Reading feel / markup look / picker on-glass = device-pass
  items (E). New strings need owner tone sign-off (table in the handoff).
  Handoff: [docs/sprints/sprint-00C-reader-ui.md](docs/sprints/sprint-00C-reader-ui.md).
- Next up: **V3 Sprint D — nav restructure + integration** (`sprint-00D-nav-integration`):
  `RootScaffold` + co-equal `NavigationBar` (Schedule | Bible, Schedule start, D-V3-16); nested
  Schedule/Bible graphs replacing the temporary `Routes.READER` push; the Robolectric
  nav-regression suite (D-V3-17, R-V3-5); `BibleProvider.IN_APP` promotion +
  `ReadingDestination.InApp(portion)` + the `DayReadingsRoute` tap-handoff (calls
  `ReaderViewModel.openPortion`); Settings teaser→real value; the Sprint-19 first-run
  reading-destination question (D-V3-19); the bundle-size CI check (D-V3-20). Resolve owner
  OQ-1/2/3 before D lands. (V2.x release prep remains queued, owner-scheduled independently.)
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
