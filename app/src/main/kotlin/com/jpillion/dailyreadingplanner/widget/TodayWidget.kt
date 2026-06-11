package com.jpillion.dailyreadingplanner.widget

import android.content.Context
import android.os.Build
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.material3.ColorProviders
import com.jpillion.dailyreadingplanner.domain.GetDayReadingsUseCase
import com.jpillion.dailyreadingplanner.ui.theme.DarkColorScheme
import com.jpillion.dailyreadingplanner.ui.theme.LightColorScheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

/**
 * Glance receivers aren't constructor-injectable, so the widget reaches the Sprint 3
 * engine through a Hilt entry point (ESpec §7) — the *same* use case the app UI uses;
 * no forked logic.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TodayWidgetEntryPoint {
    fun getDayReadings(): GetDayReadingsUseCase

    fun clock(): Clock
}

/**
 * Home-screen widget: today's three readings + completion at a glance (PRD FR-8, ESpec §7).
 *
 * Renders a *snapshot* (D-S7-1): each update resolves "today" from the injected [Clock]
 * (Feb 29 → the no-readings state, decision D1) and takes `first()` of the readings flow.
 * Freshness is owned by the explicit refresh contract — [WidgetRefresher] on app resume and
 * progress change, plus the 30-min system periodic backstop (D9); no midnight alarm in V1
 * (accepted risk R6). A failed plan load degrades to a tappable fallback, never a crash
 * (mirrors D-S4-3).
 */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val entryPoint =
            EntryPointAccessors.fromApplication(context, TodayWidgetEntryPoint::class.java)
        val today = LocalDate.now(entryPoint.clock())
        val state =
            runCatching<TodayWidgetState> {
                TodayWidgetState.Loaded(entryPoint.getDayReadings()(today).first())
            }.getOrDefault(TodayWidgetState.LoadFailed)

        provideContent {
            // D-S7-3: the widget follows the *system* light/dark (dynamic M3 on API 31+,
            // the app's static schemes below) — launcher surfaces match the launcher, so
            // the in-app ThemeMode override deliberately does not apply here.
            val colors =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    GlanceTheme.colors
                } else {
                    ColorProviders(light = LightColorScheme, dark = DarkColorScheme)
                }
            GlanceTheme(colors = colors) {
                WidgetContent(state)
            }
        }
    }
}
