# Sprint 0004 — Today screen + mark-as-read + tap-to-BLB

**Status: GOAL MET.** Closed 2026-06-10. All changes left UNCOMMITTED on the working tree for
the main session to verify and commit (per session protocol). Sprint 3 commit (`c68446a`) is
the last commit on `main`.

**Session note:** this sprint was executed across a crash. A prior session produced the bulk
of the UI layer uncommitted; this session reviewed that code against the architecture
(verdict: keep — it conformed), fixed one test defect, adversarially verified everything,
and closed the sprint. No inherited code was rewritten.

## Goal outcome

The primary user value is live (PRD G1/G2/G3): launch the app and the Today screen shows
today's three readings with stream names and collapsed references ("Genesis 1–2";
multi-book "2 John 1; 3 John 1"); each reading toggles read/unread and persists; one tap
marks (or unmarks) the whole day; tapping a reading card opens its BLB KJV chapter in a
Chrome Custom Tab. Feb 29 shows "No scheduled readings for Feb 29th" with no mark controls.
A plan-asset load failure degrades to a retryable error screen instead of crashing.
Proven by 68/68 tests (23 new: 8 ViewModel, 8 Compose-under-Robolectric, 7 formatter; the
7-test Sprint 1 gate untouched) and the full pipeline green in one command:

```
./gradlew spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug koverVerifyAppDebug
```

## Current capability

- **Today screen end to end:** `MainActivity` → `AppNavHost` (`Routes.TODAY`) → `TodayRoute`
  (hiltViewModel, collects `uiState`, owns the Custom-Tab side-effect) → stateless
  `TodayScreen(state, callbacks)`.
- **Four UI states** (sealed `TodayUiState`): `Loading`, `Scheduled(date, readings,
  dayComplete)`, `NoScheduledReadings(date)` (Feb 29, no controls, progress never queried),
  `LoadFailed(date)` (retry button re-subscribes via `loadAttempt`/`flatMapLatest`).
- **Marking:** per-reading checkbox (≥48dp target) and a full-width whole-day button —
  marks all three when incomplete, unmarks all when complete (one atomic upsert/delete via
  `MarkWholeDayUseCase`). Done cards switch to `secondaryContainer`; a centered indicator
  shows "N of 3 readings done" / "All readings done for today".
- **Display formatting (`ReadingFormatter`, FR-13):** consecutive same-book chapters
  collapse to an en-dash range; runs join with "; " (Jun 19 / Dec 19 NT portion renders
  "2 John 1; 3 John 1"). Stream titles: "Law & History" / "Psalms & Prophecy" /
  "New Testament". Plain strings, not resources (canonical English data, V1 KJV-only).
- **Tap-to-BLB:** `TodayViewModel.onReadingTapped` sends the `OpenReferenceUseCase` URL down
  a one-shot `Channel`; `TodayRoute` launches `launchCustomTab(context, url)`
  (`ui/browser/CustomTabLauncher.kt`, androidx.browser 1.10.0) — Custom Tab, fallback plain
  `ACTION_VIEW`, logged no-op on a browserless device (planner keeps working offline).
- **Adversarially verified (Riley):** mutation 1 (invert whole-day toggle direction) → 2
  ViewModel tests fail; mutation 2 (join runs with "," not ";") → 3 formatter tests fail
  incl. the Jun 19 case. Restored → 68/68 green. Kover on the domain/data filter: 93.9%
  (unchanged — UI classes are outside the floor's filter by design).

## Decisions & rationale (do not relitigate)

- **D-S4-1 — Compose UI tests run under Robolectric** in `testDebugUnitTest`
  (`unitTests.isIncludeAndroidResources = true`), not as instrumented tests: keeps the §5.2
  Sprint 4 gate in the one-command pipeline and CI without emulators. Pinned
  `@Config(sdk = [34])` like the Sprint 3 Robolectric tests.
- **D-S4-2 — URL opens are one-shot events**, not state: `Channel(BUFFERED).receiveAsFlow()`
  on the ViewModel; `TodayRoute` is the single collector and owns the Android side-effect.
  The ViewModel stays Android-free (URL string only, per Sprint 3's `OpenReferenceUseCase`).
- **D-S4-3 — Load failure degrades, never crashes:** the loader still throws (gate-verified
  asset ⇒ runtime invalidity is a build defect), but the ViewModel `catch`es into
  `TodayUiState.LoadFailed` with a retry that re-subscribes.
- **D-S4-4 — Whole-day button toggles:** "Mark whole day done" when incomplete; flips to an
  outlined "Unmark whole day" when complete. Single control, ≤2 taps to any state.
- **Today is pinned at ViewModel creation** from the injected `Clock` (`LocalDate.now(clock)`)
  — testable, and Sprint 5's date picker generalizes the date input.

## State of the codebase

- New UI code under `app/src/main/kotlin/com/jpillion/dailyreadingplanner/`:
  `ui/today/` (TodayViewModel, TodayUiState, TodayScreen incl. TodayRoute,
  ReadingFormatter), `ui/browser/CustomTabLauncher.kt`. `AppNavHost` now routes
  `Routes.TODAY` → `TodayRoute`; the Sprint 2 placeholder and MainActivity's Clock-injection
  proof are gone. All user-visible strings are in `res/values/strings.xml`.
- Tests: `ui/today/` (TodayViewModelTest, TodayScreenTest, ReadingFormatterTest) plus
  `testing/MainDispatcherRule.kt` (swaps `Dispatchers.Main`). They reuse the Sprint 3
  `domain/Fakes.kt` helpers (`threePortions`, `portion()`, FakeProgress/PlanRepository).
- Conventions: stateless screen over sealed UI state + stateful `*Route` wrapper;
  `testTag`s `reading-N` / `toggle-N` (N = stream wire number), `whole-day-button`,
  `retry-button`, `loading`.
- New deps: `androidx.browser:browser:1.10.0`, `lifecycle-runtime-compose`
  (collectAsStateWithLifecycle), Compose ui-test-junit4 + manifest on testImplementation.

## Carryover & next goal

- **Next goal (Sprint 5 per EXECUTION_PLAN §3): Date picker** — browse any date's readings
  (same schedule source) with "jump to today". Needs the Sprint-3-flagged decision note on
  year semantics (ESpec §6.1: non-today dates write progress for the *current year's*
  occurrence). TodayViewModel's pinned-date design is the seam to generalize.
- **Queued/deferred:** Settings/theme screen (Sprint 6, can parallel 5); Glance widget
  (Sprint 7); `exportSchema`/Room Gradle plugin at V2 schema work; API 26–30 static-palette
  visual check, 66-book live link check, a11y smoke incl. TalkBack (Sprint 8); Psalm 119
  verse-ranges (post-V1).
- **Scope protected out this sprint:** no date picker, no settings, no widget, no streak
  visuals — Today only, exactly the §3 row.

## Next sprint

`next: sprint-0005-date-picker`

## Open questions & risks

- **StrictMode on-device check still pending** (carried from Sprint 3): first `uiState`
  collection triggers the asset parse; the loader hops to its IO dispatcher so it should be
  clean, but nobody has watched logcat on a device yet. Do it when first sideloading.
- `openUrlEvents` assumes exactly one collector (`TodayRoute`'s `LaunchedEffect`); a second
  collector would race for events. Fine for V1's single route — revisit if routes multiply.
- No emulator/device smoke run happened this session (JVM pipeline only). The Custom-Tab
  launch path is exercised only by code review + the URL-event test; manual tap-through on
  a device before release is on Sprint 8's checklist.
- Robolectric still pinned to sdk 34; `exportSchema = false` debt unchanged.
