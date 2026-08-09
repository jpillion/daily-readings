package com.jpillion.dailyreadingplanner.data.progress

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * S3-T5 / Sprint 3 gate (execution plan §5.2): Room progress store — including YEAR
 * ISOLATION: marks are keyed by the full date, so 1 Jan 2026 ≠ 1 Jan 2027 (FR-6/U7).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgressRepositoryTest {
    private lateinit var database: ProgressDatabase
    private lateinit var repository: ProgressRepositoryImpl
    private val fixedClock = Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    RuntimeEnvironment.getApplication(),
                    ProgressDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        repository = ProgressRepositoryImpl(database.readingProgressDao(), fixedClock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `year isolation - marking 1 jan 2026 does not mark 1 jan 2027`() =
        runTest {
            val jan2026 = LocalDate.of(2026, 1, 1)
            val jan2027 = LocalDate.of(2027, 1, 1)
            repository.setWholeDay(jan2026, listOf(1, 2, 3), isRead = true)
            assertThat(repository.streamsRead(jan2026).first()).containsExactly(1, 2, 3)
            assertThat(repository.streamsRead(jan2027).first()).isEmpty()
            repository.setRead(jan2027, 3, isRead = true)
            assertThat(repository.streamsRead(jan2027).first()).containsExactly(3)
            assertThat(repository.streamsRead(jan2026).first()).containsExactly(1, 2, 3)
        }

    @Test
    fun `marking and unmarking a single reading round-trips`() =
        runTest {
            val date = LocalDate.of(2026, 6, 10)
            assertThat(repository.streamsRead(date).first()).isEmpty()
            repository.setRead(date, 1, isRead = true)
            assertThat(repository.streamsRead(date).first()).containsExactly(1)
            repository.setRead(date, 1, isRead = true) // idempotent re-mark
            assertThat(repository.streamsRead(date).first()).containsExactly(1)
            repository.setRead(date, 1, isRead = false)
            assertThat(repository.streamsRead(date).first()).isEmpty()
        }

    @Test
    fun `whole-day mark sets all three streams and unmark clears only that date`() =
        runTest {
            val date = LocalDate.of(2026, 6, 10)
            val otherDate = LocalDate.of(2026, 6, 11)
            repository.setRead(otherDate, 2, isRead = true)
            repository.setWholeDay(date, listOf(1, 2, 3), isRead = true)
            assertThat(repository.streamsRead(date).first()).containsExactly(1, 2, 3)
            repository.setWholeDay(date, listOf(1, 2, 3), isRead = false)
            assertThat(repository.streamsRead(date).first()).isEmpty()
            assertThat(repository.streamsRead(otherDate).first()).containsExactly(2)
        }

    @Test
    fun `whole-day mark over an existing partial mark stays consistent`() =
        runTest {
            val date = LocalDate.of(2026, 3, 15)
            repository.setRead(date, 3, isRead = true)
            repository.setWholeDay(date, listOf(1, 2, 3), isRead = true)
            assertThat(repository.streamsRead(date).first()).containsExactly(1, 2, 3)
        }

    @Test
    fun `read counts cover an inclusive range and count per-day marks`() =
        runTest {
            val start = LocalDate.of(2026, 6, 1)
            val end = LocalDate.of(2026, 6, 30)
            repository.setWholeDay(LocalDate.of(2026, 6, 1), listOf(1, 2, 3), isRead = true) // start bound, 3 marks
            repository.setRead(LocalDate.of(2026, 6, 15), 3, isRead = true) // 1 mark
            repository.setWholeDay(LocalDate.of(2026, 6, 30), listOf(1, 2, 3), isRead = true) // end bound
            repository.setWholeDay(LocalDate.of(2026, 5, 31), listOf(1, 2, 3), isRead = true) // just outside (before)
            repository.setWholeDay(LocalDate.of(2026, 7, 1), listOf(1, 2, 3), isRead = true) // just outside (after)
            assertThat(repository.readCounts(start, end).first()).containsExactly(
                LocalDate.of(2026, 6, 1),
                3,
                LocalDate.of(2026, 6, 15),
                1,
                LocalDate.of(2026, 6, 30),
                3,
            )
        }

    @Test
    fun `read counts flow emits when progress changes`() =
        runTest {
            val flow = repository.readCounts(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))
            assertThat(flow.first()).isEmpty()
            repository.setWholeDay(LocalDate.of(2026, 6, 10), listOf(1, 2, 3), isRead = true)
            assertThat(flow.first()).containsExactly(LocalDate.of(2026, 6, 10), 3)
        }

    @Test
    fun `clearYear deletes Jan 1 through Dec 31 of that year and nothing else`() =
        runTest {
            repository.setWholeDay(LocalDate.of(2025, 12, 31), listOf(1, 2, 3), isRead = true) // prior year, adjacent
            repository.setWholeDay(LocalDate.of(2026, 1, 1), listOf(1, 2, 3), isRead = true) // year start bound
            repository.setRead(LocalDate.of(2026, 6, 10), 1, isRead = true)
            repository.setWholeDay(LocalDate.of(2026, 12, 31), listOf(1, 2, 3), isRead = true) // year end bound
            repository.setWholeDay(LocalDate.of(2027, 1, 1), listOf(1, 2, 3), isRead = true) // next year, adjacent
            repository.clearYear(2026)
            assertThat(repository.streamsRead(LocalDate.of(2026, 1, 1)).first()).isEmpty()
            assertThat(repository.streamsRead(LocalDate.of(2026, 6, 10)).first()).isEmpty()
            assertThat(repository.streamsRead(LocalDate.of(2026, 12, 31)).first()).isEmpty()
            assertThat(repository.streamsRead(LocalDate.of(2025, 12, 31)).first())
                .containsExactly(1, 2, 3)
            assertThat(repository.streamsRead(LocalDate.of(2027, 1, 1)).first())
                .containsExactly(1, 2, 3)
        }

    @Test
    fun `streams read flow emits when progress changes`() =
        runTest {
            val date = LocalDate.of(2026, 8, 1)
            val flow = repository.streamsRead(date)
            assertThat(flow.first()).isEmpty()
            repository.setWholeDay(date, listOf(1, 2, 3), isRead = true)
            assertThat(flow.first()).hasSize(3)
        }

    @Test
    fun `hasAnyMarks is false on an empty store - true with any mark - false again after unmark`() =
        runTest {
            assertThat(repository.hasAnyMarks()).isFalse()
            repository.setRead(LocalDate.of(2026, 6, 1), 3, isRead = true)
            assertThat(repository.hasAnyMarks()).isTrue()
            repository.setRead(LocalDate.of(2026, 6, 1), 3, isRead = false)
            assertThat(repository.hasAnyMarks()).isFalse()
        }

    @Test
    fun `allReadCounts spans every stored year and groups per day`() =
        runTest {
            repository.setWholeDay(LocalDate.of(2025, 12, 31), listOf(1, 2, 3), isRead = true)
            repository.setRead(LocalDate.of(2026, 1, 1), 1, isRead = true)
            repository.setRead(LocalDate.of(2026, 1, 1), 3, isRead = true)
            repository.setRead(LocalDate.of(2027, 6, 10), 2, isRead = true)

            assertThat(repository.allReadCounts().first())
                .containsExactly(
                    LocalDate.of(2025, 12, 31),
                    3,
                    LocalDate.of(2026, 1, 1),
                    2,
                    LocalDate.of(2027, 6, 10),
                    1,
                )
        }

    @Test
    fun `allReadCounts re-emits when marks change`() =
        runTest {
            val flow = repository.allReadCounts()
            assertThat(flow.first()).isEmpty()
            repository.setWholeDay(LocalDate.of(2026, 6, 10), listOf(1, 2, 3), isRead = true)
            assertThat(flow.first()).containsExactly(LocalDate.of(2026, 6, 10), 3)
        }

    @Test
    fun `streamMarks returns per-stream date sets and respects inclusive range bounds`() =
        runTest {
            // S17 year-strip input: raw (day, stream) marks grouped per stream.
            repository.setWholeDay(LocalDate.of(2026, 1, 1), listOf(1, 2, 3), isRead = true) // boundary in
            repository.setRead(LocalDate.of(2026, 6, 10), 3, isRead = true)
            repository.setRead(LocalDate.of(2026, 12, 31), 1, isRead = true) // boundary in
            repository.setRead(LocalDate.of(2025, 12, 31), 3, isRead = true) // out of range
            repository.setRead(LocalDate.of(2027, 1, 1), 3, isRead = true) // out of range
            val marks =
                repository
                    .streamMarks(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
                    .first()
            assertThat(marks[1])
                .containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
            assertThat(marks[2]).containsExactly(LocalDate.of(2026, 1, 1))
            assertThat(marks[3])
                .containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 10))
        }

    @Test
    fun `streamCounts groups per stream and respects inclusive range bounds`() =
        runTest {
            // Inside 2026: two L&H days, one NT day.
            repository.setRead(LocalDate.of(2026, 1, 1), 1, isRead = true)
            repository.setRead(LocalDate.of(2026, 12, 31), 1, isRead = true)
            repository.setRead(LocalDate.of(2026, 6, 10), 3, isRead = true)
            // Outside both bounds by exactly one day: must not count.
            repository.setRead(LocalDate.of(2025, 12, 31), 1, isRead = true)
            repository.setRead(LocalDate.of(2027, 1, 1), 3, isRead = true)

            val counts =
                repository
                    .streamCounts(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
                    .first()
            assertThat(counts)
                .containsExactly(1, 2, 3, 1)
            assertThat(counts).doesNotContainKey(2)
        }
}
