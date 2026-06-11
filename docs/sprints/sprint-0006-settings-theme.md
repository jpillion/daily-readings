# Sprint 0006 — Settings (theme)

**Status: GOAL MET.** Closed 2026-06-10. All changes left UNCOMMITTED on the working tree for
the main session to verify and commit (per session protocol). Sprint 5 commit (`79c71f8`) is
the last commit on `main`.

## Goal outcome

The user controls the app's appearance (PRD U6 / FR-9): a Settings screen, pushed from a new
gear action in the readings top bar, offers Light / Dark / System default as a radio group.
Selecting an option restyles the whole app *immediately* (the same persisted flow drives
`MainActivity`'s theme) and survives process death via the Sprint 3 DataStore-backed
`ThemeRepository`. System-bar icon contrast follows the *app* theme, not the device theme,
so forcing Dark on a light-mode device keeps the status/navigation bars legible.

Proven by 99/99 tests (10 new; the 7-test Sprint 1 gate untouched), three killed mutations,
and the full pipeline green in one command:

```
./gradlew spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug koverVerifyAppDebug
```

## Current capability

- **Theme plumbing:** `ui/theme/ThemeViewModel` (activity-scoped via `by viewModels()`)
  exposes `StateFlow<ThemeMode>` (`stateIn`, initial `SYSTEM` — no startup flash);
  `MainActivity` maps it through the new `ThemeMode.resolveDarkTheme()` composable in
  `ui/theme/Theme.kt` (LIGHT→false, DARK→true, SYSTEM→`isSystemInDarkTheme()`) into
  `DailyReadingPlannerTheme(darkTheme = …)`. A `DisposableEffect(darkTheme)` re-issues
  `enableEdgeToEdge` with `SystemBarStyle.auto(...) { darkTheme }` so system-bar icons track
  the resolved theme.
- **Settings screen:** `ui/settings/SettingsRoute` (hiltViewModel) over stateless
  `SettingsScreen(selectedMode, onThemeModeSelected, onBack)`; `SettingsViewModel` reads
  `ThemeRepository.themeMode` and persists via `setThemeMode`. UI is a "Theme" section with
  three 56dp selectable rows (`selectableGroup`, `Role.RadioButton`, `RadioButton(onClick =
  null)` — single a11y target per row).
- **Navigation:** `Routes.SETTINGS` is the app's first pushed route. `AppNavHost` passes
  `onOpenSettings` into `DayReadingsRoute`; `DayReadingsPagerScreen`'s top bar gained a
  Settings IconButton (after the date-picker action). Back arrow pops to the pager.
- Dynamic color (API 31+) still applies on top of whichever light/dark scheme is resolved.

## Decisions & rationale (do not relitigate)

- **D-S6-1 — Activity-level ThemeViewModel, not a CompositionLocal or Application hook.**
  Smallest thing that drives the theme live and is Hilt-idiomatic; Settings has its own
  ViewModel writing to the same repository flow, so the two stay in sync by construction.
- **D-S6-2 — `resolveDarkTheme()` is a separate composable mapping function** so the
  LIGHT/DARK/SYSTEM semantics are unit-testable (Robolectric `night`/`notnight` qualifiers)
  independent of MaterialTheme.
- **D-S6-3 — Edge-to-edge re-issued on theme change** (Now-in-Android pattern). Without it,
  system-bar icon contrast keys off the device theme and breaks when the user forces the
  opposite mode. In-scope correctness for "drives the theme live", not scope creep.
- **D-S6-4 — Theme-only settings.** No other preferences added; reminders etc. stay V2.

## State of the codebase

- New: `ui/theme/ThemeViewModel.kt`, `ui/settings/SettingsViewModel.kt`,
  `ui/settings/SettingsScreen.kt`. Modified: `MainActivity.kt`, `ui/theme/Theme.kt`
  (`resolveDarkTheme`), `ui/navigation/AppNavHost.kt` (`Routes.SETTINGS`),
  `ui/day/DayReadingsScreen.kt` (`onOpenSettings` param + gear action),
  `res/values/strings.xml` (settings/theme strings).
- New tests: `ui/settings/SettingsViewModelTest` (3), `ui/settings/SettingsScreenTest` (4),
  `ui/theme/ResolveDarkThemeTest` (2, qualifier-driven), one new case in
  `DayReadingsPagerScreenTest` (entry point); shared fake at
  `testing/FakeThemeRepository.kt`. ThemeRepositoryImpl's DataStore behavior was already
  pinned in Sprint 3 — not re-tested.
- New testTags: `open-settings`, `settings-back`, `theme-option-light`, `theme-option-dark`,
  `theme-option-system`.
- Mutation checks: (1) `onThemeModeSelected` no-op → killed by 1 ViewModel test;
  (2) LIGHT mapping inverted → killed by both ResolveDarkTheme tests; (3) row callback
  reporting the wrong mode → killed by 1 screen test. Restored; 99/99 on the final gate run.
- Kover on the domain/data filter: 93.9% (UI remains outside the 70% floor's filter by
  design). No new dependencies.
- `openUrlEvents` single-collector invariant holds: collection lives only in
  `DayReadingsRoute`; pushing Settings removes it from composition and the Channel buffers.

## Carryover & next goal

- **Next goal (Sprint 7 per EXECUTION_PLAN §3): Glance widget** — today's readings +
  completion state on the launcher; taps deep-link into the app (`today` route per ESpec §7).
  Widget reads must go through the same use cases/repositories; note Glance runs outside the
  activity, so theme handling there is independent of `ThemeViewModel`.
- **Queued/deferred:** `exportSchema`/Room Gradle plugin at V2 schema work; API 26–30
  static-palette visual check, 66-book live link check, a11y smoke incl. TalkBack, on-device
  StrictMode + Custom-Tab tap-through + real-device theme/edge-to-edge check (Sprint 8);
  Psalm 119 verse-ranges (post-V1); deprecation housekeeping (`hiltViewModel` package move,
  `createComposeRule` v2 — now 3 files).
- **Scope protected out this sprint:** no extra settings (reminders, translation choice),
  no widget, no per-screen theme overrides.

## Next sprint

`next: sprint-0007-glance-widget`

## Open questions & risks

- **No emulator/device run this session** (JVM pipeline only). The edge-to-edge
  `SystemBarStyle` behavior (D-S6-3) and three-button-nav scrim on API 26–28 specifically
  need the Sprint 8 device pass; transparent nav-bar scrim on pre-29 may need revisiting.
- `AppNavHost` push/pop wiring has no JVM test (consistent with prior sprints; trivial
  wiring, covered by the device pass). The stateless screens on both sides are fully tested.
- Accepted staffing deviation: agent dispatch was unavailable this session, so the EM
  executed the tickets directly while holding the Sam/Priya/Riley acceptance criteria
  (implementation, UI review incl. the D-S6-3 fix, adversarial verification + mutations).
