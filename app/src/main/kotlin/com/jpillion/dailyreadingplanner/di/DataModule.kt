package com.jpillion.dailyreadingplanner.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.jpillion.dailyreadingplanner.data.plan.PlanAssetSource
import com.jpillion.dailyreadingplanner.data.progress.ProgressDatabase
import com.jpillion.dailyreadingplanner.data.progress.ReadingProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/** Data-source providers (ESpec §9): Room, DataStore, and the plan-asset source. */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    private const val PROGRESS_DB = "progress.db"
    private const val SETTINGS_STORE = "settings"

    @Provides
    @Singleton
    fun provideProgressDatabase(
        @ApplicationContext context: Context,
    ): ProgressDatabase = Room.databaseBuilder(context, ProgressDatabase::class.java, PROGRESS_DB).build()

    @Provides
    fun provideReadingProgressDao(database: ProgressDatabase): ReadingProgressDao = database.readingProgressDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(ioDispatcher + SupervisorJob()),
        ) { context.preferencesDataStoreFile(SETTINGS_STORE) }

    @Provides
    @Singleton
    fun providePlanAssetSource(
        @ApplicationContext context: Context,
    ): PlanAssetSource =
        PlanAssetSource { assetPath ->
            context.assets
                .open(assetPath)
                .bufferedReader()
                .use { it.readText() }
        }
}
