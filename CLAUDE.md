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

This is a **standalone, self-contained repo** — it does not depend on any other local project.

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
- ✅ **Sprint 21 (date-picker UX — one-tap select + cross-year month swipe) is DONE**
  (numbered track, parallel to the V3 lettered sprints; owner-redirected from the queued
  v2.x release prep; uncommitted in the working tree; version untouched at 1.3.5/10305).
  Two backlog items, both picker-only. (1) **One-tap selection (BACKLOG #7):** tapping any
  day cell in `ui/datepicker/DayDatePickerDialog` selects that full date and closes the
  dialog in one tap — the confirm button + `date_picker_confirm` string are gone (selection
  is non-destructive: it only navigates the day pager, so no confirm is needed); Cancel/
  dismiss kept. (2) **Cross-year month swipe (BACKLOG #6):** the months now ride a
  `HorizontalPager` (`monthForPage` + ±3,000-month window, mirroring the day-pager idiom),
  so swipes/chevrons move month-by-month freely across year boundaries (Dec 2026 → Jan 2027
  and back) — chevrons no longer disabled at Jan/Dec. **D-S21-1:** the picker is no longer
  year-anchored (supersedes the pinned-year part of D-S5-3 *for the picker only*; `year`
  param + `withYear` anchoring removed, opens on the displayed date directly) — the day
  pager and full-date progress keying are unaffected. Completion dots (green/red/neutral),
  Feb-29, today ring, and a11y carry over at every reachable month (all key to full dates).
  Tags: `date-picker-confirm` removed, `picker-month-pager` added; all others retained.
  520/520 tests (net +3; the three data/Room gates untouched — plan 7,
  BibleTextVerificationTest 18, BibleDatabaseRoomOpenTest 5), 2 mutations killed
  (`monthForPage` offset zeroed → 5 tests; day-cell onClick no-op → 5 tests), each restored
  in place; Kover ≥70% floor holds; a11y gate green. No new strings (one removed).
  Device-pass: swipe feel, one-tap accuracy on glass.
  Handoff: [docs/sprints/sprint-0021-date-picker-ux.md](docs/sprints/sprint-0021-date-picker-ux.md).
- ✅ **Sprint 22 (persistent tray notification — owner request) is DONE** (numbered V2
  track; owner-redirected from the queued v2.x release prep; uncommitted in the working
  tree; version untouched — the main session verifies + commits). A SEPARATE feature from
  the S12 popup reminder: an **always-present, ongoing** notification that shows the day's
  three readings in the tray and refreshes to the new day at **01:00 local**. Off by default
  (D-S21-5/D-S22-5), enabled in Settings → Reminders ("Keep readings in the tray"); the S12
  POST_NOTIFICATIONS flow is shared (the prompt routes to whichever toggle requested it).
  **D-S22-1:** own channel `persistent_readings` at IMPORTANCE_LOW (silent), `setOngoing(true)`
  (non-dismissible, NO foreground service needed/used — D-S22-2), `PRIORITY_MAX` +
  `CATEGORY_STATUS` to bias ranking high *within* the low channel (Android/OEM own final
  placement — "close to the top" is best-effort, not guaranteed). BigTextStyle lists the
  three references (same `ReadingFormatter`); tap opens the app on today. **D-S22-4:** content
  decided at post/refresh time via `GetDayReadingsUseCase`; Feb 29 shows "No readings
  scheduled today" (stays present — NOT suppressed; unlike the popup there is no completion
  suppression, the readings are always shown). **D-S22-3:** a DEDICATED 01:00 alarm (request
  code 2004, `ACTION_REFRESH_PERSISTENT`), reusing the S12 inexact `setAndAllowWhileIdle`
  pattern — re-armed after each fire, on BOOT_COMPLETED, and on every app launch via the
  existing `RescheduleAlarmsUseCase` hook; NOT the 00:00 widget-rollover alarm (owner said
  1am). New: `reminders/PersistentNotifier`(+`SystemPersistentNotifier`),
  `domain/RefreshPersistentNotificationUseCase` (the one home for the enable/disable +
  Feb-29 rule); `ReminderScheduler` gained `schedule/cancelPersistentRefresh()`,
  `AlarmTimes.PERSISTENT_REFRESH_TIME`, a third receiver action; DataStore key
  `persistent_notification_enabled` (default false). No new permissions, no new receivers,
  no Room/manifest changes; receivers stay non-exported. 542/542 tests (net +22; the three
  data/Room gates untouched — plan 7, BibleText 18, RoomOpen 5), 4 mutations killed
  (disabled-gate, 01:00 re-arm, Feb-29 body, persistent reschedule-on-boot), each by its
  intended test, restored in place; Kover 95.1% on domain/data; full pipeline green. S22
  strings need owner tone sign-off (table in the handoff). Device-pass: real 01:00 fire,
  reboot persistence, tray ranking/expansion, ongoing non-dismissibility.
  Handoff: [docs/sprints/sprint-0022-persistent-notification.md](docs/sprints/sprint-0022-persistent-notification.md).
- ✅ **Persistent notification ON by default (owner tweak to S22, `2026-06-15`) is DONE**
  (uncommitted in the working tree; version untouched — the main session verifies + commits).
  **D-S22-5 amended: default flipped off → on.** The persistent tray notification
  (Settings → "Keep readings in the tray") is now ON for fresh/never-touched installs;
  `SettingsRepositoryImpl.persistentNotificationEnabled` reads `?: true` (was `?: false`,
  same absent-key idiom as the S18 `show_streaks` flip) — an explicitly stored OFF survives,
  never re-defaulted. **The permission wrinkle:** on-by-default needs POST_NOTIFICATIONS
  (API 33+), ungranted on a fresh install. New `domain/ShouldRequestNotificationPermission
  OnLaunchUseCase` (JVM-tested) returns true iff persistent-enabled AND permission missing
  (reuses the S12 `NotificationPermissionChecker`, always-granted below API 33 so no prompt
  there — it just posts). `MainActivity.onCreate` hosts the `RequestPermission` launcher and,
  after `rescheduleAlarms()`, fires the system prompt **once per launch** when the use case
  says yes; on grant it re-runs `rescheduleAlarms()` so the notification posts immediately, on
  denial nothing changes (setting stays on, simply can't post — no crash, no nag; mirrors the
  S12 reminder's denial). **Sequencing:** the OS permission dialog is its own surface (not a
  third in-app dialog), fired after the alarm reschedule — deliberately NOT chained behind the
  day screen's tracking-start + reading-destination first-run dialogs. Disabling still cancels
  the 01:00 alarm + dismisses the notification; 01:00 refresh, boot/launch re-arm, Feb-29 body,
  BigText readings — all unchanged. No new permissions/receivers/Room/manifest changes. 547/547
  tests (net +5; the three data/Room gates untouched — plan 7, BibleText 18, RoomOpen 5),
  **4 mutations killed** (default `?:true`→`?:false`; read-body ignores-stored; drop
  persistent-enabled gate; drop !granted gate), each by its intended test, restored in place;
  Kover 95.1% on domain/data; full pipeline green. Version untouched. **Device-pass:** fresh
  API 33+ install shows the prompt at first launch and posts the notification after granting
  POST_NOTIFICATIONS (and no crash / toggle still on after denying).
  Addendum in the handoff: [docs/sprints/sprint-0022-persistent-notification.md](docs/sprints/sprint-0022-persistent-notification.md).
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
- ✅ **V3 Sprint D (nav restructure + integration) is DONE** (uncommitted in the working tree; the
  main session commits + handles the release; version untouched at 1.3.5/10305 — needs a bump to
  ship). **Tapping today's reading now opens it in the in-app reader, and Schedule + Bible are two
  co-equal places.** The app root is `ui/navigation/RootScaffold` — a co-equal `Schedule | Bible`
  `NavigationBar` over two nested graphs (`Graph.SCHEDULE` start = day pager + pushed Settings;
  `Graph.BIBLE` = reader); `switchTab` preserves each tab's back-stack across a switch (D-V3-16,
  D-D-3, U18). The Sprint-C temporary `Routes.READER` push + `open-reader-dev` action are deleted.
  **`BibleProvider.IN_APP`** is a real, top-of-list selectable provider (multiRefCapable, no app;
  D-V3-18); `ReadingDestination.InApp(portion)` and `OpenReferenceUseCase`'s IN_APP branch carry
  the whole portion (not a URL); Web/MySword paths byte-for-byte unchanged (R-V3-4). The
  cross-graph tap-handoff (D-D-1): `DayReadingsViewModel` publishes the tapped portion to an
  `@ActivityRetainedScoped` `ReaderHandoff`, raises `openReaderEvents`; the Route switches to the
  Bible tab; `ReaderViewModel` consumes the pending portion and renders it. **Settings** "Open
  readings in" — the S14 disabled teaser is now the enabled "Read in this app" option at the top.
  **First-run reading-destination question** (fresh installs only, no marks; in-app NEVER a silent
  default, dismiss re-asks) + a **separate one-time upgrade note** for existing users (marks
  present; their external choice preserved unless they tap "use it now") — two mutually-exclusive
  gates split on `hasAnyMarks` (D-V3-19, OQ-2, D-D-4). New DataStore markers
  `reading_destination_prompt_completed` + `upgrade_note_shown` (no Room/schema change). **CI**
  gained a bundle-size gate on `release-bundle` (fails > 12 MB = pre-V3 ~5.7 + ~6 MB budget,
  D-V3-20). New: nav-regression Robolectric suite retires part of the Sprint-6 `AppNavHost` JVM
  debt (Schedule=start, both tabs + every screen reachable, back-stack preserved across a switch).
  New test dep: `androidx.navigation:navigation-testing`. 487 tests (net +34; **both data gates
  untouched — plan gate = 7, `BibleTextVerificationTest` = 18**), full pipeline green, Kover 95.1%
  on domain/data, **4 load-bearing mutations killed** (IN_APP destination branch, first-run show
  gate, upgrade-note show-once gate, upgrade-vs-fresh-install has-marks split), each restored in
  place. Nav glyph for Bible = `AutoMirrored.List` (MenuBook absent from icons-core; OQ-3
  placeholder). Device-pass: one-screen-fit WITH the ~80dp bottom bar (R-V3-1, VD-T9), tab-state
  preservation on glass, the reading-tap→reader handoff feel. S-D strings await owner tone
  sign-off.
  Handoff: [docs/sprints/sprint-00D-nav-integration.md](docs/sprints/sprint-00D-nav-integration.md).
