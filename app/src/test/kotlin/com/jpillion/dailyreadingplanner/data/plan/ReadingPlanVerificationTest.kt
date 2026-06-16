package com.jpillion.dailyreadingplanner.data.plan

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.jpillion.dailyreadingplanner.data.plan.dto.PlanDto
import com.jpillion.dailyreadingplanner.data.plan.dto.PlanJson
import org.junit.Test
import java.io.File

/**
 * S1-T5 / S2-T6 — THE RELEASE GATE (execution plan §5.2, risk R3/M1).
 *
 * Proves the canonical reading plan (the exact asset bundled in the APK, read from
 * src/main/assets via the planAssetsDir system property set in app/build.gradle.kts)
 * matches an INDEPENDENT second source (antipas.org Bible Companion booklet), day by day,
 * plus structural invariants and book-catalog consistency. Reconciled conflicts are
 * documented in docs/data/README.md.
 */
class ReadingPlanVerificationTest {
    private data class CatalogBook(
        val order: Int,
        val name: String,
        val chapterCount: Int,
        val blbAbbrev: String,
    )

    private data class WindowedRef(
        val month: Int,
        val day: Int,
        val stream: Int,
        val ref: com.jpillion.dailyreadingplanner.data.plan.dto.RefDto,
    )

    private val assetsDir =
        File(
            System.getProperty("planAssetsDir") ?: error("planAssetsDir system property not set"),
        )
    private val plan: PlanDto = PlanJson.decode(assetsDir.resolve("plans/bible_companion/plan.json").readText())
    private val fixture: PlanDto = PlanJson.decode(testResource("reading_plan_verify.json"))
    private val catalog: List<CatalogBook> =
        testResource("book_catalog.csv")
            .lines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val (order, name, count, abbrev) = line.split(",")
                CatalogBook(order.toInt(), name, count.toInt(), abbrev)
            }

    private val daysPerMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    private val byName = catalog.associateBy { it.name }

    /**
     * D-SCHEMA-3: the per-(book, chapter) verse-count upper bound comes from the committed second-
     * source KJV verse-count witness (Sprint-A `kjv_verse_counts.csv`, 1,189 rows) — reused here as
     * an independent witness for the `verseEnd <= chapterVerseCount` bound. Cross-references a V3
     * trusted asset from the V1 plan gate; both are committed trusted data (documented coupling).
     */
    private val verseCountByBookChapter: Map<Pair<String, Int>, Int> =
        testResource("bible/kjv_verse_counts.csv")
            .lines()
            .drop(1)
            .filter { it.isNotBlank() }
            .associate { line ->
                val (book, chapter, count) = line.split(",")
                (book to chapter.toInt()) to count.toInt()
            }

    /** Every ref carrying a verse window, with its date, in plan order. */
    private val windowedRefs: List<WindowedRef> =
        plan.days.flatMap { day ->
            day.portions.flatMap { p ->
                p.refs
                    .filter { it.verseStart != null || it.verseEnd != null }
                    .map { WindowedRef(day.month, day.day, p.stream, it) }
            }
        }

    private fun testResource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource(name)) { "test resource $name not found" }.readText()

    @Test
    fun `schema header is correct`() {
        // D-ALT-2 (SA-T10): schemaVersion is now 3 — a plan declares its shape in an additive head.
        // The Bible Companion's day BODY is byte-unchanged; only the head + asset path moved.
        assertThat(plan.schemaVersion).isEqualTo(3)
        assertThat(plan.source).isNotEmpty()
        assertThat(plan.planId).isEqualTo("bible_companion")
        assertThat(plan.anchoring).isEqualTo("DATE")
        assertThat(plan.dayCount).isEqualTo(365)
        assertThat(plan.streams.map { it.number }).isEqualTo(listOf(1, 2, 3))
        assertThat(plan.streams.map { it.title })
            .isEqualTo(listOf("Law & History", "Psalms & Prophecy", "New Testament"))
    }

    @Test
    fun `plan has exactly 365 days with correct per-month counts and no Feb 29`() {
        assertThat(plan.days).hasSize(365)
        for (m in 1..12) {
            assertWithMessage("month $m day count")
                .that(plan.days.count { it.month == m })
                .isEqualTo(daysPerMonth[m - 1])
        }
        assertWithMessage("no Feb 29 entry (decision D1)")
            .that(plan.days.none { it.month == 2 && it.day == 29 })
            .isTrue()
        val keys = plan.days.map { it.month to it.day }
        assertWithMessage("every (month, day) unique").that(keys.toSet()).hasSize(keys.size)
        assertThat(
            plan.days.all { (it.month to it.day).let { (m, d) -> m in 1..12 && d in 1..daysPerMonth[m - 1] } },
        ).isTrue()
    }

    @Test
    fun `every day has exactly 3 portions with streams 1-2-3 and non-empty refs`() {
        for (day in plan.days) {
            assertWithMessage("streams on ${day.month}/${day.day}")
                .that(day.portions.map { it.stream })
                .isEqualTo(listOf(1, 2, 3))
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
                        "${ref.book} ${ref.chapter} out of range (max ${book!!.chapterCount}) on ${day.month}/${day.day}",
                    ).that(ref.chapter)
                        .isIn(1..book.chapterCount)
                }
            }
        }
    }

    @Test
    fun `every chapter of every book is read - full coverage`() {
        // The Companion reads the whole OT once and NT twice per year; at minimum every
        // chapter of all 66 books must appear. This cross-validates catalog chapterCounts
        // against both sources (it caught 5 of the 7 source conflicts in Sprint 1).
        val covered = mutableMapOf<String, MutableSet<Int>>()
        for (day in plan.days) {
            for (p in day.portions) {
                for (ref in p.refs) {
                    covered.getOrPut(ref.book) { mutableSetOf() }.add(ref.chapter)
                }
            }
        }
        for (book in catalog) {
            assertWithMessage("chapter coverage for ${book.name}")
                .that(covered[book.name] ?: emptySet<Int>())
                .isEqualTo((1..book.chapterCount).toSet())
        }
        assertWithMessage("no books outside the catalog").that(covered).hasSize(66)
    }

    @Test
    fun `book catalog has 66 ordered books with valid lowercase abbreviations`() {
        assertThat(catalog).hasSize(66)
        assertThat(catalog.map { it.order }).isEqualTo((1..66).toList())
        assertWithMessage("unique names").that(catalog.map { it.name }.toSet()).hasSize(66)
        assertWithMessage("unique abbreviations").that(catalog.map { it.blbAbbrev }.toSet()).hasSize(66)
        for (book in catalog) {
            assertWithMessage("${book.name} abbrev blank").that(book.blbAbbrev).isNotEmpty()
            assertWithMessage("${book.name} abbrev lowercase")
                .that(book.blbAbbrev)
                .isEqualTo(book.blbAbbrev.lowercase())
            assertWithMessage("${book.name} abbrev '${book.blbAbbrev}' not 3 chars")
                .that(book.blbAbbrev)
                .matches("[a-z0-9]{3}")
            assertWithMessage("${book.name} chapterCount").that(book.chapterCount).isAtLeast(1)
        }
    }

    @Test
    fun `plan matches the independent second source day by day - THE GATE`() {
        assertWithMessage("fixture must cover all 365 days").that(fixture.days).hasSize(365)
        val fixtureByDate = fixture.days.associateBy { it.month to it.day }
        val mismatches =
            plan.days.mapNotNull { day ->
                val other =
                    fixtureByDate[day.month to day.day]
                        ?: return@mapNotNull "${day.month}/${day.day}: missing from second source"
                if (day.portions != other.portions) {
                    "${day.month}/${day.day}: plan=${day.portions} second-source=${other.portions}"
                } else {
                    null
                }
            }
        assertWithMessage("day-by-day mismatches vs second source").that(mismatches).isEmpty()
    }

    // ---- Schema v2 verse-window gate (Psalm 119 sub-chapter ranges) ----

    /**
     * Gate 2 (well-formedness, every windowed ref): both fields present, 1 <= start <= end, and
     * end <= the chapter's verse count (D-SCHEMA-3, via the committed kjv_verse_counts.csv witness).
     * Whole-chapter refs carry no verse fields and are unaffected — proving the 1,090+ readings are
     * untouched (the count==4 pin below).
     */
    @Test
    fun `every windowed ref is well-formed within its chapter`() {
        for (w in windowedRefs) {
            val where = "${w.month}/${w.day} stream ${w.stream} ${w.ref.book} ${w.ref.chapter}"
            assertWithMessage("$where: verseStart present").that(w.ref.verseStart).isNotNull()
            assertWithMessage("$where: verseEnd present").that(w.ref.verseEnd).isNotNull()
            val start = w.ref.verseStart!!
            val end = w.ref.verseEnd!!
            assertWithMessage("$where: 1 <= verseStart").that(start).isAtLeast(1)
            assertWithMessage("$where: verseStart <= verseEnd").that(start).isAtMost(end)
            val verseCount =
                checkNotNull(verseCountByBookChapter[w.ref.book to w.ref.chapter]) {
                    "$where: no verse-count witness for ${w.ref.book} ${w.ref.chapter}"
                }
            assertWithMessage("$where: verseEnd <= chapter verse count ($verseCount)")
                .that(end)
                .isAtMost(verseCount)
        }
    }

    /**
     * Gate 3 (THE verse-level coverage invariant): the four Mar 9-12 stream-2 Psalm-119 windows
     * TILE 1..176 exactly — contiguous, no gap, no overlap, no verse read twice or skipped. The
     * verse analogue of the chapter full-coverage / read-once check that caught 5 of 7 Sprint-1
     * conflicts. Self-validating: if the sourced numbers don't tile 1..176, the data is wrong.
     */
    @Test
    fun `the four Psalm 119 days tile verses 1 to 176 exactly - the coverage invariant`() {
        val psalm119Days = listOf(3 to 9, 3 to 10, 3 to 11, 3 to 12)
        val windows =
            psalm119Days
                .map { (month, day) ->
                    val ref =
                        plan.days
                            .single { it.month == month && it.day == day }
                            .portions
                            .single { it.stream == 2 }
                            .refs
                            .single()
                    assertWithMessage("$month/$day stream 2 must be Psalms 119")
                        .that(ref.book to ref.chapter)
                        .isEqualTo("Psalms" to 119)
                    val start = checkNotNull(ref.verseStart) { "$month/$day missing verseStart" }
                    val end = checkNotNull(ref.verseEnd) { "$month/$day missing verseEnd" }
                    start to end
                }.sortedBy { it.first }

        assertWithMessage("first window must start at verse 1").that(windows.first().first).isEqualTo(1)
        assertWithMessage("last window must end at verse 176 (Ps 119 length)")
            .that(windows.last().second)
            .isEqualTo(176)
        for (i in 1 until windows.size) {
            assertWithMessage("window $i must start exactly one verse after window ${i - 1} ends")
                .that(windows[i].first)
                .isEqualTo(windows[i - 1].second + 1)
        }
        // belt-and-braces: the four lengths sum to 176 (no double-count masking a gap+overlap pair).
        assertWithMessage("the four windows' lengths sum to 176")
            .that(windows.sumOf { it.second - it.first + 1 })
            .isEqualTo(176)
    }

    /**
     * Gate 4 (second-source range equality): the four Psalm-119 windows in the canonical asset match
     * the independent second-source fixture verse-for-verse. The day-by-day THE GATE test already
     * compares portions structurally (now incl. the verse fields, as data-class members), so a
     * boundary changed in one file but not the other fails there too; this is the explicit pin so a
     * silent range divergence can never slip past.
     */
    @Test
    fun `Psalm 119 windows match the independent second source`() {
        val fixtureByDate = fixture.days.associateBy { it.month to it.day }
        for ((month, day) in listOf(3 to 9, 3 to 10, 3 to 11, 3 to 12)) {
            val planRef =
                plan.days
                    .single { it.month == month && it.day == day }
                    .portions
                    .single { it.stream == 2 }
                    .refs
                    .single()
            val fixtureRef =
                checkNotNull(fixtureByDate[month to day]) { "$month/$day missing from second source" }
                    .portions
                    .single { it.stream == 2 }
                    .refs
                    .single()
            assertWithMessage("$month/$day verseStart matches second source")
                .that(planRef.verseStart)
                .isEqualTo(fixtureRef.verseStart)
            assertWithMessage("$month/$day verseEnd matches second source")
                .that(planRef.verseEnd)
                .isEqualTo(fixtureRef.verseEnd)
        }
    }

    /**
     * Gate 5 (only Psalm 119 is windowed — pins the A2 audit into the gate): the ONLY refs in the
     * whole plan carrying verse fields are exactly the four Mar 9-12 stream-2 Psalms-119 refs. If a
     * future data change adds a windowed ref elsewhere without spec review, the gate flags it.
     */
    @Test
    fun `only the four Psalm 119 days carry verse windows`() {
        assertWithMessage("exactly four windowed refs in the whole plan")
            .that(windowedRefs)
            .hasSize(4)
        for (w in windowedRefs) {
            assertWithMessage("windowed ref ${w.month}/${w.day} must be Psalms 119")
                .that(w.ref.book to w.ref.chapter)
                .isEqualTo("Psalms" to 119)
            assertWithMessage("windowed ref ${w.month}/${w.day} must be on stream 2")
                .that(w.stream)
                .isEqualTo(2)
        }
        assertWithMessage("windowed refs are exactly Mar 9-12")
            .that(windowedRefs.map { it.month to it.day }.toSet())
            .isEqualTo(setOf(3 to 9, 3 to 10, 3 to 11, 3 to 12))
    }
}
