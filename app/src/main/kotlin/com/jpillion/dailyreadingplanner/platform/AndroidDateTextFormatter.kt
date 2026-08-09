package com.jpillion.dailyreadingplanner.platform

import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The Android [DateTextFormatter] (p1-01). Every body here is a **literal transcription** of the
 * expression that stood at the call site before the extraction — same `FormatStyle`, same pattern
 * string, same locale source, and the same *lifetime* for the one formatter that was cached.
 *
 * Deliberately unconsolidated. Two bodies that look mergeable ([monthDay] vs [weekdayMonthDay])
 * are separate patterns at separate call sites, and merging them would be a behaviour change
 * dressed up as tidying.
 *
 * An `object` rather than an injected class: it holds no state beyond one cached formatter, needs
 * no `Context`, and composables take it as a defaulted parameter, which needs a value that costs
 * nothing to name. Hilt still binds it as the `@Singleton` `DateTextFormatter` in `AppModule`, so
 * production code depends on the interface.
 */
object AndroidDateTextFormatter : DateTextFormatter {
    /**
     * Transcribed from `DayDatePickerDialog`'s top-level `MonthTitleFormat`, **including its
     * lifetime**: the locale was captured once when the file's class initialized, not per call.
     * A per-call `Locale.getDefault()` here would be a (subtle) behaviour change.
     */
    private val monthTitleFormat = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.getDefault())

    override fun fullDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

    override fun mediumDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

    override fun monthDay(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("MMMM d"))

    override fun weekdayMonthDay(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))

    // `YearMonth.format(MonthTitleFormat)` at the old call site. The pattern reads only
    // MONTH_OF_YEAR and YEAR, which a LocalDate supplies identically, so the text is unchanged;
    // the picker's existing "June 2026" / "December 2026" pins are the check.
    override fun monthYear(date: LocalDate): String = date.format(monthTitleFormat)

    override fun weekdayInitial(day: DayOfWeek): String = day.getDisplayName(TextStyle.NARROW, Locale.getDefault())

    override fun timeOfDay(time: LocalTime): String = time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    override fun firstDayOfWeek(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    override fun integer(value: Int): String = NumberFormat.getIntegerInstance().format(value)
}
