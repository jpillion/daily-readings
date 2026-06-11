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

        private fun entity(
            date: LocalDate,
            stream: Stream,
        ) = ReadingProgressEntity(
            dateEpochDay = date.toEpochDay(),
            stream = stream.number,
            readAtEpochMillis = clock.millis(),
        )
    }
