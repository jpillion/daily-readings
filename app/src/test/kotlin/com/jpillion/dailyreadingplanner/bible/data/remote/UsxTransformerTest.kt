package com.jpillion.dailyreadingplanner.bible.data.remote

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import org.junit.Test

/**
 * D-OT-6 / D-OT-11 — the USX → [com.jpillion.dailyreadingplanner.bible.domain.model.VerseText]
 * transformer.
 *
 * Payloads here are **synthetic**, hand-authored to the structures observed in real NKJV/NASB
 * responses. Deliberately NOT captured fixtures: committing licensed NKJV/NASB text to the repo is
 * a licensing question this project does not need to answer, and synthetic input pins the exact
 * structural case per test. This mirrors the established repo discipline for provider links —
 * verify live at dev time, keep the committed suite offline.
 */
class UsxTransformerTest {
    private fun transform(json: String) =
        UsxTransformer.transform(
            content = Json.parseToJsonElement(json).jsonArray,
            usfmToBookNo = { code -> BookCatalog.findByUsfm(code)?.order },
        )

    private fun verse(
        sid: String,
        number: String,
        vararg items: String,
    ) = """
        {"name":"verse","type":"tag","attrs":{"number":"$number","style":"v","sid":"$sid"},
         "items":[{"text":"$number","type":"text"}]}${if (items.isEmpty()) "" else "," + items.joinToString(",")}
        """.trimIndent()

    private fun text(t: String) = """{"text":"$t","type":"text"}"""

    private fun para(
        style: String,
        vararg items: String,
    ) = """{"name":"para","type":"tag","attrs":{"style":"$style"},"items":[${items.joinToString(",")}]}"""

    // ---- verse attribution -------------------------------------------------

    @Test
    fun `extracts verses with canonical id, native label and text`() {
        val r = transform("[${para("p", verse("GEN 1:1", "1", text("In the beginning.")))}]")

        assertThat(r.verses).hasSize(1)
        val v = r.verses.single()
        assertThat(v.canonicalId).isEqualTo(VerseId.encode(1, 1, 1))
        assertThat(v.nativeLabel).isEqualTo("1")
        assertThat(v.markup).isEqualTo("In the beginning.")
        assertThat(v.isTitle).isFalse()
        assertThat(v.heading).isNull()
    }

    @Test
    fun `the verse number inside the verse tag is the label, never content`() {
        val r = transform("[${para("p", verse("GEN 1:1", "1", text("Real text.")))}]")

        // Regression: naive tree-walking appends the "1" inside the verse tag to the verse body.
        assertThat(r.verses.single().markup).isEqualTo("Real text.")
        assertThat(r.verses.single().markup).doesNotContain("1Real")
    }

    @Test
    fun `text after a verse tag attaches to that verse until the next one`() {
        val r =
            transform(
                "[${para(
                    "p",
                    verse("GEN 1:1", "1", text("First. ")),
                    verse("GEN 1:2", "2", text("Second.")),
                )}]",
            )

        assertThat(r.verses.map { it.markup }).containsExactly("First.", "Second.").inOrder()
    }

    @Test
    fun `a merged verse label is preserved while the id takes the range start`() {
        // D-V3-4: the displayed label may legitimately differ from the canonical verse number.
        val r = transform("[${para("p", verse("3JN 1:14-15", "14-15", text("Peace.")))}]")

        val v = r.verses.single()
        assertThat(v.nativeLabel).isEqualTo("14-15")
        assertThat(v.canonicalId).isEqualTo(VerseId.encode(64, 1, 14))
    }

    // ---- superscription vs section heading (D-OT-10) ------------------------

    @Test
    fun `a superscription becomes a verse-0 title row, not a heading`() {
        val r =
            transform(
                "[" + para("d", text("A Psalm of David.")) + "," +
                    para("q", verse("PSA 3:1", "1", text("Lord, how they have increased."))) + "]",
            )

        assertThat(r.verses).hasSize(2)
        val title = r.verses.first()
        assertThat(title.canonicalId).isEqualTo(VerseId.encode(19, 3, 0))
        assertThat(title.isTitle).isTrue()
        assertThat(title.markup).isEqualTo("A Psalm of David.")
        assertThat(r.verses[1].isTitle).isFalse()
    }

    @Test
    fun `a section heading attaches to the following verse and is NOT its own row`() {
        val r =
            transform(
                "[" + para("s", text("Morning Prayer.")) + "," +
                    para("p", verse("PSA 3:1", "1", text("Body."))) + "]",
            )

        // The load-bearing assertion: one row, not two. A heading row would collide on the
        // LazyColumn's canonical-id key (D-V3-12) and crash Compose with a duplicate key.
        assertThat(r.verses).hasSize(1)
        assertThat(r.verses.single().heading).isEqualTo("Morning Prayer.")
        assertThat(r.verses.single().markup).isEqualTo("Body.")
    }

    @Test
    fun `a heading before a superscription attaches to the superscription row`() {
        val r =
            transform(
                "[" + para("ms2", text("Psalm 3")) + "," +
                    para("s", text("Morning Prayer.")) + "," +
                    para("d", text("A Psalm of David.")) + "," +
                    para("q", verse("PSA 3:1", "1", text("Body."))) + "]",
            )

        assertThat(r.verses).hasSize(2)
        assertThat(r.verses.first().heading).isEqualTo("Psalm 3\nMorning Prayer.")
        assertThat(r.verses.first().isTitle).isTrue()
        assertThat(r.verses[1].heading).isNull()
    }

    @Test
    fun `a mid-chapter heading attaches to the verse it introduces`() {
        val r =
            transform(
                "[" + para("p", verse("MRK 16:8", "8", text("They fled."))) + "," +
                    para("s", text("Mary Magdalene Sees the Risen Lord")) + "," +
                    para("p", verse("MRK 16:9", "9", text("Now when He rose."))) + "]",
            )

        assertThat(r.verses).hasSize(2)
        assertThat(r.verses[0].heading).isNull()
        assertThat(r.verses[1].heading).isEqualTo("Mary Magdalene Sees the Risen Lord")
    }

    // ---- markup mapping (D-OT-11) ------------------------------------------

    @Test
    fun `italic char style becomes the added-word tag`() {
        val r =
            transform(
                "[${para(
                    "p",
                    verse("GEN 1:1", "1", text("God ")),
                    """{"name":"char","type":"tag","attrs":{"style":"it"},"items":[${text("was")}]}""",
                    text(" here."),
                )}]",
            )

        assertThat(r.verses.single().markup).isEqualTo("God <a>was</a> here.")
    }

    @Test
    fun `words of Christ become the reserved red-letter tag`() {
        val r =
            transform(
                "[${para(
                    "p",
                    verse("JHN 11:35", "35"),
                    """{"name":"char","type":"tag","attrs":{"style":"wj"},"items":[${text("Come forth.")}]}""",
                )}]",
            )

        assertThat(r.verses.single().markup).isEqualTo("<w>Come forth.</w>")
    }

    @Test
    fun `a poetic paragraph continuing a verse emits a line break`() {
        val r =
            transform(
                "[" + para("q1", verse("PSA 3:1", "1", text("First line."))) + "," +
                    para("q2", text("Second line.")) + "]",
            )

        assertThat(r.verses.single().markup).isEqualTo("First line.<l/>Second line.")
    }

    @Test
    fun `small caps and Selah keep their text and drop the tag`() {
        val r =
            transform(
                "[${para(
                    "p",
                    verse("PSA 3:2", "2"),
                    """{"name":"char","type":"tag","attrs":{"style":"sc"},"items":[${text("Lord")}]}""",
                    text(". "),
                    """{"name":"char","type":"tag","attrs":{"style":"qs"},"items":[${text("Selah")}]}""",
                )}]",
            )

        assertThat(r.verses.single().markup).isEqualTo("Lord. Selah")
        assertThat(r.unmappedStyles).isEmpty()
    }

    @Test
    fun `footnotes are apparatus and are excluded entirely`() {
        val r =
            transform(
                "[${para(
                    "p",
                    verse("GEN 1:1", "1", text("Kept. ")),
                    """{"name":"note","type":"tag","attrs":{"style":"f"},"items":[${text("Dropped footnote.")}]}""",
                )}]",
            )

        assertThat(r.verses.single().markup).isEqualTo("Kept.")
        assertThat(r.verses.single().markup).doesNotContain("Dropped")
    }

    // ---- robustness --------------------------------------------------------

    @Test
    fun `an unmapped style keeps its text and is reported rather than silently dropped`() {
        val r =
            transform(
                "[${para(
                    "p",
                    verse("GEN 1:1", "1"),
                    """{"name":"char","type":"tag","attrs":{"style":"zz9"},"items":[${text("Still here.")}]}""",
                )}]",
            )

        // Scripture must never vanish from the screen because of an unknown style...
        assertThat(r.verses.single().markup).isEqualTo("Still here.")
        // ...but the gate has to be able to see it.
        assertThat(r.unmappedStyles).containsExactly("char/zz9")
    }

    @Test
    fun `a malformed or unknown sid is skipped rather than guessed onto a wrong verse`() {
        val r =
            transform(
                "[" + para("p", verse("NOPE 1:1", "1", text("Unknown book."))) + "," +
                    para("p", verse("garbage", "1", text("Malformed."))) + "]",
            )

        assertThat(r.verses).isEmpty()
    }

    @Test
    fun `empty content yields no verses and no complaints`() {
        val r = UsxTransformer.transform(JsonArray(emptyList())) { null }

        assertThat(r.verses).isEmpty()
        assertThat(r.unmappedStyles).isEmpty()
    }

    // ---- catalog integration ------------------------------------------------

    @Test
    fun `book resolution goes through the one catalog, including awkward USFM codes`() {
        // Anti-drift: these resolve via Book.usfmCode (D-S13-1), never a second table here.
        val cases =
            mapOf(
                "GEN 1:1" to VerseId.encode(1, 1, 1),
                "PSA 3:1" to VerseId.encode(19, 3, 1),
                "PHP 1:1" to VerseId.encode(50, 1, 1),
                "EZK 1:1" to VerseId.encode(26, 1, 1),
                "MRK 1:1" to VerseId.encode(41, 1, 1),
                "JUD 1:1" to VerseId.encode(65, 1, 1),
                "3JN 1:1" to VerseId.encode(64, 1, 1),
            )
        cases.forEach { (sid, expected) ->
            val r = transform("[${para("p", verse(sid, "1", text("x")))}]")
            assertThat(r.verses.single().canonicalId).isEqualTo(expected)
        }
    }
}
