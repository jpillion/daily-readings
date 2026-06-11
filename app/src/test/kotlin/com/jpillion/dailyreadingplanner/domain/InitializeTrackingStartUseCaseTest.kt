package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * S10, D-S10-1 (spec §3 option B; default value superseded by S14 D-S14-1): the one-time
 * tracking-start default. Fresh install => defaulted to **Jan 1 of the current year**
 * (owner decision, S14; was "today" in S10–S13); existing history => left null; a
 * deliberate clear is never re-defaulted (the marker, not the value, gates the
 * initializer). Already-initialized devices keep their stored value untouched.
 */
class InitializeTrackingStartUseCaseTest {
    // Fixed "today": 2026-06-10.
    private val clock = Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC)
    private val settings = FakeSettingsRepository()
    private val progress = FakeProgressRepository()
    private val useCase = InitializeTrackingStartUseCase(settings, progress, clock)

    @Test
    fun `fresh install - no marker, no marks - defaults the start date to Jan 1 of the current year`() =
        runTest {
            useCase()
            assertThat(settings.storedTrackingStartDate.value).isEqualTo(LocalDate.of(2026, 1, 1))
            assertThat(settings.storedTrackingStartInitialized.value).isTrue()
        }

    @Test
    fun `upgrader with existing marks is left unset - history is not retroactively neutralized`() =
        runTest {
            progress.setRead(LocalDate.of(2026, 1, 5), Stream.NEW_TESTAMENT, isRead = true)
            useCase()
            assertThat(settings.storedTrackingStartDate.value).isNull()
            assertThat(settings.storedTrackingStartInitialized.value).isTrue() // runs only once
        }

    @Test
    fun `a user who cleared the date is not re-defaulted on the next launch`() =
        runTest {
            useCase() // first run defaults it
            settings.setTrackingStartDate(null) // user deliberately clears
            useCase() // next launch
            assertThat(settings.storedTrackingStartDate.value).isNull()
        }

    @Test
    fun `already-initialized device keeps its stored install-date value - D-S14-1, no migration`() =
        runTest {
            // Simulates a device the S10 initializer already stamped with its install date:
            // the marker is set and the value is a mid-year date. S14 must not rewrite it.
            settings.markTrackingStartInitialized()
            settings.setTrackingStartDate(LocalDate.of(2026, 4, 17))
            useCase()
            assertThat(settings.storedTrackingStartDate.value).isEqualTo(LocalDate.of(2026, 4, 17))
        }

    @Test
    fun `idempotent - a second launch never rewrites the value`() =
        runTest {
            useCase()
            settings.setTrackingStartDate(LocalDate.of(2026, 3, 1)) // user picks their own
            useCase()
            assertThat(settings.storedTrackingStartDate.value).isEqualTo(LocalDate.of(2026, 3, 1))
        }
}
