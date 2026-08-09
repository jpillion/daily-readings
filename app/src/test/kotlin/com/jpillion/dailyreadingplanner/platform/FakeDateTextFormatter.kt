package com.jpillion.dailyreadingplanner.platform

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * A [DateTextFormatter] whose output is stable, English, and **obviously not real** — every
 * method stamps its own name onto the ISO value it was given.
 *
 * The ugliness is the point. [DateTextFormatter]'s contract says callers must never assert on
 * the returned string, so a fake that looked like `"Jun 13, 2026"` would quietly invite tests to
 * pin formatting the seam does not guarantee. `"MEDIUM(2026-06-13)"` cannot be mistaken for one.
 *
 * It also makes the wiring testable in the one direction that matters: if a composable stops
 * routing through the seam and goes back to formatting inline, its rendered text silently stops
 * containing these markers — which is exactly what `DateTextFormatterSeamTest` pins.
 */
class FakeDateTextFormatter(
    private val firstDay: DayOfWeek = DayOfWeek.SUNDAY,
    override val uses24HourTime: Boolean = false,
) : DateTextFormatter {
    override fun fullDate(date: LocalDate): String = "FULL($date)"

    override fun mediumDate(date: LocalDate): String = "MEDIUM($date)"

    override fun monthDay(date: LocalDate): String = "MONTHDAY($date)"

    override fun weekdayMonthDay(date: LocalDate): String = "WEEKDAYMONTHDAY($date)"

    override fun monthYear(date: LocalDate): String = "MONTHYEAR(${date.year}-${date.monthValue})"

    override fun weekdayInitial(day: DayOfWeek): String = "WD(${day.value})"

    override fun timeOfDay(time: LocalTime): String = "TIME($time)"

    override fun firstDayOfWeek(): DayOfWeek = firstDay

    override fun integer(value: Int): String = "INT($value)"
}
