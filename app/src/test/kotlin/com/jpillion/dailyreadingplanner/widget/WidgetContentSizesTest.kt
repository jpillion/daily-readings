package com.jpillion.dailyreadingplanner.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.MainActivity
import com.jpillion.dailyreadingplanner.domain.model.DayReadings
import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.domain.threePortions
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * S8 (D-S8-1): the responsive breakpoints. LARGE keeps Sprint 7's full layout (pinned by
 * WidgetContentTest at the rig's default size); MEDIUM drops stream titles but keeps marked
 * references; SMALL condenses to date + "n/3" completion. Every size keeps the states and
 * the single tap target, and speaks its state (never glyphs alone).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetContentSizesTest {
    private val today = LocalDate.of(2026, 6, 10)

    private fun scheduled(vararg read: Stream) =
        TodayWidgetState.Loaded(
            DayReadings.Scheduled(
                date = today,
                readings = threePortions.map { ReadingStatus(it, it.stream in read) },
                dayComplete = Stream.entries.all { it in read },
            ),
        )

    @Test
    fun `breakpoint chooser maps widths to layouts with exact bounds`() {
        assertThat(layoutFor(DpSize(250.dp, 110.dp))).isEqualTo(WidgetLayout.LARGE)
        assertThat(layoutFor(LARGE_SIZE)).isEqualTo(WidgetLayout.LARGE)
        assertThat(layoutFor(DpSize(202.dp, 110.dp))).isEqualTo(WidgetLayout.MEDIUM)
        assertThat(layoutFor(MEDIUM_SIZE)).isEqualTo(WidgetLayout.MEDIUM)
        assertThat(layoutFor(DpSize(129.dp, 110.dp))).isEqualTo(WidgetLayout.SMALL)
        assertThat(layoutFor(SMALL_SIZE)).isEqualTo(WidgetLayout.SMALL)
        assertThat(layoutFor(DpSize(40.dp, 110.dp))).isEqualTo(WidgetLayout.SMALL)
    }

    @Test
    fun `medium drops stream titles but keeps marked references`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            setAppWidgetSize(MEDIUM_SIZE)
            provideComposable { WidgetContent(scheduled(Stream.PSALMS_AND_PROPHECY)) }

            onAllNodes(hasText("Law & History")).assertCountEquals(0)
            onAllNodes(hasText("Psalms & Prophecy")).assertCountEquals(0)
            onNode(hasText("Genesis 1–2")).assertExists()
            onNode(hasText("Psalms 1–2")).assertExists()
            onNode(hasText("Matthew 1–2")).assertExists()
            onAllNodes(hasTestTag("widget-mark-read")).assertCountEquals(1)
            onAllNodes(hasTestTag("widget-mark-unread")).assertCountEquals(2)
            // The a11y description still carries the stream name at every size.
            onNode(hasContentDescriptionEqualTo("Psalms & Prophecy, Psalms 1–2, Read")).assertExists()
        }

    @Test
    fun `medium complete day shows the condensed badge with a spoken state`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            setAppWidgetSize(MEDIUM_SIZE)
            provideComposable { WidgetContent(scheduled(*Stream.entries.toTypedArray())) }

            onNode(hasTestTag("widget-day-complete")).assertExists()
            onNode(hasContentDescriptionEqualTo("All readings done")).assertExists()
        }

    @Test
    fun `small shows the date and a spoken n-of-3 completion summary, no references`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            setAppWidgetSize(SMALL_SIZE)
            provideComposable { WidgetContent(scheduled(Stream.LAW_AND_HISTORY, Stream.NEW_TESTAMENT)) }

            onNode(hasTestTag("widget-date")).assert(hasText("Jun 10"))
            onNode(hasTestTag("widget-count")).assert(hasText("2/3"))
            onNode(hasContentDescriptionEqualTo("2 of 3 readings done")).assertExists()
            onAllNodes(hasText("Genesis 1–2")).assertCountEquals(0)
            onAllNodes(hasTestTag("widget-mark-unread")).assertCountEquals(0)
        }

    @Test
    fun `small complete day reads 3 of 3 with the done mark`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            setAppWidgetSize(SMALL_SIZE)
            provideComposable { WidgetContent(scheduled(*Stream.entries.toTypedArray())) }

            onNode(hasTestTag("widget-count")).assert(hasText("3/3"))
            onNode(hasTestTag("widget-day-complete")).assertExists()
        }

    @Test
    fun `small keeps the Feb 29 and load-failure states, spoken`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            setAppWidgetSize(SMALL_SIZE)
            provideComposable {
                WidgetContent(TodayWidgetState.Loaded(DayReadings.NoScheduledReadings(LocalDate.of(2028, 2, 29))))
            }

            onNode(hasTestTag("widget-no-readings")).assertExists()
            onNode(hasContentDescriptionEqualTo("No scheduled readings for Feb 29th")).assertExists()
        }

    @Test
    fun `small load failure degrades, spoken, and stays tappable`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            setAppWidgetSize(SMALL_SIZE)
            provideComposable { WidgetContent(TodayWidgetState.LoadFailed) }

            onNode(hasTestTag("widget-error")).assertExists()
            onNode(hasContentDescriptionEqualTo("Couldn't load readings — tap to open the app")).assertExists()
            onNode(hasTestTag("widget-root")).assert(hasStartActivityClickAction<MainActivity>())
        }

    @Test
    fun `large at an explicit size still renders the full layout`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            setAppWidgetSize(LARGE_SIZE)
            provideComposable { WidgetContent(scheduled()) }

            onNode(hasText("Law & History")).assertExists()
            onNode(hasText("Genesis 1–2")).assertExists()
            onAllNodes(hasTestTag("widget-mark-unread")).assertCountEquals(3)
        }
}
