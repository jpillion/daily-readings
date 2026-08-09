package com.jpillion.dailyreadingplanner.platform

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isIn
import assertk.assertions.isLessThanOrEqualTo
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinTimeZone
import org.junit.Test
import kotlin.math.abs

/**
 * p1-02 — the production [DateProvider], measured against the implementation it replaced.
 *
 * **This test exists because its absence was measured.** Mutating
 * `SystemDateProvider.today()` to return *tomorrow* — a one-day shift in the single function that
 * decides which calendar day every user is in, and therefore which readings they see and which
 * `dateEpochDay` their marks are written under — left the entire suite green. Every other test
 * injects [FakeDateProvider], so nothing exercised the real one. The retired
 * `AppModule.provideClock()` had exactly the same hole, which is why the gap survived the port
 * rather than being created by it.
 *
 * The witness is deliberately **`java.time`**: `Clock.systemDefaultZone()` + `LocalDate.now(clock)`
 * is precisely what shipped in 1.8.1, so agreement here is a direct old-versus-new equivalence
 * check rather than a restatement of the new code. It is JVM-only and does not move to
 * `commonTest`; the iOS actual gets its own.
 */
class SystemDateProviderTest {
    private val provider = SystemDateProvider()

    /**
     * Sampling the witness on both sides of the call makes the midnight race explicit instead of
     * flaky: a run that straddles midnight legitimately sees two adjacent dates, and either is
     * correct. A one-day offset is outside that set in both directions.
     */
    @Test
    fun `today matches what the retired java-time clock would have returned`() {
        val before =
            java.time.LocalDate
                .now()
                .toKotlinLocalDate()
        val actual = provider.today()
        val after =
            java.time.LocalDate
                .now()
                .toKotlinLocalDate()

        assertThat(actual, name = "today() vs java.time.LocalDate.now()").isIn(before, after)
    }

    @Test
    fun `today is resolved in the provider's own time zone`() {
        val zoneClock = java.time.Clock.system(java.time.ZoneId.of(provider.timeZone.id))
        val before =
            java.time.LocalDate
                .now(zoneClock)
                .toKotlinLocalDate()
        val viaSeam = provider.today()
        val after =
            java.time.LocalDate
                .now(zoneClock)
                .toKotlinLocalDate()

        assertThat(viaSeam, name = "today() vs LocalDate.now(clock in timeZone)").isIn(before, after)
    }

    @Test
    fun `the time zone is the system default - what Clock systemDefaultZone used`() {
        assertThat(provider.timeZone).isEqualTo(
            java.time.ZoneId
                .systemDefault()
                .toKotlinTimeZone(),
        )
    }

    /** [DateProvider.now] replaces `clock.millis()`, the source of every mark's `readAtEpochMillis`. */
    @Test
    fun `now is the current instant in epoch millis`() {
        val before = System.currentTimeMillis()
        val actual = provider.now().toEpochMilliseconds()
        val after = System.currentTimeMillis()

        assertThat(before - TOLERANCE_MILLIS, name = "now() is not in the past").isLessThanOrEqualTo(actual)
        assertThat(actual, name = "now() is not in the future").isLessThanOrEqualTo(after + TOLERANCE_MILLIS)
    }

    /** `now()` and `today()` must describe the same moment, not two independently-read clocks. */
    @Test
    fun `now and today agree on the calendar day`() {
        val instantDay =
            java.time.Instant
                .ofEpochMilli(provider.now().toEpochMilliseconds())
                .atZone(java.time.ZoneId.of(provider.timeZone.id))
                .toLocalDate()
                .toKotlinLocalDate()
        val today = provider.today()
        assertThat(abs(today.toEpochDays() - instantDay.toEpochDays()), name = "days apart")
            .isLessThanOrEqualTo(1L)
    }

    private fun java.time.LocalDate.toKotlinLocalDate(): LocalDate = LocalDate(year, monthValue, dayOfMonth)

    private companion object {
        /** Generous enough that a slow CI worker never trips it, tight enough to catch a real bug. */
        const val TOLERANCE_MILLIS = 5_000L
    }
}
