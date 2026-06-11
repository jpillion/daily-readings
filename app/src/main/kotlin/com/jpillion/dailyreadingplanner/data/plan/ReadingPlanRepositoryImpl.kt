package com.jpillion.dailyreadingplanner.data.plan

import com.jpillion.dailyreadingplanner.core.date.ReadingDate
import com.jpillion.dailyreadingplanner.domain.model.Portion
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Memoizes the parsed plan: the asset is read and validated once per process (single-flight
 * under a mutex), then every lookup is an in-memory map hit (cold-start budget M2).
 */
@Singleton
class ReadingPlanRepositoryImpl
    @Inject
    constructor(
        private val loader: ReadingPlanAssetLoader,
    ) : ReadingPlanRepository {
        private val mutex = Mutex()

        @Volatile
        private var cached: Map<ReadingDate, List<Portion>>? = null

        override suspend fun portionsFor(date: ReadingDate): List<Portion> =
            checkNotNull(plan()[date]) { "no plan entry for $date — asset failed its gate?" }

        private suspend fun plan(): Map<ReadingDate, List<Portion>> =
            cached ?: mutex.withLock { cached ?: loader.load().also { cached = it } }
    }
