package com.jpillion.dailyreadingplanner.di

import com.jpillion.dailyreadingplanner.widget.GlanceWidgetRefresher
import com.jpillion.dailyreadingplanner.widget.WidgetRefresher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the widget refresh seam (D-S7-2) to its Glance implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {
    @Binds
    @Singleton
    abstract fun bindWidgetRefresher(impl: GlanceWidgetRefresher): WidgetRefresher
}
