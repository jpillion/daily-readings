# ADR-0009 — `java.time` → `kotlinx-datetime`, `Clock`, and `YearMonth`

**Status:** Draft, pending owner sign-off · **Date:** 2026-08-08 · **Author:** Staff / Port Architect

## Context

Date anchoring **is** the product. "January 1 is always Genesis 1–2 / Psalm 1–2 / Matthew 1–2"
is the app's founding constraint, progress is keyed by full date, streaks walk dates, the picker
grid is built from dates, and Feb 29 is a first-class no-readings case that appears in every
layer.

`java.time` is therefore everywhere: **36 of 162 main-source files**, plus most of the 115 test
files.

| Type | Files | Notes |
|---|---|---|
| `LocalDate` | 33 | the spine |
| `Clock` | 13 | injected everywhere for testability |
| `LocalTime` | 6 | reminder time |
| `YearMonth` | 4 | month completion + the picker grid |
| `ZonedDateTime` | 2 | alarm math |
| `Instant`, `ZoneOffset`, `DayOfWeek` | 3 | |
| `DateTimeFormatter` + `FormatStyle` + `TextStyle` | 5 | **user-visible strings** |
| `WeekFields` | 1 | first day of week in the picker |

`java.time` does not exist on Kotlin/Native. kotlinx-datetime is the multiplatform answer, but it
is **not** a drop-in: it deliberately has a smaller surface, and two pieces are genuinely missing.

## Decision

### 1. Adopt kotlinx-datetime for all date/time **values and arithmetic**.

`LocalDate`, `LocalTime`, `DayOfWeek`, `TimeZone`, `Instant` map directly. Epoch-day conversion —
which `ProgressRepository` depends on for its storage key — exists (`LocalDate.toEpochDays()` /
`LocalDate.fromEpochDays()`). **Verify the epoch-day semantics are identical to
`java.time.LocalDate.toEpochDay()`** (they should both be days since 1970-01-01), because every
stored progress row is keyed on that number. Pin it with a test over known dates including
pre-1970 and leap days.

### 2. Replace injected `java.time.Clock` with **one `DateProvider` seam**, not with
`kotlin.time.Clock` + `TimeZone` propagated as two parameters.

```kotlin
// shared/platform
/**
 * The app's notion of "now". Everything date-anchored resolves through this — never through a
 * global clock — so tests can pin a date and the app has exactly one place that decides which
 * calendar day the user is in.
 *
 * [today] is the user's *local* calendar date; two users in different zones legitimately see
 * different readings at the same instant, and that is the intended behaviour of a date-anchored
 * plan.
 */
interface DateProvider {
    fun today(): LocalDate
    fun now(): Instant
    val timeZone: TimeZone
}
```

**Why this and not a straight `Clock` swap.** `java.time.Clock` bundles an instant source *and* a
zone, so `LocalDate.now(clock)` reads both. `kotlin.time.Clock` gives an `Instant` only, so every
one of those 13 call sites becomes `clock.now().toLocalDateTime(zone).date` and needs a zone from
somewhere. Threading two parameters through 13 use cases doubles the plumbing and puts the
"which zone?" question in 13 places instead of one. One seam, one fake, one answer.

This is a **deliberate small deviation from a faithful port**, justified because the alternative
is worse in the target and because it is a pure mechanical substitution with no behaviour change.

### 3. `YearMonth`: use `kotlinx.datetime.YearMonth` if it is adequate; otherwise write our own.

`YearMonth` was **added to kotlinx-datetime in 0.7.0**. ⟦VERIFY⟧ **V3** (Core/Data): confirm it
carries what `GetMonthCompletionUseCase` and `DayDatePickerDialog`'s custom calendar grid need —
`atDay(n)`, first/last day, `lengthOfMonth`, month arithmetic, and comparison.

**If it is thin, do not fight it — write a ~40-line `shared/domain/PlanMonth` value class.** A
month-of-year with day arithmetic is genuinely trivial, and a domain type we own is preferable
to leaking a partial library type through `GetMonthCompletionUseCase`'s public signature. The
picker's month `HorizontalPager` (`monthForPage`, ±3,000-month window, S21) already does its own
offset arithmetic and would be unaffected either way.

### 4. **Localized formatting does NOT move to kotlinx-datetime. It becomes a platform seam.**

