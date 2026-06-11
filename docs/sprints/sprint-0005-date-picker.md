# Sprint 0005 — Date picker + day swiping

**Status: GOAL MET.** Closed 2026-06-10. All changes left UNCOMMITTED on the working tree for
the main session to verify and commit (per session protocol). Sprint 4 commit (`af472f2`) is
the last commit on `main`.

## Goal outcome

The readings screen is now "a day's readings for an arbitrary date", with today as the
default (FR-5/FR-12 plus the owner's new swipe request). The user can swipe left/right to
step real calendar days (yesterday/tomorrow at a flick), jump anywhere in the current year
via a date picker dialog, and return with a one-tap "Today" affordance that appears whenever
the displayed day isn't today. Per-reading toggles, the whole-day mark, and tap-to-BLB all
work on whatever day is displayed, with progress keyed to that day's *actual full date* —
proven down to the Dec 31 → Jan 1 cross-year write. Feb 29 is reachable by swipe (leap years)
and by picker and shows the no-readings state with no mark controls.

Proven by 89/89 tests (37 new/adapted UI-layer tests; the 7-test Sprint 1 gate untouched),
two killed mutations, and the full pipeline green in one command:

```
./gradlew spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug koverVerifyAppDebug
```

## Current capability

- **Day pager:** `MainActivity` → `AppNavHost` (`Routes.TODAY`, still the only route) →
  `DayReadingsRoute` (hiltViewModel, owns the Custom-Tab side-effect) →
  `DayReadingsPagerScreen` (Scaffold + top bar + `HorizontalPager`) → per-page stateless
  `DayContent(state, callbacks)` — Sprint 4's screen body, unchanged in behavior.
- **Each page collects its own date's state** via `DayReadingsViewModel.uiStateFor(date):
  StateFlow<DayUiState>`, so pre-composed neighbor pages are always correct mid-swipe.
- **Top bar:** title "Today" + full date on today's page; "Readings" + full date elsewhere,
  with a "Today" TextButton (testTag `jump-to-today`) that animates back to today's page.
  A calendar IconButton (testTag `open-date-picker`) opens the picker dialog.
- **Date picker (`ui/datepicker/DayDatePickerDialog`):** M3 `DatePickerDialog`, year range
  pinned to the current year, `showModeToggle = false`; confirm scrolls the pager to the
  picked date. Feb 29 is selectable in leap years.
- **Marking on any day:** `onToggleReading(date, reading)` / `onMarkWholeDay(date,
  dayComplete)` — the displayed date is threaded explicitly; nothing implicitly assumes
  "today".

## Decisions & rationale (do not relitigate)

- **D-S5-1 — Generalize, don't bolt on.** `TodayViewModel`'s pinned date became the
  date-parameterized `DayReadingsViewModel` (`ui/day/`), with a per-date cache of
  `WhileSubscribed(5s)` StateFlows (`uiStateFor`). `today` stays pinned at creation from the
  injected Clock — it anchors the pager and jump-to-today. The old `ui/today/` package is gone.
- **D-S5-2 — Picker is a dialog, not a pushed route.** ESpec §6.3 sketched a pushed
  `DatePickerScreen`, but with the generalized pager a second screen would duplicate day
  rendering; the dialog satisfies FR-5/FR-12 (confirm = scroll pager) and keeps the NavHost
  single-route. Recorded ESpec deviation.
- **D-S5-3 — Year semantics (the decision note flagged since Sprint 3, per ESpec §6.1):**
  1. **Progress is always keyed to the displayed full date** (`LocalDate`), never to a
     (month, day) re-anchored to today's year.
  2. **The picker targets the current year's occurrence**: its year range is pinned to
     `today.year` (the M3 headline shows the year but it is not navigable; a fully year-less
     custom calendar was traded away as V1 over-build). Cross-year browsing via the picker
     stays out of V1, per ESpec.
  3. **Swiping steps real calendar days**, so Dec 31 → Jan 1 crosses into the adjacent
     year's actual date and marks land there (e.g. Jan 1 2027, not Jan 1 2026). Rationale:
     a swipe is "what's tomorrow's reading", and tomorrow has exactly one true date.
     Test-pinned: marking swiped-to Jan 1 2027 leaves both Dec 31 2026 and Jan 1 2026 empty.
- **D-S5-4 — Pager window:** ±10,000 days (~27 years) around today; page `TODAY_PAGE`
  (=10,000) is today. `dateForPage`/`pageForDate` are pure internal functions (unit-tested,
  with clamping at the window edges).
- **D-S5-5 — `material-icons-core` pinned at 1.7.8** (frozen artifact, no longer in the
  Compose BOM) for the calendar icon. Only new dependency; no network/analytics implications.
- Generalized one string: `day_complete` is now "All readings done" (was "…for today",
  wrong on browsed days).

## State of the codebase

- `ui/day/`: `DayReadingsViewModel`, `DayUiState` (same four sealed states as Sprint 4's
  `TodayUiState`), `DayReadingsScreen.kt` (`DayReadingsRoute` + `DayReadingsPagerScreen` +
  pager math), `DayContent.kt` (stateless single-day body), `ReadingFormatter` (moved from
  `ui/today`). `ui/datepicker/`: `DayDatePickerDialog` + UTC-millis↔LocalDate bridge.
- Tests in `ui/day/` (`DayReadingsViewModelTest` 12, `DayContentTest` 8,
  `DayReadingsPagerScreenTest` 8, `DayPagerMathTest` 5, `ReadingFormatterTest` 7 moved) and
  `ui/datepicker/` (`DatePickerDateMappingTest` 4). Pager swipes are exercised with real
  `performTouchInput { swipeLeft() }` gestures under Robolectric (`@Config(sdk = [34])`).
- Conventions unchanged: stateless content over sealed UI state + stateful `*Route`;
  one-shot URL events via Channel (single collector in `DayReadingsRoute`); testTags
  `reading-N`/`toggle-N`/`whole-day-button`/`retry-button`/`loading` plus new
  `day-pager`/`jump-to-today`/`open-date-picker`/`date-picker-confirm`/`date-picker-cancel`;
  strings in `res/values/strings.xml`.
- Mutation checks this sprint: (1) toggle writing to `today` instead of the displayed date →
  caught by 1 ViewModel test; (2) inverted pager direction → caught by 9 tests. Restored,
  89/89 on a forced fresh run. Kover on the domain/data filter: 93.9% (UI is outside the
  70% floor's filter by design).

## Carryover & next goal

- **Next goal (Sprint 6 per EXECUTION_PLAN §3): Settings** — theme selector
  (light/dark/system) persisted via the Sprint 3 `ThemeRepository` (DataStore); FR-9. The
  `ui/settings/` placeholder dir exists; ESpec routes it as a pushed `settings` route.
- **Queued/deferred:** Glance widget (Sprint 7); `exportSchema`/Room Gradle plugin at V2
  schema work; API 26–30 static-palette visual check, 66-book live link check, a11y smoke
  incl. TalkBack, on-device StrictMode + Custom-Tab tap-through (Sprint 8); Psalm 119
  verse-ranges (post-V1).
- **Scope protected out this sprint:** no settings, no widget, no streaks, no cross-year
  *picker* browsing (swipe crosses years; the picker deliberately does not — D-S5-3).
- Two new deprecation warnings to absorb in a future housekeeping pass: `hiltViewModel`
  moved packages, and `createComposeRule` has a v2 (StandardTestDispatcher). Neither blocks.

## Next sprint

`next: sprint-0006-settings`

## Open questions & risks

- **No emulator/device run this session** (JVM pipeline only). Real swipe feel, pager fling
  tuning, and the picker dialog on small screens need the Sprint 8 device pass; StrictMode
  on-device check still pending (carried since Sprint 3).
- The per-date StateFlow cache in `DayReadingsViewModel` grows monotonically with browsed
  dates (entries go cold when unsubscribed; the map slot itself is tiny). Harmless at V1
  scale; revisit only if profiling ever says otherwise.
- `openUrlEvents` still assumes exactly one collector (now `DayReadingsRoute`); fine while
  single-route — re-check when Settings adds a second route (it shouldn't collect URLs).
- The M3 picker headline displays the (pinned) year — a cosmetic deviation from ESpec §6.1's
  "year hidden". Revisit only if the owner objects on device.
