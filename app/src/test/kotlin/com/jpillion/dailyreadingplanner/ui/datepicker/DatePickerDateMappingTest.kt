package com.jpillion.dailyreadingplanner.ui.datepicker

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/** The UTC-midnight epoch-millis bridge the M3 DatePicker state speaks (D-S5-2). */
class DatePickerDateMappingTest {
    @Test
    fun `round-trips an ordinary date`() {
        val date = LocalDate.of(2026, 6, 10)
        assertThat(utcMillisToLocalDate(localDateToUtcMillis(date))).isEqualTo(date)
    }

    @Test
    fun `round-trips Feb 29 in a leap year`() {
        val date = LocalDate.of(2028, 2, 29)
        assertThat(utcMillisToLocalDate(localDateToUtcMillis(date))).isEqualTo(date)
    }

    @Test
    fun `epoch zero is Jan 1 1970`() {
        assertThat(utcMillisToLocalDate(0L)).isEqualTo(LocalDate.of(1970, 1, 1))
    }

    @Test
    fun `withYear on Feb 29 falls back to Feb 28 in a common year`() {
        // Documents the picker's initial-date anchoring behavior when the pager sits on a
        // leap day but the picker is pinned to a common year.
        assertThat(LocalDate.of(2028, 2, 29).withYear(2026)).isEqualTo(LocalDate.of(2026, 2, 28))
    }
}
