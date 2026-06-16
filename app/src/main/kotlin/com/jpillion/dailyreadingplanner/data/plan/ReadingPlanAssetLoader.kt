package com.jpillion.dailyreadingplanner.data.plan

import com.jpillion.dailyreadingplanner.core.date.ReadingDate
import com.jpillion.dailyreadingplanner.data.plan.dto.PlanDto
import com.jpillion.dailyreadingplanner.data.plan.dto.PlanJson
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.di.IoDispatcher
import com.jpillion.dailyreadingplanner.domain.model.PlanDescriptor
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.domain.model.ReferenceVerses
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.domain.model.StreamDescriptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Reads, parses, and validates a bundled plan asset (ESpec-alt §3.5). Validation is now
 * DESCRIPTOR-DRIVEN (D-ALT-2/4): the plan declares its `dayCount` and `streams[]` in a schema-v3
 * head and the loader validates against the declaration, not the old `365` / `listOf(1,2,3)`
 * constants. Validation failures throw: every shipped plan is release-gated by a *PlanVerificationTest,
 * so an invalid asset at runtime is a build defect, not a user-data condition.
 *
 * Sprint A is descriptor-ADDITIVE: the Bible-Companion path still maps each portion through the
 * `Stream` enum (`Stream.fromNumber`), so its output map is byte-identical to today. The `Stream`
 * enum is retired in Sprint C (D-ALT-5); a plan with streams outside 1..3 (M'Cheyne) is validated
 * here at the DTO level but is not mapped to the domain `Portion` map until that retirement.
 */
class ReadingPlanAssetLoader
    @Inject
    constructor(
        private val source: PlanAssetSource,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        /** A validated plan: its schedule map + its self-declared descriptor, parsed once. */
        data class LoadedPlan(
            val schedule: Map<ReadingDate, List<Portion>>,
            val descriptor: PlanDescriptor,
        )

        /** Reads, validates, and fully parses the asset in a single pass (the repository's path). */
        suspend fun loadFull(
            assetPath: String,
            expectedPlanId: String,
        ): LoadedPlan =
            withContext(ioDispatcher) {
                val dto = PlanJson.decode(source.readText(assetPath))
                validate(dto, expectedPlanId)
                val schedule =
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
                LoadedPlan(schedule, dto.toDescriptor())
            }

        /** Validates the asset and returns its self-declared shape (descriptor only — no body map). */
        suspend fun descriptor(
            assetPath: String,
            expectedPlanId: String,
        ): PlanDescriptor =
            withContext(ioDispatcher) {
                val dto = PlanJson.decode(source.readText(assetPath))
                validate(dto, expectedPlanId)
                dto.toDescriptor()
            }

        /** Validates the asset and parses it into the immutable date-anchored schedule map. */
        suspend fun load(
            assetPath: String,
            expectedPlanId: String,
        ): Map<ReadingDate, List<Portion>> = loadFull(assetPath, expectedPlanId).schedule

        private fun validate(
            dto: PlanDto,
            expectedPlanId: String,
        ) {
            check(dto.schemaVersion == SUPPORTED_SCHEMA_VERSION) {
                "unsupported plan schemaVersion ${dto.schemaVersion}"
            }
            // Anti-drift (mirrors D-S9-1): the asset's planId must equal the registry id we asked for.
            check(dto.planId == expectedPlanId) {
                "plan asset declares planId '${dto.planId}', expected '$expectedPlanId'"
            }
            // This cut ships date-anchored, 365-day plans only (FR-ALT-5). Reject anything else
            // cleanly — the fields exist so a later anchoring/length is a recognized value, never a
            // silent mis-interpretation.
            check(dto.anchoring == SUPPORTED_ANCHORING) {
                "unsupported anchoring '${dto.anchoring}', expected '$SUPPORTED_ANCHORING'"
            }
            check(dto.dayCount == SUPPORTED_DAY_COUNT) {
                "unsupported dayCount ${dto.dayCount}, expected $SUPPORTED_DAY_COUNT"
            }
            check(dto.days.size == dto.dayCount) {
                "plan has ${dto.days.size} days, declares dayCount ${dto.dayCount}"
            }
            // streams[].number must be 1..N contiguous.
            val declaredStreams = dto.streams.map { it.number }
            check(declaredStreams == (1..dto.streams.size).toList()) {
                "streams must be numbered 1..N contiguous, got $declaredStreams"
            }
            check(dto.streams.all { it.title.isNotBlank() || dto.streams.size == 1 }) {
                "every stream of a multi-stream plan needs a title"
            }
            val declaredStreamSet = declaredStreams.toSet()
            val keys = dto.days.map { it.month to it.day }
            check(keys.toSet().size == keys.size) { "plan has duplicate (month, day) entries" }
            // 365-day + DATE: February has 28 entries, no Feb 29 (D1; every date-anchored plan).
            check(dto.days.none { it.month == 2 && it.day == 29 }) { "plan has a Feb 29 entry" }
            dto.days.forEach { day ->
                // Every day's portion stream-set is EXACTLY the declared streams (the generalized
                // form of the old `== listOf(1,2,3)` check, D-ALT-2).
                check(day.portions.map { it.stream }.toSet() == declaredStreamSet) {
                    "day ${day.month}/${day.day} portions ${day.portions.map { it.stream }} != streams $declaredStreams"
                }
                day.portions.forEach { p ->
                    check(p.refs.isNotEmpty()) { "day ${day.month}/${day.day} stream ${p.stream} has no refs" }
                }
            }
        }

        private companion object {
            const val SUPPORTED_SCHEMA_VERSION = 3
            const val SUPPORTED_ANCHORING = "DATE"
            const val SUPPORTED_DAY_COUNT = 365
        }
    }

private fun PlanDto.toDescriptor(): PlanDescriptor =
    PlanDescriptor(
        planId = planId,
        name = name,
        anchoring = anchoring,
        dayCount = dayCount,
        streams = streams.map { StreamDescriptor(it.number, it.title) },
    )

/**
 * Maps the optional schema verse fields to a [ReferenceVerses] window. Both must be present or
 * both absent (a lone bound is ambiguous → reject); `1 <= start <= end` (the `ReferenceVerses`
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
