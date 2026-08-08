# p1-01 — Extract the localized date/time formatting seam (`DateTextFormatter`), still on `java.time`

> **Assignee:** Senior Shared-UI Engineer
> **Release:** 1.9.0 · **Merge order:** Group A (parallel with `p1-03`, `p1-04`, `p1-08`).
> **`p1-02` merges strictly after this** — they share six files.
> **Inherits:** [`p1-00-overview.md`](p1-00-overview.md) rules R1–R7. Read it first.
> **Precondition:** Gate 0 closed.

---

## Objective

Move every piece of **locale-dependent** date, time and number formatting out of composables and
behind **one interface**, `DateTextFormatter`, with a single Android implementation that is a
literal transcription of today's code.

**This task does not change `java.time` to anything.** It changes *where the formatting call
happens*. The value types stay exactly as they are, so the extraction is provably
behaviour-preserving and every existing literal-string test assertion must still pass unchanged.

---

## Context

kotlinx-datetime's formatting is **pattern-based and not locale-aware**. There is no equivalent
for `DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)`, `ofLocalizedTime(SHORT)`,
`WeekFields.of(locale).firstDayOfWeek`, `java.text.NumberFormat`, or
`android.text.format.DateFormat.is24HourFormat`. So this is not a library swap; it is a **platform
seam** (ADR-0009 §4).

Doing it *before* the type swap is deliberate. Extracting the seam while both sides are still
`java.time` makes the Android implementation a copy-paste of the current expressions — you can
diff it. Doing both at once would mean the only proof that formatting is unchanged is that the
tests pass, and several of those tests are the ones being moved.

### The call sites — this is the complete list, verified on `main`

| File:line | Expression |
|---|---|
| `ui/datepicker/DayDatePickerDialog.kt:114` | `WeekFields.of(locale).firstDayOfWeek` |
| `ui/datepicker/DayDatePickerDialog.kt:257` | `date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))` |
| `ui/datepicker/DayDatePickerDialog.kt:304` | `DateTimeFormatter.ofPattern("MMMM uuuu", Locale.getDefault())` |
| `ui/datepicker/DayDatePickerDialog.kt` | `java.time.format.TextStyle` (weekday header initials) |
| `ui/settings/SettingsScreen.kt:563` | `reminderTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))` |
| `ui/settings/SettingsScreen.kt:605` | `DateFormat.is24HourFormat(LocalContext.current)` |
| `ui/settings/SettingsScreen.kt:638` | `trackingStartDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))` |
| `ui/day/TrackingStartPromptDialog.kt:72` | `today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))` |
| `ui/day/DayReadingsScreen.kt:297` | `formatMonthDay` → `ofPattern("MMMM d")` |
| `ui/day/DayReadingsScreen.kt:307` | `formatDayDate` → `ofPattern("EEEE, MMMM d")` |
| `ui/stats/StatsContent.kt:186` | `NumberFormat.getIntegerInstance()` |
| `widget/WidgetContent.kt:95-96` | `ofPattern("EEE, MMM d", Locale.US)`, `ofPattern("MMM d", Locale.US)` |

**The widget's two are already `Locale.US` — deliberately hard-coded.** They go through the seam
for consistency of *shape*, but their Android implementation keeps `Locale.US`, and the widget is
Android-only forever anyway (ADR-0006). Do not "fix" them to `getDefault()`; that is a behaviour
change.

### The part that is shared logic and must NOT move behind the seam

`DayReadingsScreen`'s title rule is **D-S16-1**, real product logic sitting on top of a localized
fragment:

> today = `"Today – June 10"` (en dash, no year); any other day = `"Friday, June 13"`; **the year
> is appended only when it differs from today's year** (the Dec 31 → Jan 1 swipe case, pinned by
> test); `maxLines = 1` + ellipsis guarantees one line.

**The rule stays in the composable. Only the localized fragment comes from the seam.** Splitting it
the other way would push a product decision into a platform implementation, where iOS would have to
reimplement it and the two would drift. Same for the reference-formatting rules in
`ReadingFormatter` — **those are not date formatting and are not in scope for this task at all.**

---

## Contract

### The interface — Staff-owned. Copy it exactly. Do not add methods.

Create `app/src/main/kotlin/com/jpillion/dailyreadingplanner/platform/DateTextFormatter.kt`:

```kotlin
package com.jpillion.dailyreadingplanner.platform

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * User-facing date, time and number text, rendered in the device's own locale and calendar
 * conventions.
 *
 * Implementations delegate to the platform's localized formatting, so the exact wording, ordering
 * and separators are the platform's. **Output is NOT guaranteed to match across platforms, and
 * callers must never assert on the returned string.** Tests that need determinism inject a fake.
 *
 * Every method is pure and cheap; none touches disk or network, and all may be called from a
 * composable.
 */
interface DateTextFormatter {

    /** The full, spoken-quality form. English example: "Friday, June 13, 2026". */
    fun fullDate(date: LocalDate): String

    /** The compact form used in settings rows. English example: "Jun 13, 2026". */
    fun mediumDate(date: LocalDate): String

    /** Month and day, no year and no weekday. English example: "June 13". */
    fun monthDay(date: LocalDate): String

    /** Weekday, month and day, no year. English example: "Friday, June 13". */
    fun weekdayMonthDay(date: LocalDate): String

    /** Month and year, for a calendar heading. English example: "June 2026". */
    fun monthYear(date: LocalDate): String

    /**
     * A weekday's short label for a calendar column header. English example: "M".
     * Length and case follow the platform's own short/narrow convention.
     */
    fun weekdayInitial(day: DayOfWeek): String

    /**
     * Time of day, following the device's 12- vs 24-hour preference.
     * English examples: "8:00 AM" (12-hour) or "08:00" (24-hour).
     */
    fun timeOfDay(time: LocalTime): String

    /** The locale's first day of the week, for laying out a calendar grid. */
    fun firstDayOfWeek(): DayOfWeek

    /** An integer with the locale's grouping separator. English example: "1,095". */
    fun integer(value: Int): String
}
```

