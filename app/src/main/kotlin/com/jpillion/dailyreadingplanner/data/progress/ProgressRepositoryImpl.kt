package com.jpillion.dailyreadingplanner.data.progress

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * D-ALT-12 (Alt Sprint B): every method scopes its DAO call by [planId], which the interface
 * defaults to the flagship `bible_companion` — so the default path is byte-for-byte the pre-Sprint-B
 * behavior (parity). The live active-plan threading into the use-case callers is Sprint C/D.
 *
 * D-ALT-5 (Alt Sprint C): a stream is now its plain `Int` number. The DAO already stored/returned
 * the int `stream` column; the impl no longer maps through the retired `Stream` enum.
 */
class ProgressRepositoryImpl
    @Inject
    constructor(
        private val dao: ReadingProgressDao,
        private val clock: Clock,
    ) : ProgressRepository {
        override fun streamsRead(
            date: LocalDate,
            planId: String,
        ): Flow<Set<Int>> =
            dao
                .streamsRead(planId, date.toEpochDay())
                .map { streams -> streams.toSet() }
                .distinctUntilChanged()

        override fun readCounts(
            start: LocalDate,
            end: LocalDate,
            planId: String,
        ): Flow<Map<LocalDate, Int>> =
            dao
                .readCountsInRange(planId, start.toEpochDay(), end.toEpochDay())
                .map { rows -> rows.associate { LocalDate.ofEpochDay(it.dateEpochDay) to it.readCount } }
                .distinctUntilChanged()

        override fun allReadCounts(planId: String): Flow<Map<LocalDate, Int>> =
            dao
                .allReadCounts(planId)
                .map { rows -> rows.associate { LocalDate.ofEpochDay(it.dateEpochDay) to it.readCount } }
                .distinctUntilChanged()

        override fun streamCounts(
            start: LocalDate,
            end: LocalDate,
            planId: String,
        ): Flow<Map<Int, Int>> =
            dao
                .streamCountsInRange(planId, start.toEpochDay(), end.toEpochDay())
                .map { rows -> rows.associate { it.stream to it.readCount } }
                .distinctUntilChanged()

        override fun streamMarks(
            start: LocalDate,
            end: LocalDate,
            planId: String,
        ): Flow<Map<Int, Set<LocalDate>>> =
            dao
                .marksInRange(planId, start.toEpochDay(), end.toEpochDay())
                .map { rows ->
                    rows
                        .groupBy({ it.stream }, { LocalDate.ofEpochDay(it.dateEpochDay) })
                        .mapValues { (_, dates) -> dates.toSet() }
                }.distinctUntilChanged()

        // D-ALT-15: GLOBAL on purpose — "any mark in any plan." No plan scoping.
        override suspend fun hasAnyMarks(): Boolean = dao.hasAnyRows()

        override suspend fun setRead(
            date: LocalDate,
            streamNumber: Int,
            isRead: Boolean,
            planId: String,
        ) {
            if (isRead) {
                dao.upsert(listOf(entity(planId, date, streamNumber)))
            } else {
                dao.delete(planId, date.toEpochDay(), streamNumber)
            }
        }

        override suspend fun setWholeDay(
            date: LocalDate,
            streamNumbers: List<Int>,
            isRead: Boolean,
            planId: String,
        ) {
            if (isRead) {
                dao.upsert(streamNumbers.map { entity(planId, date, it) })
            } else {
                dao.deleteDay(planId, date.toEpochDay())
            }
        }

        override suspend fun clearYear(
            year: Int,
            planId: String,
        ) {
            dao.deleteRange(
                planId = planId,
                startEpochDay = LocalDate.of(year, 1, 1).toEpochDay(),
                endEpochDay = LocalDate.of(year, 12, 31).toEpochDay(),
            )
        }

        private fun entity(
            planId: String,
            date: LocalDate,
            streamNumber: Int,
        ) = ReadingProgressEntity(
            planId = planId,
            dateEpochDay = date.toEpochDay(),
            stream = streamNumber,
            readAtEpochMillis = clock.millis(),
        )
    }
