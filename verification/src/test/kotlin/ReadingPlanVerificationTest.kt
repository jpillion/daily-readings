import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * S1-T5 — THE RELEASE GATE (execution plan §5.2, risk R3/M1).
 *
 * Proves the canonical reading plan (extracted from christadelphia.org chart.pdf)
 * matches an INDEPENDENT second source (antipas.org Bible Companion booklet),
 * day by day, plus structural invariants and book-catalog consistency.
 * Reconciled conflicts are documented in docs/data/README.md.
 */

@Serializable
data class Ref(val book: String, val chapter: Int)

@Serializable
data class Portion(val stream: Int, val refs: List<Ref>)

@Serializable
data class Day(val month: Int, val day: Int, val portions: List<Portion>)

@Serializable
data class Plan(val schemaVersion: Int, val source: String, val days: List<Day>)

data class CatalogBook(val order: Int, val name: String, val chapterCount: Int, val blbAbbrev: String)

class ReadingPlanVerificationTest {

    private val dataDir = File(System.getProperty("dataDir") ?: error("dataDir system property not set"))
    private val json = Json { ignoreUnknownKeys = false }
    private val plan: Plan = json.decodeFromString(dataDir.resolve("reading_plan.json").readText())
    private val fixture: Plan = json.decodeFromString(dataDir.resolve("reading_plan_verify.json").readText())
    private val catalog: List<CatalogBook> = dataDir.resolve("book_catalog.csv").readLines()
        .drop(1).filter { it.isNotBlank() }
        .map { line ->
            val (order, name, count, abbrev) = line.split(",")
            CatalogBook(order.toInt(), name, count.toInt(), abbrev)
        }

    private val daysPerMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    private val byName = catalog.associateBy { it.name }

    @Test
    fun `schema header is correct`() {
        assertEquals(1, plan.schemaVersion)
        assertTrue(plan.source.isNotBlank())
    }

    @Test
    fun `plan has exactly 365 days with correct per-month counts and no Feb 29`() {
        assertEquals(365, plan.days.size)
        for (m in 1..12) {
            assertEquals(daysPerMonth[m - 1], plan.days.count { it.month == m }, "month $m day count")
        }
        assertTrue(plan.days.none { it.month == 2 && it.day == 29 }, "no Feb 29 entry (decision D1)")
        val keys = plan.days.map { it.month to it.day }
        assertEquals(keys.size, keys.toSet().size, "every (month, day) unique")
        assertTrue(plan.days.all { (it.month to it.day).let { (m, d) -> m in 1..12 && d in 1..daysPerMonth[m - 1] } })
    }

    @Test
    fun `every day has exactly 3 portions with streams 1-2-3 and non-empty refs`() {
        for (day in plan.days) {
            assertEquals(listOf(1, 2, 3), day.portions.map { it.stream }, "streams on ${day.month}/${day.day}")
            day.portions.forEach { p ->
                assertTrue(p.refs.isNotEmpty(), "empty refs on ${day.month}/${day.day} stream ${p.stream}")
            }
        }
    }

    @Test
    fun `every ref resolves in the catalog with chapter in range`() {
        for (day in plan.days) for (p in day.portions) for (ref in p.refs) {
            val book = byName[ref.book]
            assertTrue(book != null, "unknown book '${ref.book}' on ${day.month}/${day.day}")
            assertTrue(
                ref.chapter in 1..book!!.chapterCount,
                "${ref.book} ${ref.chapter} out of range (max ${book.chapterCount}) on ${day.month}/${day.day}"
            )
        }
    }

    @Test
    fun `every chapter of every book is read - full coverage`() {
        // The Companion reads the whole OT once and NT twice per year; at minimum every
        // chapter of all 66 books must appear. This cross-validates catalog chapterCounts
        // against both sources (it caught 5 of the 7 source conflicts in Sprint 1).
        val covered = mutableMapOf<String, MutableSet<Int>>()
        for (day in plan.days) for (p in day.portions) for (ref in p.refs) {
            covered.getOrPut(ref.book) { mutableSetOf() }.add(ref.chapter)
        }
        for (book in catalog) {
            assertEquals(
                (1..book.chapterCount).toSet(), covered[book.name] ?: emptySet<Int>(),
                "chapter coverage for ${book.name}"
            )
        }
        assertEquals(66, covered.size, "no books outside the catalog")
    }

    @Test
    fun `book catalog has 66 ordered books with valid lowercase abbreviations`() {
        assertEquals(66, catalog.size)
        assertEquals((1..66).toList(), catalog.map { it.order })
        assertEquals(66, catalog.map { it.name }.toSet().size, "unique names")
        assertEquals(66, catalog.map { it.blbAbbrev }.toSet().size, "unique abbreviations")
        for (book in catalog) {
            assertTrue(book.blbAbbrev.isNotBlank(), "${book.name} abbrev blank")
            assertEquals(book.blbAbbrev.lowercase(), book.blbAbbrev, "${book.name} abbrev lowercase")
            assertTrue(book.blbAbbrev.matches(Regex("[a-z0-9]{3}")), "${book.name} abbrev '${book.blbAbbrev}' not 3 chars")
            assertTrue(book.chapterCount >= 1, "${book.name} chapterCount")
        }
    }

    @Test
    fun `plan matches the independent second source day by day - THE GATE`() {
        assertEquals(365, fixture.days.size, "fixture must cover all 365 days")
        val fixtureByDate = fixture.days.associateBy { it.month to it.day }
        val mismatches = plan.days.mapNotNull { day ->
            val other = fixtureByDate[day.month to day.day]
                ?: return@mapNotNull "${day.month}/${day.day}: missing from second source"
            if (day.portions != other.portions)
                "${day.month}/${day.day}: plan=${day.portions} second-source=${other.portions}"
            else null
        }
        assertTrue(mismatches.isEmpty(), "day-by-day mismatches vs second source:\n" + mismatches.joinToString("\n"))
    }
}
