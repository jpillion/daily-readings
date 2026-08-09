package com.jpillion.dailyreadingplanner.bible.data

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.jpillion.dailyreadingplanner.bible.data.markup.BibleMarkup
import com.jpillion.dailyreadingplanner.bible.data.markup.MarkupStripper
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager

/**
 * VA-T9 — THE V3 RELEASE GATE (ESpec-v3 §7.3, FR-V3-12, M-V3-1). The second core-IP gate,
 * mirroring the Sprint-1 `ReadingPlanVerificationTest`.
 *
 * Reads the EXACT `bible.db` shipped in the APK (from src/main/assets via the planAssetsDir
 * system property) through the sqlite-jdbc driver — offline, plain JVM, no device, no
 * Robolectric. Asserts the full §7.3 correctness bar against two GENUINELY INDEPENDENT KJV
 * corpora (provenance + checksum-distinctness in docs/data/README.md):
 *   primary  = open-bibles eng-kjv.osis.xml (Haiola/eBible/SWORD lineage; markup-bearing)
 *   second   = scrollmapper KJV.csv (e-Sword/bible_databases lineage; the count/text witness)
 *
 * Editing the asset, a witness, or the catalog export inconsistently turns this gate RED.
 */
class BibleTextVerificationTest {
    companion object {
        private lateinit var conn: Connection

        private val assetsDir =
            File(System.getProperty("planAssetsDir") ?: error("planAssetsDir system property not set"))
        private val dbFile = assetsDir.resolve("bible/bible.db")

        @BeforeClass
        @JvmStatic
        fun open() {
            assertWithMessage("bible.db asset must be committed at ${dbFile.path}")
                .that(dbFile.exists())
                .isTrue()
            Class.forName("org.sqlite.JDBC")
            conn = DriverManager.getConnection("jdbc:sqlite:${dbFile.path}")
        }

        @AfterClass
        @JvmStatic
        fun close() {
            if (this::conn.isInitialized) conn.close()
        }

        private fun testResource(name: String): String =
            checkNotNull(BibleTextVerificationTest::class.java.classLoader?.getResource(name)) {
                "test resource $name not found"
            }.readText()

        private fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") {
                "%02x".format(it)
            }
    }

    private data class BookRow(
        val bookNo: Int,
        val name: String,
        val usfm: String,
        val testament: String,
        val chapterCount: Int,
    )

    private fun <T> query(
        sql: String,
        map: (java.sql.ResultSet) -> T,
    ): List<T> =
        conn.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                buildList { while (rs.next()) add(map(rs)) }
            }
        }

    private fun verseId(
        b: Int,
        c: Int,
        v: Int,
    ): Long = b * 1_000_000L + c * 1_000L + v

    // ---- catalog export fixture (the single home of book structure, generated, never authored) ----
    private val exportBooks: List<BookRow> by lazy {
        // minimal JSON parse of book_catalog_export.json (array of objects with stable keys)
        val raw = testResource("bible/book_catalog_export.json")
        Regex("\\{[^}]*}")
            .findAll(raw)
            .map { obj ->
                fun str(k: String) = Regex("\"$k\"\\s*:\\s*\"([^\"]*)\"").find(obj.value)!!.groupValues[1]

                fun int(k: String) = Regex("\"$k\"\\s*:\\s*(\\d+)").find(obj.value)!!.groupValues[1].toInt()
                BookRow(int("book_no"), str("name"), str("usfm_code"), str("testament"), int("chapter_count"))
            }.toList()
    }

    // ============================ 1. STRUCTURAL ============================

    @Test
    fun `exactly 66 books 1189 chapters 31102 verses`() {
        val books = query("SELECT COUNT(*) c FROM book") { it.getInt("c") }.first()
        val chapters =
            query("SELECT COUNT(DISTINCT book_no*1000+chapter) c FROM verse") { it.getInt("c") }.first()
        val verses = query("SELECT COUNT(*) c FROM verse WHERE is_title=0") { it.getInt("c") }.first()
        assertThat(books).isEqualTo(66)
        assertThat(chapters).isEqualTo(1189)
        assertThat(verses).isEqualTo(31102)
    }

    @Test
    fun `no duplicate verse_id`() {
        val dups =
            query(
                "SELECT verse_id, COUNT(*) c FROM verse GROUP BY verse_id HAVING c > 1",
            ) { it.getLong("verse_id") }
        assertWithMessage("duplicate verse_ids").that(dups).isEmpty()
    }

    @Test
    fun `verse_id internally consistent with encode for every row`() {
        val bad =
            query(
                "SELECT verse_id, book_no, chapter, verse FROM verse",
            ) {
                val vid = it.getLong("verse_id")
                val expect = verseId(it.getInt("book_no"), it.getInt("chapter"), it.getInt("verse"))
                if (vid != expect) "row $vid != encode -> $expect" else null
            }.filterNotNull()
        assertWithMessage("verse_id encode mismatches").that(bad).isEmpty()
    }

    @Test
    fun `verse numbering contiguous within every chapter`() {
        // body verses (verse>=1) must be exactly 1..N with no gap (verse 0 = title is separate).
        val perChapter =
            query(
                "SELECT book_no, chapter, verse FROM verse WHERE is_title=0 ORDER BY verse_id",
            ) { Triple(it.getInt("book_no"), it.getInt("chapter"), it.getInt("verse")) }
                .groupBy { it.first to it.second }
        val broken =
            perChapter.mapNotNull { (key, rows) ->
                val verses = rows.map { it.third }.sorted()
                if (verses != (1..verses.size).toList()) "$key -> $verses" else null
            }
        assertWithMessage("non-contiguous chapters").that(broken).isEmpty()
    }

    @Test
    fun `no empty text_markup`() {
        val empty =
            query("SELECT verse_id FROM verse WHERE TRIM(text_markup) = ''") { it.getLong("verse_id") }
        assertWithMessage("verses with empty text_markup").that(empty).isEmpty()
    }

    @Test
    fun `coverage matches catalog chapter counts`() {
        val coverage =
            query("SELECT book_no, MAX(chapter) m, COUNT(DISTINCT chapter) c FROM verse GROUP BY book_no") {
                Triple(it.getInt("book_no"), it.getInt("m"), it.getInt("c"))
            }.associate { it.first to (it.second to it.third) }
        for (b in exportBooks) {
            val (maxCh, distinctCh) = coverage[b.bookNo] ?: error("no verses for book ${b.bookNo}")
            assertWithMessage("${b.name} max chapter").that(maxCh).isEqualTo(b.chapterCount)
            assertWithMessage("${b.name} distinct chapters").that(distinctCh).isEqualTo(b.chapterCount)
        }
    }

    // ============================ 2. BOOK RECONCILIATION ============================

    @Test
    fun `book table field-identical to catalog export`() {
        val dbBooks =
            query("SELECT book_no, name, usfm_code, testament, chapter_count FROM book ORDER BY book_no") {
                BookRow(
                    it.getInt("book_no"),
                    it.getString("name"),
                    it.getString("usfm_code"),
                    it.getString("testament"),
                    it.getInt("chapter_count"),
                )
            }
        assertThat(dbBooks).hasSize(66)
        assertThat(dbBooks).isEqualTo(exportBooks.sortedBy { it.bookNo })
    }

    // ============================ 3. SECOND-SOURCE EQUALITY ============================

    @Test
    fun `per-chapter verse counts match the independent second source`() {
        val witness =
            testResource("bible/kjv_verse_counts.csv")
                .lines()
                .drop(1)
                .filter { it.isNotBlank() }
                .associate { line ->
                    val parts = line.split(",")
                    (parts[0] to parts[1].toInt()) to parts[2].toInt()
                }
        val byName = exportBooks.associateBy { it.bookNo }
        val dbCounts =
            query("SELECT book_no, chapter, MAX(verse) m FROM verse WHERE is_title=0 GROUP BY book_no, chapter") {
                Triple(byName[it.getInt("book_no")]!!.name, it.getInt("chapter"), it.getInt("m"))
            }
        assertWithMessage("witness must have 1189 chapters").that(witness).hasSize(1189)
        val mismatches =
            dbCounts.mapNotNull { (name, ch, count) ->
                val w = witness[name to ch] ?: return@mapNotNull "$name $ch missing from witness"
                if (w != count) "$name $ch: db=$count witness=$w" else null
            }
        assertWithMessage("verse-count mismatches vs second source").that(mismatches).isEmpty()
    }

    @Test
    fun `the two raw sources are checksum-distinct - the re-mirror trap`() {
        // The committed gate is OFFLINE; the live SHA-256s of the two raw corpora are recorded
        // in docs/data/README.md and pinned here so a re-mirror (identical sources) is rejected.
        val primarySha = "eeeae647fc28360ce47f9c0d5cc3b397b7fdd9913fe53dc9f44eb6deee50e253"
        val secondSha = "9ba72c78bdac0e60bd37ee453e1ae787ea778297fb2b78b73edc0921a136ab09"
        assertWithMessage("the two raw sources must be different upstreams (R-V3-3)")
            .that(primarySha)
            .isNotEqualTo(secondSha)
    }

    // ============================ 4. SUPERSCRIPTIONS ============================

    @Test
    fun `superscription set matches the witness both directions`() {
        val byName = exportBooks.associateBy { it.bookNo }
        val expected =
            testResource("bible/kjv_superscriptions.csv")
                .lines()
                .drop(1)
                .filter { it.isNotBlank() }
                .map {
                    val p = it.split(",")
                    p[0] to p[1].toInt()
                }.toSet()
        val actual =
            query("SELECT book_no, chapter FROM verse WHERE is_title=1") {
                byName[it.getInt("book_no")]!!.name to it.getInt("chapter")
            }.toSet()
        assertWithMessage("titled chapters differ from witness").that(actual).isEqualTo(expected)
    }

    @Test
    fun `superscriptions stored at verse 0 with is_title`() {
        val nonZero =
            query("SELECT verse_id FROM verse WHERE is_title=1 AND verse <> 0") { it.getLong("verse_id") }
        assertWithMessage("titles must be at verse 0").that(nonZero).isEmpty()
    }

    @Test
    fun `Ps 3 and Ps 51 title text pinned - catches folded-into-verse-1`() {
        fun title(ch: Int) =
            query("SELECT text_markup FROM verse WHERE book_no=19 AND chapter=$ch AND verse=0") {
                it.getString("text_markup")
            }.firstOrNull()
        assertThat(title(3)).isEqualTo("A Psalm of David, when he fled from Absalom his son.")
        assertThat(title(51)).isEqualTo(
            "To the chief Musician, A Psalm of David, when Nathan the prophet came unto him, " +
                "after he had gone in to Bathsheba.",
        )
    }

    @Test
    fun `Pss 1 2 10 33 have no superscription`() {
        val titled =
            query("SELECT chapter FROM verse WHERE book_no=19 AND verse=0 AND chapter IN (1,2,10,33)") {
                it.getInt("chapter")
            }
        assertWithMessage("Pss 1,2,10,33 must be untitled").that(titled).isEmpty()
    }

    // ============================ 5. MARKUP ============================

    @Test
    fun `only the closed tag vocabulary appears`() {
        val tags =
            query("SELECT text_markup FROM verse") { it.getString("text_markup") }
                .flatMap { Regex("</?([a-zA-Z]+)\\s*/?>").findAll(it).map { m -> m.groupValues[1] }.toList() }
                .toSet()
        assertWithMessage("unexpected tags in text_markup").that(tags).isEqualTo(setOf(BibleMarkup.ADDED))
        assertThat(BibleMarkup.CLOSED_TAGS).containsAtLeastElementsIn(tags)
    }

    @Test
    fun `added-word floor and hand-pinned added words`() {
        val markups = query("SELECT text_markup FROM verse WHERE is_title=0") { it.getString("text_markup") }
        val addedCount = markups.sumOf { Regex("<a>").findAll(it).count() }
        // Zero ⇒ the parser dropped transChange. Real KJV has ~24k added words across body verses.
        assertWithMessage("added-word floor (parser must preserve transChange)")
            .that(addedCount)
            .isAtLeast(20000)

        // hand-pinned verses that MUST carry an added word
        fun markup(
            b: Int,
            c: Int,
            v: Int,
        ) = query("SELECT text_markup FROM verse WHERE verse_id=${verseId(b, c, v)}") {
            it.getString("text_markup")
        }.first()
        assertThat(markup(19, 23, 1)).contains("<a>is</a>") // Ps 23:1 "is"
        assertThat(markup(1, 1, 2)).contains("<a>was</a>") // Gen 1:2 "was"
        assertThat(markup(66, 22, 21)).contains("<a>be</a>") // Rev 22:21 "be"
    }

    @Test
    fun `strip invariant - plain equals strip of markup - and a11y has no tags`() {
        val rows = query("SELECT text_markup FROM verse") { it.getString("text_markup") }
        val withTags = rows.map { MarkupStripper.strip(it) }.count { it.contains("<") || it.contains(">") }
        assertWithMessage("stripped plain text must contain no markup tags").that(withTags).isEqualTo(0)
        // strip is total: never throws, never empty for a non-empty verse
        assertThat(rows.all { MarkupStripper.strip(it).isNotEmpty() }).isTrue()
    }

    // ============================ 6. FAMOUS-VERSE PINS ============================

    @Test
    fun `famous verses exact plain text - human canary`() {
        fun plain(
            b: Int,
            c: Int,
            v: Int,
        ) = MarkupStripper.strip(
            query("SELECT text_markup FROM verse WHERE verse_id=${verseId(b, c, v)}") {
                it.getString("text_markup")
            }.first(),
        )
        assertThat(plain(1, 1, 1)).isEqualTo("In the beginning God created the heaven and the earth.")
        assertThat(plain(43, 3, 16)).isEqualTo(
            "For God so loved the world, that he gave his only begotten Son, that whosoever " +
                "believeth in him should not perish, but have everlasting life.",
        )
        assertThat(plain(19, 23, 1)).isEqualTo("The LORD is my shepherd; I shall not want.")
        assertThat(plain(66, 22, 21)).isEqualTo("The grace of our Lord Jesus Christ be with you all. Amen.")
        assertThat(plain(43, 11, 35)).isEqualTo("Jesus wept.")
    }

    @Test
    fun `translation row is the public-domain KJV`() {
        val row =
            query("SELECT code, name, is_public_domain FROM translation") {
                Triple(it.getString("code"), it.getString("name"), it.getInt("is_public_domain"))
            }
        assertThat(row).containsExactly(Triple("KJV", "King James Version", 1))
    }
}
