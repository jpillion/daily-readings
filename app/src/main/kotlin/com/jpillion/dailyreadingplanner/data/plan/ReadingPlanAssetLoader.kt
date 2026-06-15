package com.jpillion.dailyreadingplanner.data.plan

import com.jpillion.dailyreadingplanner.core.date.ReadingDate
import com.jpillion.dailyreadingplanner.data.plan.dto.PlanDto
import com.jpillion.dailyreadingplanner.data.plan.dto.PlanJson
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.di.IoDispatcher
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.domain.model.ReferenceVerses
import com.jpillion.dailyreadingplanner.domain.model.Stream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Reads, parses, and validates the bundled plan asset into the immutable schedule map
 * (ESpec §5.1). Validation failures throw: the asset is release-gated by
 * ReadingPlanVerificationTest, so an invalid plan at runtime is a build defect, not a
 * user-data condition.
 */
class ReadingPlanAssetLoader
    @Inject
    constructor(
        private val source: PlanJsonSource,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        suspend fun load(): Map<ReadingDate, List<Portion>> =
            withContext(ioDispatcher) {
                val dto = PlanJson.decode(source.readText())
                validate(dto)
                dto.days.associate { day ->
                    ReadingDate(day.month, day.day) to
                        day.portions.map { portion ->
                            Portion(
                                stream = Stream.fromNumber(portion.stream),
                                refs =
                                    portion.refs.map { ref ->
                                        Reference(
                                            book = BookCatalog.requireByName(ref.book),
                                            chapter = ref.chapter,
                                            verses = ref.toVerses(),
                                        )
                                    },
                            )
                        }
                }
            }

        private fun validate(dto: PlanDto) {
            check(dto.schemaVersion == SUPPORTED_SCHEMA_VERSION) {
                "unsupported plan schemaVersion ${dto.schemaVersion}"
            }
            check(dto.days.size == EXPECTED_DAYS) { "plan has ${dto.days.size} days, expected $EXPECTED_DAYS" }
            val keys = dto.days.map { it.month to it.day }
            check(keys.toSet().size == keys.size) { "plan has duplicate (month, day) entries" }
            dto.days.forEach { day ->
                check(day.portions.map { it.stream } == listOf(1, 2, 3)) {
                    "day ${day.month}/${day.day} does not have exactly streams 1,2,3"
                }
            }
        }

        private companion object {
            const val SUPPORTED_SCHEMA_VERSION = 2
            const val EXPECTED_DAYS = 365
        }
    }

/**
 * Maps the optional schema-v2 verse fields to a [ReferenceVerses] window. Both must be present
 * or both absent (a lone bound is ambiguous → reject); `1 <= start <= end` (the `ReferenceVerses`
 * init also enforces this — defense-in-depth at the asset boundary). The per-chapter upper bound
 * is the gate's job. The asset is release-gated, so a throw here is a build defect, not user data.
 */
private fun com.jpillion.dailyreadingplanner.data.plan.dto.RefDto.toVerses(): ReferenceVerses? {
    val start = verseStart
    val end = verseEnd
    return when {
        start == null && end == null -> null
        start == null || end == null ->
            error("ref $book $chapter has only one of verseStart/verseEnd; both are required")
        else -> ReferenceVerses(start, end)
    }
}
