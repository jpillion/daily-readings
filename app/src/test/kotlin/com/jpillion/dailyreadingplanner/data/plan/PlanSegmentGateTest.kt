package com.jpillion.dailyreadingplanner.data.plan

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.jpillion.dailyreadingplanner.bible.ui.reader.GlobalChapterIndex
import com.jpillion.dailyreadingplanner.bible.ui.reader.ReadingPagerIndex
import com.jpillion.dailyreadingplanner.data.plan.dto.PlanDto
import com.jpillion.dailyreadingplanner.data.plan.dto.PlanJson
import com.jpillion.dailyreadingplanner.data.plan.dto.PortionDto
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.domain.ReadingSegments
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.domain.model.ReferenceVerses
import com.jpillion.dailyreadingplanner.ui.day.ReadingFormatter
import org.junit.Test
import java.io.File

/**
 * SEG-1b — **THE SEGMENT GATE (D-SEG-2, sprint-00P)**: the data gate that makes the Ticket-1 P0
 * — a Chronological reading tap opening **Genesis 1** instead of the reading — structurally
 * non-recurring.
 *
 * Over the EXACT shipped assets (read via the `planAssetsDir` system property, the same mechanism
 * that guards the real APK files — never a fixture copy) and over EVERY portion of EVERY day of
 * ALL THREE bundled plans (`bible_companion`, `mcheyne`, `chronological`), it proves that every
 * `ReadingSegments.segmentsOf` segment — i.e. every reading card, and therefore everything a card
 * tap can hand the reader — is a contiguous ascending global-chapter run that
 * `bible/ui/reader/ReadingPagerIndex` accepts.
 *
 * Ticket 1's root cause was exactly the absence of this proof: `ReadingPagerIndex` was handed a
 * whole *portion* (Chronological 07/25 = `Isaiah 37, 38, 39, Psalms 76`), whose refs run BACKWARDS
 * in canon order, so its `init` threw; `ReaderViewModel.enterReading` swallowed the failure and the
 * reader stayed in Browse at its Genesis-1 default. 83 of 365 Chronological days were affected.
 *
 * **What a failure here means:** a bundled plan asset (or a change to the D-SEG-1 rule) has
 * produced a reading card whose refs are not a contiguous ascending canon run — i.e. data that
 * would reintroduce the Genesis-1 bug on a real device. Fix the asset or the segmentation rule;
 * do NOT relax the assertion.
 *
 * Alongside the sweep it pins the measured segment-count distribution per plan, the Ticket-1 repro
 * (including the documentary proof that the UNDIVIDED portion genuinely fails to build a
 * `ReadingPagerIndex` — the executable record of the root cause), and the D-SEG-1 merge case
 * (M'Cheyne 02/28 stream 1 stays ONE card).
 *
 * This is a **NEW, additional** gate. It does not read, modify, or weaken any of the five existing
 * data/Room gates — `ReadingPlanVerificationTest` (11), `McheynePlanVerificationTest` (10),
 * `ChronologicalPlanVerificationTest` (8), `BibleTextVerificationTest` (18),
 * `BibleDatabaseRoomOpenTest` (5) — whose counts and content are untouched. Mirrors their
 * discipline: shipped assets only, `assertWithMessage` naming the exact offending day.
 */
class PlanSegmentGateTest {
    private val plansDir =
        File(System.getProperty("planAssetsDir") ?: error("planAssetsDir system property not set"))

    private fun plan(id: String): PlanDto = PlanJson.decode(plansDir.resolve("plans/$id/plan.json").readText())

    private val planIds = listOf("bible_companion", "mcheyne", "chronological")

    /** Every portion of all three bundled plans — independently re-derived from the shipped JSON. */
    private val totalPortions = 2920

    /** One portion of one day, with enough context to name it in a failure message. */
    private data class DayPortion(
        val planId: String,
        val month: Int,
        val day: Int,
        val portion: Portion,
    ) {
        val where: String get() = "$planId $month/$day stream ${portion.streamNumber}"
    }

    private fun PortionDto.toDomain(): Portion =
        Portion(
            streamNumber = stream,
            refs =
                refs.map { ref ->
                    Reference(
                        book = BookCatalog.requireByName(ref.book),
                        chapter = ref.chapter,
                        verses =
                            if (ref.verseStart != null && ref.verseEnd != null) {
                                ReferenceVerses(ref.verseStart, ref.verseEnd)
                            } else {
                                null
                            },
                    )
                },
        )

    private fun portionsOf(planId: String): List<DayPortion> =
        plan(planId).days.flatMap { day ->
            day.portions.map { DayPortion(planId, day.month, day.day, it.toDomain()) }
        }

    private val allPortions: List<DayPortion> = planIds.flatMap { portionsOf(it) }

    private fun portionOf(
        planId: String,
        month: Int,
        day: Int,
        stream: Int,
    ): Portion =
        allPortions
            .single {
                it.planId == planId && it.month == month && it.day == day && it.portion.streamNumber == stream
            }.portion

    private fun refPairs(portion: Portion): List<Pair<String, Int>> =
        portion.refs.map { it.book.canonicalName to it.chapter }

    @Test
    fun `THE INVARIANT - every segment of every portion of every plan is a contiguous ascending run`() {
        // D-SEG-2. A book change or a chapter gap is exactly what breaks global contiguity, and
        // D-SEG-1 splits on precisely those two conditions — so this holds by construction. Proving
        // it over the REAL data is what stops a future plan asset from reintroducing Ticket 1.
        val errors = mutableListOf<String>()
        for (dp in allPortions) {
            for ((segIndex, segment) in ReadingSegments.segmentsOf(dp.portion).withIndex()) {
                val base = GlobalChapterIndex.indexOf(segment.refs.first().book, segment.refs.first().chapter)
                segment.refs.forEachIndexed { j, ref ->
                    val global = GlobalChapterIndex.indexOf(ref.book, ref.chapter)
                    if (global != base + j) {
                        errors +=
                            "${dp.where} segment $segIndex ref $j (${ref.book.canonicalName} ${ref.chapter}) " +
                            "is global chapter $global, expected ${base + j}"
                    }
                }
            }
        }
        // Anti-vacuity: the sweep must actually have inspected every bundled portion.
        assertWithMessage("portions inspected").that(allPortions).hasSize(totalPortions)
        assertWithMessage("non-contiguous segments across all bundled plans").that(errors).isEmpty()
    }

    @Test
    fun `THE INVARIANT - ReadingPagerIndex constructs for every segment of every portion of every plan`() {
        // The assertion that makes Ticket 1 non-recurring: this is the exact construction that threw
        // on Chronological 07/25 and was silently swallowed into a Genesis-1 fallback.
        assertWithMessage("portions inspected").that(allPortions).hasSize(totalPortions)
        for (dp in allPortions) {
            for ((segIndex, segment) in ReadingSegments.segmentsOf(dp.portion).withIndex()) {
                val result = runCatching { ReadingPagerIndex(segment) }
                assertWithMessage(
                    "ReadingPagerIndex must build for ${dp.where} segment $segIndex " +
                        "(${refPairs(segment)}): ${result.exceptionOrNull()?.message}",
                ).that(result.isSuccess)
                    .isTrue()
                assertWithMessage("collapsedSpan for ${dp.where} segment $segIndex (${refPairs(segment)})")
                    .that(result.getOrThrow().collapsedSpan)
                    .isEqualTo(segment.refs.size - 1)
            }
        }
    }

    @Test
    fun `segments partition every portion - concatenation reproduces the refs in order, stream kept`() {
        assertWithMessage("portions inspected").that(allPortions).hasSize(totalPortions)
        for (dp in allPortions) {
            val segments = ReadingSegments.segmentsOf(dp.portion)
            assertWithMessage("segments must partition ${dp.where}")
                .that(segments.flatMap { it.refs })
                .containsExactlyElementsIn(dp.portion.refs)
                .inOrder()
            assertWithMessage("every segment of ${dp.where} carries the portion's stream number")
                .that(segments.map { it.streamNumber }.toSet())
                .containsExactly(dp.portion.streamNumber)
        }
    }

    @Test
    fun `segment-count distribution per plan is pinned to the measured asset values`() {
        // A literal pin of the verified fact (docs/features/reading-card-segments.md, D-SEG-2 table),
        // independently re-derived from the shipped JSON. If a plan asset ever changes shape this
        // reddens, so the UI assumption it feeds — how many cards one reading can render — is
        // re-reviewed rather than silently drifting.
        val expected =
            mapOf(
                "bible_companion" to mapOf(1 to 1093, 2 to 2),
                "chronological" to mapOf(1 to 282, 2 to 57, 3 to 13, 4 to 7, 5 to 5, 6 to 1),
                "mcheyne" to mapOf(1 to 1459, 2 to 1),
            )
        val expectedPortionCounts =
            mapOf("bible_companion" to 1095, "chronological" to 365, "mcheyne" to 1460)

        for (planId in planIds) {
            val portions = allPortions.filter { it.planId == planId }
            assertWithMessage("$planId portion count")
                .that(portions)
                .hasSize(expectedPortionCounts.getValue(planId))
            val distribution =
                portions
                    .groupingBy { ReadingSegments.segmentsOf(it.portion).size }
                    .eachCount()
            assertWithMessage("$planId segment-count distribution")
                .that(distribution)
                .containsExactlyEntriesIn(expected.getValue(planId))
        }

        assertWithMessage("total portions across all three bundled plans")
            .that(allPortions)
            .hasSize(totalPortions)
        assertWithMessage("the most cards any single reading can render")
            .that(allPortions.maxOf { ReadingSegments.segmentsOf(it.portion).size })
            .isEqualTo(6)
    }

    @Test
    fun `REGRESSION PIN Ticket 1 - Chronological 07-25 splits into Isaiah 37-39 and Psalms 76`() {
        val whole = portionOf("chronological", month = 7, day = 25, stream = 1)
        assertThat(refPairs(whole))
            .containsExactly("Isaiah" to 37, "Isaiah" to 38, "Isaiah" to 39, "Psalms" to 76)
            .inOrder()

        // THE ROOT CAUSE, recorded executably: the UNDIVIDED portion cannot build a
        // ReadingPagerIndex — Psalms 76 precedes Isaiah 37 in canon order, so the run is reversed.
        // ReaderViewModel.enterReading swallowed this and the reader opened Genesis 1.
        val undivided = runCatching { ReadingPagerIndex(whole) }
        assertWithMessage("the whole 07/25 portion must NOT build a ReadingPagerIndex")
            .that(undivided.isFailure)
            .isTrue()
        assertThat(undivided.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)

        // THE FIX: each segment builds, and each is what a tap now hands the reader (D-SEG-6).
        val segments = ReadingSegments.segmentsOf(whole)
        assertThat(segments).hasSize(2)
        assertThat(refPairs(segments[0]))
            .containsExactly("Isaiah" to 37, "Isaiah" to 38, "Isaiah" to 39)
            .inOrder()
        assertThat(refPairs(segments[1])).containsExactly("Psalms" to 76)
        assertThat(segments.map { it.streamNumber }).containsExactly(1, 1)
        assertThat(ReadingPagerIndex(segments[0]).collapsedSpan).isEqualTo(2)
        assertThat(ReadingPagerIndex(segments[1]).collapsedSpan).isEqualTo(0)
    }

    @Test
    fun `MERGE PIN D-SEG-1 - M'Cheyne 02-28 stream 1 stays ONE card whose text shows both refs`() {
        // A verse window does NOT split (same book, consecutive chapters), so Exodus 11 +
        // Exodus 12:1-21 is ONE card. Its text still shows both refs, because ReadingFormatter's
        // own display-run logic — deliberately a different concern, display WITHIN a card — does
        // break on the window.
        val whole = portionOf("mcheyne", month = 2, day = 28, stream = 1)
        val segments = ReadingSegments.segmentsOf(whole)

        assertThat(segments).hasSize(1)
        val only = segments.single()
        assertThat(refPairs(only)).containsExactly("Exodus" to 11, "Exodus" to 12).inOrder()
        assertThat(only.refs[0].verses).isNull()
        assertThat(only.refs[1].verses).isEqualTo(ReferenceVerses(1, 21))
        assertThat(ReadingFormatter.format(only)).isEqualTo("Exodus 11; Exodus 12:1–21")
        assertThat(ReadingPagerIndex(only).collapsedSpan).isEqualTo(1)
    }
}
