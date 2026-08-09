package com.jpillion.dailyreadingplanner.data.plan

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.jpillion.dailyreadingplanner.data.plan.dto.PlanDto
import com.jpillion.dailyreadingplanner.data.plan.dto.PlanJson
import com.jpillion.dailyreadingplanner.data.plan.dto.RefDto
import org.junit.Test
import java.io.File

/**
 * SA-T9 — THE M'CHEYNE GATE (alternate-schedules ESpec §7, D-ALT-20, FR-ALT-3).
 *
 * Proves the SHIPPED M'Cheyne asset (assets/plans/mcheyne/plan.json, read via planAssetsDir, the
 * same mechanism that guards the exact APK file) matches an INDEPENDENT second source (Carson/TGC
 * RtB_Reading-Plan_2020.pdf — a genuinely different lineage from the canonical Edgington/Haslam the
 * asset is built from; checksum-distinct, parsed by a separate parser) day by day, plus the M'Cheyne
 * structural + verse-window + coverage invariants. Reconciled extraction artifacts are in
 * docs/data/README.md. Mirrors ReadingPlanVerificationTest's discipline (the Bible Companion gate).
 */
class McheynePlanVerificationTest {
    private data class CatalogBook(
        val order: Int,
        val name: String,
        val chapterCount: Int,
        val blbAbbrev: String,
    )

    private val plansDir =
        File(System.getProperty("planAssetsDir") ?: error("planAssetsDir system property not set"))

    private val plan: PlanDto = PlanJson.decode(plansDir.resolve("plans/mcheyne/plan.json").readText())
    private val fixture: PlanDto = PlanJson.decode(testResource("plans/mcheyne/plan_verify.json"))

