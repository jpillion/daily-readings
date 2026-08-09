package com.jpillion.dailyreadingplanner.core.date

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Test

/** Sprint 3 gate (execution plan §5.2): the Feb-29 no-readings rule, decision D1. */
class ScheduleDateResolverTest {
    private val resolver = ScheduleDateResolver()

    @Test
    fun `feb 29 in leap years resolves to the no-readings state`() {
        for (year in listOf(2024, 2028, 2032, 2000)) {
            assertThat(resolver.resolve(LocalDate(year, 2, 29)), name = "Feb 29 $year")
                .isEqualTo(ResolvedDate.NoScheduledReadings)
        }
    }

    @Test
    fun `dates around feb 29 resolve normally`() {
        assertThat(resolver.resolve(LocalDate(2024, 2, 28)))
            .isEqualTo(ResolvedDate.Scheduled(ReadingDate(2, 28)))
        assertThat(resolver.resolve(LocalDate(2024, 3, 1)))
            .isEqualTo(ResolvedDate.Scheduled(ReadingDate(3, 1)))
        assertThat(resolver.resolve(LocalDate(2026, 1, 1)))
            .isEqualTo(ResolvedDate.Scheduled(ReadingDate(1, 1)))
        assertThat(resolver.resolve(LocalDate(2026, 12, 31)))
            .isEqualTo(ResolvedDate.Scheduled(ReadingDate(12, 31)))
    }

    @Test
    fun `every day of a non-leap year is scheduled`() {
        assertThat(LocalDate(2026, 12, 31).dayOfYear).isEqualTo(365) // not a leap year
        var date = LocalDate(2026, 1, 1)
        var scheduled = 0
        while (date.year == 2026) {
            assertThat(resolver.resolve(date), name = "$date").isInstanceOf<ResolvedDate.Scheduled>()
            scheduled++
            date = date.plus(1, DateTimeUnit.DAY)
        }
        assertThat(scheduled).isEqualTo(365)
    }

    @Test
    fun `a leap year has exactly one no-readings day`() {
        assertThat(LocalDate(2028, 12, 31).dayOfYear).isEqualTo(366) // a leap year
        var date = LocalDate(2028, 1, 1)
        val noReadings = mutableListOf<LocalDate>()
        while (date.year == 2028) {
            if (resolver.resolve(date) == ResolvedDate.NoScheduledReadings) noReadings += date
            date = date.plus(1, DateTimeUnit.DAY)
        }
        assertThat(noReadings).containsExactlyInAnyOrder(LocalDate(2028, 2, 29))
    }

    @Test
    fun `reading date cannot represent feb 29 or invalid dates`() {
        assertFailure { ReadingDate(2, 29) }.isInstanceOf<IllegalArgumentException>()
        assertFailure { ReadingDate(4, 31) }.isInstanceOf<IllegalArgumentException>()
        assertFailure { ReadingDate(13, 1) }.isInstanceOf<IllegalArgumentException>()
        assertFailure { ReadingDate(0, 1) }.isInstanceOf<IllegalArgumentException>()
    }
}
