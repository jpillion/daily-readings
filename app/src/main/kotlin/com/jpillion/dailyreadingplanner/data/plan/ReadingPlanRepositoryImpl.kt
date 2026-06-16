package com.jpillion.dailyreadingplanner.data.plan

import com.jpillion.dailyreadingplanner.core.date.ReadingDate
import com.jpillion.dailyreadingplanner.domain.model.PlanDescriptor
import com.jpillion.dailyreadingplanner.domain.model.Portion
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Memoizes each plan's parsed descriptor and schedule PER PLAN (ESpec-alt §3.5), single-flight
 * under a mutex. Only the active plan's asset is parsed, so the default user's cold-start budget
 * (M2) is unchanged.
 *
 * The descriptor and the schedule map are cached SEPARATELY: `descriptor(planId)` parses only the
 * head (valid for ANY stream count, incl. M'Cheyne's 4), while `portionsFor(planId, …)` maps the
 * body through the `Stream` enum — valid for N<=3 plans this sprint (the enum retires in Sprint C,
 * D-ALT-5). Sprint A only ever loads the default plan's body through the live app; a 4-stream
 * plan's descriptor is reachable, its body is not, until that retirement.
 */
@Singleton
class ReadingPlanRepositoryImpl
    @Inject
    constructor(
        private val registry: PlanRegistry,
        private val loader: ReadingPlanAssetLoader,
    ) : ReadingPlanRepository {
        private val mutex = Mutex()
        private val scheduleCache = mutableMapOf<String, Map<ReadingDate, List<Portion>>>()
        private val descriptorCache = mutableMapOf<String, PlanDescriptor>()

        override suspend fun portionsFor(
            planId: String,
            date: ReadingDate,
        ): List<Portion> =
            checkNotNull(schedule(planId)[date]) {
                "no plan entry for $date in '$planId' — asset failed its gate?"
            }

        override suspend fun descriptor(planId: String): PlanDescriptor =
            descriptorCache[planId] ?: mutex.withLock {
                descriptorCache[planId] ?: loader
                    .descriptor(assetFor(planId), planId)
                    .also { descriptorCache[planId] = it }
            }

        private suspend fun schedule(planId: String): Map<ReadingDate, List<Portion>> =
            scheduleCache[planId] ?: mutex.withLock {
                scheduleCache[planId] ?: run {
                    val full = loader.loadFull(assetFor(planId), planId)
                    descriptorCache[planId] = full.descriptor
                    full.schedule.also { scheduleCache[planId] = it }
                }
            }

        private suspend fun assetFor(planId: String): String =
            checkNotNull(registry.assetFor(planId)) {
                "plan '$planId' is not in the registry — a registry entry is a build guarantee"
            }
    }