### The Android implementation

`platform/AndroidDateTextFormatter.kt`, taking `@ApplicationContext Context` (needed only by
`is24HourFormat`, which `timeOfDay` consults). Each method body is **the current expression,
moved**. Do not consolidate, do not "simplify", do not change a `FormatStyle`.

### The widget

`widget/WidgetContent.kt` keeps `Locale.US` behaviour. Either give the widget its own
`Locale.US`-pinned instance, or add nothing to the interface and leave the widget's two patterns
where they are — **your call, but state which you chose and why in the PR description.** The
interface must not grow a `Locale` parameter to accommodate it.

### The test fake

`app/src/test/.../platform/FakeDateTextFormatter.kt`, producing **stable, obviously-fake, English
output** — e.g. `fullDate` → `"FULL(2026-06-13)"`. Deliberately not realistic: a fake that looks
like real output invites tests to assert on formatting that the seam does not guarantee.

Where an existing test pins a **real** literal (`DayReadingsScreenTest` pins `"Today – June 10"`
and `"Friday, June 13"`), keep that test on the **real Android implementation** for now. It is a
correct Android test. `p2-*` decides which of those becomes a platform test.

### Wiring

Bind it in `di/AppModule.kt` as a `@Singleton`. Composables receive it — via the existing
ViewModel/state plumbing, or as a parameter with a default for previews. **Do not introduce a
`CompositionLocal` for it**; that would be a new architectural idiom in a task whose job is to
change nothing.

---

## Acceptance criteria

1. **Zero** `java.time.format.*`, `java.text.NumberFormat`, `java.time.temporal.WeekFields`,
   `java.util.Locale` or `android.text.format.DateFormat` imports remain in `ui/**` or
   `bible/ui/**`. Prove it: `grep -rn "java.time.format\|java.text.NumberFormat\|WeekFields\|java.util.Locale\|text.format.DateFormat" app/src/main/kotlin/com/jpillion/dailyreadingplanner/ui app/src/main/kotlin/com/jpillion/dailyreadingplanner/bible/ui` returns nothing.
2. `DateTextFormatter` is **byte-identical to the source in this brief.** No added methods, no
   changed signatures, no widened parameters.
3. **All existing literal-string date assertions pass unchanged**, notably
   `DayReadingsScreenTest`'s `"Today – June 10"` and `"Friday, June 13"`. **A change to any of
   them is a bug in this task, not a stale test.**
4. The D-S16-1 title rule — including *year-appended-only-when-it-differs* — is still tested in
   `shared`-destined code (i.e. still in the composable's test), not inside the formatter.
5. **≥1 killed mutation, each by its intended test:** replace `mediumDate`'s body with
   `fullDate`'s and confirm the settings/tracking-start pins go red; restore byte-identically.
6. Every test file touched is converted Truth → assertk (R4).
7. Full pipeline green:
   `./gradlew spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug koverVerifyAppDebug`
8. **The six data gates are untouched, counts unchanged: 11 / 10 / 8 / 6 / 18 / 5.**
9. `AccessibilityGateTest` green — the picker grid's spoken dates flow through `fullDate`.
10. **R5 R8 release-build device smoke** run and reported, exercising: the day-screen title, the
    picker heading and a day cell's spoken date, the settings reminder-time row, the tracking-start
    row, the first-run prompt, the stats "n of 1,095", and **the widget's date header at two sizes**.

---

## Boundaries / write set

**Yours:**
- `app/src/main/kotlin/.../platform/DateTextFormatter.kt` **(new)**
- `app/src/main/kotlin/.../platform/AndroidDateTextFormatter.kt` **(new)**
- `app/src/main/kotlin/.../ui/datepicker/DayDatePickerDialog.kt`
- `app/src/main/kotlin/.../ui/settings/SettingsScreen.kt`
- `app/src/main/kotlin/.../ui/day/DayReadingsScreen.kt`
- `app/src/main/kotlin/.../ui/day/TrackingStartPromptDialog.kt`
- `app/src/main/kotlin/.../ui/stats/StatsContent.kt`
- `app/src/main/kotlin/.../widget/WidgetContent.kt`
- `app/src/main/kotlin/.../di/AppModule.kt`
- The test files covering the above, plus `app/src/test/.../platform/FakeDateTextFormatter.kt`
  **(new)**

**Not yours:**
- Anything under `domain/`, `core/`, `data/`, `bible/` — **`p1-02` and `p1-03` own those.**
- `app/build.gradle.kts`, `gradle/libs.versions.toml` — **Build & Release** (invariant 6).
- `docs/parity-matrix.md` — **Verification**. Report divergences to them; do not write the file.
- Any `docs/adr/**` — **Staff**.

---

## Escalation triggers

- **You want a tenth method on `DateTextFormatter`.** Escalate to **Staff**. Nine covers every call
  site listed above; a tenth means either a call site was missed (say which) or a formatting
  decision is being invented.
- **An existing literal-string test needs its expectation changed.** Escalate to **Staff**,
  blocking. That means the extraction changed output, which it must not.
- **You need a `Locale` parameter on the interface.** Escalate. The interface is deliberately
  locale-free — the *implementation* owns the locale. A `Locale` in the signature would be a
  `java.util` type in a `shared/platform`-destined file (invariant 1).
- **A composable needs the formatter but has no plumbing to receive it.** Escalate to **Staff**
  rather than reaching for a `CompositionLocal`.
