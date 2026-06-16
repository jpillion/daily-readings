package com.jpillion.dailyreadingplanner.domain

import com.jpillion.dailyreadingplanner.core.date.ReadingDate
import com.jpillion.dailyreadingplanner.data.plan.ActivePlanRepository
import com.jpillion.dailyreadingplanner.data.plan.PlanRegistry
import com.jpillion.dailyreadingplanner.data.plan.ReadingPlanRepository
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.domain.model.PlanDescriptor
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.domain.model.ReferenceVerses
import com.jpillion.dailyreadingplanner.domain.model.StreamDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate

fun portion(
    streamNumber: Int,
    vararg refs: Pair<String, Int>,
): Portion =
    Portion(
        streamNumber = streamNumber,
        refs = refs.map { (book, chapter) -> Reference(BookCatalog.requireByName(book), chapter) },
    )

/** A single-ref portion whose ref carries a chapter-relative verse window (schema v2). */
fun windowedPortion(
    streamNumber: Int,
    book: String,
    chapter: Int,
    start: Int,
    end: Int,
): Portion =
    Portion(
        streamNumber = streamNumber,
        refs = listOf(Reference(BookCatalog.requireByName(book), chapter, ReferenceVerses(start, end))),
    )

/** The Bible Companion's three streams (numbers 1..3), the parity baseline. */
val threePortions =
    listOf(
        portion(1, "Genesis" to 1, "Genesis" to 2),
        portion(2, "Psalms" to 1, "Psalms" to 2),
        portion(3, "Matthew" to 1, "Matthew" to 2),
    )

/** The Bible-Companion descriptor (N=3) — the default-plan parity baseline for tests. */
val bibleCompanionDescriptor =
    PlanDescriptor(
        planId = PlanRegistry.DEFAULT_PLAN_ID,
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

class FakeReadingPlanRepository(
    private val portions: List<Portion> = threePortions,
    private val descriptor: PlanDescriptor = bibleCompanionDescriptor,
) : ReadingPlanRepository {
    override suspend fun portionsFor(
        planId: String,
        date: ReadingDate,
    ): List<Portion> = portions

    override suspend fun descriptor(planId: String): PlanDescriptor = descriptor
}

/**
 * A single-active-plan fake (D-ALT-17). Defaults to the Bible Companion (N=3) — the parity
 * baseline — but a test can hand it any descriptor/id to exercise N=1/2/4 through the same seam.
 */
class FakeActivePlanRepository(
    descriptor: PlanDescriptor = bibleCompanionDescriptor,
    planId: String = descriptor.planId,
) : ActivePlanRepository {
    override val activePlanId: Flow<String> = MutableStateFlow(planId)
    override val activeDescriptor: Flow<PlanDescriptor> = MutableStateFlow(descriptor)
}

/**
 * In-memory progress store. D-ALT-5: streams are plain `Int` numbers now. Single-plan model —
 * it ignores `planId` (two-plan isolation is proven against real Room in
 * ProgressRepositoryPlanScopeTest, not the fake), so every use-case test reads the default plan.
 */
class FakeProgressRepository : ProgressRepository {
    private val marks = MutableStateFlow<Map<LocalDate, Set<Int>>>(emptyMap())
    var streamsReadQueries = 0
        private set

    override fun streamsRead(
        date: LocalDate,
        planId: String,
    ): Flow<Set<Int>> {
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
    ): Flow<Map<Int, Int>> =
        marks
            .map { all ->
                all
                    .filterKeys { !it.isBefore(start) && !it.isAfter(end) }
                    .values
                    .flatten()
                    .groupingBy { it }
                    .eachCount()
            }.distinctUntilChanged()

    override fun streamMarks(
        start: LocalDate,
        end: LocalDate,
        planId: String,
    ): Flow<Map<Int, Set<LocalDate>>> =
        marks
            .map { all ->
                val inRange = all.filterKeys { !it.isBefore(start) && !it.isAfter(end) }
                val streams = inRange.values.flatten().toSet()
                streams
                    .associateWith { stream ->
                        inRange.filterValues { stream in it }.keys
                    }.filterValues { it.isNotEmpty() }
            }.distinctUntilChanged()

    override suspend fun hasAnyMarks(): Boolean = marks.value.any { it.value.isNotEmpty() }

    override suspend fun setRead(
        date: LocalDate,
        streamNumber: Int,
        isRead: Boolean,
        planId: String,
    ) {
        marks.value =
            marks.value.toMutableMap().apply {
                val current = (this[date] ?: emptySet()).toMutableSet()
                if (isRead) current += streamNumber else current -= streamNumber
                this[date] = current
            }
    }

    override suspend fun setWholeDay(
        date: LocalDate,
        streamNumbers: List<Int>,
        isRead: Boolean,
        planId: String,
    ) {
        marks.value =
            marks.value.toMutableMap().apply {
                this[date] = if (isRead) streamNumbers.toSet() else emptySet()
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

    fun marksFor(date: LocalDate): Set<Int> = marks.value[date] ?: emptySet()
}
