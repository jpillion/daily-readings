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
 * S10, D-S10-1 (spec §3 option B): the one-time tracking-start default. Fresh install =>
 * defaulted to "today"; existing history => left null; a deliberate clear is never
 * re-defaulted (the marker, not the value, gates the initializer).
 */
class InitializeTrackingStartUseCaseTest {
    // Fixed "today": 2026-06-10.
    private val clock = Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC)
    private val settings = FakeSettingsRepository()
    private val progress = FakeProgressRepository()
    private val useCase = InitializeTrackingStartUseCase(settings, progress, clock)

    @Test
    fun `fresh install - no marker, no marks - defaults the start date to today`() =
        runTest {
            useCase()
            assertThat(settings.storedTrackingStartDate.value).isEqualTo(LocalDate.of(2026, 6, 10))
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
    fun `idempotent - a second launch never rewrites the value`() =
        runTest {
            useCase()
            settings.setTrackingStartDate(LocalDate.of(2026, 3, 1)) // user picks their own
            useCase()
            assertThat(settings.storedTrackingStartDate.value).isEqualTo(LocalDate.of(2026, 3, 1))
        }
}
