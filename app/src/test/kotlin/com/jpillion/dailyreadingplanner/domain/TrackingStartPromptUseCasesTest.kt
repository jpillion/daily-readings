package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

/**
 * S19, D-S19-1 (supersedes the auto-write half of D-S14-1): the first-run tracking-start
 * PROMPT gate. Fresh install => prompt, with NOTHING persisted until the answer; existing
 * marks => no prompt, marker set, date left null (pre-S10 behavior preserved); already
 * initialized (incl. a deliberate clear) => never prompted or re-defaulted again.
 */
class TrackingStartPromptUseCasesTest {
    private val settings = FakeSettingsRepository()
    private val progress = FakeProgressRepository()
    private val resolve = ResolveTrackingStartPromptUseCase(settings, progress)
    private val complete = CompleteTrackingStartPromptUseCase(settings)

    @Test
    fun `fresh install - no marker, no marks - prompts and persists nothing yet`() =
        runTest {
            assertThat(resolve()).isTrue()
            // Process death before an answer must re-ask: nothing may be written here.
            assertThat(settings.storedTrackingStartDate.value).isNull()
            assertThat(settings.storedTrackingStartInitialized.value).isFalse()
            // Unanswered => still prompts on the next launch.
            assertThat(resolve()).isTrue()
        }

    @Test
    fun `upgrader with existing marks is never prompted - history is not retroactively neutralized`() =
        runTest {
            progress.setRead(LocalDate.of(2026, 1, 5), Stream.NEW_TESTAMENT, isRead = true)
            assertThat(resolve()).isFalse()
            assertThat(settings.storedTrackingStartDate.value).isNull()
            assertThat(settings.storedTrackingStartInitialized.value).isTrue() // runs only once
        }

    @Test
    fun `already-initialized device is never prompted and keeps its stored value`() =
        runTest {
            settings.markTrackingStartInitialized()
            settings.setTrackingStartDate(LocalDate.of(2026, 4, 17))
            assertThat(resolve()).isFalse()
            assertThat(settings.storedTrackingStartDate.value).isEqualTo(LocalDate.of(2026, 4, 17))
        }

    @Test
    fun `completing the prompt persists the choice and the marker - never asked again`() =
        runTest {
            assertThat(resolve()).isTrue()
            complete(LocalDate.of(2026, 6, 11))
            assertThat(settings.storedTrackingStartDate.value).isEqualTo(LocalDate.of(2026, 6, 11))
            assertThat(settings.storedTrackingStartInitialized.value).isTrue()
            assertThat(resolve()).isFalse()
        }

    @Test
    fun `a user who clears the date after answering is not re-prompted or re-defaulted`() =
        runTest {
            complete(LocalDate.of(2026, 1, 1))
            settings.setTrackingStartDate(null) // deliberate clear in Settings
            assertThat(resolve()).isFalse()
            assertThat(settings.storedTrackingStartDate.value).isNull()
        }
}
