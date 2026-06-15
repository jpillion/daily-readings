package com.jpillion.dailyreadingplanner.di

import com.jpillion.dailyreadingplanner.update.InAppUpdateManager
import com.jpillion.dailyreadingplanner.update.PlayInAppUpdateManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped

/**
 * S-L (D-L-1) — binds the Play-backed [PlayInAppUpdateManager] as the [InAppUpdateManager] seam.
 * Installed in [ActivityRetainedComponent] so the manager and the [InAppUpdateState] it writes to
 * share one `@ActivityRetainedScoped` instance per activity, surviving configuration change.
 */
@Module
@InstallIn(ActivityRetainedComponent::class)
abstract class UpdateModule {
    @Binds
    @ActivityRetainedScoped
    abstract fun bindInAppUpdateManager(impl: PlayInAppUpdateManager): InAppUpdateManager
}
