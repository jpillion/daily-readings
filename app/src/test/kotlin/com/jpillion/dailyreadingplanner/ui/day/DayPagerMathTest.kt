package com.jpillion.dailyreadingplanner.ui.day

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.Test

/** Pure pager geometry (D-S5-4): page <-> date mapping and window clamping. */
class DayPagerMathTest {
    private val today = LocalDate(2026, 6, 10)

    @Test
    fun `the center page is today`() {
        assertThat(dateForPage(today, TODAY_PAGE)).isEqualTo(today)
        assertThat(pageForDate(today, today)).isEqualTo(TODAY_PAGE)
    }

    @Test
    fun `adjacent pages are adjacent real calendar days`() {
        assertThat(dateForPage(today, TODAY_PAGE + 1)).isEqualTo(LocalDate(2026, 6, 11))
        assertThat(dateForPage(today, TODAY_PAGE - 1)).isEqualTo(LocalDate(2026, 6, 9))
    }

    @Test
    fun `page mapping crosses the year boundary to the adjacent year`() {
        val nye = LocalDate(2026, 12, 31)
        assertThat(dateForPage(nye, TODAY_PAGE + 1)).isEqualTo(LocalDate(2027, 1, 1))
    }

    @Test
    fun `pageForDate inverts dateForPage across the window`() {
        for (offset in listOf(-DAY_WINDOW, -400, -1, 0, 1, 400, DAY_WINDOW)) {
            val page = TODAY_PAGE + offset
            assertThat(pageForDate(today, dateForPage(today, page))).isEqualTo(page)
        }
    }

    @Test
    fun `dates outside the window clamp to the edges`() {
        assertThat(pageForDate(today, today.plus(DAY_WINDOW + 500L, DateTimeUnit.DAY))).isEqualTo(PAGE_COUNT - 1)
        assertThat(pageForDate(today, today.minus(DAY_WINDOW + 500L, DateTimeUnit.DAY))).isEqualTo(0)
    }
}
