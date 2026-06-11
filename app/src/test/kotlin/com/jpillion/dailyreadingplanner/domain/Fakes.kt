package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.core.date.ReadingDate
import com.jpillion.dailyreadingplanner.data.plan.ReadingPlanRepository
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.domain.model.Stream
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

val threePortions =
    listOf(
        portion(Stream.LAW_AND_HISTORY, "Genesis" to 1, "Genesis" to 2),
        portion(Stream.PSALMS_AND_PROPHECY, "Psalms" to 1, "Psalms" to 2),
        portion(Stream.NEW_TESTAMENT, "Matthew" to 1, "Matthew" to 2),
    )

class FakeReadingPlanRepository(
    private val portions: List<Portion> = threePortions,
) : ReadingPlanRepository {
    override suspend fun portionsFor(date: ReadingDate): List<Portion> = portions
}

class FakeProgressRepository : ProgressRepository {
    private val marks = MutableStateFlow<Map<LocalDate, Set<Stream>>>(emptyMap())
    var streamsReadQueries = 0
        private set

    override fun streamsRead(date: LocalDate): Flow<Set<Stream>> {
        streamsReadQueries++
        return marks.map { it[date] ?: emptySet() }.distinctUntilChanged()
    }

    override suspend fun setRead(
        date: LocalDate,
        stream: Stream,
        isRead: Boolean,
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
    ) {
        marks.value =
            marks.value.toMutableMap().apply {
                this[date] = if (isRead) Stream.entries.toSet() else emptySet()
            }
    }

    fun marksFor(date: LocalDate): Set<Stream> = marks.value[date] ?: emptySet()
}
