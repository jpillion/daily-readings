package com.jpillion.dailyreadingplanner.core.date

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.time.LocalDate
import java.time.Year

/** Sprint 3 gate (execution plan §5.2): the Feb-29 no-readings rule, decision D1. */
class ScheduleDateResolverTest {
    private val resolver = ScheduleDateResolver()

    @Test
    fun `feb 29 in leap years resolves to the no-readings state`() {
        for (year in listOf(2024, 2028, 2032, 2000)) {
            assertWithMessage("Feb 29 $year")
                .that(resolver.resolve(LocalDate.of(year, 2, 29)))
                .isEqualTo(ResolvedDate.NoScheduledReadings)
        }
    }

    @Test
    fun `dates around feb 29 resolve normally`() {
        assertThat(resolver.resolve(LocalDate.of(2024, 2, 28)))
            .isEqualTo(ResolvedDate.Scheduled(ReadingDate(2, 28)))
        assertThat(resolver.resolve(LocalDate.of(2024, 3, 1)))
            .isEqualTo(ResolvedDate.Scheduled(ReadingDate(3, 1)))
        assertThat(resolver.resolve(LocalDate.of(2026, 1, 1)))
            .isEqualTo(ResolvedDate.Scheduled(ReadingDate(1, 1)))
        assertThat(resolver.resolve(LocalDate.of(2026, 12, 31)))
            .isEqualTo(ResolvedDate.Scheduled(ReadingDate(12, 31)))
    }

    @Test
    fun `every day of a non-leap year is scheduled`() {
        assertThat(Year.of(2026).isLeap).isFalse()
        var date = LocalDate.of(2026, 1, 1)
        var scheduled = 0
        while (date.year == 2026) {
            assertWithMessage("$date").that(resolver.resolve(date)).isInstanceOf(ResolvedDate.Scheduled::class.java)
            scheduled++
            date = date.plusDays(1)
        }
        assertThat(scheduled).isEqualTo(365)
    }

    @Test
    fun `a leap year has exactly one no-readings day`() {
        assertThat(Year.of(2028).isLeap).isTrue()
        var date = LocalDate.of(2028, 1, 1)
        val noReadings = mutableListOf<LocalDate>()
        while (date.year == 2028) {
            if (resolver.resolve(date) == ResolvedDate.NoScheduledReadings) noReadings += date
            date = date.plusDays(1)
        }
        assertThat(noReadings).containsExactly(LocalDate.of(2028, 2, 29))
    }

    @Test
    fun `reading date cannot represent feb 29 or invalid dates`() {
        assertThat(runCatching { ReadingDate(2, 29) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { ReadingDate(4, 31) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { ReadingDate(13, 1) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { ReadingDate(0, 1) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