kotlinx-datetime's formatting is pattern-based and **not locale-aware**. There is no equivalent
for `DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)` or `ofLocalizedTime(SHORT)`, and no
equivalent for `WeekFields.of(locale).firstDayOfWeek` or `DateFormat.is24HourFormat`.

```kotlin
// shared/platform
/**
 * User-facing date and time text, rendered in the device's locale and calendar conventions.
 *
 * Implementations delegate to the platform's own localized formatting, so the exact wording,
 * ordering and separators are the platform's — they are NOT guaranteed to match across
 * platforms, and callers must never assert on the returned string.
 */
interface DateTextFormatter {
    /** e.g. "Friday, June 13, 2026" — the full spoken-quality form used for accessibility. */
    fun fullDate(date: LocalDate): String
    /** e.g. "Jun 13, 2026" — the compact form used in settings rows. */
    fun mediumDate(date: LocalDate): String
    /** e.g. "June 13" — month and day only, no year. */
    fun monthDay(date: LocalDate): String
    /** e.g. "Friday, June 13" — weekday, month and day, no year. */
    fun weekdayMonthDay(date: LocalDate): String
    /** e.g. "June 2026" — the picker's month heading. */
    fun monthYear(date: LocalDate): String
    /** e.g. "8:00 AM" or "08:00", following the device's 12/24-hour preference. */
    fun timeOfDay(time: LocalTime): String
    /** The locale's first day of the week, for laying out a calendar grid. */
    fun firstDayOfWeek(): DayOfWeek
    /** e.g. "1,095" — an integer with the locale's grouping separator. */
    fun integer(value: Int): String
}
```

Android's actual wraps `java.time.format` + `java.text.NumberFormat`. iOS's wraps `NSDateFormatter`
+ `NSNumberFormatter`.

## Alternatives rejected

**Keep `java.time` via a Kotlin/Native `java.time` shim.** No such thing exists in a form worth
depending on. Rejected.

**Use `kotlinx-datetime` formatting with explicit patterns everywhere** (`"EEEE, MMMM d"` etc.).
Tempting — it would keep formatting in shared code and produce byte-identical output on both
platforms, which would let the existing literal string pins survive. **Rejected**, because the app
would then render English month names to a French user, and ignore the user's 12/24-hour
preference on the reminder time row. The app is not localized today, but hard-coding English is a
different and worse thing than not shipping translations. Two of the current pattern uses
(`widget/WidgetContent.kt:95-96`, already `Locale.US`) are effectively hard-coded already —
those can keep patterns.

**Write our own full date/time library.** Absurd for this scope. Rejected.

## Consequences accepted

- **36 main files + most of the test suite change.** Mechanical, high-volume, and best done as
  one atomic task rather than trickled through feature work. Sequence it as the *first*
  substantial task of Phase B, before anything depends on it.
- **`java.time.Clock` fakes in ~40 test files change shape.** Provide one
  `FakeDateProvider(today: LocalDate)` in a shared test-fixtures source set and convert
  mechanically. Cheaper than it sounds because most tests just want "today is this date".
- **Formatted date strings will differ between Android and iOS.** Every test that pins one
  literally must move. Concretely at least: `DayReadingsScreenTest` pins `"Today – June 10"`
  and `"Friday, June 13"`; `SettingsScreenTest` and `DayDatePickerDialogTest` pin others. These
  become platform tests, or tests against a fake `DateTextFormatter` with deterministic output.
  **Prefer the fake** — it keeps the *composition* logic (the en dash, the year-only-when-it-differs
  rule from D-S16-1) shared and tested, while the localized fragment comes from the seam.
- The `DayReadingsScreen` title rule ("Today – June 10", year appended only when it differs from
  today's) is real shared logic sitting on top of a localized fragment. Split it accordingly: the
  rule stays in `shared/ui`, the fragment comes from `DateTextFormatter`.
- **Timezone semantics get an explicit home for the first time.** Today `LocalDate.now(clock)`
  quietly uses the system default zone. `DateProvider.timeZone` makes that explicit, which is
  good, and surfaces a question nobody has had to answer: what should happen when a user flies
  across the date line mid-day? Current Android behaviour is "the readings change when the local
  date changes". Keep that; document it in the parity matrix as intentional.

## Revisit when

- V3 resolves (`YearMonth` adequacy).
- The app is localized for real. At that point `DateTextFormatter` is already the right seam, and
  ADR-0013's resource decision becomes the constraint instead.
- Any bug report involves dates near midnight or a timezone change — `DateProvider` is where to
  look, and it should be the only place.
