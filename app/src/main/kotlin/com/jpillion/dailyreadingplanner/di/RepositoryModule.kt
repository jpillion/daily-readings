package com.jpillion.dailyreadingplanner.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Repository interface bindings (ESpec §9). Sprint 3 adds @Binds for
 * ReadingPlanRepository, ProgressRepository, ThemeRepository → their Impls.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule
