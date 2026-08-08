# p1-02 — `java.time` → kotlinx-datetime, and the `DateProvider` seam

> **Assignee:** Senior Shared-Core Engineer
> **Release:** 1.9.0 · **Merge order:** Group B — **strictly after `p1-01`** (six shared files).
> Parallel with nothing.
> **Inherits:** [`p1-00-overview.md`](p1-00-overview.md) rules R1–R7.
> **Preconditions:** Gate 0 closed. **`gate0-minor-spikes.md` M1 and M3 answered.** `p1-01` merged.
> **Decides nothing** — ADR-0009 already did. This brief executes it.

---

## Objective

Replace every `java.time` type in `:app` with its kotlinx-datetime equivalent, and replace the
**13 injected `java.time.Clock` sites with one `DateProvider` seam**.

**36 main-source files and 38 test files.** The single largest mechanical change in the port. One
atomic task, one PR, no behaviour change.

---

## Context

Date anchoring **is** the product. "January 1 is always Genesis 1–2 / Psalm 1–2 / Matthew 1–2" is
the founding constraint; progress is keyed by full date; streaks walk dates; the picker grid is
built from dates; Feb 29 is a first-class no-readings case in every layer.

`java.time` does not exist on Kotlin/Native.

### The decision you are executing, and why — one `DateProvider`, not `Clock` + `TimeZone`

**This was an open question in the brief that produced this one. It is now closed. Decided:
one `DateProvider` seam.**

`java.time.Clock` bundles an instant source **and a zone**, so `LocalDate.now(clock)` reads both in
one call. `kotlin.time.Clock` yields an `Instant` only. A straight swap turns each of the 13 sites
into `clock.now().toLocalDateTime(zone).date` and requires a `TimeZone` from somewhere — so a
faithful "just rename it" port threads **two** parameters through 13 use cases and puts the
question *"which zone?"* in **13 places instead of one**.

Rejected alternatives, explicitly:

- **Thread `Clock` + `TimeZone` through every use case.** Doubles the plumbing; makes the timezone
  question ambient; and every one of ~40 test fakes grows a second parameter that no test cares
  about. It is *more* faithful to the letter and *less* faithful to the intent.
- **A global/default `TimeZone.currentSystemDefault()` inside each use case.** Untestable across
  zones, and it hides the decision instead of naming it.
- **Keep injecting a `Clock` and derive the zone at each site.** The same 13-places problem with
  extra steps.

Consequence accepted: this is a **deliberate small deviation from a faithful port**, sanctioned by
ADR-0009 and by `p1-00` rule R1. It is a pure mechanical substitution with **no behavioural
difference** — `DateProvider.today()` returns what `LocalDate.now(clock)` returns today.

Bonus, and it should be stated rather than discovered: `DateProvider.timeZone` gives timezone
semantics an **explicit home for the first time**. Today `LocalDate.now(clock)` quietly uses the
system default. Current behaviour — "the readings change when the local date changes," including
when a user crosses the date line mid-day — is **preserved and now documented**. Report it to
Verification for `docs/parity-matrix.md` as intentional.

### The 36 main-source files

```
core/date/ScheduleDateResolver.kt
data/prefs/{PartialReadingRepository,PartialReadingRepositoryImpl,SettingsRepository,SettingsRepositoryImpl}.kt
data/progress/{ProgressRepository,ProgressRepositoryImpl}.kt
di/AppModule.kt
domain/CompleteTrackingStartPromptUseCase.kt   domain/DayCompletionClassifier.kt
domain/DeliverDueReminderUseCase.kt            domain/GetDayReadingsUseCase.kt
domain/GetMonthCompletionUseCase.kt            domain/GetPartialSegmentsUseCase.kt
domain/GetReadingStatsUseCase.kt               domain/GetYearStripsUseCase.kt
domain/MarkReadOnOpenUseCase.kt                domain/MarkSegmentReadOnOpenUseCase.kt
domain/MarkWholeDayUseCase.kt                  domain/RefreshPersistentNotificationUseCase.kt
domain/ResetYearProgressUseCase.kt             domain/ToggleReadingUseCase.kt
domain/ToggleSegmentCheckUseCase.kt            domain/model/DayReadings.kt
reminders/AlarmTimes.kt                        reminders/ReminderScheduler.kt
ui/datepicker/DayDatePickerDialog.kt           ui/day/DayReadingsScreen.kt
ui/day/DayReadingsViewModel.kt                 ui/day/DayUiState.kt
ui/day/TrackingStartPromptDialog.kt            ui/settings/SettingsScreen.kt
ui/settings/SettingsViewModel.kt               ui/settings/TrackingStartDatePickerDialog.kt
widget/TodayWidget.kt                          widget/WidgetContent.kt
```

