package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.core.date.ReadingDate
import com.jpillion.dailyreadingplanner.data.plan.ReadingPlanRepository
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.domain.model.PlanDescriptor
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.domain.model.ReferenceVerses
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.domain.model.StreamDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate

fun portion(
    stream: Stream,
    vararg refs: Pair<String, Int>,
): Portion =
    Portion(
        stream = stream,
        refs = refs.map { (book, chapter) -> Reference(BookCatalog.requireByName(book), chapter) },
    )

/** A single-ref portion whose ref carries a chapter-relative verse window (schema v2). */
fun windowedPortion(
    stream: Stream,
    book: String,
    chapter: Int,
    start: Int,
    end: Int,
): Portion =
    Portion(
        stream = stream,
        refs = listOf(Reference(BookCatalog.requireByName(book), chapter, ReferenceVerses(start, end))),
    )

val threePortions =
    listOf(
        portion(Stream.LAW_AND_HISTORY, "Genesis" to 1, "Genesis" to 2),
        portion(Stream.PSALMS_AND_PROPHECY, "Psalms" to 1, "Psalms" to 2),
        portion(Stream.NEW_TESTAMENT, "Matthew" to 1, "Matthew" to 2),
    )

class FakeReadingPlanRepository(
    private val portions: List<Portion> = threePortions,
) : ReadingPlanRepository {
    override suspend fun portionsFor(
        planId: String,
        date: ReadingDate,
    ): List<Portion> = portions

    override suspend fun descriptor(planId: String): PlanDescriptor =
        PlanDescriptor(
            planId = planId,
            name = "Bible Companion",
            anchoring = "DATE",
            dayCount = 365,
            streams =
                listOf(
                    StreamDescriptor(1, "Law & History"),
                    StreamDescriptor(2, "Psalms & Prophecy"),
                    StreamDescriptor(3, "New Testament"),
                ),
        )
}

class FakeProgressRepository : ProgressRepository {
    private val marks = MutableStateFlow<Map<LocalDate, Set<Stream>>>(emptyMap())
    var streamsReadQueries = 0
        private set

    override fun streamsRead(
        date: LocalDate,
        planId: String,
    ): Flow<Set<Stream>> {
        streamsReadQueries++
        return marks.map { it[date] ?: emptySet() }.distinctUntilChanged()
    }

    override fun readCounts(
        start: LocalDate,
        end: LocalDate,
        planId: String,
    ): Flow<Map<LocalDate, Int>> =
        marks
            .map { all ->
                all
                    .filterKeys { !it.isBefore(start) && !it.isAfter(end) }
                    .filterValues { it.isNotEmpty() }
                    .mapValues { (_, streams) -> streams.size }
            }.distinctUntilChanged()

    override fun allReadCounts(planId: String): Flow<Map<LocalDate, Int>> =
        marks
            .map { all ->
                all.filterValues { it.isNotEmpty() }.mapValues { (_, streams) -> streams.size }
            }.distinctUntilChanged()

    override fun streamCounts(
        start: LocalDate,
        end: LocalDate,
        planId: String,
    ): Flow<Map<Stream, Int>> =
        marks
            .map { all ->
                Stream.entries
                    .associateWith { stream ->
                        all.count { (date, streams) ->
                            stream in streams && !date.isBefore(start) && !date.isAfter(end)
                        }
                    }.filterValues { it > 0 }
            }.distinctUntilChanged()

    override fun streamMarks(
        start: LocalDate,
        end: LocalDate,
        planId: String,
    ): Flow<Map<Stream, Set<LocalDate>>> =
        marks
            .map { all ->
                Stream.entries
                    .associateWith { stream ->
                        all
                            .filterKeys { !it.isBefore(start) && !it.isAfter(end) }
                            .filterValues { stream in it }
                            .keys
                    }.filterValues { it.isNotEmpty() }
            }.distinctUntilChanged()

    override suspend fun hasAnyMarks(): Boolean = marks.value.any { it.value.isNotEmpty() }

    override suspend fun setRead(
        date: LocalDate,
        stream: Stream,
        isRead: Boolean,
        planId: String,
    ) {
        marks.value =
            marks.value.toMutableMap().apply {
                val current = (this[date] ?: emptySet()).toMutableSet()
                if (isRead) current += stream else current -= stream
                this[date] = current
            }
    }

    override suspend fun setWholeDay(
        date: LocalDate,
        isRead: Boolean,
        planId: String,
    ) {
        marks.value =
            marks.value.toMutableMap().apply {
                this[date] = if (isRead) Stream.entries.toSet() else emptySet()
            }
    }

    override suspend fun clearYear(
        year: Int,
        planId: String,
    ) {
        clearYearCalls += year
        marks.value = marks.value.filterKeys { it.year != year }
    }

    val clearYearCalls = mutableListOf<Int>()

    fun marksFor(date: LocalDate): Set<Stream> = marks.value[date] ?: emptySet()
}
