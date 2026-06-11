package com.jpillion.dailyreadingplanner.data.progress

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.Stream
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
            repository.setWholeDay(jan2026, isRead = true)
            assertThat(repository.streamsRead(jan2026).first()).containsExactlyElementsIn(Stream.entries)
            assertThat(repository.streamsRead(jan2027).first()).isEmpty()
            repository.setRead(jan2027, Stream.NEW_TESTAMENT, isRead = true)
            assertThat(repository.streamsRead(jan2027).first()).containsExactly(Stream.NEW_TESTAMENT)
            assertThat(repository.streamsRead(jan2026).first()).containsExactlyElementsIn(Stream.entries)
        }

    @Test
    fun `marking and unmarking a single reading round-trips`() =
        runTest {
            val date = LocalDate.of(2026, 6, 10)
            assertThat(repository.streamsRead(date).first()).isEmpty()
            repository.setRead(date, Stream.LAW_AND_HISTORY, isRead = true)
            assertThat(repository.streamsRead(date).first()).containsExactly(Stream.LAW_AND_HISTORY)
            repository.setRead(date, Stream.LAW_AND_HISTORY, isRead = true) // idempotent re-mark
            assertThat(repository.streamsRead(date).first()).containsExactly(Stream.LAW_AND_HISTORY)
            repository.setRead(date, Stream.LAW_AND_HISTORY, isRead = false)
            assertThat(repository.streamsRead(date).first()).isEmpty()
        }

    @Test
    fun `whole-day mark sets all three streams and unmark clears only that date`() =
        runTest {
            val date = LocalDate.of(2026, 6, 10)
            val otherDate = LocalDate.of(2026, 6, 11)
            repository.setRead(otherDate, Stream.PSALMS_AND_PROPHECY, isRead = true)
            repository.setWholeDay(date, isRead = true)
            assertThat(repository.streamsRead(date).first()).containsExactlyElementsIn(Stream.entries)
            repository.setWholeDay(date, isRead = false)
            assertThat(repository.streamsRead(date).first()).isEmpty()
            assertThat(repository.streamsRead(otherDate).first()).containsExactly(Stream.PSALMS_AND_PROPHECY)
        }

    @Test
    fun `whole-day mark over an existing partial mark stays consistent`() =
        runTest {
            val date = LocalDate.of(2026, 3, 15)
            repository.setRead(date, Stream.NEW_TESTAMENT, isRead = true)
            repository.setWholeDay(date, isRead = true)
            assertThat(repository.streamsRead(date).first()).containsExactlyElementsIn(Stream.entries)
        }

    @Test
    fun `streams read flow emits when progress changes`() =
        runTest {
            val date = LocalDate.of(2026, 8, 1)
            val flow = repository.streamsRead(date)
            assertThat(flow.first()).isEmpty()
            repository.setWholeDay(date, isRead = true)
            assertThat(flow.first()).hasSize(3)
        }
}
