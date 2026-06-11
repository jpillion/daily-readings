package com.jpillion.dailyreadingplanner.data.progress

import com.jpillion.dailyreadingplanner.domain.model.Stream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class ProgressRepositoryImpl
    @Inject
    constructor(
        private val dao: ReadingProgressDao,
        private val clock: Clock,
    ) : ProgressRepository {
        override fun streamsRead(date: LocalDate): Flow<Set<Stream>> =
            dao
                .streamsRead(date.toEpochDay())
                .map { streams -> streams.map(Stream.Companion::fromNumber).toSet() }
                .distinctUntilChanged()

        override fun readCounts(
            start: LocalDate,
            end: LocalDate,
        ): Flow<Map<LocalDate, Int>> =
            dao
                .readCountsInRange(start.toEpochDay(), end.toEpochDay())
                .map { rows -> rows.associate { LocalDate.ofEpochDay(it.dateEpochDay) to it.readCount } }
                .distinctUntilChanged()

        override suspend fun hasAnyMarks(): Boolean = dao.hasAnyRows()

        override suspend fun setRead(
            date: LocalDate,
            stream: Stream,
            isRead: Boolean,
        ) {
            if (isRead) {
                dao.upsert(listOf(entity(date, stream)))
            } else {
                dao.delete(date.toEpochDay(), stream.number)
            }
        }

        override suspend fun setWholeDay(
            date: LocalDate,
            isRead: Boolean,
        ) {
            if (isRead) {
                dao.upsert(Stream.entries.map { entity(date, it) })
            } else {
                dao.deleteDay(date.toEpochDay())
            }
        }

        override suspend fun clearYear(year: Int) {
            dao.deleteRange(
                startEpochDay = LocalDate.of(year, 1, 1).toEpochDay(),
                endEpochDay = LocalDate.of(year, 12, 31).toEpochDay(),
            )
        }

        private fun entity(
            date: LocalDate,
            stream: Stream,
        ) = ReadingProgressEntity(
            dateEpochDay = date.toEpochDay(),
            stream = stream.number,
            readAtEpochMillis = clock.millis(),
        )
    }
