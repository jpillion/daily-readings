package com.jpillion.dailyreadingplanner.di

import android.content.Context
import androidx.room.Room
import com.jpillion.dailyreadingplanner.bible.data.BibleDatabase
import com.jpillion.dailyreadingplanner.bible.data.RoomBibleTextSource
import com.jpillion.dailyreadingplanner.bible.data.VerseDao
import com.jpillion.dailyreadingplanner.bible.domain.BibleTextSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * VA-T8 / ESpec-v3 §3 — Hilt wiring for the `bible/` feature area.
 *
 * Provides the read-only [BibleDatabase] (opened from the bundled asset via `createFromAsset`)
 * and its [VerseDao]. The seam binding (`RoomBibleTextSource → BibleTextSource`) lands in
 * Sprint B; this module establishes the asset open path and the two-DB coexistence (the bible
 * DB uses its own name `bible.db`, never sharing a connection with `progress.db`, D-V3-15).
 *
 * Net new runtime deps: ZERO (Room is already present for ProgressDatabase). Asset content
 * re-copy (D-V3-8) is orchestrated by
 * [com.jpillion.dailyreadingplanner.bible.data.BibleAssetVersion] before the builder runs;
 * that wiring lands with the startup hook in a later sprint (the compare logic is unit-pinned
 * in Sprint A).
 */
@Module
@InstallIn(SingletonComponent::class)
object BibleModule {
    private const val BIBLE_DB = "bible.db"
    private const val BIBLE_ASSET = "bible/bible.db"

    @Provides
    @Singleton
    fun provideBibleDatabase(
        @ApplicationContext context: Context,
    ): BibleDatabase =
        Room
            .databaseBuilder(context, BibleDatabase::class.java, BIBLE_DB)
            .createFromAsset(BIBLE_ASSET)
            .fallbackToDestructiveMigration(false)
            .build()

    @Provides
    fun provideVerseDao(database: BibleDatabase): VerseDao = database.verseDao()
}

/**
 * VB-T2 — binds the seam. Separate abstract module because [BibleModule] is an `object`
 * (provides) and `@Binds` requires an abstract method. [RoomBibleTextSource] is the only
 * implementation; the domain layer injects [BibleTextSource] alone (the Room types stay in
 * `bible/data`).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BibleBindsModule {
    @Binds
    @Singleton
    abstract fun bindBibleTextSource(impl: RoomBibleTextSource): BibleTextSource
}
