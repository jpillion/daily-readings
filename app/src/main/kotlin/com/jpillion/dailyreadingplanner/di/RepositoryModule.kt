package com.jpillion.dailyreadingplanner.di

import com.jpillion.dailyreadingplanner.data.plan.ReadingPlanRepository
import com.jpillion.dailyreadingplanner.data.plan.ReadingPlanRepositoryImpl
import com.jpillion.dailyreadingplanner.data.prefs.ThemeRepository
import com.jpillion.dailyreadingplanner.data.prefs.ThemeRepositoryImpl
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Repository interface bindings (ESpec §9) — domain/UI depend on interfaces, never Impls. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindReadingPlanRepository(impl: ReadingPlanRepositoryImpl): ReadingPlanRepository

    @Binds
    @Singleton
    abstract fun bindProgressRepository(impl: ProgressRepositoryImpl): ProgressRepository

    @Binds
    @Singleton
    abstract fun bindThemeRepository(impl: ThemeRepositoryImpl): ThemeRepository
}
