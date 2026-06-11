# Feature spec: Tracking start date

**Status:** Planned — post-release. Build **after the current V1 release sprint (Sprint 9) commits.**
**Author:** Diego (Android tech lead). **Owner sign-off:** behavior settled (see §0); one sub-decision open (§3, default value).
**Size:** **M** (see §9).

This is an implementation-ready spec. Another engineer should be able to build it end to end with
no open questions except the flagged default-value sub-decision (§3). All paths are absolute from the
repo root `app/src/main/kotlin/com/jpillion/dailyreadingplanner/`.

---

## 0. Settled product behavior (do not re-open)

A setting lets the user pick a **tracking start date**. Days **before** that date are **not treated
as missed**:

- No red "missed" dot in the date-picker calendar grid for pre-start days.
- Pre-start days are excluded from any missed/streak counting (streaks are V2; we make the predicate
  honor the start date now so V2 inherits it for free).
- The user can still **navigate to and mark** earlier days. Pre-start days are simply **neutral**
  (no indicator) by default, and **any marks already made on earlier days remain valid and are still
  shown** (a fully-read pre-start day still shows its green "complete" dot — see §2).

This solves the "installed mid-year → wall of red for days I never intended to track" problem: a new
user who starts the app on June 10 sets (or is defaulted to) a start date of June 10 and does not see
months of red for January–May.

---

## 1. Summary & rationale

The app currently classifies every **past scheduled day with fewer than three marks as MISSED** (red
dot in the picker). For anyone who adopts the app partway through the year, that paints the entire
preceding calendar red — discouraging, and semantically wrong, since they never intended to track
those days. The tracking start date lets the user declare "treat everything before this as not my
concern," turning pre-start days neutral while preserving any marks they choose to make there.

The change is deliberately **narrow and centralized**: it adds one persisted preference and threads it
into the **single** function that decides "is this past day missed?" (§5). Nothing about marking,
navigation, the reading content, or today/future behavior changes.

---

## 2. Behavior spec (precise rules)

Let `S` = the configured tracking start date (a full `LocalDate`, or `null` = unset), and `D` = the
day being classified. Inclusivity is **start-date-inclusive**: tracking includes `S` and every day
after it. So a day is "before tracking" iff `D < S`.

### What the start date suppresses

For a day `D` where `S != null` **and** `D.isBefore(S)`:

- The day is **never** classified `MISSED`, regardless of how many streams are read or how far in the
  past it is. It maps to `COMPLETE` if all three streams are read (existing marks are honored — see
  below), otherwise `NONE` (no dot).
- Therefore no red dot in the picker for that day, and the day is excluded from missed/streak counting.

### What it explicitly does NOT change

- **Existing marks persist and are shown.** A pre-start day with all three streams read still shows
  the **green COMPLETE** dot. We suppress *MISSED* only, not *COMPLETE*. (Rationale: a green dot is
  never discouraging; hiding earned completion would feel like data loss.)
- **Earlier days remain openable and markable.** Swiping/picking to a pre-start day works exactly as
  today; the per-reading and whole-day marks still write and read normally (`ProgressRepository` is
  untouched). The user can build up completion on pre-start days if they want — it just won't ever go
  red.
- **Today and future are unaffected.** The MISSED branch already only fires for `date.isBefore(today)`,
  so today/future were never MISSED. The start date only ever *removes* MISSED classifications; it can
  never *add* one.
- **Feb 29 is unaffected** — it is already `NONE` (no scheduled readings, D1) ahead of any start-date
  logic.

### Truth table (the only behavioral change is the new first row)

| Condition (evaluated in order)                              | Result      |
|------------------------------------------------------------|-------------|
| `D` is Feb 29 / `NoScheduledReadings`                      | `NONE`      |
| all three streams read                                      | `COMPLETE`  |
| **`S != null` && `D < S`** (NEW — would-be MISSED suppressed) | `NONE`    |
| `D < today` (past, incomplete, in-tracking)                | `MISSED`    |
| otherwise (today/future, incomplete)                       | `NONE`      |

Note ordering: COMPLETE is checked **before** the start-date gate so that a fully-read pre-start day
still shows green. The start-date gate sits **immediately before** the MISSED branch — it only
intercepts days that would otherwise have gone red.

---

## 3. Default value — ONE OPEN SUB-DECISION (owner)

Two viable defaults:

- **(A) Default = unset (`null`)** → current behavior preserved exactly; nobody is affected until they
  opt in. Zero behavior change for existing users on upgrade.