Regenerate the list yourself before starting — `grep -rl "^import java\.time" app/src/main/kotlin`
— and confirm it is still 36. If it is not, `p1-01` moved something and you should know that.

### The 13 `java.time.Clock` sites

`di/AppModule.kt` (the provider) · `data/prefs/PartialReadingRepositoryImpl.kt` ·
`data/progress/ProgressRepositoryImpl.kt` · `domain/{GetMonthCompletionUseCase,
GetReadingStatsUseCase, GetYearStripsUseCase, DeliverDueReminderUseCase,
RefreshPersistentNotificationUseCase, ResetYearProgressUseCase}.kt` ·
`reminders/ReminderScheduler.kt` · `ui/day/DayReadingsViewModel.kt` ·
`ui/settings/SettingsViewModel.kt` · `widget/TodayWidget.kt`

---

## Contract

### The seam — Staff-owned. Copy exactly.

`app/src/main/kotlin/.../platform/DateProvider.kt`:

```kotlin
package com.jpillion.dailyreadingplanner.platform

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * The app's notion of "now".
 *
 * Everything date-anchored resolves through this — never through a global clock — so that tests
 * can pin a date and the app has exactly ONE place that decides which calendar day the user is in.
 *
 * [today] is the user's *local* calendar date. Two users in different zones legitimately see
 * different readings at the same instant: that is the intended behaviour of a date-anchored plan,
 * and it is why this interface exposes a date rather than an instant to its callers.
 *
 * Implementations are cheap and side-effect-free. [today] may be called from a composable.
 */
interface DateProvider {
    /** The user's current local calendar date. */
    fun today(): LocalDate

    /** The current instant, for timestamping a mark (`readAtEpochMillis`). */
    fun now(): Instant

    /** The zone [today] is resolved in. Exposed so date arithmetic can be explicit, not ambient. */
    val timeZone: TimeZone
}
```

Android implementation: `platform/SystemDateProvider.kt`, using
`Clock.System` + `TimeZone.currentSystemDefault()`. Bound `@Singleton` in `di/AppModule.kt`,
**replacing** the `java.time.Clock` provider, which is deleted.

### Type mapping

| From | To |
|---|---|
| `java.time.LocalDate` | `kotlinx.datetime.LocalDate` |
| `java.time.LocalTime` | `kotlinx.datetime.LocalTime` |
| `java.time.DayOfWeek` | `kotlinx.datetime.DayOfWeek` |
| `java.time.Instant` | `kotlin.time.Instant` |
| `java.time.Clock` | **deleted** → `DateProvider` |
| `java.time.ZonedDateTime` (`reminders/AlarmTimes.kt`, `ReminderScheduler.kt`) | `LocalDateTime` + an explicit `TimeZone` from `DateProvider` |
| `java.time.YearMonth` | **see below** |

### `YearMonth` — both branches are pre-decided; take the one M1 returned

- **M1 = adequate** → `kotlinx.datetime.YearMonth`. A rename.
- **M1 = inadequate** → write `domain/model/PlanMonth.kt`, a ~40-line value class carrying exactly:
  construct-from-`LocalDate`, `atDay(n)`, `lengthOfMonth()`, `plusMonths(n: Int)` (signed,
  year-crossing), and `equals`/`hashCode`/`compareTo`. Nothing else.
  **`GetMonthCompletionUseCase`'s public signature takes `PlanMonth`** — a type we own is
  preferable to leaking a partial library type through a domain signature.

Either way `DayDatePickerDialog.monthForPage`'s ±3,000-month offset arithmetic is unchanged, and
must still cross year boundaries (D-S21-1).

### The epoch-day pin — write this test, and it is permanent

`toEpochDays()` keys **every progress row**, the tracking-start date, the partial-segment token
cache and the day-pager page index — **19 conversion sites**. A one-day disagreement silently
shifts every user's history.

M3 answered whether the two agree. **Regardless of the answer, commit the pin**, covering:
`1970-01-01` (0), `1969-12-31` (−1), `2000-02-29`, `2024-02-29`, `1900-03-01` (the non-leap
century), `2026-12-31` / `2027-01-01`, and a pre-1970 spread. Plus
`fromEpochDays(toEpochDays(d)) == d` for all of them.

### The test fake

`app/src/test/.../platform/FakeDateProvider.kt`:

