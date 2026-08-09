package com.jpillion.dailyreadingplanner.platform

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

/**
 * The Android [DateTextFormatter] (p1-01). Every body here is a **literal transcription** of the
 * expression that stood at the call site before the extraction — same `FormatStyle`, same pattern
 * string, same locale source, and the same *lifetime* for the one formatter that was cached.
 *
 * Deliberately unconsolidated. Two bodies that look mergeable ([monthDay] vs [weekdayMonthDay])
 * are separate patterns at separate call sites, and merging them would be a behaviour change
 * dressed up as tidying.
 *
 * A class rather than an object because [uses24HourTime] reads a device setting and so needs a
 * `Context`; the other nine members are pure. Composables take a [DateTextFormatter] as a
 * defaulted parameter and get their instance from [rememberDateTextFormatter]; Hilt binds this as
 * the `@Singleton` [DateTextFormatter] in `AppModule` for non-composable consumers.
 */
class AndroidDateTextFormatter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DateTextFormatter {
        override fun fullDate(date: LocalDate): String =
            date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

        override fun mediumDate(date: LocalDate): String =
            date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

        override fun monthDay(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("MMMM d"))

        override fun weekdayMonthDay(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))

        // `YearMonth.format(MonthTitleFormat)` at the old call site. The pattern reads only
        // MONTH_OF_YEAR and YEAR, which a LocalDate supplies identically, so the text is unchanged;
        // the picker's existing "June 2026" / "December 2026" pins are the check.
        override fun monthYear(date: LocalDate): String = date.format(MONTH_TITLE_FORMAT)

        override fun weekdayInitial(day: DayOfWeek): String = day.getDisplayName(TextStyle.NARROW, Locale.getDefault())

        override fun timeOfDay(time: LocalTime): String =
            time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

        override fun firstDayOfWeek(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

        override fun integer(value: Int): String = NumberFormat.getIntegerInstance().format(value)

        /**
         * Transcribed from `SettingsScreen`'s `DateFormat.is24HourFormat(LocalContext.current)`,
         * **including its lifetime**: a getter, so it is re-read on every access exactly as the old
         * per-composition call was. A stored `val` would freeze the setting at construction.
         *
         * Note this is the *device setting*, whereas [timeOfDay] is `ofLocalizedTime(SHORT)` and so
         * follows the *locale*. The two can disagree, and did before this seam existed; that is
         * shipped behaviour and is deliberately not corrected here.
         */
        override val uses24HourTime: Boolean
            get() = DateFormat.is24HourFormat(context)

        private companion object {
            /**
             * Transcribed from `DayDatePickerDialog`'s top-level `MonthTitleFormat`, **including its
             * lifetime**: the locale was captured once when the file's class initialized, not per
             * call. It lives on the companion so the class conversion did not quietly make it
             * per-instance — a per-call `Locale.getDefault()` here would be a behaviour change.
             */
            private val MONTH_TITLE_FORMAT = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.getDefault())
        }
    }

/**
 * The [DateTextFormatter] for composable default parameters.
 *
 * `remember`ed on the context so the four screens that take a defaulted formatter do not allocate
 * one per recomposition — they sit on pagers that recompose on every swipe.
 */
@Composable
fun rememberDateTextFormatter(): DateTextFormatter {
    val context = LocalContext.current
    return remember(context) { AndroidDateTextFormatter(context) }
}
