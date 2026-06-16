package com.jpillion.dailyreadingplanner.data.plan.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Serialization DTOs for assets/plans/registry.json (ESpec-alt §3.2, D-ALT-1). */
@Serializable
data class RegistryDto(
    val registryVersion: Int,
    val defaultPlanId: String,
    val plans: List<RegistryEntryDto>,
)

@Serializable
data class RegistryEntryDto(
    val id: String,
    val asset: String,
)

/** Strict decoder for the plan registry. */
object RegistryJson {
    private val format = Json { ignoreUnknownKeys = false }

    fun decode(text: String): RegistryDto = format.decodeFromString<RegistryDto>(text)
}
