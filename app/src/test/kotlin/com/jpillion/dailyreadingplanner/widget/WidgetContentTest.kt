package com.jpillion.dailyreadingplanner.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import com.jpillion.dailyreadingplanner.MainActivity
import com.jpillion.dailyreadingplanner.domain.model.DayReadings
import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.domain.portion
import com.jpillion.dailyreadingplanner.domain.threePortions
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Pins the widget UI (ESpec §7) state-by-state on the JVM via Glance's unit-test rig:
 * the same DayReadings the app renders, including the Feb 29 (D1) and degrade-don't-crash
 * (D-S4-3) states, plus the single whole-surface tap into MainActivity (FR-8).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetContentTest {
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
    fun `scheduled day renders date, all three stream titles and collapsed references`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { WidgetContent(scheduled()) }

            onNode(hasTestTag("widget-date")).assert(hasText("Wed, Jun 10"))
            onNode(hasText("Law & History")).assertExists()
            onNode(hasText("Psalms & Prophecy")).assertExists()
            onNode(hasText("New Testament")).assertExists()
            onNode(hasText("Genesis 1–2")).assertExists()
            onNode(hasText("Psalms 1–2")).assertExists()
            onNode(hasText("Matthew 1–2")).assertExists()
        }

    @Test
    fun `unread day shows three unread marks and no day-complete badge`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { WidgetContent(scheduled()) }

            onAllNodes(hasTestTag("widget-mark-unread")).assertCountEquals(3)
            onAllNodes(hasTestTag("widget-mark-read")).assertCountEquals(0)
            onAllNodes(hasTestTag("widget-day-complete")).assertCountEquals(0)
        }

    @Test
    fun `partially read day marks exactly the read stream`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { WidgetContent(scheduled(Stream.PSALMS_AND_PROPHECY)) }

            onAllNodes(hasTestTag("widget-mark-read")).assertCountEquals(1)
            onAllNodes(hasTestTag("widget-mark-unread")).assertCountEquals(2)
            onAllNodes(hasTestTag("widget-day-complete")).assertCountEquals(0)
            // The read/unread state is spoken, not just drawn (a11y).
            onNode(hasContentDescriptionEqualTo("Psalms & Prophecy, Psalms 1–2, Read"))
                .assertExists()
            onNode(hasContentDescriptionEqualTo("Law & History, Genesis 1–2, Not read yet"))
                .assertExists()
        }

    @Test
    fun `complete day shows the all-done badge and three read marks`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { WidgetContent(scheduled(*Stream.entries.toTypedArray())) }

            onNode(hasTestTag("widget-day-complete")).assert(hasText("All readings done"))
            onAllNodes(hasTestTag("widget-mark-read")).assertCountEquals(3)
        }

    @Test
    fun `multi-book portion renders both books in one row`() =
        runGlanceAppWidgetUnitTest {
            // Jun 19 / Dec 19: 2 John + 3 John are ONE portion — never assume one book.
            val day =
                TodayWidgetState.Loaded(
                    DayReadings.Scheduled(
                        date = LocalDate.of(2026, 6, 19),
                        readings =
                            listOf(
                                ReadingStatus(
                                    portion(Stream.NEW_TESTAMENT, "2 John" to 1, "3 John" to 1),
                                    isRead = false,
                                ),
                            ),
                        dayComplete = false,
                    ),
                )
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { WidgetContent(day) }

            onNode(hasText("2 John 1; 3 John 1")).assertExists()
        }

    @Test
    fun `Feb 29 shows the no-scheduled-readings state with no marks`() =
        runGlanceAppWidgetUnitTest {
            val feb29 = TodayWidgetState.Loaded(DayReadings.NoScheduledReadings(LocalDate.of(2028, 2, 29)))
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { WidgetContent(feb29) }

            onNode(hasTestTag("widget-no-readings"))
                .assert(hasText("No scheduled readings for Feb 29th"))
            onAllNodes(hasTestTag("widget-mark-read")).assertCountEquals(0)
            onAllNodes(hasTestTag("widget-mark-unread")).assertCountEquals(0)
        }

    @Test
    fun `load failure degrades to the fallback message, never a crash`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { WidgetContent(TodayWidgetState.LoadFailed) }

            onNode(hasTestTag("widget-error"))
                .assert(hasText("Couldn't load readings — tap to open the app"))
        }

    @Test
    fun `the whole widget surface is one tap target that opens MainActivity`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { WidgetContent(scheduled()) }

            onNode(hasTestTag("widget-root"))
                .assert(hasStartActivityClickAction<MainActivity>())
        }
}
