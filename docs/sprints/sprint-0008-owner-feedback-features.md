# Sprint 0008 — Owner-feedback features

**Status: GOAL MET.** Closed 2026-06-10. (Commit to `main` performed by the main session per
protocol — this sprint's working tree was handed over uncommitted by request.)

This sprint was inserted by owner direction after a partial device pass; the previously
planned "Sprint 8 — hardening/release" is renumbered to **Sprint 9** in
`docs/EXECUTION_PLAN.md` (§3, §5).

## Device-pass results recorded (retired from the Sprint 9 checklist)

Owner-verified on a real device, 2026-06-10:
- Glance widget on a real launcher: add, render, tap-through ✅
- 66-book BLB link check passes live → **G-LINKS signed off** ✅
- Performance good → **G-PERF verified** ✅ (StrictMode clean run still owed)

## Goal outcome

All four owner requests are live:
1. **The widget is useful at 3x2, 2x2, and 1x2** — one responsive widget; the launcher picks
   the layout as the user resizes (full rows → marks+references → date + "n/3" completion).
2. **The date picker shows how the reader is tracking** — every day cell carries a completion
   dot (green = all three read for past/today/future; red = past day missed; nothing for
   incomplete today/future or Feb 29) with a spoken state, live-updating while open,
   year-scoped so indicators naturally reset each Jan 1 (progress keys by full date).
3. **Settings → Reset progress** — confirm-gated dialog naming the year; clears the *current
   year only*; the widget refreshes immediately after.
4. **Settings → Text size** — slider 85%–150% (0.05 steps, default 100%) with an inline
   preview; persisted; the whole app rescales live as the thumb moves.

Proven by **147/147 tests** (37 new/adapted; the 7-test Sprint 1 gate untouched and passing),
**4 mutations killed**, full pipeline green (forced rerun), **Kover 95.1%** on domain/data
(floor 70%):

```
./gradlew spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug koverVerifyAppDebug
```

## Decisions & rationale (do not relitigate)

- **D-S8-1 — One responsive widget, not separate picker entries.** `SizeMode.Responsive`
  with width-keyed breakpoints `SMALL_SIZE`(57dp) / `MEDIUM_SIZE`(130dp) / `LARGE_SIZE`(203dp)
  (heights all 2 rows, 102dp); pure chooser `layoutFor(DpSize)` is unit/mutation-tested.
  Metadata gains `minResizeWidth="40dp"` so launchers allow 1-column resize.
- **D-S8-2 — Custom calendar grid replaces M3 `DatePicker` internals.** M3 offers no
  per-day-cell slot (`DatePickerColors` is global). Indicator = **dot** under the day number
  (tint would fight the selection circle and dynamic color); fixed semantic green/red with
  light/dark variants (`Indicator*` in `ui/theme/Color.kt`, chosen by surface luminance);
  state always also in the cell `contentDescription` ("…, All readings done" / "…, Readings
  missed") — never color alone. Dialog contract preserved: tags `date-picker-confirm`/
  `date-picker-cancel`, year pinned (D-S5-3), Feb 29 selectable in leap years, leap-day
  initial date anchors to Feb 28.
- **D-S8-3 — Range-completion domain seam.** One grouped Room query
  (`COUNT(stream) GROUP BY dateEpochDay BETWEEN`) → `ProgressRepository.readCounts(start,end):
  Flow<Map<LocalDate,Int>>` → `GetMonthCompletionUseCase(YearMonth): Flow<Map<LocalDate,
  DayCompletion>>` (`COMPLETE`/`MISSED`/`NONE`; resolver guard makes Feb 29 never MISSED) →
  `DayReadingsViewModel.monthCompletionFor(YearMonth)` (per-month `StateFlow` cache,
  `WhileSubscribed`, degrades to empty map on failure). No per-day queries from UI.
- **D-S8-4 — Reset = current year only** (owner decision). `ProgressRepository.clearYear(year)`
  = inclusive ranged delete Jan 1..Dec 31 (bounds test- and mutation-pinned);
  `ResetYearProgressUseCase` takes the year from the injected `Clock`; only callable through
  the confirmation dialog; refreshes the widget via the Sprint 7 `WidgetRefresher` seam.
- **D-S8-5 — Text size = density multiplication; widget excluded.** `fontScale` (0.85–1.5,
  default 1.0, clamped on read AND write) lives in the existing DataStore `ThemeRepository`
  (it is the appearance-prefs repo; no rename churn). `DailyReadingPlannerTheme(fontScale=…)`
  wraps content in `LocalDensity provides Density(density, fontScale * systemFontScale)` —
  sp scales, dp does not, system a11y scaling *composes* (all three pinned in
  `FontScaleThemeTest`). The widget deliberately keeps following system settings only
  (consistent with D-S7-3).
- **ESpec deviation:** none new. The picker remains a dialog (D-S5-2 deviation carried), now
  fully custom rather than wrapping M3 `DatePicker`.

## State of the codebase

- **New:** `domain/model/DayCompletion.kt`, `domain/GetMonthCompletionUseCase.kt`,
  `domain/ResetYearProgressUseCase.kt`; tests `GetMonthCompletionUseCaseTest`,
  `ResetYearProgressUseCaseTest`, `DayDatePickerDialogTest` (replaces the deleted
  `DatePickerDateMappingTest` — the UTC-millis bridge died with the M3 picker),
  `FontScaleThemeTest`, `WidgetContentSizesTest`.
- **Rewritten:** `ui/datepicker/DayDatePickerDialog.kt` (custom grid; pure helpers
  `leadingEmptyCells`/`weekdayOrder`; locale read via `LocalConfiguration` — lint
  `NonObservableLocale` is an error here), `ui/settings/SettingsScreen.kt` (Theme + Text size
  + Progress sections; scrollable), `widget/WidgetContent.kt` (three layouts + `layoutFor`).
- **Extended:** `ReadingProgressDao` (`readCountsInRange`, `deleteRange`, `DayReadCount` row),
  `ProgressRepository`(+Impl), `ThemeRepository`(+Impl, `fontScale`, range consts on the
  interface companion), `SettingsViewModel` (reset + fontScale + `currentYear`; now injects
  `ResetYearProgressUseCase`, `WidgetRefresher`, `Clock`), `ThemeViewModel`, `MainActivity`
  (collects fontScale), `DayReadingsViewModel`/`DayReadingsScreen` (`monthCompletionFor`
  plumbed to the dialog), `TodayWidget` (`sizeMode = Responsive`), `today_widget_info.xml`,
  `ui/theme/Theme.kt` + `Color.kt`, strings (14 new), fakes (`FakeProgressRepository`,
  `FakeThemeRepository`).
- **No new dependencies**; version catalog untouched. Room schema unchanged (queries only —
  no migration).
- New test tags: `picker-day-N`, `picker-month-title`, `picker-prev/next-month`,
  `reset-progress`, `reset-confirm`, `reset-cancel`, `text-size-slider`, `text-size-value`,
  `text-size-preview`, `widget-count`.
- Mutation checks (all killed, restorations re-verified): past-only guard inverted
  (`isBefore`→`!isAfter`), Feb-29 resolver guard dropped, `clearYear` end bound +1 day,
  `layoutFor` LARGE bound made exclusive.

## Carryover & next goal

- **Next goal (Sprint 9, renumbered from 8): hardening, a11y & release readiness**, minus the
  retired items above. Remaining checklist: signing + Play release pipeline (D7, Jordan);
  TalkBack/a11y smoke incl. the new picker grid (cell descriptions, 48dp targets, dot
  contrast), the text-size slider, and reset dialog; system font-scale × in-app slider
  interplay on device; slider drag feel (each drag event writes to DataStore — verify no
  jank; debounce if needed); edge-to-edge `SystemBarStyle` scrim API 26–28 (Sprint 6 debt);
  StrictMode clean run; Custom Tabs tap-through; widget resize across breakpoints + 30-min
  update on a real launcher (new S8 surface); widget `previewImage`.
- **Queued/deferred (unchanged):** toggle-from-widget (V2), `exportSchema`/Room Gradle plugin
  at V2 schema work, Psalm 119 verse-ranges (post-V1), deprecation housekeeping
  (`hiltViewModel` package move, `createComposeRule` v2).
- **Scope protected out this sprint:** multi-month/year calendar views, streak UI (V2),
  font scale applied to the widget (D-S8-5), reset granularities other than current-year.

## Next sprint

`next: sprint-0009-hardening-release`

## Open questions & risks

- The custom picker grid is JVM-proven but has not rendered on a device; visual polish
  (dot size/contrast vs. dynamic-color themes, dialog width on narrow screens — fixed 360dp
  surface) is a Sprint 9 device-pass item.
- Slider writes to DataStore on every drag event (live preview by design). Correct and
  conflated, but unverified for drag smoothness on device — Sprint 9.
- `minResizeWidth=40dp` interacts with launcher grids differently per OEM; the 2x2 and 1x2
  breakpoints (130dp/57dp) chosen from common ~57–80dp cell widths — verify on device.
- Known debt carried: `exportSchema = false` on `ProgressDatabase`; Robolectric pinned
  `@Config(sdk = [34])`; `AppNavHost` push/pop untested on JVM.
