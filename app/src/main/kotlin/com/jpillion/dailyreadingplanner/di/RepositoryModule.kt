package com.jpillion.dailyreadingplanner.di

import com.jpillion.dailyreadingplanner.data.apps.AppInstallChecker
import com.jpillion.dailyreadingplanner.data.apps.PackageManagerAppInstallChecker
import com.jpillion.dailyreadingplanner.data.plan.ActivePlanRepository
import com.jpillion.dailyreadingplanner.data.plan.ActivePlanRepositoryImpl
import com.jpillion.dailyreadingplanner.data.plan.ReadingPlanRepository
import com.jpillion.dailyreadingplanner.data.plan.ReadingPlanRepositoryImpl
import com.jpillion.dailyreadingplanner.data.prefs.PartialReadingRepository
import com.jpillion.dailyreadingplanner.data.prefs.PartialReadingRepositoryImpl
import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepositoryImpl
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
    abstract fun bindActivePlanRepository(impl: ActivePlanRepositoryImpl): ActivePlanRepository

    @Binds
    @Singleton
    abstract fun bindProgressRepository(impl: ProgressRepositoryImpl): ProgressRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindPartialReadingRepository(impl: PartialReadingRepositoryImpl): PartialReadingRepository

    @Binds
    @Singleton
    abstract fun bindAppInstallChecker(impl: PackageManagerAppInstallChecker): AppInstallChecker
}