```kotlin
class FakeDateProvider(
    var today: LocalDate,
    override val timeZone: TimeZone = TimeZone.UTC,
) : DateProvider {
    override fun today(): LocalDate = today
    override fun now(): Instant = today.atStartOfDayIn(timeZone)
}
```

**Most of the ~40 affected tests just want "today is this date."** Convert mechanically. Any test
needing a specific instant (`readAtEpochMillis` assertions) sets `now()` explicitly — do not add a
second constructor parameter unless a test actually needs it.

---

## Acceptance criteria

1. `grep -rn "^import java\.time" app/src/main/kotlin app/src/test/kotlin` returns **nothing**.
2. `grep -rn "java.time.Clock\|Clock.systemDefaultZone\|Clock.fixed" app/src/` returns **nothing**.
   Zero `java.time.Clock` injection sites remain.
3. `DateProvider` is byte-identical to the source above.
4. The epoch-day equivalence pin is committed and green, covering every listed date.
5. **`AlarmTimes`' next-occurrence maths is byte-for-byte behaviourally identical.** Its existing
   tests — including the **exactly-now no-refire boundary** (a killed mutation from sprint 12) —
   pass unchanged. This is the highest-risk file in the sweep because it does zone-crossing
   arithmetic and its bugs are invisible until 08:00 the next morning.
6. **`ScheduleDateResolver`'s Feb 29 → `NoScheduledReadings` behaviour is unchanged**, and Feb 29
   remains unrepresentable in `ReadingDate`.
7. Test count **940 → 940 + the new pin(s)**, with **zero deletions**. A test that no longer
   compiles is *converted*, never dropped. State the before/after count explicitly.
8. **≥3 killed mutations, each by exactly its intended test, each restored byte-identically:**
   (a) `DateProvider.today()` returns `today.plus(1, DAY)` → the Feb-29 and day-boundary pins go
   red; (b) `toEpochDays` ↔ `fromEpochDays` swapped at one `ProgressRepositoryImpl` site → the
   progress pins go red; (c) `AlarmTimes`' next-occurrence comparison flipped to `<=` → the
   exactly-now boundary test goes red.
9. Every test file touched is converted Truth → assertk (R4).
10. Full pipeline green; **Kover ≥ the current floor** on domain/data (currently ~96%).
11. **The six data gates untouched, counts unchanged: 11 / 10 / 8 / 6 / 18 / 5.**
12. **R5 R8 release-build device smoke**, with the date-sensitive path exercised deliberately:
    swipe **Dec 31 → Jan 1** (the year-crossing case), open **Feb 29** in a leap year, set a
    reminder for **two minutes from now and wait for it to fire**, and confirm the widget shows
    today.

---

## Boundaries / write set

**Yours:** the 36 main files listed above, their ~38 test files, plus
`platform/{DateProvider,SystemDateProvider}.kt` and `test/.../platform/FakeDateProvider.kt`
**(new)**, and `domain/model/PlanMonth.kt` if M1 said so.

**Not yours:**
- `platform/DateTextFormatter.kt` and its Android implementation — **`p1-01` owns them.** They are
  merged before you start; you consume them. If a signature there is wrong for kotlinx types,
  **escalate to Staff** — do not edit it.
- `bible/data/remote/**` and `data/reference/**` — **`p1-03`**.
- `bible/data/{BibleAssetVersion,BibleAssetGate,RoomBibleTextSource}.kt`,
  `di/{DispatcherModule,BibleModule}.kt` — **`p1-04`**.
- `app/build.gradle.kts`, `gradle/libs.versions.toml` — **Build & Release.** kotlinx-datetime is a
  new dependency: request the coordinate and version, do not add it.
- `docs/parity-matrix.md` — **Verification.**

---

## Escalation triggers

- **Any behaviour difference at all**, however small — a date off by one, a different rounding, a
  different `DayOfWeek` ordinal base → **Staff**, blocking. `kotlinx.datetime.DayOfWeek`'s ordinal
  base versus `java.time`'s `MONDAY = 1` is a specific thing to check: the picker grid's
  `firstDayOfWeek` offset arithmetic depends on it.
- **The epoch-day pin fails** → **Staff**, blocking, immediately. Do not add a correcting offset.
- **You need a fourth member on `DateProvider`** → **Staff**. Three covers 13 sites.
- **A test must be deleted rather than converted** → **Staff**. There is no acceptable reason;
  say which test and why it seems impossible.
- **`PlanMonth` grows past ~60 lines** → **Staff**. The boundary is wrong, not the class.
- **The sweep exceeds its estimate by more than half** → **EM + Staff**. This is the file-count
  spine of Phase 1 and a blown estimate here re-plans the release.
