package com.jpillion.dailyreadingplanner.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The opportunistic half of the widget refresh contract (D9, ESpec §7): callers invoke
 * this on app resume and after progress mutations; the 30-min system periodic update is
 * the only other freshness source in V1 (no midnight alarm — accepted risk R6).
 *
 * An interface so UI-layer callers (ViewModel, Activity) stay testable on the JVM without
 * touching real Glance machinery (D-S7-2).
 */
interface WidgetRefresher {
    suspend fun refreshTodayWidget()
}

/** Production implementation: re-composes every pinned [TodayWidget] instance. */
class GlanceWidgetRefresher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : WidgetRefresher {
        override suspend fun refreshTodayWidget() {
            TodayWidget().updateAll(context)
        }
    }
