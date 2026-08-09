package com.jpillion.dailyreadingplanner.di

import android.content.Context
import com.jpillion.dailyreadingplanner.platform.AndroidAppFilePaths
import com.jpillion.dailyreadingplanner.platform.AndroidDateTextFormatter
import com.jpillion.dailyreadingplanner.platform.AppFilePaths
import com.jpillion.dailyreadingplanner.platform.DateProvider
import com.jpillion.dailyreadingplanner.platform.DateTextFormatter
import com.jpillion.dailyreadingplanner.platform.SystemDateProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okio.FileSystem
import javax.inject.Singleton

/** App-wide primitives (ESpec §9). The injectable [DateProvider] makes "today" swappable in tests. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /**
     * p1-02 (ADR-0009): the one place that decides which calendar day the user is in. Replaces the
     * `java.time.Clock` this module used to provide — `java.time` does not exist on Kotlin/Native,
     * and `kotlin.time.Clock` yields an instant with no zone, so a straight swap would have put the
     * "which zone?" question at all 13 former injection sites instead of here.
     */
    @Provides
    @Singleton
    fun provideDateProvider(impl: SystemDateProvider): DateProvider = impl

    /**
     * p1-01: the localized date/time/number text seam. Production code depends on the interface;
     * `p2-03` lifts it to `shared/platform` and supplies an iOS actual.
     */
    @Provides
    @Singleton
    fun provideDateTextFormatter(impl: AndroidDateTextFormatter): DateTextFormatter = impl

    /**
     * p1-04: the "where does this app keep its private data" seam. Bound here beside the other
     * platform seam rather than in a feature module, because the answer is app-wide — its two
     * current callers both happen to be in `bible/`, but the location decision is not theirs.
     */
    @Provides
    @Singleton
    fun provideAppFilePaths(
        @ApplicationContext context: Context,
    ): AppFilePaths = AndroidAppFilePaths(context)

    /**
     * p1-04: the real filesystem. Injected rather than referenced as `FileSystem.SYSTEM` at the
     * call sites, so the file-touching data classes can be driven through an okio
     * `ForwardingFileSystem` in tests. That is what makes the asset-version delete and the 14-day
     * freshness rule directly assertable — including an entry of an EXACT age and one whose
     * last-modified time is unknown — instead of inferred from a round trip.
     *
     * (okio's `FakeFileSystem` lives in a separate artifact this project does not depend on;
     * `ForwardingFileSystem` ships in okio core, so no new dependency was needed.)
     */
    @Provides
    @Singleton
    fun provideFileSystem(): FileSystem = FileSystem.SYSTEM
}
