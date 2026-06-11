package com.jpillion.dailyreadingplanner.ui.stats

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.core.date.ScheduleDateResolver
import com.jpillion.dailyreadingplanner.domain.DayCompletionClassifier
import com.jpillion.dailyreadingplanner.domain.FakeProgressRepository
import com.jpillion.dailyreadingplanner.domain.GetReadingStatsUseCase
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import com.jpillion.dailyreadingplanner.testing.MainDispatcherRule
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** S11: the Stats screen state is nothing but the live use-case derivation (FR-15/FR-17). */
class StatsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC)
    private val progress = FakeProgressRepository()

    private fun viewModel() =
        StatsViewModel(
            GetReadingStatsUseCase(
                DayCompletionClassifier(ScheduleDateResolver()),
                progress,
                FakeSettingsRepository(),
                clock,
            ),
        )

    @Test
    fun `starts null then exposes the derived stats`() =
        runTest {
            progress.setWholeDay(LocalDate.of(2026, 6, 9), isRead = true)
            progress.setWholeDay(LocalDate.of(2026, 6, 10), isRead = true)
            val viewModel = viewModel()
            assertThat(viewModel.stats.value).isNull()
            val stats = viewModel.stats.filterNotNull().first()
            assertThat(stats.currentStreakDays).isEqualTo(2)
            assertThat(stats.yearReadCount).isEqualTo(6)
        }

    @Test
    fun `reflects marks made while subscribed`() =
        runTest {
            val viewModel = viewModel()
            assertThat(
                viewModel.stats
                    .filterNotNull()
                    .first()
                    .currentStreakDays,
            ).isEqualTo(0)
            progress.setWholeDay(LocalDate.of(2026, 6, 10), isRead = true)
            val updated = viewModel.stats.filterNotNull().first { it.currentStreakDays > 0 }
            assertThat(updated.currentStreakDays).isEqualTo(1)
        }
}
