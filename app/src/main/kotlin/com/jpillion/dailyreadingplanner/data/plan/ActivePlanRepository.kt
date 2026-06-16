package com.jpillion.dailyreadingplanner.data.plan

import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.domain.model.PlanDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single place the rest of the app asks "what plan am I on, and what's its shape?" (D-ALT-17).
 * Joins the `selected_plan` setting with the [PlanRegistry] and the [ReadingPlanRepository] to
 * expose the active plan id + its descriptor as live flows.
 *
 * Sprint A stands this up and proves the default; NO use case consumes it yet (that is Sprint C/D).
 * Unknown stored id ⇒ the registry default — the `bible_provider` degrade-never-crash posture.
 */
interface ActivePlanRepository {
    val activePlanId: Flow<String>
    val activeDescriptor: Flow<PlanDescriptor>
}

@Singleton
class ActivePlanRepositoryImpl
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val registry: PlanRegistry,
        private val planRepository: ReadingPlanRepository,
    ) : ActivePlanRepository {
        /** Selected id, degraded to the registry default if it is not a known plan. */
        override val activePlanId: Flow<String> =
            settingsRepository.selectedPlanId.map { stored ->
                if (registry.isKnown(stored)) stored else registry.defaultPlanId()
            }

        override val activeDescriptor: Flow<PlanDescriptor> =
            activePlanId.map { id -> planRepository.descriptor(id) }
    }
