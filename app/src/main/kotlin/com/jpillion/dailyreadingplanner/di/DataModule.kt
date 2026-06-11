package com.jpillion.dailyreadingplanner.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Data-source providers (ESpec §9). Sprint 3 adds: Room ProgressDatabase + ReadingProgressDao,
 * DataStore<Preferences>, ReadingPlanAssetLoader, BookCatalog/BlbAbbreviations.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule
