package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.core.date.ScheduleDateResolver
import com.jpillion.dailyreadingplanner.domain.model.PlanDescriptor
import com.jpillion.dailyreadingplanner.domain.model.StreamDescriptor
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * SC-T10 (Alt Sprint C) — N-correctness through the ONE classifier seam (D-ALT-6, R-STREAK-5).
 *
 * The Bible-Companion (N=3) behavior is pinned by the existing suite UNCHANGED (parity). This gate
 * proves the *generalization* at the two edges the BC suite cannot reach: M'Cheyne N=4 and a
 * synthetic single-stream N=1. Completion always flows THROUGH `DayCompletionClassifier` with the
 * active descriptor's `streamCount` — never a forked, per-plan predicate, never a hard-coded 3. Each
 * test below is also the home of a load-bearing mutation: "ignore the passed streamCount, hard-code
 * 3" reddens the N=4 and N=1 completion pins; "denominator stays 1095/365" reddens the denominator
 * pins; "iterate a fixed three" reddens the per-descriptor iteration pins.
 */
class NStreamCorrectnessTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC)
    private val progress = FakeProgressRepository()
    private val settings = FakeSettingsRepository()

    private fun mcheyneN4() =
        PlanDescriptor(
            planId = "mcheyne",
            name = "M'Cheyne",
            anchoring = "DATE",
            dayCount = 365,
            streams =
                listOf(
                    StreamDescriptor(1, "Family — Old Testament"),
                    StreamDescriptor(2, "Family — Gospels"),
                    StreamDescriptor(3, "Personal — Psalms & Prophets"),
                    StreamDescriptor(4, "Personal — Epistles"),
                ),
        )

    private fun chronologicalN1() =
        PlanDescriptor(
            planId = "chronological",
            name = "Chronological",
            anchoring = "DATE",
            dayCount = 365,
            streams = listOf(StreamDescriptor(1, "The Whole Bible")),
        )

    private fun stats(descriptor: PlanDescriptor) =
        GetReadingStatsUseCase(
            DayCompletionClassifier(ScheduleDateResolver()),
            progress,
            settings,
            FakeActivePlanRepository(descriptor = descriptor, planId = descriptor.planId),
            clock,
        )

    private fun yearStrips(descriptor: PlanDescriptor) =
        GetYearStripsUseCase(
            DayCompletionClassifier(ScheduleDateResolver()),
            progress,
            settings,
            FakeActivePlanRepository(descriptor = descriptor, planId = descriptor.planId),
            clock,
        )

    private fun june(day: Int) = LocalDate.of(2026, 6, day)

    // --- N=4 (M'Cheyne): COMPLETE requires FOUR marks, through the classifier ---

    @Test
    fun `N4 - four marks on a past day is COMPLETE and extends the streak`() =
        runTest {
            // June 8 + 9 fully read (4 streams each); June 10 is today (grace). A clean 2-day streak
            // ONLY if the classifier's threshold is the descriptor's 4, not a hard-coded 3.
            progress.setWholeDay(june(8), listOf(1, 2, 3, 4), isRead = true)
            progress.setWholeDay(june(9), listOf(1, 2, 3, 4), isRead = true)
            val s = stats(mcheyneN4())().first()
            assertThat(s.currentStreakDays).isEqualTo(2)
            assertThat(s.longestStreakDays).isEqualTo(2)
        }

    @Test
    fun `N4 - three of four marks on a past day is NOT complete - breaks the streak`() =
        runTest {
            // MUTATION ("hard-code 3"): with a real 4-stream threshold, June 9 (3 of 4) is MISSED, so
            // the streak resets and the current streak is 0. If the classifier ignored streamCount and
            // used 3, June 9 would wrongly read COMPLETE and the streak would be 2 — this pin reddens.
            progress.setWholeDay(june(8), listOf(1, 2, 3, 4), isRead = true)
            progress.setWholeDay(june(9), listOf(1, 2, 3), isRead = true) // only 3 of 4
            val s = stats(mcheyneN4())().first()
            assertThat(s.currentStreakDays).isEqualTo(0)
            assertThat(s.longestStreakDays).isEqualTo(1) // only June 8 was a complete day
        }

    @Test
    fun `N4 - denominators are dayCount times four and dayCount - and there are four stream rows`() =
        runTest {
            val s = stats(mcheyneN4())().first()
            assertThat(s.yearTotalReadings).isEqualTo(1_460) // 365 * 4, NOT 1095
            assertThat(s.streamTotalDays).isEqualTo(365)
            assertThat(s.streams.map { it.number }).containsExactly(1, 2, 3, 4).inOrder()
            assertThat(s.streamReadCounts.keys).containsExactly(1, 2, 3, 4)
        }

    @Test
    fun `N4 - the year strips render one strip per declared stream - keyed by number`() =
        runTest {
            val strips = yearStrips(mcheyneN4())().first()
            assertThat(strips.dayStates.keys).containsExactly(1, 2, 3, 4)
            assertThat(strips.streams.map { it.number }).containsExactly(1, 2, 3, 4).inOrder()
        }

    // --- N=1 (synthetic chronological): COMPLETE requires ONE mark ---

    @Test
    fun `N1 - a single mark on a past day is COMPLETE`() =
        runTest {
            // One reading per day: marking the one stream IS a complete day. June 8 + 9 = a 2-day
            // streak. If the classifier hard-coded 3, a single mark could never be COMPLETE here.
            progress.setRead(june(8), 1, isRead = true)
            progress.setRead(june(9), 1, isRead = true)
            val s = stats(chronologicalN1())().first()
            assertThat(s.currentStreakDays).isEqualTo(2)
        }

    @Test
    fun `N1 - denominators are dayCount times one and there is exactly one stream`() =
        runTest {
            val s = stats(chronologicalN1())().first()
            assertThat(s.yearTotalReadings).isEqualTo(365) // 365 * 1
            assertThat(s.streamTotalDays).isEqualTo(365)
            assertThat(s.streams).hasSize(1)
            assertThat(s.streamReadCounts.keys).containsExactly(1)
        }

    @Test
    fun `N1 - the year strips render exactly one strip`() =
        runTest {
            val strips = yearStrips(chronologicalN1())().first()
            assertThat(strips.dayStates.keys).containsExactly(1)
        }

    @Test
    fun `the classifier itself takes the threshold as a parameter - not a constant`() {
        // Direct seam pin (D-ALT-6): the SAME classifier instance classifies the SAME readCount
        // differently per streamCount — proof generality flows through the one predicate, not a fork.
        val classifier = DayCompletionClassifier(ScheduleDateResolver())
        val pastDay = june(1)
        val today = june(10)
        // 3 marks: COMPLETE for N=3, but only MISSED for N=4 (one short).
        assertThat(classifier.classify(pastDay, readCount = 3, streamCount = 3, today, trackingStart = null))
            .isEqualTo(com.jpillion.dailyreadingplanner.domain.model.DayCompletion.COMPLETE)
        assertThat(classifier.classify(pastDay, readCount = 3, streamCount = 4, today, trackingStart = null))
            .isEqualTo(com.jpillion.dailyreadingplanner.domain.model.DayCompletion.MISSED)
        // 1 mark: COMPLETE for N=1, MISSED for N=3.
        assertThat(classifier.classify(pastDay, readCount = 1, streamCount = 1, today, trackingStart = null))
            .isEqualTo(com.jpillion.dailyreadingplanner.domain.model.DayCompletion.COMPLETE)
        assertThat(classifier.classify(pastDay, readCount = 1, streamCount = 3, today, trackingStart = null))
            .isEqualTo(com.jpillion.dailyreadingplanner.domain.model.DayCompletion.MISSED)
    }

    // --- D-ALT-22/23: the stream TITLE is resolved from the active descriptor onto each reading ---

    @Test
    fun `N4 - each reading carries its plan-supplied title - resolved from the active descriptor`() =
        runTest {
            val descriptor = mcheyneN4()
            val plan =
                FakeReadingPlanRepository(
                    portions = (1..4).map { portion(it, "Genesis" to it) },
                    descriptor = descriptor,
                )
            val useCase =
                GetDayReadingsUseCase(
                    ScheduleDateResolver(),
                    plan,
                    progress,
                    FakeActivePlanRepository(descriptor = descriptor, planId = descriptor.planId),
                )
            val day = useCase(june(10)).first() as com.jpillion.dailyreadingplanner.domain.model.DayReadings.Scheduled
            assertThat(day.readings.map { it.streamTitle })
                .containsExactly(
                    "Family — Old Testament",
                    "Family — Gospels",
                    "Personal — Psalms & Prophets",
                    "Personal — Epistles",
                ).inOrder()
        }

    @Test
    fun `N1 - the single reading carries a null title - so the card renders no stream label`() =
        runTest {
            // D-ALT-23 at the SOURCE: a single-stream plan resolves a null title in the use case
            // (not just the UI). A mutation that drops the single-stream guard in titleFor reddens.
            val descriptor = chronologicalN1()
            val plan =
                FakeReadingPlanRepository(
                    portions = listOf(portion(1, "Genesis" to 1)),
                    descriptor = descriptor,
                )
            val useCase =
                GetDayReadingsUseCase(
                    ScheduleDateResolver(),
                    plan,
                    progress,
                    FakeActivePlanRepository(descriptor = descriptor, planId = descriptor.planId),
                )
            val day = useCase(june(10)).first() as com.jpillion.dailyreadingplanner.domain.model.DayReadings.Scheduled
            assertThat(day.readings).hasSize(1)
            assertThat(day.readings.single().streamTitle).isNull()
        }
}
