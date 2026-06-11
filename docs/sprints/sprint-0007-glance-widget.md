# Sprint 0007 — Glance widget

**Status: GOAL MET.** Closed 2026-06-10. Committed to `main` per session protocol.
Note: the working session dropped mid-sprint; a fresh session rehydrated from the tree,
re-verified everything from scratch (full pipeline `--rerun-tasks` + mutations), and closed.

## Goal outcome

Today's readings live on the launcher (PRD FR-8, ESpec §7): a resizable home-screen widget
shows the date, all three stream titles + collapsed references (same `ReadingFormatter` as
the app), a per-reading read/unread mark, and an "All readings done" badge. Tapping anywhere
opens the app on the today-centered day pager. Feb 29 shows the no-readings state; a plan
load failure degrades to a tappable fallback, never a crash.

Proven by 110/110 tests (11 new: 8 Glance unit-rig + 3 ViewModel refresh-hook tests; the
7-test Sprint 1 gate untouched), two killed mutations, and the full pipeline green from
clean (`--rerun-tasks`, all 74 tasks executed):

```
./gradlew spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug koverVerifyAppDebug
```

## Current capability

- **Widget:** `widget/TodayWidget` (GlanceAppWidget) + `widget/TodayWidgetReceiver`
  (manifest-declared, `exported=false`) + `widget/WidgetContent` (stateless over sealed
  `TodayWidgetState { Loaded(DayReadings), LoadFailed }`). Metadata at
  `res/xml/today_widget_info.xml` (3x2 cells, resizable, `updatePeriodMillis=1800000`).
- **Same engine, no fork:** the widget reaches `GetDayReadingsUseCase` + `Clock` via a Hilt
  `@EntryPoint` (`TodayWidgetEntryPoint`, SingletonComponent) and renders a snapshot
  (`first()` of the flow).
- **Freshness contract (D9, no midnight alarm — accepted risk R6):** `widget/WidgetRefresher`
  interface (Hilt-bound to `GlanceWidgetRefresher` via `di/WidgetModule`), invoked from
  `MainActivity.onResume` (date-rollover-on-open case) and after both progress mutations in
  `DayReadingsViewModel` (`onToggleReading`, `onMarkWholeDay`); the 30-min system periodic
  update is the only other freshness source.
- The full surface is a single `actionStartActivity<MainActivity>` tap target; the start
  destination is the today-centered pager, satisfying the "deep-link to today" requirement
  without a synthetic deep link.

## Decisions & rationale (do not relitigate)

- **D-S7-1 — Snapshot rendering.** Each `provideGlance` resolves "today" from the injected
  Clock and takes `first()` of the readings flow; freshness is owned by the explicit refresh
  contract, not a long-lived flow collection inside Glance.
- **D-S7-2 — `WidgetRefresher` seam.** UI-layer callers (ViewModel, Activity) depend on the
  interface so refresh hooks are pinned on the JVM with a fake; only the production binding
  touches Glance machinery.
- **D-S7-3 — Widget follows the *system* theme** (dynamic M3 via `GlanceTheme.colors` on
  API 31+, the app's static schemes — now `internal` in `ui/theme/Theme.kt` — as
  `ColorProviders` below). Launcher surfaces match the launcher; the in-app ThemeMode
  override deliberately does not apply. Glance runs outside the activity/`ThemeViewModel`.
- **D-S7-4 — Read-only widget.** Marking happens in-app; toggle-from-widget is a V2
  candidate. One tap target, no per-row actions in V1.

## State of the codebase

- New: `widget/{TodayWidget,TodayWidgetReceiver,WidgetContent,WidgetRefresher}.kt`,
  `di/WidgetModule.kt`, `res/xml/today_widget_info.xml`. Modified: manifest (receiver),
  `MainActivity` (`@Inject WidgetRefresher` + `onResume` refresh), `DayReadingsViewModel`
  (refresh after mutations), `ui/theme/Theme.kt` (schemes `internal`), strings (4 new).
- New deps: `glance-material3` (impl), `glance-appwidget-testing` (test) — same `glance`
  version ref as the existing `glance-appwidget`.
- New tests: `widget/WidgetContentTest` (8, `runGlanceAppWidgetUnitTest` under Robolectric
  `@Config(sdk=[34])` — all states incl. multi-book Jun 19 portion, a11y contentDescription,
  whole-surface `hasStartActivityClickAction<MainActivity>`), 3 new cases in
  `DayReadingsViewModelTest` (toggle refreshes, whole-day refreshes, BLB tap does NOT),
  shared fake `testing/FakeWidgetRefresher.kt`.
- New Glance testTags: `widget-root`, `widget-date`, `widget-day-complete`,
  `widget-mark-read`/`widget-mark-unread`, `widget-no-readings`, `widget-error`.
- Mutation checks (run post-drop, from scratch): (1) refresh call dropped in
  `onToggleReading` → killed by exactly 1 ViewModel test; (2) read/unread mark inverted in
  `WidgetContent` → killed by 3 widget tests. Restored; final gate 110/110.
- Kover on the domain/data filter: 93.9% (widget UI is outside the filter by design, same
  as all UI).

## Carryover & next goal

- **Next goal (Sprint 8): the on-device verification pass** — everything the JVM-only
  pipeline cannot prove: widget on a real launcher (render, resize, tap-through, 30-min
  update, system light/dark + dynamic color, API 26-30 static palette), edge-to-edge
  `SystemBarStyle`/nav-bar scrim on API 26–28 (Sprint 6 debt), Custom Tabs tap-through,
  StrictMode clean run, a11y smoke incl. TalkBack, 66-book live BLB link check.
- **Queued/deferred:** widget `previewImage`/`previewLayout` polish (no preview asset yet —
  picker shows the loading layout); `exportSchema`/Room Gradle plugin at V2 schema work;
  Psalm 119 verse-ranges (post-V1); deprecation housekeeping (`hiltViewModel` package move,
  `createComposeRule` v2); toggle-from-widget (V2 candidate).
- **Scope protected out this sprint:** no toggle-from-widget, no midnight alarm, no widget
  configuration screen, no in-app ThemeMode applied to the widget (D-S7-3).

## Next sprint

`next: sprint-0008-device-pass`

## Open questions & risks

- **Nothing widget-related has run on a real device/emulator** — the Glance unit rig proves
  composition and semantics, not launcher rendering, sizing, or the periodic update. Sprint 8
  is the proof.
- `MainActivity.onResume` refresh fires on *every* resume (cheap: `updateAll` on usually one
  widget instance), and runs via `lifecycleScope.launch` — fire-and-forget, untested on JVM
  (trivial wiring, covered by the device pass).
- Mid-sprint session drop: one file (`DayReadingsViewModel.kt`) was accidentally reverted
  during mutation testing and re-applied from the captured diff; the final from-clean
  pipeline run re-proves the whole tree.