- **(B) Default = first-run date** → new installs get the benefit automatically (no wall of red), no
  setting to discover. Captured once on first launch (when no value has ever been written).

**Recommendation: (B), default to the first-run date**, because it directly fixes the motivating
problem for the exact users who hit it (new mid-year adopters) without requiring them to find a
setting. Existing upgraders are unaffected: only write the first-run default when **no value has ever
been persisted AND there is no existing progress** — i.e., a genuinely fresh install. If an upgrading
user already has marks, leave it `null` (they've been using the app; don't retroactively neutralize
their history). The setting remains fully user-editable and clearable in both cases.

**Implementation of (B):** in `SettingsRepository` (§4), expose the start date as a *stored* nullable
value. A small first-run initializer (e.g., in `MainApplication.onCreate` or a one-time `WorkManager`-free
suspend call from `MainActivity`) checks: if the "start date initialized" marker pref is absent, set
the marker, and — only if `ProgressRepository` reports zero existing marks — write today's date as the
start date. Keep the marker separate from the value so a user who deliberately clears the date to
`null` is not re-defaulted on next launch.

> If the owner prefers the zero-surprise path, choose (A): drop the first-run initializer entirely and
> ship with `null`. Everything else in this spec is identical.

---

## 4. Data / persistence

The start date is an appearance/behavior preference and belongs with theme + `fontScale` in the
**DataStore-Preferences** store, not in Room.

**Repository rename (recommended, low churn): `ThemeRepository` → `SettingsRepository`.** It already
holds non-theme prefs (`fontScale`), so the name is now misleading, and this feature adds a third
unrelated pref. Rename the interface + impl + the `@Binds` in `di/RepositoryModule.kt` and the
constructor injection sites (`SettingsViewModel`, `ThemeViewModel`, `MainActivity` theme wiring). This
is a pure rename (no logic change); IDE-assisted, ~10 minute mechanical edit. The DataStore file name
(`DataModule.SETTINGS_STORE`) and existing keys (`theme_mode`, `font_scale`) **must not change** —
renaming the Kotlin type does not migrate stored data, and the keys are what's persisted.

> Least-churn alternative if the team wants to avoid touching call sites during the post-release window:
> just **add the pref to `ThemeRepository`** as-is and defer the rename. Acceptable; the rename is
> cosmetic. Recommendation is to do the rename since we're already opening the file.

### New pref shape

Add to `data/prefs/SettingsRepository.kt` (interface) and `SettingsRepositoryImpl.kt`:

```kotlin
// SettingsRepository (interface)
/** Tracking start date: days strictly before this are never MISSED. null = unset (track all). */
val trackingStartDate: Flow<LocalDate?>

suspend fun setTrackingStartDate(date: LocalDate?)   // null clears it
```

```kotlin
// SettingsRepositoryImpl — store as epoch-day Long; absent key => null
private val TRACKING_START_EPOCH_DAY = longPreferencesKey("tracking_start_epoch_day")

override val trackingStartDate: Flow<LocalDate?> =
    dataStore.data.map { prefs ->
        prefs[TRACKING_START_EPOCH_DAY]?.let(LocalDate::ofEpochDay)
    }

override suspend fun setTrackingStartDate(date: LocalDate?) {
    dataStore.edit { prefs ->
        if (date == null) prefs.remove(TRACKING_START_EPOCH_DAY)
        else prefs[TRACKING_START_EPOCH_DAY] = date.toEpochDay()
    }
}
```

**Type choice: nullable `LocalDate` backed by `longPreferencesKey` (epoch day).** Epoch day is
timezone-free, compact, and trivially comparable; it matches how the rest of the codebase keys days
(`ReadingProgressEntity` PK `dateEpochDay`, `selectedEpochDay` in the picker). `null` (absent key) is
the canonical "unset = track everything" state. (For default-value option B, see §3 for the separate
first-run marker pref.)

---

## 5. Domain change — the single missed-day predicate

**This is the central design decision: there is exactly ONE place where MISSED is decided, and that is
where the start date is consulted.**

`domain/GetMonthCompletionUseCase.kt` → `private fun classify(...)`. Today:

```kotlin
private fun classify(date, readCount, today) = when {
    resolver.resolve(date) is ResolvedDate.NoScheduledReadings -> DayCompletion.NONE
    readCount >= STREAM_COUNT -> DayCompletion.COMPLETE
    date.isBefore(today) -> DayCompletion.MISSED
    else -> DayCompletion.NONE
}
```

Change: inject the tracking start date into the use case and add **one guard immediately before the
MISSED branch** (preserving the truth-table ordering in §2):

```kotlin
class GetMonthCompletionUseCase @Inject constructor(
    private val resolver: ScheduleDateResolver,
    private val progressRepository: ProgressRepository,
    private val settingsRepository: SettingsRepository,   // NEW
    private val clock: Clock,
) {
    operator fun invoke(month: YearMonth): Flow<Map<LocalDate, DayCompletion>> {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        // combine so the picker re-emits when EITHER marks OR the start date changes.
        return combine(
            progressRepository.readCounts(start, end),
            settingsRepository.trackingStartDate,
        ) { counts, trackingStart ->
            val today = LocalDate.now(clock)
            buildMap {
                var date = start
                while (!date.isAfter(end)) {
                    put(date, classify(date, counts[date] ?: 0, today, trackingStart))
                    date = date.plusDays(1)
                }
            }
        }
    }

    private fun classify(
        date: LocalDate,
        readCount: Int,
        today: LocalDate,
        trackingStart: LocalDate?,   // NEW
    ): DayCompletion = when {
        resolver.resolve(date) is ResolvedDate.NoScheduledReadings -> DayCompletion.NONE
        readCount >= STREAM_COUNT -> DayCompletion.COMPLETE
        trackingStart != null && date.isBefore(trackingStart) -> DayCompletion.NONE   // NEW
        date.isBefore(today) -> DayCompletion.MISSED
        else -> DayCompletion.NONE
    }
}
```

Why here and nowhere else:

- `DayCompletion.MISSED` is produced in exactly one function in the codebase. The date-picker grid
  (`ui/datepicker/DayDatePickerDialog.kt` `DayCell`) and any future streak/summary surface consume
  `DayCompletion`; they render whatever they are handed. Centralizing in `classify` means **every
  current and future consumer** (picker dots, a11y `contentDescription`, V2 streaks) honors the start
  date with no per-call-site edits.
- Using `combine` (vs. the current single `map`) makes the picker live-update when the user changes the
  start date while a picker is open — the same liveness the existing month flow already gives for marks.
- The flow already degrades to `emptyMap()` on error in `DayReadingsViewModel.monthCompletionFor`
  (`.catch { emit(emptyMap()) }`), so adding a second upstream flow does not change failure behavior.

No change is needed in `DayReadingsViewModel.monthCompletionFor`, `DayReadingsScreen`, or
`DayDatePickerDialog` — they pass `Map<LocalDate, DayCompletion>` through unchanged. The pre-start days
simply arrive as `NONE`/`COMPLETE` instead of `MISSED`.

(Optional polish, out of scope unless asked: a future "Streaks" V2 use case would inject the same
`trackingStartDate` and use the same `date.isBefore(trackingStart)` exclusion — the predicate is the
contract.)

---

## 6. UI — Settings row

Add a row to `ui/settings/SettingsScreen.kt`, in a new section (reuse `SectionTitle`) below the
existing Reset-progress section. Pattern mirrors the existing rows: a `Row` with `selectable(role =
Role.Button)`, a `testTag`, 56.dp height, 16.dp horizontal padding.

- **Label:** "Start tracking from" (string resource `tracking_start_title`).
- **Trailing value:** the formatted date (`DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)`) when
  set, or "Not set" (`tracking_start_unset`) when `null`.
- **Tap** opens a date picker. **Important:** this is a **full calendar date including the year** —
  unlike the schedule's month/day picker (`DayDatePickerDialog`, which pins the year per D-S5-3). Do
  **not** reuse `DayDatePickerDialog` here. Use the **stock M3 `DatePickerDialog` + `DatePicker`**
  (`rememberDatePickerState`), which gives a real year-navigable picker out of the box and needs no
  per-cell completion dots. Keep it stateless-screen-friendly: hoist visibility with a
  `rememberSaveable` boolean exactly like `showResetDialog`.
- **Clear affordance:** when a date is set, show a "Clear" `TextButton`/trailing icon
  (`tracking_start_clear`) in the row that calls `onTrackingStartChanged(null)`. Also acceptable: a
  "Clear" dismiss-style button inside the date dialog. Provide at least one obvious path back to unset.

### Wiring

- `SettingsViewModel`: add
  `val trackingStartDate: StateFlow<LocalDate?>` (`stateIn(..., initialValue = null)`) collected from
  `settingsRepository.trackingStartDate`, and
  `fun onTrackingStartChanged(date: LocalDate?) { viewModelScope.launch { settingsRepository.setTrackingStartDate(date) } }`.
- `SettingsRoute`: collect `trackingStartDate` with `collectAsStateWithLifecycle()` and pass value +
  callback into `SettingsScreen` (keep the screen stateless, as it is today).
