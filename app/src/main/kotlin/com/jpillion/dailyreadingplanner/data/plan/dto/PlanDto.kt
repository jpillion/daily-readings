package com.jpillion.dailyreadingplanner.data.plan.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Serialization DTOs for assets/reading_plan.json (ESpec §5.1, schemaVersion 1). */
@Serializable
data class PlanDto(
    val schemaVersion: Int,
    val source: String,
    val days: List<PlanDayDto>,
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
)

/** Strict decoder for the plan asset; Sprint 3's ReadingPlanAssetLoader builds on this. */
object PlanJson {
    private val format = Json { ignoreUnknownKeys = false }

    fun decode(text: String): PlanDto = format.decodeFromString<PlanDto>(text)
}
