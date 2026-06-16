package com.jpillion.dailyreadingplanner.data.plan

import com.jpillion.dailyreadingplanner.data.plan.dto.RegistryDto
import com.jpillion.dailyreadingplanner.data.plan.dto.RegistryJson
import com.jpillion.dailyreadingplanner.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A plan's registry entry: its stable [id] and bundled [asset] path. */
data class RegisteredPlan(
    val id: String,
    val asset: String,
)

/**
 * The single bundled artifact that ENUMERATES the curated plan set (ESpec-alt §3.2, D-ALT-1):
 * reads `assets/plans/registry.json` once, memoized. The heavy per-plan descriptor lives in each
 * plan's own asset head, so a registry typo can't contradict a plan.
 *
 * A registry entry is a BUILD GUARANTEE (the plan's asset + gate ship together) — there is no
 * runtime "plan not found" path for a listed id. `DEFAULT_PLAN_ID` is a build constant asserted
 * `== defaultPlanId` so the app never ships without the flagship default.
 */
@Singleton
class PlanRegistry
    @Inject
    constructor(
        private val source: PlanAssetSource,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        private val mutex = Mutex()

        @Volatile
        private var cached: RegistryDto? = null

        /** The flagship/cold-start default (FR-ALT-2). */
        suspend fun defaultPlanId(): String = registry().defaultPlanId

        /** The curated set, in registry order. */
        suspend fun plans(): List<RegisteredPlan> = registry().plans.map { RegisteredPlan(it.id, it.asset) }

        /**
         * The asset path for [planId], or `null` if it is not a registered id (the caller degrades
         * to the default — the `bible_provider` posture). The default is always registered.
         */
        suspend fun assetFor(planId: String): String? = registry().plans.firstOrNull { it.id == planId }?.asset

        /** True iff [planId] is a registered plan. */
        suspend fun isKnown(planId: String): Boolean = registry().plans.any { it.id == planId }

        private suspend fun registry(): RegistryDto =
            cached ?: mutex.withLock {
                cached ?: withContext(ioDispatcher) { RegistryJson.decode(source.readText(REGISTRY_ASSET)) }
                    .also { dto ->
                        check(dto.defaultPlanId == DEFAULT_PLAN_ID) {
                            "registry defaultPlanId '${dto.defaultPlanId}' != build constant '$DEFAULT_PLAN_ID'"
                        }
                        check(dto.plans.any { it.id == DEFAULT_PLAN_ID }) {
                            "registry must contain the default plan '$DEFAULT_PLAN_ID'"
                        }
                        cached = dto
                    }
            }

        companion object {
            const val REGISTRY_ASSET = "plans/registry.json"

            /** The flagship default plan id (D-ALT-1). The migration's `plan_id` stamp equals this (Sprint B). */
            const val DEFAULT_PLAN_ID = "bible_companion"
        }
    }