- No new route or nav entry — Settings is already a pushed route (`Routes.SETTINGS` in
  `ui/navigation/AppNavHost.kt`).

### Accessibility

- The row gets a `contentDescription` combining label + current value ("Start tracking from, June 10
  2026" / "Start tracking from, not set"), and 48dp+ touch target (the 56.dp row already satisfies this).
- The Clear control gets its own `contentDescription` ("Clear tracking start date").
- The picker dots already pair color with a spoken state in `DayCell.contentDescription` (`day_complete`
  / `day_missed`); since pre-start days become `NONE`, they correctly speak just the date with no state
  word — consistent with other no-indicator days. No a11y change needed in the picker.

### Strings (add to `res/values/strings.xml` — single file, but note: do not edit during the active
release session; add these when the feature is built)

`tracking_start_section_title`, `tracking_start_title`, `tracking_start_unset` ("Not set"),
`tracking_start_clear` ("Clear"), and a content-description string for the row + clear button.

---

## 7. Edge cases

- **Start date in the future:** allowed. Then *every* day before it (including some that are in the
  past relative to `today`) is suppressed from MISSED → fewer/no red dots. This is internally
  consistent ("I'll start tracking next Monday"). No special handling; the `date.isBefore(trackingStart)`
  guard covers it. Today itself: if `today < trackingStart`, today is incomplete → `NONE` (today is
  never MISSED anyway), so no visible anomaly.
- **Start date = today:** today and all future days track normally; only strictly-earlier days are
  suppressed. This is the expected mid-year-adoption case (and the option-B default).
- **Clearing it (`null`):** reverts to exact current behavior — all past incomplete scheduled days are
  MISSED again. The picker re-emits live via the `combine` (no app restart needed). Previously-earned
  COMPLETE dots are unaffected (they never depended on the start date).
- **Interaction with Sprint 8 Reset-progress — keep them INDEPENDENT (recommended).** Reset clears
  *marks* for the current year (`ResetYearProgressUseCase` → `clearYear`); it must **not** touch the
  tracking start date, and changing the start date must **not** touch marks. They are orthogonal: one
  edits data, the other edits a display/classification predicate. Do not clear the start date on reset
  and do not reset progress when the start date changes. (If, after a reset, the user also wants a fresh
  start date, they set it explicitly.)
- **Leap day / Feb 29 before the start date:** no special case. Feb 29 is `NONE` *before* the start-date
  gate is even reached (first branch), so a pre-start Feb 29 behaves identically to any other Feb 29.
- **Start date on a Feb 29 (leap year) chosen via the M3 picker:** harmless. The value is stored as an
  epoch day; comparisons are pure date math. A common-year day before it still suppresses correctly.
- **Widget:** the launcher widget (`widget/TodayWidget.kt` + `WidgetContent.kt`) shows **today's**
  readings and per-stream read/unread + an "all done" badge via `GetDayReadingsUseCase` — it does **not**
  render a MISSED concept or any past-day state, so there is **no missed indicator to suppress today**.
  **Therefore the widget needs no change for V1 of this feature.** Call-out for consistency: if a future
  widget surface ever shows past-day completion (e.g., a week strip), it must read `trackingStartDate`
  via the same `@EntryPoint` pattern (`TodayWidgetEntryPoint`) and apply the identical predicate. Flag
  this in the widget handoff so it isn't missed when the widget grows. For now: no widget work.

---

## 8. Testing

Follow existing conventions: pure-JVM unit tests for domain/data; Robolectric + Compose UI tests run
under `./gradlew testDebugUnitTest` (`@Config(sdk = [34])`, `isIncludeAndroidResources = true`); Kover
floor applies to domain/data. Use a fixed `Clock` (the codebase injects `java.time.Clock`) so "today"
is deterministic.

1. **`GetMonthCompletionUseCase` — the missed-day predicate (highest value; mutation-targeted).**
   With a fixed clock at e.g. 2026-06-15 and `trackingStart = 2026-06-10`:
   - day **before** start (Jun 9), past, incomplete → `NONE` (was `MISSED`). *(kills the boundary
     mutation: `<` vs `<=`.)*
   - day **at** start (Jun 10), past, incomplete → `MISSED` (start date itself IS tracked — inclusivity
     per §2).
   - day **after** start (Jun 12), past, incomplete → `MISSED`.
   - pre-start day **fully read** (all 3 streams) → `COMPLETE` (marks honored; COMPLETE wins over the
     gate — ordering test).
   - `trackingStart = null` → identical to current behavior (Jun 9 → `MISSED`). *(regression guard.)*
   - future start date (e.g. `2026-07-01` with clock 2026-06-15) → all June past days → `NONE`.
   - Feb 29 in a leap-year month before start → `NONE` (unchanged; ordering: Feb-29 branch precedes the
     new guard).
   - **liveness:** emit a new `trackingStartDate` on the fake settings flow → assert the use case
     re-emits an updated map (proves the `combine`, not the old single `map`).
