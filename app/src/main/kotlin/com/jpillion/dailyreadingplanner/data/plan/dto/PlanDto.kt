package com.jpillion.dailyreadingplanner.data.plan.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Serialization DTOs for a plan asset (ESpec-alt §3.3, schemaVersion 3).
 *
 * D-ALT-2/3: a plan now DECLARES its shape in an additive descriptor head — `planId`, `name`,
 * `anchoring`, `dayCount`, `streams[]`. The `days`/`portions`/`refs` body is a STRICT SUBSET of
 * schema v2 (unchanged), so the Psalm-119 verse window and the two-book Jun 19/Dec 19 portion
 * keep working for every plan for free. The Bible Companion is "just the first plan written in v3".
 */
@Serializable
data class PlanDto(
    val schemaVersion: Int,
    val planId: String,
    val name: String,
    val source: String,
    val anchoring: String,
    val dayCount: Int,
    val streams: List<StreamDto>,
    val days: List<PlanDayDto>,
)

@Serializable
data class StreamDto(
    val number: Int,
    val title: String,
)

@Serializable
data class PlanDayDto(
    val month: Int,
    val day: Int,
    val portions: List<PortionDto>,
)

@Serializable
data class PortionDto(
    val stream: Int,
    val refs: List<RefDto>,
)

@Serializable
data class RefDto(
    val book: String,
    val chapter: Int,
    // schemaVersion 2 body (D-SCHEMA-1), carried unchanged into v3 (D-ALT-3): an optional verse
    // window inside the chapter. Absent on whole-chapter readings (null ⇒ whole chapter); present
    // on verse-windowed days (Bible Companion: the four Psalm-119 days; M'Cheyne: ~24 days). Both
    // fields are present together or both absent (validated at load).
    val verseStart: Int? = null,
    val verseEnd: Int? = null,
)

/** Strict decoder for a plan asset; the loader builds on this. */
object PlanJson {
    private val format = Json { ignoreUnknownKeys = false }

    fun decode(text: String): PlanDto = format.decodeFromString<PlanDto>(text)
}
