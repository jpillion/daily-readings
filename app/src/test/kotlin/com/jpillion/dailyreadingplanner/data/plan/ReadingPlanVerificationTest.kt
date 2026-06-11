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

    private val assetsDir =
        File(
            System.getProperty("planAssetsDir") ?: error("planAssetsDir system property not set"),
        )
    private val plan: PlanDto = PlanJson.decode(assetsDir.resolve("reading_plan.json").readText())
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

    private fun testResource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource(name)) { "test resource $name not found" }.readText()

    @Test
    fun `schema header is correct`() {
        assertThat(plan.schemaVersion).isEqualTo(1)
        assertThat(plan.source).isNotEmpty()
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
}