2. **`SettingsRepositoryImpl` persistence** (DataStore test, in-memory/temp file): set a date → read it
   back as `LocalDate`; set `null` → reads back `null` (key removed); round-trip an epoch-day boundary
   (e.g. a leap day) to confirm no off-by-one.
3. **`SettingsViewModel`:** `onTrackingStartChanged(date)` writes through; `trackingStartDate` StateFlow
   reflects the repo flow; `onTrackingStartChanged(null)` clears.
4. **`SettingsScreen` Compose UI test:** row shows formatted date when set / "Not set" when null;
   tapping opens the date dialog; Clear control invokes the callback with `null`; row
   `contentDescription` present. Reuse the existing tag conventions (add tags like
   `tracking-start-row`, `tracking-start-clear`, `tracking-start-dialog`).
5. **Reset independence:** a test (unit at use-case/VM level is enough) asserting `ResetYearProgressUseCase`
   does not alter the persisted start date, and that changing the start date does not invoke
   `clearYear`.
6. **(If default option B is chosen)** first-run initializer test: fresh store + zero progress → start
   date defaulted to "today"; store with existing marks → left `null`; user-cleared (marker set, value
   absent) → not re-defaulted on next launch.

Add one or two of these as mutation-kill targets (boundary `<`/`<=`, the new branch reachability) to
match the project's mutation-verification habit.

---

## 9. Effort, ownership, sequencing

**Overall size: M.** Small, well-bounded surface; the one-line predicate change is the core, the rest is
plumbing + UI + tests.

| Workstream | Owner | Size | Notes |
|---|---|---|---|
| Pref in `SettingsRepository` (+ optional `ThemeRepository`→`SettingsRepository` rename), `di` `@Binds`/injection | **Diego / data** | S | Rename is mechanical; keep DataStore file name + existing keys. |
| `GetMonthCompletionUseCase` predicate + `combine` | **Diego / data** | S | The central change (§5). |
| First-run default initializer (only if owner picks option B) | **Diego / data** | S | Separate marker pref; gated on zero existing progress. |
| Settings row + M3 full-date `DatePickerDialog` + clear + a11y + strings | **Sam** (Settings UI) | M | Stock M3 picker, **not** `DayDatePickerDialog`. |
| `SettingsViewModel`/`SettingsRoute` wiring | **Sam** | S | Mirrors existing theme/fontScale wiring. |
| Tests (predicate, persistence, VM, Compose, reset-independence) | **Riley** | M | Predicate tests are the priority + mutation targets. |

**Dependencies / sequencing:**

- **Start only AFTER the Sprint 9 release work commits.** Many source files are currently
  modified-but-uncommitted (settings, widget, picker, formatter). Building this on top would collide;
  branch off the post-release `main`.
- Order: (1) data pref + DI, (2) use-case predicate + its tests, (3) Settings UI + VM wiring + UI tests,
  (4) optional first-run default. UI depends on the repo flow existing; the predicate change is
  independent of the UI and can land + be tested first.
- **Re-verify these integration points against the final post-release code before merging** (they are
  the files the active release session is touching, so signatures may shift):
  - `data/prefs/ThemeRepository(Impl).kt` — confirm the rename target and existing keys.
  - `domain/GetMonthCompletionUseCase.kt` — confirm `classify` shape and the `readCounts` flow source
    didn't change.
  - `ui/settings/SettingsScreen.kt` / `SettingsViewModel.kt` — confirm the stateless-screen + Route
    pattern and tag conventions held.
  - `di/RepositoryModule.kt`, `di/DataModule.kt` — confirm DataStore provider + `@Binds` for the
    (possibly renamed) settings repo.
  - `widget/TodayWidget.kt` `TodayWidgetEntryPoint` — only relevant if a past-day widget surface was
    added (it was not as of Sprint 8); confirm and keep "no widget change" if so.

---

## 10. Out of scope

- Streaks / longest-streak (V2) — but the predicate added here is the contract they will reuse.
- Any change to how marks are stored or how readings are shown.
- A widget change (see §7 — none required for V1 of this feature).
- An end-of-tracking date or per-stream start dates (not requested).
