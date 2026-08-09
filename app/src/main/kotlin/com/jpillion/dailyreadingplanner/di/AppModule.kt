package com.jpillion.dailyreadingplanner.di

import com.jpillion.dailyreadingplanner.platform.AndroidDateTextFormatter
import com.jpillion.dailyreadingplanner.platform.DateTextFormatter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/** App-wide primitives (ESpec §9). The injectable [Clock] makes "today" swappable in tests. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    /**
     * p1-01: the localized date/time/number text seam. Production code depends on the interface;
     * `p2-03` lifts it to `shared/platform` and supplies an iOS actual.
     */
    @Provides
    @Singleton
    fun provideDateTextFormatter(): DateTextFormatter = AndroidDateTextFormatter
}
