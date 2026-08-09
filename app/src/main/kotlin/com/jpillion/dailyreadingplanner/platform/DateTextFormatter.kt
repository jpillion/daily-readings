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

    /** Whether the device is set to display time in 24-hour form, for a control that must match it. */
    val uses24HourTime: Boolean
}