    private val catalog: List<CatalogBook> =
        testResource("book_catalog.csv")
            .lines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val (order, name, count, abbrev) = line.split(",")
                CatalogBook(order.toInt(), name, count.toInt(), abbrev)
            }
    private val byName = catalog.associateBy { it.name }

    private val verseCountByBookChapter: Map<Pair<String, Int>, Int> =
        testResource("bible/kjv_verse_counts.csv")
            .lines()
            .drop(1)
            .filter { it.isNotBlank() }
            .associate { line ->
                val (book, chapter, count) = line.split(",")
                (book to chapter.toInt()) to count.toInt()
            }

    private val daysPerMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    /** OT (non-Psalms) books, read once per year; Psalms + the whole NT are read twice (D-ALT). */
    private val otNonPsalmsBooks =
        setOf(
            "Genesis",
            "Exodus",
            "Leviticus",
            "Numbers",
            "Deuteronomy",
            "Joshua",
            "Judges",
            "Ruth",
            "1 Samuel",
            "2 Samuel",
            "1 Kings",
            "2 Kings",
            "1 Chronicles",
            "2 Chronicles",
            "Ezra",
            "Nehemiah",
            "Esther",
            "Job",
            "Proverbs",
            "Ecclesiastes",
            "Song of Solomon",
            "Isaiah",
            "Jeremiah",
            "Lamentations",
            "Ezekiel",
            "Daniel",
            "Hosea",
            "Joel",
            "Amos",
            "Obadiah",
            "Jonah",
            "Micah",
            "Nahum",
            "Habakkuk",
            "Zephaniah",
            "Haggai",
            "Zechariah",
            "Malachi",
        )

    private fun testResource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource(name)) { "test resource $name not found" }.readText()

    @Test
    fun `schema header declares a 4-stream date-anchored 365-day M'Cheyne plan`() {
        assertThat(plan.schemaVersion).isEqualTo(3)
        assertThat(plan.planId).isEqualTo("mcheyne")
        assertThat(plan.anchoring).isEqualTo("DATE")
        assertThat(plan.dayCount).isEqualTo(365)
        assertThat(plan.source).isNotEmpty()
        assertThat(plan.streams.map { it.number }).isEqualTo(listOf(1, 2, 3, 4))
        assertThat(plan.streams.all { it.title.isNotBlank() }).isTrue()
    }

    @Test
    fun `plan has exactly 365 days with correct per-month counts and no Feb 29`() {
        assertThat(plan.days).hasSize(365)
        for (m in 1..12) {
            assertWithMessage("month $m day count")
                .that(plan.days.count { it.month == m })
                .isEqualTo(daysPerMonth[m - 1])
        }
        assertWithMessage("no Feb 29 entry (D1)")
            .that(plan.days.none { it.month == 2 && it.day == 29 })
            .isTrue()
        val keys = plan.days.map { it.month to it.day }
        assertWithMessage("every (month, day) unique").that(keys.toSet()).hasSize(keys.size)
    }

    @Test
    fun `every day has exactly 4 portions with streams 1-2-3-4 and non-empty refs`() {
        for (day in plan.days) {
            assertWithMessage("streams on ${day.month}/${day.day}")
                .that(day.portions.map { it.stream })
                .isEqualTo(listOf(1, 2, 3, 4))
            day.portions.forEach { p ->
                assertWithMessage("refs on ${day.month}/${day.day} stream ${p.stream}")
                    .that(p.refs)
                    .isNotEmpty()
            }
        }
    }

    @Test
    fun `every ref resolves in the catalog with chapter in range`() {
        for (day in plan.days) {
            for (p in day.portions) {
                for (ref in p.refs) {
                    val book = byName[ref.book]
                    assertWithMessage("unknown book '${ref.book}' on ${day.month}/${day.day}")
                        .that(book)
                        .isNotNull()
                    assertWithMessage(
                        "${ref.book} ${ref.chapter} out of range on ${day.month}/${day.day}",
                    ).that(ref.chapter)
                        .isIn(1..book!!.chapterCount)
                }
            }
        }
    }

    @Test
    fun `every windowed ref is well-formed within its chapter`() {
        for (w in windowedRefs()) {
            val where = "${w.month}/${w.day} stream ${w.stream} ${w.ref.book} ${w.ref.chapter}"
            assertWithMessage("$where: verseStart present").that(w.ref.verseStart).isNotNull()
            assertWithMessage("$where: verseEnd present").that(w.ref.verseEnd).isNotNull()
            val start = w.ref.verseStart!!
            val end = w.ref.verseEnd!!
            assertWithMessage("$where: 1 <= verseStart").that(start).isAtLeast(1)
            assertWithMessage("$where: verseStart <= verseEnd").that(start).isAtMost(end)
            val verseCount =
                checkNotNull(verseCountByBookChapter[w.ref.book to w.ref.chapter]) {
                    "$where: no verse-count witness"
                }
            assertWithMessage("$where: verseEnd <= chapter verse count ($verseCount)")
                .that(end)
                .isAtMost(verseCount)
            // A window that covers the whole chapter would have been collapsed to a whole-chapter
            // ref (no verse fields) by the build — a window is always a proper sub-range.
            assertWithMessage("$where: a window must be a PROPER sub-chapter (not 1..count)")
                .that(start > 1 || end < verseCount)
                .isTrue()
        }
    }

    @Test
    fun `plan matches the independent Carson-TGC second source day by day - THE GATE`() {
        assertWithMessage("fixture must cover all 365 days").that(fixture.days).hasSize(365)
        val fixtureByDate = fixture.days.associateBy { it.month to it.day }
        val mismatches =
            plan.days.mapNotNull { day ->
                val other =
                    fixtureByDate[day.month to day.day]
                        ?: return@mapNotNull "${day.month}/${day.day}: missing from second source"
                if (day.portions != other.portions) {
                    "${day.month}/${day.day}: asset=${day.portions} second-source=${other.portions}"
                } else {
                    null
                }
            }
        assertWithMessage("day-by-day mismatches vs Carson/TGC").that(mismatches).isEmpty()
    }

    @Test
    fun `coverage invariant - OT verses once - Psalms and NT verses twice - every verse covered`() {
        // The M'Cheyne load-bearing structural proof. Verse-aware: a verse-windowed chapter is read
        // across days, so coverage is checked per VERSE (the windows must tile each chapter exactly).
        val reads = mutableMapOf<Triple<String, Int, Int>, Int>()
        for (day in plan.days) {
            for (p in day.portions) {
                for (ref in p.refs) {
                    val total = verseCountByBookChapter.getValue(ref.book to ref.chapter)
                    val lo = ref.verseStart ?: 1
                    val hi = ref.verseEnd ?: total
                    for (v in lo..hi) {
                        val key = Triple(ref.book, ref.chapter, v)
                        reads[key] = (reads[key] ?: 0) + 1
                    }
                }
            }
        }
        val errors = mutableListOf<String>()
        for (book in catalog) {
            val expected = if (book.name in otNonPsalmsBooks) 1 else 2 // Psalms + NT twice
            for (ch in 1..book.chapterCount) {
                val total = verseCountByBookChapter.getValue(book.name to ch)
                for (v in 1..total) {
                    val got = reads[Triple(book.name, ch, v)] ?: 0
                    if (got != expected) {
                        errors += "${book.name} $ch:$v read $got times, expected $expected"
                    }
                }
            }
        }
        assertWithMessage("verse-level coverage errors").that(errors).isEmpty()
    }

    @Test
    fun `coverage anchor - Matthew 1 is read in Family on Jan 1 and in Personal on Jun 21`() {
        // The canonical M'Cheyne "NT twice" witness (sourcing doc): Matthew 1 appears Jan 1 (Family,
        // stream 2) AND Jun 21 (Personal, stream 4). Pins the double-NT coverage at a named day.
        val jan1 = plan.days.single { it.month == 1 && it.day == 1 }
        val jun21 = plan.days.single { it.month == 6 && it.day == 21 }
        assertThat(
            jan1.portions
                .single { it.stream == 2 }
                .refs
                .map { it.book to it.chapter },
        ).contains("Matthew" to 1)
        assertThat(
            jun21.portions
                .single { it.stream == 4 }
                .refs
                .map { it.book to it.chapter },
        ).contains("Matthew" to 1)
    }

    @Test
    fun `Psalm 119 is split into seven verse windows that tile 1 to 176 - twice`() {
        // Ps 119 is read twice (Family Jun 22-28; Personal Oct 25-31). Each occurrence's windows must
        // TILE verses 1..176 exactly (the Sprint-J tiling invariant; here proven for both readings).
        val ps119Windows =
            windowedRefs()
                .filter { it.ref.book == "Psalms" && it.ref.chapter == 119 }
                .groupBy { it.stream }
        assertWithMessage("Ps 119 read on exactly two streams (Family + Personal)")
            .that(ps119Windows.keys)
            .containsExactly(2, 4)
        for ((stream, windows) in ps119Windows) {
            val sorted = windows.map { it.ref.verseStart!! to it.ref.verseEnd!! }.sortedBy { it.first }
            assertWithMessage("stream $stream Ps 119: seven windows").that(sorted).hasSize(7)
            assertWithMessage("stream $stream Ps 119: first window starts at 1")
                .that(sorted.first().first)
                .isEqualTo(1)
            assertWithMessage("stream $stream Ps 119: last window ends at 176")
                .that(sorted.last().second)
                .isEqualTo(176)
            for (i in 1 until sorted.size) {
                assertWithMessage("stream $stream Ps 119: window $i is contiguous with ${i - 1}")
                    .that(sorted[i].first)
                    .isEqualTo(sorted[i - 1].second + 1)
            }
        }
    }

    @Test
    fun `the cross-chapter ranges are encoded faithfully - not chapter-collapsed`() {
        // The fidelity invariant (R-ALT-3): the verse-faithful spanning ranges must be present, NOT
        // the chapter-collapsed bibleplan.org form (which had a Feb-28 off-by-one). Spot-pin the
        // documented spanning days against the verse-faithful source.
        fun refsOn(
            month: Int,
            day: Int,
            stream: Int,
        ): List<RefDto> =
            plan.days
                .single { it.month == month && it.day == day }
                .portions
                .single { it.stream == stream }
                .refs

        // Feb 28 = Ex 11:1-12:21  => whole Ex 11 + Ex 12:1-21 (NOT 'Ex 11-12:20').
        assertThat(refsOn(2, 28, 1))
            .containsExactly(
                RefDto("Exodus", 11),
                RefDto("Exodus", 12, verseStart = 1, verseEnd = 21),
            ).inOrder()
        // Mar 1 = Ex 12:22-51  => Ex 12:22-51 (NOT 'Ex 12:21-50').
        assertThat(refsOn(3, 1, 1)).containsExactly(
            RefDto("Exodus", 12, verseStart = 22, verseEnd = 51),
        )
        // Aug 8 Personal-Psalms = Jer 36,45 (the non-adjacent double-chapter slot).
        assertThat(refsOn(8, 8, 3).map { it.book to it.chapter })
            .containsExactly("Jeremiah" to 36, "Jeremiah" to 45)
            .inOrder()
        // Ps 78 split (May 24/25 Family): 78:1-37 then 78:38-72.
        assertThat(refsOn(5, 24, 2)).containsExactly(RefDto("Psalms", 78, verseStart = 1, verseEnd = 37))
        assertThat(refsOn(5, 25, 2)).containsExactly(RefDto("Psalms", 78, verseStart = 38, verseEnd = 72))
    }

    private data class WindowedRef(
        val month: Int,
        val day: Int,
        val stream: Int,
        val ref: RefDto,
    )

    private fun windowedRefs(): List<WindowedRef> =
        plan.days.flatMap { day ->
            day.portions.flatMap { p ->
                p.refs
                    .filter { it.verseStart != null || it.verseEnd != null }
                    .map { WindowedRef(day.month, day.day, p.stream, it) }
            }
        }
}