- ✅ **V3 Sprint E (V3.0 hardening + release readiness) is DONE** (uncommitted in the working
  tree; the main session/owner commits + cuts the release; version untouched at 1.3.5/10305 —
  **recommended bump to 1.4.0/10400** per D-S9-3, NOT applied this sprint). **V3.0 is
  release-ready pending the owner's device pass + string/presentation sign-offs** — no new
  reader features; this sprint hardened, wired the last deferred piece, and assembled the
  owner's sign-off artifacts. (1) **Deferred asset-version startup hook now wired (VE-T0,
  D-V3-8):** `bible/data/BibleAssetGate` (Singleton) runs **inside `BibleModule.provideBibleDatabase`
  BEFORE `.build()`** — that provider only ever resolves off-main (the bible DB is first touched
  from a suspend query on Room's executor), so its blocking DataStore read/write is
  StrictMode-clean. On a bumped `BibleAssetVersion.ASSET_CONTENT_VERSION` it deletes the copied
  `bible.db`/`-wal`/`-shm` so `createFromAsset` re-copies the corrected asset, then persists the
  new version under a new `bible_asset_content_version` DataStore key (`DataStoreBibleAssetVersionStore`
  over the existing shared store — NOT inside the read-only bible.db, the D-V3-8 converse rule);
  no `SettingsRepository` interface change. 3 Robolectric wiring tests; 2 load-bearing mutations
  killed (comparison flip, skipped delete). Real on-device re-copy = device-pass. (2) **JVM
  hardening confirmed (VE-T2):** StrictMode review — the only new I/O path (bible read +
  asset-gate) is off-main; **no `INTERNET` in the merged release manifest** (offline identity
  NFR-V3-A holds; ACCESS_NETWORK_STATE/WAKE_LOCK etc. are pre-V3 Glance/WorkManager library
  merges, not INTERNET, no network grant); a11y gate green incl. `ReaderScreen` + `BookChapterPicker`
  (C/D extension); R8 keeps the `VerseEntity` members (defense-in-depth) + the InputMerger rule
  intact; bundle-size gate green (**7.67 MB AAB** vs the 12 MB ceiling). (3) **Owner artifacts in
  the handoff:** the consolidated owner-runnable **device-pass checklist** (A real createFromAsset
  copy + re-copy; C reading-feel/markup/M-V3-2; D one-screen-fit-with-bottom-bar/tab-state/
  tap→reader), the **full V3 strings table** for tone sign-off (reader/picker/nav/first-run/upgrade
  + the OQ-3 "Bible"/"Schedule" label revisit), and the version-bump + whatsnew recommendation.
  (4) **AR-1 recorded** (accepted UK Crown-copyright risk) in `docs/data/README.md`. (5) **whatsnew
  draft updated** for V3.0. 490 tests (net +3; **both data gates untouched — plan gate = 7,
  `BibleTextVerificationTest` = 18**), full pipeline green from clean, `bundleRelease` clean, Kover
  95.1% on domain/data. **Blocking V3.0 release:** owner's consolidated device pass, M-V3-2
  presentation sign-off, OQ-3 + S-A..S-D string tone sign-offs, then the version bump + tag-to-Play
  rollout (owner/main session).
  Handoff: [docs/sprints/sprint-00E-v3-hardening-release.md](docs/sprints/sprint-00E-v3-hardening-release.md).
- Next up: **release cut** — owner runs the device pass + sign-offs from the Sprint E handoff,
  the main session applies the 1.4.0/10400 bump and the closed-track tag-to-Play rollout. (V2.x
  release prep remains queued, owner-scheduled independently.)
- 🐛 **P0 FIX — KJV reader load failure (`sprint-00F-kjv-load-fix`) is DONE** (owner-reported on
  device: the V3 in-app reader showed "couldn't load this chapter" for every book+chapter even
  though JVM tests were green). **Root cause:** the prebuilt `app/src/main/assets/bible/bible.db`
  had tables `translation`/`book`/`verse` but **no `room_master_table`**, and its `verse` DDL
  carried a foreign key + an `idx_verse_book_ch` index that `VerseEntity` does not declare — so
  Room's `createFromAsset` schema validation threw `IllegalStateException: Pre-packaged database
  has an invalid schema` on the first query, surfacing as the reader's load-failed state.
  Nothing caught it because `BibleTextVerificationTest` opens the `.db` via the sqlite-jdbc driver
  (bypassing Room) and the reader/use-case tests fake `BibleTextSource` — Room never opened the
  real asset in any test. **Fix:** `tools/build_bible_db.py` now emits the `verse` table with the
  EXACT DDL Room generates (no FK, no secondary index — only the implicit PK autoindex, which
  Room ignores) plus a `room_master_table` carrying Room's identity hash
  `8144e1bc57f05006d1a15856ac762552` (`ROOM_IDENTITY_HASH` constant, re-derivable from the
  generated `BibleDatabase_Impl`); the asset was REGENERATED from the script (not hand-edited),
  so the `data-rebuild` byte-diff gate still reproduces it. **`exportSchema` stays `false`** on
  `BibleDatabase` (read-only asset DB; the hash is a pinned build artifact, the new test is the
  drift guard — no checked-in schema JSON). **Test gap closed:** new
  `BibleDatabaseRoomOpenTest` (Robolectric, real SQLite) opens the SAME `BibleDatabase` via the
  SAME `createFromAsset` builder as `BibleModule` and reads Gen 1:1 / John 3:16 / John 11:35 /
  Ps 3 verse-0 superscription through `RoomBibleTextSource`+`VerseDao` — proven to FAIL against
  the broken asset ("invalid schema") and PASS after the fix. Verse content is byte-identical
  before/after (only Room metadata + DDL changed; 31,102 verses + 117 superscriptions intact).
  495 tests (net +5; **both data gates untouched — plan gate = 7, `BibleTextVerificationTest`
  = 18**), full pipeline green, Kover 95.1% on domain/data, asset reproduces byte-identically.
  No version bump (main session ships). Known follow-up (non-blocking, queued): `getChapter`
  lost its dedicated index — declare `@Index` on `VerseEntity` if a chapter-open profile ever
  shows it (re-derives the hash).
  Handoff: [docs/sprints/sprint-00F-kjv-load-fix.md](docs/sprints/sprint-00F-kjv-load-fix.md).
- ✅ **Picker grid redesign (owner UI tweak, `sprint-00G-picker-grid`) is DONE** (uncommitted
  in the working tree; no version bump). The in-app reader's book/chapter picker now fits all
  66 books on one screen: step 1 is a dense `LazyVerticalGrid(GridCells.Fixed(5))` of
  **abbreviated** book labels (reusing the one catalog `Book.displayAbbrev` — "Gen", "2Jo";
  NO second abbreviation table, one-catalog discipline held) with full-width OT/NT section
  headers spanning the grid (`GridItemSpan(maxLineSpan)`) so the testament grouping survived
  the single-screen goal. Step 2 (chapters) reuses the SAME grid idiom (Fixed-5, left-to-right
  top-to-bottom) and scrolls within the sheet for high-chapter books (Psalms 150). A11y: each
  cell shows the abbreviated/number label but **speaks the full name** — cells carry a
  `contentDescription` ("Genesis"; chapter cell "Genesis chapter 3" via new
  `picker_chapter_cd`), the inner label `Text` is `clearAndSetSemantics{}` so TalkBack hears
  "Genesis" once, never "Gen Genesis". Same modal bottom sheet, same "All books" back
  affordance, same testTags (`picker-book-N`/`picker-chapter-N`/`picker-book-list`) so the
  AccessibilityGateTest stays green; ≥48dp cells (`heightIn(min=48.dp)`). 497 tests (net +2;
  all three data/Room gates UNTOUCHED — plan = 7, BibleTextVerificationTest = 18,
  BibleDatabaseRoomOpenTest = 5), full pipeline green, Kover 94.9% on domain/data. **Device-pass
  items:** single-screen fit at default font, cell density / abbreviation legibility on glass,
  Psalms-150 scroll feel — none JVM-provable.
  Handoff: [docs/sprints/sprint-00G-picker-grid.md](docs/sprints/sprint-00G-picker-grid.md).
- ✅ **Sprint H (reader chapter-swipe + per-verse external links + schedule cleanup — owner UI
  request, `sprint-00H-reader-swipe-verse-links`) is DONE** (uncommitted in the working tree; no
  version bump — the main session verifies + commits). Five owner changes:
  (1) **Reader chapter SWIPE** replaces the Prev/Next buttons — the reader is now a
  `HorizontalPager` over the WHOLE canon (`GlobalChapterIndex`, **D-H-2**: page == global chapter
  index, `TOTAL_CHAPTERS` = 1189, Genesis 1 = page 0, Revelation 22 = last). Continuous swipe
  crosses book boundaries (Genesis 50 → Exodus 1) and is bounded at Gen 1 / Rev 22 for free (the
  pager can't scroll past the first/last page). Each page renders the existing verse-id-keyed
  `LazyColumn` (markup, superscriptions, open-at-top reset). **D-H-1:** `GlobalChapterIndex`
  adjacency is pinned field-by-field against the mutation-pinned `ChapterNavigator` at every
  index. **D-H-7:** a Schedule reading tap (IN_APP) lands the pager on the portion's FIRST chapter
  (single-chapter-per-page supersedes the prior multi-block portion render); the `ChapterNavBar`
  and `ReaderViewModel.openChapter/openPortion/uiState` are gone, replaced by per-page
  `uiStateForPage(page)`. The reclaimed button row is now text space.
  (2) **Schedule: whole-day button REMOVED** (`DayContent`/`DayReadingsScreen`) — the three
  per-reading checkboxes are the only mark affordance. `MarkWholeDayUseCase` + the VM's
  `onMarkWholeDay` are KEPT (the widget seam); only the button UI is gone.
  (3) **Schedule: "All readings done" badge REMOVED** at every state (supersedes the D-S16-2 part
  that kept it) — the checked checkboxes are the only completion cue.
  (4) **Reader: tap any verse → open that exact book+chapter+VERSE in the user's external Bible
  app** (BACKLOG #5). Verse-level URLs per provider (`ProviderUrlBuilder.buildVerse`, **D-H-5**,
  reusing the existing token columns — no new catalog): BLB `/kjv/gen/1/1/`, Bible Gateway
  `?search=Genesis 1:1&version=KJV`, YouVersion `GEN.1.1.KJV`, MySword numeric `{order}.{ch}.{verse}`.
  `OpenVerseUseCase` reads the stored provider at tap time and returns the S15 `ReadingDestination`
  (Web / MySwordApp+BLB-verse fallback). **D-H-4 (IN_APP fallback):** a verse-tap-out has no
  external target when the user reads in-app, so it falls back to BLB at that verse — the persisted
  IN_APP choice is NEVER rewritten. **D-H-3:** the verse coordinate is the canonical decode of the
  `canonicalId` (`VerseId.verse`), NOT the display label. A superscription tap (verse 0) clamps to
  verse 1. Each verse is a ≥48dp `Role.Button` tap target speaking "Open <Book> <ch>:<verse>. <text>".
  **Verse URLs live-verified 2026-06-15** across providers and awkward books (Psalms incl.
  Ps 119:176, Philemon, 2/3 John) — recorded in docs/data/provider-link-checks.md; the committed
  suite stays OFFLINE (`ProviderUrlBuilderTest`/`OpenVerseUseCaseTest` pin every shape; MySword is
  the owner's on-device pass per S15). No INTERNET permission.
  517 tests (net +19; **all three data/Room gates UNTOUCHED — plan = 7, BibleTextVerificationTest =
  18, BibleDatabaseRoomOpenTest = 5**), full pipeline green, Kover 95.2% on domain/data,
  **5 load-bearing mutations killed** (global-index book-boundary off-by-one, BLB verse segment,
  IN_APP→BLB fallback, verse-0 clamp, VM canonical-verse decode), each restored in place.
  **Orphaned strings** (now unused in main, left in place — removable debt): `mark_whole_day_done`,
  `unmark_whole_day`, `day_progress`. **Device-pass items (NOT JVM-provable):** chapter-swipe feel
  across book boundaries + at the Gen-1/Rev-22 bounds; the reclaimed-space reader layout; verse-tap
  on glass (target accuracy, the right external app opening at the right verse, MySword in-app).
  Strings for tone sign-off: the verse-tap spoken label "Open <Book> <ch>:<verse>. <text>".
  Handoff: [docs/sprints/sprint-00H-reader-swipe-verse-links.md](docs/sprints/sprint-00H-reader-swipe-verse-links.md).
- ✅ **Sprint I (reading-portion view — Phase 1, multi-chapter combined page) is DONE**
  (uncommitted in the working tree; the main session verifies + commits; version untouched at
  1.3.5/10305 — needs a bump to ship). **Phase 1 only** per docs/features/reader-portion-view.md;
  the Psalm-119 verse-range track (Phase 2, a plan-data change) is OUT of scope and the plan
  data/schema were NOT touched. **Tapping a multi-chapter reading on the Schedule now opens it as
  ONE combined page in the reader, and swiping out and back is consistent.** The reader now has
  TWO contexts (D-I-1, sealed `bible/ui/reader/ReaderContext`): **Browse** (Bible tab / picker) =
  the Sprint-H single-chapter swipe over `GlobalChapterIndex` (1189 pages, UNCHANGED); **Reading**
  (a Schedule reading tap) = a portion-anchored pager over the new pure
  `bible/ui/reader/ReadingPagerIndex(portion)` — the portion's contiguous global-chapter span
  `[first…last]` COLLAPSES to ONE atomic page at index `first`, flanked by the single chapters
  before (pages `0..first-1`, identity) and after (pages shifted left by `collapsedSpan = last-first`
  so the collapsed chapters never reappear); `pageCount = TOTAL_CHAPTERS - collapsedSpan`, Gen-1/
  Rev-22 bounds free. The portion page renders the WHOLE portion as ordered blocks via the revived
  `GetPortionTextUseCase` (`ReaderViewModel` re-injects it). Single-chapter readings (776 of the
  plan) are `collapsedSpan == 0` = behaves exactly like Browse. **Two-book Jun 19/Dec 19 (2 John +
  3 John) are globally adjacent → one contiguous page for free.** **Consistency (the owner's core
  ask, pinned):** swipe right from the portion → the next single chapter after `last` (James 4–5 →
  1 Peter 1 since James ends at ch 5; James 1–2 → James 3); swipe back left → the SAME combined
  portion page (never the portion's last chapter alone), because the portion is one fixed page
  index. **D-I-2 (OQ-A, owner-resolved):** tapping the Bible **tab** always resets to single-chapter
  Browse at the last-read chapter; only a Schedule reading tap enters Reading; the picker also forces
  Browse. Implemented via two mutually-exclusive signals on the shared `@ActivityRetainedScoped`
  `ReaderHandoff` — `request(portion)` (→ Reading, the existing D-D-1 path) vs the new
  `requestBrowse()` (→ Browse), the latter raised by a new `ui/navigation/RootViewModel` from the
  nav bar's Bible-tab click; a reading tap supersedes a stale browse request and vice-versa. The
  route rebuilds the `PagerState` per context via `key(...)` so the page-index spaces never collide.
  **Per-verse external tap-out (Sprint H) works UNCHANGED inside the combined page** — each verse
  keeps its canonical id (James 2:3 in the James 1–2 page taps out to jas/2/3, not the first
  chapter). Supersedes D-H-7 (reading-tap-lands-on-first-chapter) for the Reading context. New:
  `ReadingPagerIndex`, `ReaderContext`, `RootViewModel`; changed: `ReaderViewModel` (two contexts +
  per-context page cache cleared on switch + last-read tracks the underlying single chapter, D-I-4),
  `ReaderRoute` (context-driven pager), `ReaderHandoff` (+browse signal), `RootScaffold` (Bible-tab
  reset). NO plan-data/schema, Room, DataStore, manifest, or new-dependency changes. 568 tests
  (net +21; **all three data/Room gates UNTOUCHED — plan = 7, BibleTextVerificationTest = 18,
  BibleDatabaseRoomOpenTest = 5**), full pipeline green, Kover 95.3% on domain/data, a11y gate
  green (7/7), **5 load-bearing mutations killed** (flank shift dropped, page-count not shrunk,
  combined-render branch disabled, resetToBrowse no-op, portionLastGlobal from first ref), each
  restored in place. **Device-pass items (NOT JVM-provable):** combined-page scroll feel for 3–5-
  chapter portions; the swipe-out-and-back feel on glass; the tab-reset-to-Browse behaviour.
  Handoff: [docs/sprints/sprint-00I-reading-portion-view.md](docs/sprints/sprint-00I-reading-portion-view.md).
- ✅ **Sprint J (Psalm 119 sub-chapter verse ranges — Phase 2 of the reading-portion work) is
  DONE** (uncommitted in the working tree; the main session verifies + commits; version untouched
  at 1.3.5/10305). **The reading plan now encodes Psalm 119's four-day verse division as
  gate-verified trusted IP, and the app renders those ranges everywhere.** The four Mar 9-12
  stream-2 days carry verse windows — **Psalms 119:1–40 / 41–80 / 81–128 /
  129–176** (owner-confirmed from the Bible Companion booklet; both plan sources agree
  day-by-day; they tile verses 1..176 exactly: 40+40+48+48). Schema **v1 → v2**. Two tracks:
  (A) **data correction** — four windowed refs in BOTH `reading_plan.json` (canonical) AND
  `reading_plan_verify.json` (independent second source), `schemaVersion: 2`, extraction scripts
  (`tools/extract_*.py`) now EMIT `verseStart`/`verseEnd` (they previously detected and discarded
  the suffix) so the asset stays script-reproducible; provenance + reconciliation recorded in
  docs/data/README.md (the "deferred" verse-fidelity note is now RESOLVED). (B) **app support** —
  **D-SCHEMA-1** optional `RefDto.verseStart?/verseEnd?`; **D-MODEL-1** new planner-domain
  `ReferenceVerses(start,end)` on `Reference` (chapter-relative ints, NEVER the bible spine's
  `VerseRange` — `domain`/`data` stay free of any `bible/` dep; the verse_id conversion lives in
  `PortionVerseBridge`), loader validates (both-present, 1≤start≤end) + bumps
  `SUPPORTED_SCHEMA_VERSION` to 2; **ReadingFormatter** renders "Psalms 119:1–40" (abbreviated
  "Psa 119:1–40"; single-verse "Psalms 119:7"; a windowed ref is its own run) — all four
  collapsed-reference surfaces (Schedule card, widget tiers, reminder, persistent tray) flow through
  it, no per-surface change; **D-READER-1** `PortionVerseBridge.rangesFor` maps a windowed ref to the
  exact verse_id `VerseRange` so `GetPortionTextUseCase` returns ONLY the in-range verses (the reader
  shows verses 1–40 and no others on Mar 9, JVM-proven over a window-aware fake source).
  **THE GATE (`ReadingPlanVerificationTest`) grew 7 → 11**: schema pin = 2 (D-SCHEMA-2),
  every-windowed-ref-well-formed (end ≤ chapter verse count via the committed
  `bible/kjv_verse_counts.csv` witness — D-SCHEMA-3, Ps 119 = 176), the **four days tile
  1..176 exactly** (THE verse-level coverage invariant), second-source range equality, and the
  **"only Psalm 119 is windowed"** audit pin (exactly 4 windowed refs, all Mar 9-12 stream-2
  Psalms 119). 590 tests (net +22; **the other two data/Room gates UNTOUCHED —
  BibleTextVerificationTest = 18, BibleDatabaseRoomOpenTest = 5**), full pipeline green, Kover 95.4%
  on domain/data, a11y gate 7/7, **8 mutations killed** (4 data: tiling gap, range over-bound,
  non-Ps119 window, schema revert; 4 code: ReferenceVerses require, loader lone-bound, bridge
  windowing, formatter run-break), each by its intended test, restored in place. No version bump, no
  new deps/permissions, no Room/manifest/DataStore change. **D-UI-1 tone sign-off:** the rendered
  reference is computed (no static string) — owner to confirm "Psalms" (plural, catalog) vs
  "Psalm" (singular, as the booklet) and the en dash. Device-pass: the window's look in the Schedule
  card / widget tiers / notification, and the reader showing exactly verses 1–40 on glass.
  Handoff: [docs/sprints/sprint-00J-psalm-119-verse-ranges.md](docs/sprints/sprint-00J-psalm-119-verse-ranges.md).
- ✅ **Psalms singular/plural display fix (owner UI tweak, `sprint-00K-psalm-singular`) is DONE**
  (uncommitted in the working tree; the main session verifies + commits; no version bump — display-only).
  **D-UI-2 (resolves the Sprint J D-UI-1 open question — owner chose singular for one chapter):**
  `ReadingFormatter` now renders **"Psalm" (singular)** when a collapsed reference covers exactly
  **one chapter** of Psalms — single chapter with or without a verse window: "Psalm 23",
  "Psalm 119:1–40", "Psalm 119:7" — and keeps **"Psalms" (plural)** for multi-chapter runs
  ("Psalms 1–2", "Psalms 149–150"). Psalms-specific (the only book with a singular form); all
  other books and all existing formatting (en-dash ranges, multi-book Jun 19/Dec 19 portion, the
  windowed-ref-is-its-own-run rule) are byte-for-byte unchanged. Applies to all four collapsed-
  reference surfaces (Schedule card, widget tiers, reminder, persistent tray) for free — they all
  flow through `format`. **Abbreviated form left unchanged ("Psa" for both singular and plural** —
  reads fine either way, D-UI-2, pinned by test). The branch lives in a single
  `ReadingFormatter.displayBookName` helper keyed on `run.size == 1` (a size-1 run IS one chapter)
  AND the resolved name == "Psalms"; only `Book::canonicalName` ("Psalms") triggers it, never
  `Book::displayAbbrev` ("Psa"). **The in-app reader's "Psalms 23" titles were OUT of scope this
  sprint** (it built its own titles from `book.canonicalName`, independent of `ReadingFormatter`) —
  **closed by the `sprint-00L-psalm-reader` follow-up below.** NO plan-data change (the data still
  says book=Psalms; display-only). 596 tests
  (net +6; **all three data/Room gates UNTOUCHED — plan gate = 11, BibleTextVerificationTest = 18,
  BibleDatabaseRoomOpenTest = 5**), full pipeline green, Kover 95.4% on domain/data, **2 mutations
  killed** (always-plural → 8 single-chapter tests red; always-singular → 2 multi-chapter tests red),
  each restored in place. No new deps/permissions, no Room/manifest/DataStore change.
- ✅ **Psalms singular/plural in the in-app reader (owner UI follow-up, `sprint-00L-psalm-reader`)
  is DONE** (uncommitted in the working tree; the main session verifies + commits; no version bump —
  display-only). Closes the reader gap Sprint K left open: the V3 reader built its OWN titles from
  `book.canonicalName` and still showed "Psalms 23", inconsistent with the Schedule. Now **header,
  top-bar single-chapter title, portion title, and the verse-tap spoken label** all apply D-UI-2:
  a single Psalms chapter ⇒ **"Psalm N"** (incl. a verse-windowed Psalm 119 day — the window does
  not change the chapter count, so "Psalm 119"); a multi-chapter Psalms portion ⇒ **"Psalms M–N"**
  (en dash); the two-book Jun 19/Dec 19 portion keeps its shape (per-block singular). **No second
  source of truth:** the rule is extracted ONCE into public `ReadingFormatter.singularizeBookName(
  canonicalName, singleChapter)` and `ReadingFormatter`'s private `displayBookName` now delegates to
  it — the reader (`bible/ui/reader/ReaderViewModel` + `ReaderScreen`) imports and calls that same
  function, so Schedule and reader cannot drift. Abbreviated form ("Psa") still untouched; all other
  books byte-for-byte unchanged. Display-only — NO plan-data/asset/Room/DataStore/manifest/version/
  dependency change. Reader pins flipped "Psalms 23"→"Psalm 23"; new pins: `ReaderScreenTest` singular
  header (`reader-header-19-23` == "Psalm 23"; nothing on screen says "Psalms 23"),
  `ReaderViewModelTest` singular single-chapter title + plural multi-chapter portion title.
  AccessibilityGateTest unchanged (its Psalms assertions are substring, not the full string). 599
  tests (net +3; **all three data/Room gates UNTOUCHED — plan gate = 11, BibleTextVerificationTest =
  18, BibleDatabaseRoomOpenTest = 5**), full pipeline green, Kover 95.4% on domain/data, **2 mutations
  killed** (disabling `singularizeBookName` reddens the reader's singular pins AND the Schedule's
  `ReadingFormatter` pins — proving the single source of truth; flipping the multi-chapter portion
  branch to `singleChapter = true` reddens only the plural portion pin), each restored in place.
  Note: docs/features/psalm-singular-reader.md. Device-pass: the singular header/title/spoken label
  on glass.
- ✅ **Schedule reading-tile hint reflects the selected provider (owner UI fix,
  `sprint-00M-tile-hint-provider`) is DONE** (uncommitted in the working tree; the main session
  verifies + commits; no version bump — display-only). The small hint line under each Schedule
  reading was hardcoded "Opens %1$s on Blue Letter Bible"; it now reflects the user's selected
  **"Open readings in"** provider, **reactively** (change it in Settings and the tiles update live).
  **Threading:** `DayReadingsViewModel.selectedProvider` = `settingsRepository.bibleProvider`
  `stateIn`'d (seeded `BibleProvider.DEFAULT`, no flicker) → collected in `DayReadingsRoute` →
  new `selectedProvider` param on `DayReadingsPagerScreen` (default BLB for tests/previews) →
  `provider` on `DayContent`/`ReadingCard`. **Per-provider hint strings with natural prepositions**
  (replace the single `reading_open_hint`): `reading_open_hint_inapp` "Opens %1$s **in this app**",
  `_blb` "…on Blue Letter Bible", `_gateway` "…on Bible Gateway", `_youversion` "…on YouVersion",
  `_mysword` "…**in** MySword". Single `@StringRes` mapping `readingOpenHintRes(provider)` in
  `DayContent.kt` (one home, no second enum). **IN_APP wording chosen: "in this app"** (owner
  sign-off noted). **MySword-not-installed:** the hint mirrors the *setting*, not install-aware
  tap-time resolution — it always reads "…in MySword" even when the tap falls back to BLB (the
  fallback lives in `OpenReferenceUseCase`, untouched; deliberately not over-engineered). Tile
  layout/spacing + a11y (hint is supplementary text) unchanged. 605 tests (net +6: 5 per-provider
  hint UI pins in `DayContentTest` with LITERAL expected strings + 1 `selectedProvider`-reactivity
  pin in `DayReadingsViewModelTest`; **all three data/Room gates UNTOUCHED — plan = 11,
  BibleTextVerificationTest = 18, BibleDatabaseRoomOpenTest = 5**), full pipeline green, Kover 95.4%
  on domain/data, a11y gate 7/7, **3 mutations killed** (IN_APP→BLB hint, MYSWORD→YouVersion hint,
  VM selectedProvider ignoring the setting), each restored in place. No new deps/permissions, no
  Room/manifest/DataStore change. Note: docs/features/tile-hint-provider.md. **Owner tone sign-off:**
  the five hint strings (esp. "in this app" vs "Reads … in this app"). Device-pass: the live update
  when switching provider in Settings.
- ✅ **Reader footer hint (owner UI request, `sprint-00K-reader-footer-hint`) is DONE** (uncommitted
  in the working tree; the main session verifies + commits; no version bump — display-only; built
  on the Sprint K settings split that renamed `BibleProvider` → `ExternalBibleApp` +
  `ReadingDestinationMode`). The first Sprint-K pass shipped the *day-tile* hint but missed the
  owner's PRIMARY ask: a hint at the **bottom of the reading pane** (the reader), in the empty band
  below the chapter text. Now the reader's verse `LazyColumn` ends with an always-shown italic
  footer — "Tap a verse to open it on Blue Letter Bible" / "…on Bible Gateway" / "…on YouVersion" /
  "…in MySword" (`bodySmall`/italic/`onSurfaceVariant`, ~24dp top / 16dp bottom, start-aligned under
  the 20dp verse padding) keying the verse-tap-out to the user's chosen external app — the
  read-here / study-there bridge, shown **regardless of reading destination** (most useful when
  reading IN_APP) and **reactive** (`ReaderViewModel.externalApp` = `SettingsRepository.externalBibleApp`
  `stateIn`'d; the Route collects + passes it to `ReaderScreen`; change "Open readings in" in
  Settings and the hint updates live). **D-K-HINT-1 (one home, no drift):** the reader-hint +
  external-app-name `when(externalApp)` mappings (`readerVerseTapHintRes`/`externalBibleAppNameRes`)
  live beside the day-tile `readingOpenHintRes` in `DayContent.kt`; the reader hint is the
  external-app axis ALONE (no in-app/external branch). **D-K-HINT-2:** the footer is the last verse
  `LazyColumn` item, NOT the `bottomBar`/`ReaderAudioSlot` (that stays reserved for V4 audio).
  **D-K-HINT-3:** `clearAndSetSemantics{}` skips it from TalkBack (every verse already speaks
  "Open <Book> <ch>:<verse>…") — the `testTag` is re-declared inside the clear block so it stays
  test-findable while speaking nothing. 627 tests (net +7: 4 LITERAL per-app wording pins resolved
  through the string resources + 1 a11y-silence/render pin + 1 verse-keyed coexistence pin in
  `ReaderScreenTest`, 1 reactivity pin in `ReaderViewModelTest`; **all three data/Room gates
  UNTOUCHED — plan = 11, BibleTextVerificationTest = 18, BibleDatabaseRoomOpenTest = 5**), full
  pipeline green, Kover 95.8% on domain/data, **3 mutations killed** (external-app name mapping,
  MySword "in" preposition template, VM `externalApp` ignoring the stored setting), each restored in
  place. New strings (`reader_verse_tap_hint_*`, `external_app_name_*`) await owner tone sign-off.
  No new deps/permissions, no Room/manifest/DataStore change. Device-pass: footer placement in the
  circled empty band on a short chapter, end-of-scroll reach on a long chapter, live update on a
  provider change. Handoff:
  [docs/sprints/sprint-00K-reader-footer-hint.md](docs/sprints/sprint-00K-reader-footer-hint.md).
- ✅ **Sprint 23 (in-app update flow — Play In-App Updates, `sprint-0023-in-app-update`) is DONE**
  (owner-redirected from the queued v2.x release prep; uncommitted in the working tree; version
  untouched at 1.4.1/10401 — the main session bumps to 1.4.2/10402 + tags + deploys). Closes
  **BACKLOG #4** with **Option A** (Play In-App Updates, flexible/non-blocking flow). On launch the
  app checks Play for a newer build; the pure `domain/UpdatePromptDecision.shouldPrompt` gates the
  prompt — **PATCH=silent, MINOR/MAJOR=prompt** via the D-S9-3 `/100` rule (drops the two patch
  digits); a downloaded update raises a calm **"Restart" snackbar** in `RootScaffold`
  (`UpdateRestartSnackbarEffect`, indefinite, ≥48dp spoken action) that installs + relaunches on tap.
  Stalled downloads re-surface on `onResume`; the whole feature is inert on Play-less devices / failed
  checks (no crash, never gates the readings). **New `update/` package:** `InAppUpdateState`
  (`@ActivityRetainedScoped` seam à la `ReaderHandoff` — `phase` Idle/ReadyToRestart + the
  process-lifetime no-nag flag, **D-L-5: NO new DataStore key**), `InAppUpdateManager` interface +
  `PlayInAppUpdateManager` (maps Play `UpdateAvailability`/`isUpdateTypeAllowed(FLEXIBLE)` →
  `UpdateAvailabilitySignal`, off-main, runCatching-wrapped); `di/UpdateModule` binds it; MainActivity
  drives `checkForUpdate`/`resume`/`unregister` + the `StartIntentSenderForResult` launcher; `RootViewModel`
  exposes `updatePhase` + `restartToInstallUpdate`. **D-L-6 (VERIFIED, required finding):** the
  `app-update`/`app-update-ktx` **2.1.0** dep (+ transitive `core-common 2.0.3`,
  `play-services-basement 18.1.0`, `play-services-tasks 18.0.2`) adds **ZERO new manifest permission**
  — diffed the merged manifest with/without the dep — **no INTERNET, no GMS perms**, only a
  `PlayCoreDialogWrapperActivity` + the `gms.version` meta-data. The 6 existing permissions are
  unchanged (pre-L Glance/WorkManager merges). **The no-INTERNET offline identity (NFR-V3-A) holds** —
  Play's networking is brokered via the Play Store app/GMS, not an app-held INTERNET grant. **644 tests**
  (net +17; the three data/Room gates UNTOUCHED — plan = 11, `BibleTextVerificationTest` = 18,
  `BibleDatabaseRoomOpenTest` = 5), full pipeline green, Kover 95.8% on domain/data, **5 load-bearing
  mutations killed** (minor-boundary `/100`, signal gate, staleness guard, no-nag flag, snackbar
  branch), each restored in place. **`bundleRelease` builds clean** with the new dep (R8 + Play Core
  consumer rules; 777 Play Core entries survive R8; no app-side keep rule needed; AAB **8.07 MB** <
  12 MB ceiling). S-L strings (`update_downloaded_message`/`update_restart_action`) await owner tone
  sign-off. Device-pass (needs a real Play internal track): minor-bump surfaces the flow, DOWNLOADED →
  Restart installs, resume re-surface, patch-only stays silent.
  Handoff: [docs/sprints/sprint-0023-in-app-update.md](docs/sprints/sprint-0023-in-app-update.md).
- ✅ **Reader top-bar redesign (owner UI request, `sprint-00N-reader-version-topbar`) is DONE**
  (uncommitted in the working tree; the main session verifies + commits; no version bump —
  display/structure-only, stays 1.4.2/10402). The in-app reader top bar now reads as the owner
  asked: **a PENCIL (`Icons.Filled.Edit`, present in the frozen material-icons-core 1.7.8 — no
  custom drawable, D-N-4) sits INLINE to the LEFT of the chapter heading and opens the book/chapter
  picker** (replacing the `AutoMirrored.Filled.List` action; keeps `onOpenPicker`, the
  `reader-open-picker` tag, the `reader_pick_chapter` contentDescription, ≥48dp), and **the bundled
  version sits on the RIGHT.** **D-N-3:** `bible/ui/reader/ReaderVersionSelector` — ONE version
  (today) renders the **code "KJV"** as a static `labelLarge`/onSurfaceVariant title (NOT a control;
  tag `reader-version-title`), MORE than one renders the M3 `DropdownMenu` switch idiom (tags
  `reader-version-dropdown`/`reader-version-option-<code>`) — the dropdown branch is built + tested
  but UNEXERCISED in prod (version-switching machinery / second artifact / versification are the
  deferred V4 work, NOT built). **D-N-2:** the visible label is the compact CODE; TalkBack hears the
  unabbreviated NAME ("King James Version"). **D-N-1 (version sourced from data, NO Room-schema
  change):** `BibleTextSource.translations()` reads the asset's existing `translation` table via a
  raw `SimpleSQLiteQuery` on `database.openHelper.readableDatabase` (off-main) — deliberately NOT a
  Room `@Entity`, so the pinned `room_master_table` identity hash (sprint-00F) is untouched;
  `RoomBibleTextSource` injects `BibleDatabase` (its only ctor change). New: `BibleTranslation`,
  `GetTranslationsUseCase`, `ReaderVersionState`/`ReaderVersionSelector`; `ReaderViewModel` exposes
  `versionState` (loaded once from the seam) + a no-op `selectVersion` placeholder. New string
  `reader_version_dropdown_description` (multi-version dropdown only). New `RoomBibleTextSourceTranslationsTest`
  opens the REAL asset via the same Room `createFromAsset` builder and proves `translations()`
  returns the KJV row (SEPARATE from the 5-test `BibleDatabaseRoomOpenTest` gate, whose count +
  content are unchanged). 651 tests (net +7; **all three data/Room gates UNTOUCHED — plan = 11,
  BibleTextVerificationTest = 18, BibleDatabaseRoomOpenTest = 5**), full pipeline green, Kover 95.8%
  on domain/data, a11y gate 8/8, **5 mutations killed** (static-title branch disabled → single-version
  pins red; static-title forced always → dropdown pin red; pencil onClick no-op → picker pin red;
  `translations()` wrong code → real-asset pin red; VM drops the available list → versionState pin
  red), each restored in place. No manifest/Room-schema/asset/plan-data/DataStore/dependency change.
  Device-pass: pencil + heading + version on one top-bar line at default/large font (no overflow),
  pencil tap accuracy, version-title alignment/contrast in light + dark. String sign-off:
  `reader_version_dropdown_description`; the visible-"KJV" / spoken-"King James Version" choice.
  Handoff: [docs/sprints/sprint-00N-reader-version-topbar.md](docs/sprints/sprint-00N-reader-version-topbar.md).
- ✅ **Alternate-Schedules Sprint A (plan-data model + active-plan spine + M'Cheyne asset & gate —
  the HARD GATE) is DONE** (uncommitted in the working tree; the main session verifies the gate +
  commits; version untouched — renders no second-plan UI). **A second trustworthy, gate-verified
  reading plan (M'Cheyne) now exists, and the app can name an active plan.** (1) **schemaVersion 3
  (D-ALT-2/3):** a plan DECLARES its shape — `PlanDto` head `planId`/`name`/`anchoring`/`dayCount`/
  `streams[]` (`PortionDto`/`RefDto` unchanged, v2 body a strict subset); new
  `PlanDescriptor`/`StreamDescriptor`; `ReadingPlanAssetLoader` validates against the descriptor
  (anchoring==DATE, dayCount==365, Feb-29-absent, streams 1..N contiguous, every day's stream-set ==
  declared, planId==expected anti-drift) NOT the old `365`/`listOf(1,2,3)` constants. (2) **Registry +
  spine:** `assets/plans/registry.json` + `PlanRegistry` (`DEFAULT_PLAN_ID="bible_companion"` pinned ==
  registry default); `PlanAssetSource(assetPath)` (replaces `PlanJsonSource`); per-plan
  `ReadingPlanRepository` (`portionsFor(planId,date)`/`descriptor(planId)`, separate descriptor/schedule
  caches); `selected_plan` DataStore key + `ActivePlanRepository` (absent⇒default, unknown id⇒default).
  The BC asset MOVED `assets/reading_plan.json` → `assets/plans/bible_companion/plan.json`, re-authored
  v3 with its **days body byte-identical** (proven). **Descriptor-ADDITIVE only** — the `Stream` enum is
  NOT retired (Sprint C, D-ALT-5); BC portions still map via `Stream.fromNumber` so the output map is
  byte-identical; a 4-stream plan's descriptor is reachable but its `Portion` map is not until C.
  (3) **The M'Cheyne asset (the data track):** `assets/plans/mcheyne/plan.json` (schema 3, 365 days,
  Feb=28, **4 streams** Family OT/Family Gospels/Secret Psalms&Prophets/Secret Epistles, **38
  verse-windowed refs** — Ps 119 ×7 ×2, Ps 78 ×2 ×2, Luke 1 ×2 ×2, ~13 cross-chapter ranges as
  multi-ref portions, Aug 8 `Jer 36,45`). **Built from the verse-faithful edginet source**
  (Edgington/Haslam) by `tools/build_mcheyne_plan.py`; **bibleplan.org `plan.js` REJECTED** (its verse
  windows are corrupt — the documented Feb-28 off-by-one). **Verified against the GENUINELY INDEPENDENT
  Carson/TGC witness** (checksum-distinct lineage; `tools/extract_mcheyne_second.py` →
  `app/src/test/resources/plans/mcheyne/plan_verify.json`): **zero day-by-day mismatches across all 365
  days.** Two reconciled edginet column-extraction artifacts (Aug 29 clipped "2", Jun 28 trailing-bleed
  "2") — content confirmed against TGC, fixed in `reconcile()`. **`McheynePlanVerificationTest` (10
  tests) is GREEN as the release gate:** structural + 2nd-source day-by-day + the **verse-aware coverage
  invariant** (OT verses once, Psalms+NT verses twice, every verse covered — Matthew 1 read Family Jan 1
  AND Secret Jun 21) + Ps-119 tiling (both occurrences) + spanning-range fidelity; **4 mutations killed**
  (dropped window, 4th-stream→3, coverage double-count, vacuous-gate-code), each restored in place. New
  `mcheyne-rebuild` CI job re-derives asset+fixture from the two pinned-SHA sources and asserts a
  byte-diff of zero. **BC parity held:** `ReadingPlanVerificationTest` (11 tests) passes UNCHANGED
  against the moved+re-authored v3 BC asset (path + schema-literal change only); the other two gates
  untouched (BibleTextVerificationTest 18, BibleDatabaseRoomOpenTest 5). 677 tests (net +26; 0 failures),
  full pipeline green from clean, Kover 95.7% on domain/data. Zero net-new runtime deps, no new
  permissions, no INTERNET, no version bump. M'Cheyne stream-title strings await owner tone sign-off.
  Provenance/reconciliation: [docs/data/README.md](docs/data/README.md) (M'Cheyne section).
  Handoff: [docs/sprints/sprint-alt-A-plan-foundation.md](docs/sprints/sprint-alt-A-plan-foundation.md).
- ✅ **Alt-Schedules Sprint B (per-plan progress Room migration — the HIGH-RISK, isolated sprint)
  is DONE** (uncommitted in the working tree; the main session independently verifies the migration
  test + commits; NO version bump — no UI/feature change this sprint). **The progress spine is now
  per-plan and proven LOSSLESS** — an upgrading Bible Companion user keeps every mark, perceives no
  migration, and the store can isolate a second plan's marks. NO UI, NO completion/stats/streak-logic,
  NO `Stream`-enum change (all Sprint C). (1) **Schema v1→v2 (D-ALT-12):** `ReadingProgressEntity`
  gains `@ColumnInfo(name = "plan_id") val planId: String`; PK → `(plan_id, dateEpochDay, stream)`;
  `ProgressDatabase version = 2`, `exportSchema` stays true; `2.json` checked in at
  `app/schemas/.../ProgressDatabase/2.json` (the only column/PK diff vs `1.json`). (2) **THE migration
  (D-ALT-13):** hand-written `ProgressMigrations.MIGRATION_1_2` (new `data/progress/ProgressMigrations.kt`),
  recreate-and-copy (SQLite can't alter a PK in place): create `reading_progress_new` with the v2 DDL,
  `INSERT … SELECT '${'$'}{ReadingProgressEntity.DEFAULT_PLAN_ID}', dateEpochDay, stream, readAtEpochMillis
  FROM reading_progress` (touch every row once, zero loss), drop old, rename; registered on the
  `DataModule` builder; **`fallbackToDestructiveMigration` stays OFF** (D-V3-15 — a failed migration is
  a loud crash, never silent loss). The `bible_companion` stamp is a migration literal sourced from the
  shared `ReadingProgressEntity.DEFAULT_PLAN_ID == PlanRegistry.DEFAULT_PLAN_ID` constant (anti-drift
  pin). (3) **Plan-scoped store (D-ALT-12):** every DAO query gained a `plan_id = :planId` clause and
  every `ProgressRepository` method gained `planId: String = PlanRegistry.DEFAULT_PLAN_ID` — additive
  with a default, so the ~10 use-case callers + the 13-test `ProgressRepositoryTest` compile and behave
  byte-identically (parity). The interface still speaks the `Stream` enum (retiring it is C). **D-ALT-15
  CONFIRMED:** `hasAnyMarks()` stays GLOBAL (`SELECT EXISTS(... reading_progress)`, no plan filter — the
  per-device 'fresh install' signal) and the tracking-start date stays global. (4) **THE hard gate:**
  `ProgressMigrationTest` (`MigrationTestHelper`, real exported schemas) seeds a v1 DB (multi-year,
  whole+partial, a Feb-29-adjacent 2024-02-28 day), runs `MIGRATION_1_2`, and asserts row-count
  identical + every `(dateEpochDay, stream, readAtEpochMillis)` tuple preserved with
  `plan_id='bible_companion'` + Room's `validateMigration`. Plus `ProgressMigrationNoPerceptibleChangeTest`
  (SB-T6): a migrated v1 DB read through the NEW repo with the default plan returns IDENTICAL
  streams-read/counts to v1. Plus `ProgressRepositoryPlanScopeTest` (two-plan isolation, per-plan
  counts/clearYear, GLOBAL-`hasAnyMarks`, default-arg parity). **6 mutations killed** (drop row copy /
  wrong stamp / corrupt `readAtEpochMillis` → migration tests; impl hard-codes plan id / `hasAnyRows`
  scoped-not-global / default planId flipped to a bogus id → scope+parity tests), each restored in
  place. **Schema-asset wiring:** the exported `app/schemas` are added as **DEBUG-only** assets
  (`sourceSets.getByName("debug").assets.srcDir("schemas")`) so Robolectric's `MigrationTestHelper`
  finds `1.json`/`2.json` in the debug unit-test resource APK — **VERIFIED absent from the release AAB
  (0 entries) and present in the debug APK (2)**, so the ~3 KB never ships. New TEST dep only:
  `androidx.room:room-testing` (catalog, version-pinned to `room`). **685 tests** (net +8; the four
  data/Room gates UNCHANGED — BC plan 11, McheynePlanVerificationTest 10, BibleTextVerificationTest 18,
  BibleDatabaseRoomOpenTest 5; the 13-test `ProgressRepositoryTest` parity-green), full pipeline green
  from clean, Kover 95.8% on domain/data. Zero net-new RUNTIME deps, no new permissions, no INTERNET,
  no manifest change, no version bump. **Device-pass item (Alt Sprint E):** the real migrated-history
  upgrade on a device.
  Handoff: [docs/sprints/sprint-alt-B-progress-migration.md](docs/sprints/sprint-alt-B-progress-migration.md).
- ✅ **Alt-Schedules Sprint C (N-stream UI generalization) is DONE** (committed `7e6e9a6`; no
  version bump). **The whole app renders the active plan's ACTUAL stream count truthfully** — every
  completion/stat/strip/widget surface flows through the ONE `DayCompletionClassifier` seam,
  parameterized never forked. (1) **D-ALT-5:** the `Stream` enum is retired — a stream is a plain
  `Int` number (`Portion.streamNumber`), already the persisted key. (2) **D-ALT-6:** the classifier
  takes `streamCount: Int` (the truth-table order untouched). (3) **D-ALT-7/8:** stats denominators
  are `dayCount × N` / `dayCount` from the active descriptor (the `1095`/`365` consts gone); the three
  stat use cases (`GetReadingStatsUseCase`, `GetYearStripsUseCase`, `GetMonthCompletionUseCase`) plus
  `GetDayReadingsUseCase`, `MarkWholeDayUseCase`, `ToggleReadingUseCase` all inject
  `ActivePlanRepository`, **`flatMapLatest` on `activePlanId` and `combine` the `activeDescriptor`** —
  so a plan switch re-emits the whole app LIVE with no further use-case change (the load-bearing fact
  Sprint D builds on), and every progress query is scoped to the active `plan_id`. (4) **D-ALT-22/23:**
  stream titles come from `StreamDescriptor.title` (the `ReadingFormatter.streamTitle` enum-`when`
  retired); a single-stream plan renders the reference with NO stream label (`PlanDescriptor.titleFor`
  returns null for N≤1). (5) **D-ALT-9/10:** day cards, stats strips/rows, and the row-count-aware
  widget tier policy render N rows. For the Bible Companion everything resolves to 3 streams / 1,095 /
  365 exactly (parity — all existing pins green). The widget reads the active plan automatically
  through `GetDayReadingsUseCase`. N-correctness is JVM-gate-proven (M'Cheyne N=4 + a synthetic N=1).
  Test seams: `FakeActivePlanRepository(descriptor, planId)`, `bibleCompanionDescriptor`, `StatsFixtures`.
  (Sprint C's handoff doc was not written to disk; this entry + the committed code are the record.)
- ✅ **Alt-Schedules Sprint D (plan selector + whole-app integration) is DONE** (uncommitted in the
  working tree; the main session verifies + commits; NO version bump). **A user can select M'Cheyne in
  Settings and the ENTIRE app — schedule, stats, strips, day pager, widget, reminders — shows M'Cheyne
  live; switch back and the Bible Companion view AND its progress are restored intact.** A Bible
  Companion reader who never opens the selector sees the app byte-for-byte as today (default =
  `bible_companion`). (1) **Settings "Reading plan" selector (D-ALT-18):** a compact `SettingsDropdownRow`
  (S14 idiom) at the TOP of Settings showing the active plan's `name` (read from the plan's own
  descriptor head — "Bible Companion" / "M'Cheyne", never a second name table), anchoring a menu of the
  registry's plans (tags `plan-dropdown`, `plan-option-<id>`); selecting writes `selected_plan`. The row
  IS the "active plan visible" affordance (FR-ALT-6); a11y-gate-pinned ≥48dp + spoken "Reading plan,
  <value>". (2) **The explained non-destructive switch (D-ALT-19):** selecting a DIFFERENT plan raises a
  one-time dialog (`plan-switch-dialog`) naming both plans — "Your <old> progress is saved — switch back
  any time and it'll be here. <new> starts fresh." — that writes NOTHING until confirm; selecting the
  already-active plan is a no-op. Confirm writes `selected_plan` + fires `WidgetRefresher`; dismiss
  writes nothing. NO data operation runs — per-plan progress (Sprint B) makes the switch non-destructive
  by construction. (3) **Live switch (D-ALT-17):** because Sprint C made the use cases `combine` the
  active descriptor, the write alone re-emits schedule/stats/strips/picker-dots/day-pager live — NO
  use-case change in D. (4) **Widget (D-ALT-10 wiring half):** the `@EntryPoint` needed NO read-side
  change — it already resolves the active plan through `GetDayReadingsUseCase`; D only fires
  `WidgetRefresher` on a switch so the launcher snaps to the new plan. (5) **Reminder/persistent
  content:** already active-plan-correct (Sprint C wired the bodies through `GetDayReadingsUseCase`) —
  verified, no new code. (6) **OQ-7 RESOLVED (D-D-ALT-1): Settings-only, no first-run plan question,
  default Bible Companion** — first-run already has tracking-start + reading-destination prompts; a third
  would clutter the zero-setup promise. THE end-to-end gate (`PlanSwitchIntegrationTest`): drives
  `GetDayReadingsUseCase` through the REAL `ActivePlanRepositoryImpl` over the REAL bundled BC + M'Cheyne
  assets, flips `selected_plan` on one open subscription → 3-stream-all-read → 4-stream-fresh →
  3-stream-all-read-again, plus per-plan marks isolation. New: `ui/settings/PlanSelectorState.kt`
  (`PlanOption`/`PlanSelectorUiState`/`PendingPlanSwitch`); `SettingsViewModel` (`planSelector`,
  `pendingPlanSwitch`, `onPlanSelected/Confirmed/Dismissed`, injects `ActivePlanRepository`/`PlanRegistry`/
  `ReadingPlanRepository`); `SettingsScreen` (`PlanDropdown` + switch dialog). **710 tests** (net +14; the
  four data/Room gates UNCHANGED — BC plan 11, McheynePlanVerificationTest 10, BibleTextVerificationTest
  18, BibleDatabaseRoomOpenTest 5), full pipeline green, Kover 95.9% on domain/data, a11y gate 8/8,
  nav-regression green, **5 mutations killed** (confirm-skips-write; confirm-skips-widget-refresh;
  active-plan-ignores-selection → integration gate red; same-plan-no-op-guard-removed; option-name-from-id-
  not-descriptor), each restored byte-identical. No new deps/permissions, no Room/schema/plan-data/manifest
  change. **New strings need owner tone sign-off** (`plan_section_title`, `plan_dropdown_description`, the
  four `plan_switch_dialog_*`). **Device-pass items:** the live switch on glass; the widget showing
  M'Cheyne after a switch.
  Handoff: [docs/sprints/sprint-alt-D-plan-selector-integration.md](docs/sprints/sprint-alt-D-plan-selector-integration.md).
- ✅ **Alternate-Schedules Sprint E (chronological GO/NO-GO + hardening + release readiness) is DONE**
  (uncommitted in the working tree — only `docs/data/README.md` + `distribution/whatsnew/whatsnew-en-US`
  changed; NO code/asset/test/version change; the main session/owner commits + cuts the release). **The
  alternate-schedules epic is COMPLETE** and ships valuably with **TWO** gate-verified, live-switchable,
  per-plan-progress plans (Bible Companion + M'Cheyne). **Track 1 — chronological = NO-GO (do not ship),
  the correct honesty-gate outcome (D-ALT-21), recorded in `docs/data/README.md`.** A *named* candidate
  (Blue Letter Bible "Chronological Plan", Nathan Gammie) exists, but every second source for it is a
  verbatim re-host of BLB's own PDF (the re-mirror trap = ONE witness); the only other wide lineage
  ("2020/Bible Study Tools") is anonymous/untraceable AND **genuinely disagrees** with BLB on real
  editorial choices (Day 104 Psalm 91; Day 121/150/200/209 verse splits — cross-confirmed from the live
  PDFs) — so neither witnesses the other. The contested ordering IS the IP; two disagreeing "chronological"
  sources are NOT second-source verification. No chronological asset/registry-entry/script/gate/CI-job was
  created; the FOUR data gates stay UNCHANGED (BC plan 11, McheynePlanVerificationTest 10,
  BibleTextVerificationTest 18, BibleDatabaseRoomOpenTest 5). **Track 2 — hardening (landed):** merged
  RELEASE manifest carries **NO INTERNET / zero new permissions** (the 6 perms are pre-alt Glance/
  WorkManager/reminder merges; offline identity holds); `bundleRelease` clean = **8.12 MB AAB** (< the
  12 MB CI ceiling; both plan assets packaged — BC 168 KB / M'Cheyne 199 KB uncompressed, ~8–9 KB gzipped
  each); StrictMode/off-main review of the NEW plan-load paths = CLEAN (multi-plan changed only the asset
  PATH, not the threading — the day-screen load runs inside a `combine` over a Room Flow = Room's
  background dispatcher; the settings plan-name + active-descriptor loads run inside a `.map` over a
  DataStore Flow = IO; only the ACTIVE plan's asset is parsed, per-plan single-flight under a mutex, so the
  default user never parses M'Cheyne — cold-start budget unchanged); a11y gate **8/8** (incl. the
  `plan-dropdown` ≥48dp + spoken pin); full pipeline green (**710 tests, 0 failures**), Kover **95.9%** on
  domain/data. **Recommended release version: 1.5.0 / 10500** (MINOR bump per D-S9-3 — a significant
  feature; NOT applied — main session/owner bumps + tags + deploys; current 1.4.3/10403). whatsnew draft
  updated for the alt-schedules feature. **The full alt-schedules strings tone-sign-off table** (M'Cheyne
  stream titles "Family — Old Testament"/"Family — Gospels"/"Secret — Psalms & Prophets"/"Secret —
  Epistles"; plan name "M'Cheyne"; the selector + switch-dialog copy) and the **consolidated owner device-
  pass checklist** (N=4 day cards one-screen-fit WITH the bottom bar; stats one-screen-fit + 4 strips/
  legend/a11y at N=4; widget tiers at N=4 every size; the live switch on glass; the widget snapping to
  M'Cheyne; a real migrated-history upgrade) are in the handoff. **Blocking the release:** the owner's
  device pass + the string/tone sign-offs, then the 1.5.0/10500 bump + closed-track tag-to-Play rollout.
  Handoff: [docs/sprints/sprint-alt-E-chronological-hardening-release.md](docs/sprints/sprint-alt-E-chronological-hardening-release.md).
- ✅ **Sprint 00O (tap-to-mark-read — owner feature) is DONE** (uncommitted in the working tree; the
  main session/owner commits + ships; no version bump — stays 1.4.3/10403). Team-driven (Maya framed,
  Morgan planned, Sam implemented, Riley verified). **Tapping a reading card on the Schedule to open it
  now marks that reading read for the displayed date** — in the in-app reader, on any external Bible
  site (BLB/Bible Gateway/YouVersion), or in the MySword app — and refreshes the home-screen widget;
  the checkbox stays the only un-mark affordance. Everything derived from per-reading marks (whole-day
  completion, widget, picker dots, streaks/stats, year strips) reflects auto-marks for free through the
  existing seams. **Product (Maya, owner-confirmed):** one-way SET not toggle (re-opening an already-read
  reading keeps it read); ALL destinations; mark **on tap** (when the open is initiated, NOT gated on a
  confirmed open — external launches don't report back and the in-app path is a tab switch; the
  MySword→BLB fallback still opens); **no opt-out Setting** (the on-card checkbox is the one-tap undo).
  **D-O-1:** a dedicated `domain/MarkReadOnOpenUseCase` (thin clone of `ToggleReadingUseCase` but always
  `setRead(..., isRead = true)` — the param is `isRead`, not `markRead`), the clean mutation target and
  the single seam a future opt-out would gate. **D-O-2:** the mark fires in `onReadingTapped` BEFORE
  destination resolution in one `viewModelScope.launch`, so it lands uniformly for InApp/Web/MySwordApp
  and is never lost on an early-returning branch; the open isn't blocked on it. **D-O-3:**
  `onReadingTapped(date, portion)` is threaded the displayed `date` exactly like `onToggleReading`; the
  card (`DayContent`/`ReadingCard`) keeps its `(Portion) -> Unit` signature (stateless/date-agnostic).
  **D-O-4:** widget refresh reuses the existing `WidgetRefresher` call. **D-O-5:** Feb 29 renders no
  cards, so `onReadingTapped` is unreachable — no guard. The intentional behavior change ("opening on
  BLB now refreshes the widget") was renamed honestly, not silently flipped. 719 tests (net +9; the
  three data/Room gates UNTOUCHED — plan = 11, `BibleTextVerificationTest` = 18,
  `BibleDatabaseRoomOpenTest` = 5; M'Cheyne = 10), full pipeline green from clean, a11y gate 8/8,
  Kover 96.0% on domain/data, **6 mutations killed** (isRead-true→false, drop the mark call, drop the
  widget refresh, date→today, pager wrapper date-swap, mark-inside-in-app-branch-only), each restored
  byte-identical. No new strings/deps/permissions, no Room/DataStore/manifest change. **Device-pass:**
  the on-glass tap-opens-and-checks feel; the widget refreshing read-state on a real launcher.
  **Queued out (not absorbed):** an opt-out Setting (only if a device pass shows surprise);
  mark-only-on-successful-open (racy). Handoff:
  [docs/sprints/sprint-00O-tap-to-mark-read.md](docs/sprints/sprint-00O-tap-to-mark-read.md).
- ✅ **Release 1.5.0 / 10500 SHIPPED to the Play closed-testing track** (`2026-06-16`; tag `v1.5.0`
  → commit `63b7443`; `release.yml` "Test, build & upload to Play" green in 5m39s — upload-key-signed
  AAB **7.74 MiB** < 12 MB gate, no INTERNET, 719 tests). MINOR bump per D-S9-3. **Ships the
  alternate-schedules epic (BACKLOG #3 — Bible Companion default + M'Cheyne, per-plan progress,
  whole-app live switch; chronological recorded NO-GO) AND Sprint 00O tap-to-mark-read.** whatsnew
  updated for both. Still owner-side (non-blocking, for a 1.5.x patch): a real **device pass** on the
  closed track, and the **string/tone sign-offs** (M'Cheyne stream titles + selector/switch-dialog copy
  + the broader standing strings backlog) — all currently shipping as DRAFT wording.
- ✅ **M'Cheyne stream titles — owner sign-off #1 (`2026-06-16`, post-1.5.0, for the next patch):**
  streams 3 & 4 renamed **"Secret —" → "Personal —"** (display) — "Family — Old Testament/Gospels"
  unchanged; now "Personal — Psalms & Prophets" / "Personal — Epistles". Done via the **reproducible
  pipeline** (NOT a hand-edit): the literals changed in `tools/build_mcheyne_plan.py` +
  `tools/extract_mcheyne_second.py`, assets regenerated from the SHA-pinned sources, asset diff is
  ONLY the four title lines (day-data byte-identical, source SHAs unchanged), the CI `mcheyne-rebuild`
  byte-diff gate reproduces locally, and the literal title pins updated (`DayContentTest`,
  `NStreamCorrectnessTest`, cosmetic `McheynePlanVerificationTest` comment/name refs). 719 tests green;
  the four data/Room gates UNTOUCHED (BC plan 11, M'Cheyne 10, BibleText 18, RoomOpen 5). **Docs
  deliberately retain "Secret"** for source-material provenance (M'Cheyne's own historical Family/Secret
  column naming, Matt 6:6) — only the user-facing *display* changed. Ships in the next patch (no bump yet).
- ✅ **One-screen-fit fix for N-stream plans (owner feedback, `2026-06-16`, for the next patch):**
  the 4-stream M'Cheyne main screen had BOTH panes scrolling — Priya's analysis found the real cause is
  the 4th reading card + 4th stats row/strip pushing ~70dp over budget on a P7P, compounded by the V3
  ~80dp bottom nav bar that the S16/S18 one-screen tuning never subtracted. **Owner picked: relocate the
  per-card destination hint to ONE list-level caption + small trims, applied CONSISTENTLY to all plans
  (no stream-count-conditional layout — keeps Sprint C's parameterized-not-forked discipline).** Changes
  (Priya impl, Riley verified): the per-card "Opens … in [app]" hint (5 `%1$s` `reading_open_hint_*`
  strings) is GONE from each `ReadingCard`; one reactive caption below the list ("Tap a reading to open
  it in this app" / "…on Blue Letter Bible" / "…in MySword"; 5 new `reading_list_hint_*` strings; tag
  `reading-list-hint`; the `readingOpenHintRes`→`readingListHintRes(mode, externalApp)` single-home
  mapping). Card vertical padding 8→6dp; stats strip height 10→8dp + stream-group gap 10→8dp
  (`StatsContent.kt`, unconditional). Reclaims ~60dp on the N=4 readings column (~52dp at N=3, which
  gains slack — no regression); readings stop scrolling at N=4, stats fits clean at the default
  streaks-off (streaks-on N=4 keeps a small internal scroll, the 45% cap's job). 720 tests (net +1; the
  four data/Room gates UNTOUCHED — plan 11, M'Cheyne 10, BibleText 18, RoomOpen 5), a11y gate 8/8,
  StatsContent 11, full pipeline green, Kover 95.0%/96.0% on domain/data, **3 mutations killed**
  (IN_APP-ignores-external caption, MySword "in" preposition, caption-present branch). 5 new caption
  strings await owner tone sign-off. **Device-pass:** the actual one-screen fit at default + large font,
  and the 8dp strip look — neither JVM-provable.
- ✅ **Alt Sprint F (Chronological plan — owner REVERSED the Alt Sprint E NO-GO) is DONE** (`2026-06-17`;
  uncommitted in the working tree at handoff; the main session verifies + commits; ships in 1.5.1).
  **A third gate-verified plan ships — and it's the real N=1 single-stream proof of the multi-plan
  generalization.** The owner exercised the documented GO path ([README:451-457](docs/data/README.md)):
  **D-ALT-24** — designate a single named plan canonical + accept a **rigorous single-source structural
  gate** in place of the two-independent-witness day-by-day gate (FR-ALT-3 relaxation, owner-signed
  accepted risk; scoped to THIS plan only — BC + M'Cheyne keep the full two-witness gate). Designated
  source: **Blue Letter Bible "Chronological Plan" (Nathan Gammie)**, SHA-pinned PDF
  `b055f5f4…fe54` (BLB is already the app's flagship destination). The plan is single-stream, date-
  anchored, 365 days, **WHOLE CHAPTERS ONLY (zero verse splits)**, all 66 books — Day 1 = Gen 1-3,
  Day 365 = Rev 19-22. New: `tools/build_chronological_plan.py` (stdlib, byte-deterministic, SHA-pinned,
  parses the 3-column PDF), `assets/plans/chronological/plan.json` (schema 3, planId "chronological",
  name "Chronological"), registry entry, **`ChronologicalPlanVerificationTest` (8) — THE GATE:** the
  **exactly-once whole-Bible coverage invariant (all 1,189 chapters read exactly once, no gaps/dupes)**
  is the single-source structural substitute for a second witness, + pinned endpoints + no-windowed-refs
  + catalog-in-range + planId anti-drift. CI `chronological-rebuild` job (byte-diff zero, no second-source
  fixture by design). **N=1 confirmed with ZERO production code change** (Sprint C's generalization holds
  for a real single-stream plan: `titleFor` null at N≤1 ⇒ no stream label, stats denominators 365×1=365,
  per-plan progress isolated, widget one row) — pinned by an extended `PlanSwitchIntegrationTest` over the
  REAL 3 bundled assets. **D-ALT-24 honest limitation (recorded):** the structural gate cannot detect a
  pure interior between-day chapter SWAP (multiset unchanged); unreachable by a real parse bug (each day's
  cell parses independently) — interior ordering rests on the SHA-pinned source + byte-diff reproduction.
  733 tests (net +13; the four prior data/Room gates UNTOUCHED — BC plan 11, M'Cheyne 10, BibleText 18,
  RoomOpen 5), full pipeline green from clean, byte-diff reproduction zero (re-verified independently from
  a live fetch), `bundleRelease` 7.75 MB < 12 MB, Kover 95%/96%. ~6 mutations killed incl. the book-join
  misparse probe (Riley also caught a stale `SettingsViewModelTest` 2→3-plan pin that Diego's
  assembleDebug-only check had masked). Plan name/title "Chronological" await owner tone sign-off.
  Device-pass: the N=1 day-card/stats/widget look + the live switch to Chronological on glass.
- ✅ **Settings: external-app choice always visible + context (owner feedback, `2026-06-17`, ships 1.5.1)
  is DONE** (Maya spec, Morgan map, Priya impl, Riley verify; on `main`). The "Open readings in" external
  Bible-app dropdown (BLB/Gateway/YouVersion/MySword) was hidden when the mode was "In this app"
  (`SettingsScreen.kt:258` `if (mode == EXTERNAL)`), even though the choice still drives the **per-verse
  external tap-out in the Bible reader** (Sprint H) regardless of mode. Fix: the dropdown now renders
  unconditionally (fully active in both modes — it routes through `SettingsDropdownRow`, no disabled
  path), with a new caption `external_app_help` (tag `provider-context-caption`): "Used when you tap a
  verse in the Bible tab — and, if you open readings in your Bible app, for the Schedule too." Display
  + visibility only — NO behavior/persistence/ViewModel change. The stale `SettingsScreenTest`
  hidden-in-IN_APP pin flipped to visible-in-both-modes (proven to bite via transient revert); 2 caption
  pins added (literal copy, load-bearing — caption-removal reddens them). 735 tests (+2); five data/Room
  gates UNTOUCHED (BC 11, M'Cheyne 10, Chronological 8, BibleText 18, RoomOpen 5); a11y 8/8; Kover 96%.
  Caption copy awaits owner tone sign-off (pinned literally). Device-pass: caption wrap at large font.
- ⚠️ **CI follow-up (non-blocking):** the `release.yml`/`ci.yml` actions (`actions/checkout@v4`,
  `setup-java@v4`, `upload-artifact@v4`, `gradle/actions/setup-gradle@v4`) run on **Node 20, which
  GitHub is forcing to Node 24 from 2026-06-16** — the 1.5.0 run warned. Bump the action versions
  before the next release so a future tag-to-Play doesn't break (Jordan/devops). A branch
  `ci/actions-node24-bump` (commit `3753b08`, local) has this bump; PR pending owner review.
- ✅ **Release 1.5.1 / 10501 SHIPPED to the Play alpha/closed-testing track** (`2026-06-17`; tag `v1.5.1`
  → commit `fa5e68f`; `release.yml` green in 5m35s, run 27673141482 — upload-key-signed AAB **7.75 MiB**
  < 12 MB, no INTERNET, 735 tests). PATCH bump per the owner's explicit request (chronological is
  feature-ish but the owner chose 1.5.1). **Ships since 1.5.0:** the **Chronological plan** (Alt Sprint F),
  the M'Cheyne "Personal" rename, the N-stream one-screen-fit, and the always-visible external-app
  setting. Ran on the existing Node-20 workflows (still functioning despite the deprecation; the Node24
  bump stays on its unmerged branch). whatsnew refreshed (Chronological headline). Still owner-side
  (non-blocking, patchable): a device pass on 1.5.x + the accumulated string/tone sign-offs (M'Cheyne
  titles, the chronological plan name, the 5 caption strings, the external-app help caption).
- 🚀 **1.5.1 / 10501 SUBMITTED TO PRODUCTION at 100% (full rollout) — `2026-07-23`, owner
  approved.** In Google review now (the "Changes in review" state; reviews typically ≤7 days,
  faster for an update of an already-tested build) → rolls out to all production users on
  approval. Done by **promoting the already-reviewed alpha AAB in the Play Console UI** (Test
  and release → Closed testing → Alpha → 1.5.1 → Promote release → Production → Save → Publishing
  overview → Submit for review); same bundle/notes, no rebuild.
  **Why the UI and not CI:** owner chose to automate, so a `workflow_dispatch` promote pipeline
  was added — `.github/workflows/promote-production.yml` (Fastlane `supply --track_promote_to
  production`, moves the AAB by `versionCode`; inputs `version_code`/`source_track`/`rollout`,
  default `10501`/`alpha`/`1`; `r0adkll` can't promote without re-upload). But every CI dispatch
  **403'd** — root cause (now documented, [docs/RELEASING.md](docs/RELEASING.md)): the Play
  Developer API **cannot create the FIRST release on a track that's never had one** (production
  was Inactive), returning "caller does not have permission" even with correct perms. So the
  first production release HAD to be a manual Console promotion; the CI workflow is for
  **subsequent** production releases. `release.yml` (tag → alpha) unchanged.
  **✅ CI promote is now ARMED (2026-07-23):** the `play-publisher@…gserviceaccount.com` service
  account was granted **"Release to production…"** for the app (minimal scope — testing-tracks
  permission kept, no Admin/financial; verified persisted), and the first release seeded the
  production track. So from the NEXT release, promotion is one click: Actions → "Promote to
  Production" (or `gh workflow run "Promote to Production" -f version_code=<code>`). Both
  `promote-production.yml` prerequisites are documented as satisfied in
  [docs/RELEASING.md](docs/RELEASING.md).
- Next up: **watch for Google's production review of 1.5.1 to clear** (Play Console → Publishing
  overview / Submission activity), then confirm it's live at 100% on the store. Still pending
  (non-blocking): device pass + string/tone sign-offs on 1.5.1; the Node24 CI bump
  (`ci/actions-node24-bump`) PR. (V2.x release prep remains queued, owner-scheduled independently.)
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
  `schemaVersion: 2` (Sprint J: optional `verseStart`/`verseEnd` window on a ref; only the four
  Psalm-119 days carry one — absent ⇒ whole chapter).
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

- New, separate, self-contained repo. ✅
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
