package com.jpillion.dailyreadingplanner.data.plan

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.core.date.ScheduleDateResolver
import com.jpillion.dailyreadingplanner.data.progress.ProgressRepository
import com.jpillion.dailyreadingplanner.domain.DayCompletionClassifier
import com.jpillion.dailyreadingplanner.domain.GetDayReadingsUseCase
import com.jpillion.dailyreadingplanner.domain.GetReadingStatsUseCase
import com.jpillion.dailyreadingplanner.domain.GetYearStripsUseCase
import com.jpillion.dailyreadingplanner.domain.model.DayReadings
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Alt Sprint D (SD-T5, D-ALT-17/19) — THE end-to-end live-switch gate.
 *
 * Drives [GetDayReadingsUseCase] through the REAL [ActivePlanRepositoryImpl] over the REAL bundled
 * Bible Companion + M'Cheyne assets (via `planAssetsDir`), flipping `selected_plan` on a
 * [FakeSettingsRepository] while a single subscription stays open. Proves:
 *
 *  1. Default ⇒ the 3-stream Bible Companion descriptor + Bible-Companion-scoped progress.
 *  2. Selecting `mcheyne` re-emits LIVE (same subscription, no use-case recreation) the 4-stream
 *     M'Cheyne shape + M'Cheyne-scoped progress.
 *  3. Switching BACK restores the 3-stream Bible Companion view AND its marks intact — the switch
 *     is non-destructive by construction (per-plan progress, D-ALT-12): each plan's marks are a
 *     different partition, untouched and restored on return.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanSwitchIntegrationTest {
    private val assetsRoot =
        java.io.File(System.getProperty("planAssetsDir") ?: error("planAssetsDir not set"))
    private val source = PlanAssetSource { path -> assetsRoot.resolve(path).readText() }
    private val dispatcher = UnconfinedTestDispatcher()
    private val registry = PlanRegistry(source, dispatcher)
    private val planRepo = ReadingPlanRepositoryImpl(registry, ReadingPlanAssetLoader(source, dispatcher))

    private val settings = FakeSettingsRepository()
    private val activePlan = ActivePlanRepositoryImpl(settings, registry, planRepo)
    private val progress = PerPlanProgressFake()

    private val useCase =
        GetDayReadingsUseCase(
            resolver = ScheduleDateResolver(),
            planRepository = planRepo,
            progressRepository = progress,
            activePlanRepository = activePlan,
        )

    // CHR-5: a fixed clock pinned in 2026 so the year-keyed stats denominators are stable.
    private val clock = Clock.fixed(LocalDate.of(2026, 6, 16).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

    private val statsUseCase =
        GetReadingStatsUseCase(
            classifier = DayCompletionClassifier(ScheduleDateResolver()),
            progressRepository = progress,
            settingsRepository = settings,
            activePlanRepository = activePlan,
            clock = clock,
        )

    private val stripsUseCase =
        GetYearStripsUseCase(
            classifier = DayCompletionClassifier(ScheduleDateResolver()),
            progressRepository = progress,
            settingsRepository = settings,
            activePlanRepository = activePlan,
            clock = clock,
        )

    @Test
    fun `selecting M'Cheyne makes the day live-emit the 4-stream plan, then switching back restores Bible Companion`() =
        runTest {
            val date = LocalDate.of(2026, 1, 1)
            // A Bible-Companion mark that must survive the round-trip.
            progress.setWholeDay(date, listOf(1, 2, 3), isRead = true, planId = "bible_companion")

            useCase(date).test {
                // 1. Default = Bible Companion (3 streams), all read.
                val bc = awaitItem() as DayReadings.Scheduled
                assertThat(bc.readings).hasSize(3)
                assertThat(bc.readings.map { it.isRead }).containsExactly(true, true, true)
                assertThat(bc.dayComplete).isTrue()

                // 2. Switch to M'Cheyne — same subscription re-emits the 4-stream shape, no marks.
                settings.storedSelectedPlanId.value = "mcheyne"
                var mc = awaitItem() as DayReadings.Scheduled
                while (mc.readings.size != 4) mc = awaitItem() as DayReadings.Scheduled
                assertThat(mc.readings).hasSize(4)
                assertThat(mc.readings.map { it.isRead }).containsExactly(false, false, false, false)
                assertThat(mc.dayComplete).isFalse()

                // 3. Switch back — the Bible Companion view AND its marks are intact.
                settings.storedSelectedPlanId.value = "bible_companion"
                var back = awaitItem() as DayReadings.Scheduled
                while (back.readings.size != 3) back = awaitItem() as DayReadings.Scheduled
                assertThat(back.readings).hasSize(3)
                assertThat(back.readings.map { it.isRead }).containsExactly(true, true, true)
                assertThat(back.dayComplete).isTrue()

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `M'Cheyne marks are isolated from Bible Companion marks (non-destructive switch)`() =
        runTest {
            val date = LocalDate.of(2026, 1, 1)
            // Mark M'Cheyne stream 1 only.
            settings.storedSelectedPlanId.value = "mcheyne"
            progress.setRead(date, 1, isRead = true, planId = "mcheyne")

            useCase(date).test {
                var mc = awaitItem() as DayReadings.Scheduled
                while (mc.readings.size != 4) mc = awaitItem() as DayReadings.Scheduled
                assertThat(mc.readings.single { it.portion.streamNumber == 1 }.isRead).isTrue()
                assertThat(mc.readings.filter { it.portion.streamNumber != 1 }.map { it.isRead })
                    .containsExactly(false, false, false)

                // Bible Companion sees NONE of the M'Cheyne marks.
                settings.storedSelectedPlanId.value = "bible_companion"
                var bc = awaitItem() as DayReadings.Scheduled
                while (bc.readings.size != 3) bc = awaitItem() as DayReadings.Scheduled
                assertThat(bc.readings.map { it.isRead }).containsExactly(false, false, false)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---- CHR-5: real single-stream (N=1) integration over the bundled Chronological asset ----------

    @Test
    fun `selecting Chronological makes the day live-emit a single-stream reading with NO stream-title label`() =
        runTest {
            val date = LocalDate.of(2026, 1, 1)
            // A Bible-Companion mark that must NOT bleed into the chronological view.
            progress.setWholeDay(date, listOf(1, 2, 3), isRead = true, planId = "bible_companion")

            useCase(date).test {
                // Default = Bible Companion (3 streams).
                val bc = awaitItem() as DayReadings.Scheduled
                assertThat(bc.readings).hasSize(3)

                // Switch to the real chronological asset — same subscription re-emits the N=1 shape.
                settings.storedSelectedPlanId.value = "chronological"
                var chr = awaitItem() as DayReadings.Scheduled
                while (chr.readings.size != 1) chr = awaitItem() as DayReadings.Scheduled

                // N=1: exactly ONE portion, stream 1.
                assertThat(chr.readings).hasSize(1)
                val only = chr.readings.single()
                assertThat(only.portion.streamNumber).isEqualTo(1)
                // D-ALT-22/23 (titleFor null at N<=1): a lone reading carries NO "which stream" label.
                assertThat(only.streamTitle).isNull()
                // Chronological Day 1 = Genesis 1-3 (whole chapters); the chronological plan is unmarked
                // (the BC mark is a different partition), so the single reading is NOT read.
                assertThat(only.isRead).isFalse()
                assertThat(chr.dayComplete).isFalse()
                assertThat(only.portion.refs.map { it.book.canonicalName to it.chapter })
                    .containsExactly("Genesis" to 1, "Genesis" to 2, "Genesis" to 3)
                    .inOrder()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Chronological marks are isolated from Bible Companion and M'Cheyne (three-plan partition)`() =
        runTest {
            val date = LocalDate.of(2026, 1, 1)
            // Mark the single chronological stream read; mark BC and M'Cheyne nothing.
            settings.storedSelectedPlanId.value = "chronological"
            progress.setRead(date, 1, isRead = true, planId = "chronological")

            useCase(date).test {
                // Chronological: the one stream is read, the whole (single-stream) day is complete.
                var chr = awaitItem() as DayReadings.Scheduled
                while (chr.readings.size != 1) chr = awaitItem() as DayReadings.Scheduled
                assertThat(chr.readings.single().isRead).isTrue()
                assertThat(chr.dayComplete).isTrue()

                // Bible Companion sees NONE of the chronological mark.
                settings.storedSelectedPlanId.value = "bible_companion"
                var bc = awaitItem() as DayReadings.Scheduled
                while (bc.readings.size != 3) bc = awaitItem() as DayReadings.Scheduled
                assertThat(bc.readings.map { it.isRead }).containsExactly(false, false, false)

                // M'Cheyne sees NONE of the chronological mark either.
                settings.storedSelectedPlanId.value = "mcheyne"
                var mc = awaitItem() as DayReadings.Scheduled
                while (mc.readings.size != 4) mc = awaitItem() as DayReadings.Scheduled
                assertThat(mc.readings.map { it.isRead }).containsExactly(false, false, false, false)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `stats denominators at N=1 are 365 year-total and 365 per-stream over the real chronological descriptor`() =
        runTest {
            settings.storedSelectedPlanId.value = "chronological"
            // Two chronological days marked in 2026 (the fixed clock's year) to exercise a non-zero count.
            progress.setRead(LocalDate.of(2026, 1, 1), 1, isRead = true, planId = "chronological")
            progress.setRead(LocalDate.of(2026, 1, 2), 1, isRead = true, planId = "chronological")

            val stats = statsUseCase().first()
            // D-ALT-7: year total = dayCount(365) * streamCount(1) = 365 — NOT 1,095, no off-by-N.
            assertThat(stats.yearTotalReadings).isEqualTo(365)
            // D-ALT-8: each stream's denominator = dayCount = 365.
            assertThat(stats.streamTotalDays).isEqualTo(365)
            // Exactly one stream row (N=1), numbered 1, no empty-stream crash, no div-by-zero.
            assertThat(stats.streams.map { it.number }).containsExactly(1)
            assertThat(stats.streamReadCounts.keys).containsExactly(1)
            // The two marks land on the single stream's count (current year).
            assertThat(stats.yearReadCount).isEqualTo(2)
            assertThat(stats.streamReadCounts[1]).isEqualTo(2)
        }

    @Test
    fun `year strips at N=1 produce exactly one full-year strip over the real chronological descriptor`() =
        runTest {
            settings.storedSelectedPlanId.value = "chronological"
            progress.setRead(LocalDate.of(2026, 1, 1), 1, isRead = true, planId = "chronological")

            val strips = stripsUseCase().first()
            // Exactly one strip (N=1), keyed by stream number 1 — no empty-stream crash.
            assertThat(strips.streams.map { it.number }).containsExactly(1)
            assertThat(strips.dayStates.keys).containsExactly(1)
            // 2026 is a non-leap year: 365 segments per strip.
            assertThat(strips.dayCount).isEqualTo(365)
            assertThat(strips.dayStates.getValue(1)).hasSize(365)
        }
}

/**
 * A PER-PLAN in-memory progress store (the production [ProgressRepository] is per-plan from
 * Sprint B; the shared `FakeProgressRepository` collapses plans). Keyed by `(planId, date)`.
 * Only the methods this gate exercises carry real behavior; the rest are unused here.
 */
private class PerPlanProgressFake : ProgressRepository {
    private val marks = MutableStateFlow<Map<Pair<String, LocalDate>, Set<Int>>>(emptyMap())

    override fun streamsRead(
        date: LocalDate,
        planId: String,
    ): Flow<Set<Int>> = marks.map { it[planId to date] ?: emptySet() }

    override fun readCounts(
        start: LocalDate,
        end: LocalDate,
        planId: String,
    ): Flow<Map<LocalDate, Int>> =
        marks.map { all ->
            all
                .filterKeys { (p, d) -> p == planId && !d.isBefore(start) && !d.isAfter(end) }
                .entries
                .associate { (k, v) -> k.second to v.size }
                .filterValues { it > 0 }
        }

    override fun allReadCounts(planId: String): Flow<Map<LocalDate, Int>> =
        marks.map { all ->
            all
                .filterKeys { (p, _) -> p == planId }
                .entries
                .associate { (k, v) -> k.second to v.size }
                .filterValues { it > 0 }
        }

    override fun streamCounts(
        start: LocalDate,
        end: LocalDate,
        planId: String,
    ): Flow<Map<Int, Int>> =
        marks.map { all ->
            val counts = mutableMapOf<Int, Int>()
            all
                .filterKeys { (p, d) -> p == planId && !d.isBefore(start) && !d.isAfter(end) }
                .values
                .forEach { streams -> streams.forEach { s -> counts[s] = (counts[s] ?: 0) + 1 } }
            counts
        }

    override fun streamMarks(
        start: LocalDate,
        end: LocalDate,
        planId: String,
    ): Flow<Map<Int, Set<LocalDate>>> = MutableStateFlow(emptyMap())

    override suspend fun hasAnyMarks(): Boolean = marks.value.values.any { it.isNotEmpty() }

    override suspend fun setRead(
        date: LocalDate,
        streamNumber: Int,
        isRead: Boolean,
        planId: String,
    ) {
        val key = planId to date
        val current = marks.value[key].orEmpty()
        val next = if (isRead) current + streamNumber else current - streamNumber
        marks.value = marks.value + (key to next)
    }

    override suspend fun setWholeDay(
        date: LocalDate,
        streamNumbers: List<Int>,
        isRead: Boolean,
        planId: String,
    ) {
        val key = planId to date
        marks.value = marks.value + (key to if (isRead) streamNumbers.toSet() else emptySet())
    }

    override suspend fun clearYear(
        year: Int,
        planId: String,
    ) {
        marks.value = marks.value.filterKeys { (p, d) -> !(p == planId && d.year == year) }
    }
}
