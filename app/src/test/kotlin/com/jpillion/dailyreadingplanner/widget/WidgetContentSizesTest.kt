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
import com.jpillion.dailyreadingplanner.domain.portion
import com.jpillion.dailyreadingplanner.domain.threePortions
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * S8 D-S8-1, refined S9 D-S9-2, redesigned S14 D-S14-2: the responsive tiers. The three
 * readings are listed at EVERY size (owner feedback — completion is never the focus):
 * LARGE (needs room in both axes) keeps the full layout; MEDIUM (any MEDIUM-or-wider
 * width, including the wide-short size points) drops stream titles but keeps full
 * references — the date header only when the height affords it; SMALL (1x2) and TINY
 * (1x1) show abbreviated references (D-S9-1) with marks — SMALL keeps the date, TINY
 * drops it. Every size keeps the Feb 29 / load-failure states, the single tap target,
 * and full spoken descriptions (never glyphs or abbreviations alone).
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

    private val twoBookDay =
        TodayWidgetState.Loaded(
            DayReadings.Scheduled(
                date = LocalDate.of(2026, 6, 19),
                readings =
                    listOf(
                        ReadingStatus(portion(Stream.LAW_AND_HISTORY, "Deuteronomy" to 33, "Deuteronomy" to 34), false),
                        ReadingStatus(portion(Stream.PSALMS_AND_PROPHECY, "Isaiah" to 63), false),
                        ReadingStatus(portion(Stream.NEW_TESTAMENT, "2 John" to 1, "3 John" to 1), false),
                    ),
                dayComplete = false,
            ),
        )

    @Test
    fun `tier chooser maps width and height to layouts with exact bounds`() {
        assertThat(layoutFor(DpSize(250.dp, 110.dp))).isEqualTo(WidgetLayout.LARGE)
        assertThat(layoutFor(LARGE_SIZE)).isEqualTo(WidgetLayout.LARGE)
        // S14 D-S14-2: LARGE needs room in BOTH axes — a wide-but-short widget is MEDIUM.
        assertThat(layoutFor(DpSize(250.dp, 101.dp))).isEqualTo(WidgetLayout.MEDIUM)
        assertThat(layoutFor(WIDE_SHORT_SIZE)).isEqualTo(WidgetLayout.MEDIUM)
        assertThat(layoutFor(MEDIUM_SHORT_SIZE)).isEqualTo(WidgetLayout.MEDIUM)
        assertThat(layoutFor(DpSize(202.dp, 110.dp))).isEqualTo(WidgetLayout.MEDIUM)
        assertThat(layoutFor(MEDIUM_SIZE)).isEqualTo(WidgetLayout.MEDIUM)
        // Below MEDIUM width, the height decides: >= 102dp is 1x2 (SMALL), under it 1x1 (TINY).
        assertThat(layoutFor(DpSize(129.dp, 110.dp))).isEqualTo(WidgetLayout.SMALL)
        assertThat(layoutFor(SMALL_SIZE)).isEqualTo(WidgetLayout.SMALL)
        assertThat(layoutFor(DpSize(57.dp, 101.dp))).isEqualTo(WidgetLayout.TINY)
        assertThat(layoutFor(TINY_SIZE)).isEqualTo(WidgetLayout.TINY)
        assertThat(layoutFor(DpSize(40.dp, 40.dp))).isEqualTo(WidgetLayout.TINY)
    }

    @Test
    fun `the date header is a height decision - tall tiers keep it, short tiers spend the room on readings`() {
        assertThat(showsHeader(WidgetLayout.LARGE, LARGE_SIZE)).isTrue()
        assertThat(showsHeader(WidgetLayout.MEDIUM, MEDIUM_SIZE)).isTrue()
        assertThat(showsHeader(WidgetLayout.MEDIUM, MEDIUM_SHORT_SIZE)).isFalse()
        assertThat(showsHeader(WidgetLayout.MEDIUM, WIDE_SHORT_SIZE)).isFalse()
        assertThat(showsHeader(WidgetLayout.SMALL, SMALL_SIZE)).isTrue()
        assertThat(showsHeader(WidgetLayout.TINY, TINY_SIZE)).isFalse()
    }

    @Test
    fun `every declared responsive size point resolves to its intended tier`() {
        // The declared set is what the launcher picks from (D-S14-2): each entry must land
        // on the tier it was added for, or a resize renders the wrong design.
        assertThat(RESPONSIVE_SIZES.associateWith { layoutFor(it) })
            .containsExactly(
                TINY_SIZE,
                WidgetLayout.TINY,
                SMALL_SIZE,
                WidgetLayout.SMALL,
                MEDIUM_SHORT_SIZE,
                WidgetLayout.MEDIUM,
                MEDIUM_SIZE,
                WidgetLayout.MEDIUM,
                WIDE_SHORT_SIZE,
                WidgetLayout.MEDIUM,
                LARGE_SIZE,
                WidgetLayout.LARGE,
            )
    }

    @Test
    fun `wide-short widget keeps full references and marks but drops the date header`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            setAppWidgetSize(WIDE_SHORT_SIZE)
            provideComposable { WidgetContent(scheduled(Stream.LAW_AND_HISTORY)) }

            onNode(hasText("Genesis 1–2")).assertExists()
            onNode(hasText("Psalms 1–2")).assertExists()
            onNode(hasText("Matthew 1–2")).assertExists()
            onAllNodes(hasTestTag("widget-mark-read")).assertCountEquals(1)
            onAllNodes(hasTestTag("widget-mark-unread")).assertCountEquals(2)
            onAllNodes(hasTestTag("widget-date")).assertCountEquals(0)
        }

    @Test
    fun `headerless complete day still speaks its state via the compact badge`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            setAppWidgetSize(TINY_SIZE)
            provideComposable { WidgetContent(scheduled(*Stream.entries.toTypedArray())) }

            onNode(hasTestTag("widget-day-complete")).assertExists()
            onNode(hasContentDescriptionEqualTo("All readings done")).assertExists()
        }

    @Test
    fun `medium drops stream titles but keeps marked full references`() =
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
    fun `small lists the three abbreviated readings with marks and the date - no count`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            setAppWidgetSize(SMALL_SIZE)
            provideComposable { WidgetContent(scheduled(Stream.LAW_AND_HISTORY, Stream.NEW_TESTAMENT)) }

            onNode(hasTestTag("widget-date")).assert(hasText("Jun 10"))
            onNode(hasText("Gen 1–2")).assertExists()
            onNode(hasText("Psa 1–2")).assertExists()
            onNode(hasText("Mat 1–2")).assertExists()
            onAllNodes(hasTestTag("widget-mark-read")).assertCountEquals(2)
            onAllNodes(hasTestTag("widget-mark-unread")).assertCountEquals(1)
            // Completion count is gone (S9 owner feedback) and a11y speaks full names.
            onAllNodes(hasTestTag("widget-count")).assertCountEquals(0)
            onAllNodes(hasText("2/3")).assertCountEquals(0)
            onNode(hasContentDescriptionEqualTo("Psalms & Prophecy, Psalms 1–2, Not read yet")).assertExists()
        }

    @Test
    fun `small complete day keeps the readings and adds only a small spoken badge`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            setAppWidgetSize(SMALL_SIZE)
            provideComposable { WidgetContent(scheduled(*Stream.entries.toTypedArray())) }

            onNode(hasText("Gen 1–2")).assertExists()
            onAllNodes(hasTestTag("widget-mark-read")).assertCountEquals(3)
            onNode(hasTestTag("widget-day-complete")).assertExists()
            onNode(hasContentDescriptionEqualTo("All readings done")).assertExists()
        }

    @Test
    fun `tiny lists the three abbreviated readings with marks and no date`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            setAppWidgetSize(TINY_SIZE)
            provideComposable { WidgetContent(scheduled(Stream.PSALMS_AND_PROPHECY)) }

            onNode(hasText("Gen 1–2")).assertExists()
            onNode(hasText("Psa 1–2")).assertExists()
            onNode(hasText("Mat 1–2")).assertExists()
            onAllNodes(hasTestTag("widget-mark-read")).assertCountEquals(1)
            onAllNodes(hasTestTag("widget-mark-unread")).assertCountEquals(2)
            onAllNodes(hasTestTag("widget-date")).assertCountEquals(0)
            onAllNodes(hasTestTag("widget-count")).assertCountEquals(0)
        }

    @Test
    fun `tiny abbreviates the two-book day sensibly - Jun 19 case`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            setAppWidgetSize(TINY_SIZE)
            provideComposable { WidgetContent(twoBookDay) }

            onNode(hasText("Deu 33–34")).assertExists()
            onNode(hasText("Isa 63")).assertExists()
            onNode(hasText("2Jo 1; 3Jo 1")).assertExists()
            // The spoken description stays canonical, never abbreviated.
            onNode(hasContentDescriptionEqualTo("New Testament, 2 John 1; 3 John 1, Not read yet")).assertExists()
        }

    @Test
    fun `tiny keeps the Feb 29 state, spoken`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            setAppWidgetSize(TINY_SIZE)
            provideComposable {
                WidgetContent(TodayWidgetState.Loaded(DayReadings.NoScheduledReadings(LocalDate.of(2028, 2, 29))))
            }

            onNode(hasTestTag("widget-no-readings")).assertExists()
            onNode(hasContentDescriptionEqualTo("No scheduled readings for Feb 29th")).assertExists()
        }

    @Test
    fun `small keeps the dated Feb 29 state, spoken`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            setAppWidgetSize(SMALL_SIZE)
            provideComposable {
                WidgetContent(TodayWidgetState.Loaded(DayReadings.NoScheduledReadings(LocalDate.of(2028, 2, 29))))
            }

            onNode(hasTestTag("widget-date")).assert(hasText("Feb 29"))
            onNode(hasTestTag("widget-no-readings")).assertExists()
            onNode(hasContentDescriptionEqualTo("No scheduled readings for Feb 29th")).assertExists()
        }

    @Test
    fun `tiny load failure degrades, spoken, and stays tappable`() =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            setAppWidgetSize(TINY_SIZE)
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
