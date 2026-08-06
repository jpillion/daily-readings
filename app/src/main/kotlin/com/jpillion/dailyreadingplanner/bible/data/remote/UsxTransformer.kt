package com.jpillion.dailyreadingplanner.bible.data.remote

import com.jpillion.dailyreadingplanner.bible.data.markup.BibleMarkup
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * D-OT-6 — API.Bible's USX tree (`content-type=json`) → the seam's [VerseText] rows.
 *
 * Pure and JVM-testable: takes already-parsed JSON, returns rows. No network, no Android.
 *
 * Three things this must get right, each of which has a dedicated test:
 *  - **Verse attribution.** A `verse` tag opens a verse; everything after it belongs to that verse
 *    until the next one. The number *inside* the verse tag is the label, NOT content (D-V3-4:
 *    [VerseText.nativeLabel] is read, never derived from the id).
 *  - **Superscription vs. section heading** (D-OT-10). A `para style="d"` is canonical text and
 *    becomes a verse-0 row with `isTitle = true`. A `para style="s"`/`"ms*"` is *editorial* and
 *    becomes the [VerseText.heading] of the row that follows it — never its own row, because the
 *    reader keys the list by canonical id and there is no id free between verse 8 and verse 9.
 *  - **Nothing is ever silently dropped.** An unrecognised style keeps its text and is reported in
 *    [UsxParseResult.unmappedStyles] so a test fails on it, rather than scripture quietly vanishing
 *    from the screen. Production degrades to plain text; the gate is where it gets loud.
 *
 * Style mapping is D-OT-11, measured from captured NKJV + NASB payloads rather than assumed.
 */
object UsxTransformer {
    /** `char` styles that carry translator-added words — KJV renders these italic (`<a>`). */
    private val ADDED_STYLES = setOf("it", "add")

    /** `char` style for words of Christ. Activates the reserved [BibleMarkup.WORDS_OF_CHRIST]. */
    private const val WORDS_OF_CHRIST_STYLE = "wj"

    /**
     * `char` styles whose text is kept verbatim with the tag dropped: small caps / divine name
     * (the bundled KJV shows "LORD"; these render "Lord" — styling lost, text preserved) and
     * Selah. Listed explicitly so they are a decision, not an accident.
     */
    private val PASSTHROUGH_CHAR_STYLES = setOf("sc", "nd", "qs", "k", "tl", "pn", "w")

    /** `para` styles that are editorial headings → [VerseText.heading] (D-OT-7 / D-OT-10). */
    private val HEADING_PARA_STYLES =
        setOf("s", "s1", "s2", "s3", "ms", "ms1", "ms2", "mt", "mt1", "mt2", "mte", "sp", "r", "sr")

    /** `para` style for a psalm superscription → a verse-0 row with `isTitle = true`. */
    private const val SUPERSCRIPTION_PARA_STYLE = "d"

    /** `para` styles that are poetic lines — each begins on a new line (`<l/>`). */
    private val POETRY_PARA_STYLES =
        setOf("q", "q1", "q2", "q3", "q4", "qc", "qr", "qm", "qm1", "qm2")

    /** `para` styles that are ordinary prose containers. */
    private val PROSE_PARA_STYLES =
        setOf("p", "m", "nb", "pi", "pi1", "pi2", "pc", "pm", "mi", "cls", "li", "li1", "li2", "b")

    /**
     * @param content the `data.content` array from an API.Bible passage response.
     * @param usfmToBookNo maps a USFM code ("PSA") to the catalog book order. Supplied by the
     *   caller from the ONE `BookCatalog` (`Book.usfmCode`, D-S13-1) — never a second table here.
     */
    fun transform(
        content: JsonArray,
        usfmToBookNo: (String) -> Int?,
    ): UsxParseResult {
        val state = TransformState(usfmToBookNo)
        content.forEach { state.visitBlock(it) }
        return state.finish()
    }

    private class TransformState(
        private val usfmToBookNo: (String) -> Int?,
    ) {
        private val rows = mutableListOf<MutableRow>()
        private val unmapped = sortedSetOf<String>()

        /** Editorial heading text awaiting the row it introduces (D-OT-10). */
        private var pendingHeading: String? = null

        /** Superscription text; held until a verse tag reveals which book/chapter it belongs to. */
        private var pendingSuperscription: String? = null

        private var current: MutableRow? = null

        fun visitBlock(node: JsonElement) {
            val obj = node as? JsonObject ?: return
            val style = obj.attr("style")
            when {
                style == SUPERSCRIPTION_PARA_STYLE -> {
                    pendingSuperscription = appendPending(pendingSuperscription, obj.plainText())
                }
                style in HEADING_PARA_STYLES -> {
                    pendingHeading = appendPending(pendingHeading, obj.plainText())
                }
                else -> {
                    if (style != null && style !in POETRY_PARA_STYLES && style !in PROSE_PARA_STYLES) {
                        unmapped.add("para/$style")
                    }
                    // A poetic paragraph starts a new line within the verse it continues.
                    if (style in POETRY_PARA_STYLES) current?.startNewLine()
                    obj.items().forEach { visitInline(it, italic = false, wordsOfChrist = false) }
                }
            }
        }

        private fun visitInline(
            node: JsonElement,
            italic: Boolean,
            wordsOfChrist: Boolean,
        ) {
            val obj = node as? JsonObject ?: return
            when (obj.stringOrNull("type")) {
                "text" -> current?.append(obj.stringOrNull("text").orEmpty(), italic, wordsOfChrist)
                "tag" ->
                    when (obj.stringOrNull("name")) {
                        "verse" -> openVerse(obj)
                        "char" -> visitChar(obj, italic, wordsOfChrist)
                        // note/footnote content is apparatus, not scripture: skip entirely.
                        "note" -> Unit
                        else -> obj.items().forEach { visitInline(it, italic, wordsOfChrist) }
                    }
            }
        }

        private fun visitChar(
            obj: JsonObject,
            italic: Boolean,
            wordsOfChrist: Boolean,
        ) {
            val style = obj.attr("style")
            val nowItalic = italic || style in ADDED_STYLES
            val nowWoc = wordsOfChrist || style == WORDS_OF_CHRIST_STYLE
            if (style != null &&
                style !in ADDED_STYLES &&
                style != WORDS_OF_CHRIST_STYLE &&
                style !in PASSTHROUGH_CHAR_STYLES
            ) {
                unmapped.add("char/$style")
            }
            obj.items().forEach { visitInline(it, nowItalic, nowWoc) }
        }

        /**
         * A `verse` tag opens a new verse. `sid` ("PSA 3:1") carries book/chapter/verse; `number`
         * is the display label, which may legitimately differ from the id (D-V3-4) — e.g. a merged
         * verse labelled "1-2". The number text *inside* this tag is skipped, never appended.
         */
        private fun openVerse(obj: JsonObject) {
            val sid = obj.attr("sid") ?: return
            val ref = parseSid(sid) ?: return
            flushSuperscription(ref.bookNo, ref.chapter)
            val row =
                MutableRow(
                    canonicalId = VerseId.encode(ref.bookNo, ref.chapter, ref.verse),
                    nativeLabel = obj.attr("number") ?: ref.verse.toString(),
                    isTitle = false,
                    heading = pendingHeading,
                )
            pendingHeading = null
            rows.add(row)
            current = row
        }

        /** Emits the held superscription as the chapter's verse-0 row, once its chapter is known. */
        private fun flushSuperscription(
            bookNo: Int,
            chapter: Int,
        ) {
            val text = pendingSuperscription ?: return
            pendingSuperscription = null
            val row =
                MutableRow(
                    canonicalId = VerseId.encode(bookNo, chapter, 0),
                    nativeLabel = "",
                    isTitle = true,
                    heading = pendingHeading,
                )
            pendingHeading = null
            row.append(text, italic = false, wordsOfChrist = false)
            rows.add(row)
        }

        fun finish(): UsxParseResult =
            UsxParseResult(
                verses = rows.map { it.toVerseText() },
                unmappedStyles = unmapped.toSet(),
            )

        private fun appendPending(
            existing: String?,
            addition: String,
        ): String? {
            val clean = addition.trim()
            if (clean.isEmpty()) return existing
            return if (existing.isNullOrEmpty()) clean else "$existing\n$clean"
        }

        private data class Sid(val bookNo: Int, val chapter: Int, val verse: Int)

        /** "PSA 3:1" → (19, 3, 1). Returns null for anything malformed — never a wrong guess. */
        private fun parseSid(sid: String): Sid? {
            val (bookPart, rest) =
                sid.trim().split(' ', limit = 2).takeIf { it.size == 2 } ?: return null
            val bookNo = usfmToBookNo(bookPart.uppercase()) ?: return null
            val (chapterPart, versePart) =
                rest.split(':', limit = 2).takeIf { it.size == 2 } ?: return null
            val chapter = chapterPart.trim().toIntOrNull() ?: return null
            // A merged verse sid can read "1-2"; the range START is the canonical id.
            val verse = versePart.trim().substringBefore('-').toIntOrNull() ?: return null
            return Sid(bookNo, chapter, verse)
        }
    }

    private class MutableRow(
        val canonicalId: Long,
        val nativeLabel: String,
        val isTitle: Boolean,
        val heading: String?,
    ) {
        private val markup = StringBuilder()
        private var italicOpen = false
        private var wocOpen = false

        fun startNewLine() {
            if (markup.isNotEmpty()) markup.append("<${BibleMarkup.LINE_BREAK}/>")
        }

        fun append(
            text: String,
            italic: Boolean,
            wordsOfChrist: Boolean,
        ) {
            if (text.isEmpty()) return
            setSpan(wordsOfChrist, wocOpen, BibleMarkup.WORDS_OF_CHRIST) { wocOpen = it }
            setSpan(italic, italicOpen, BibleMarkup.ADDED) { italicOpen = it }
            markup.append(text)
        }

        private inline fun setSpan(
            want: Boolean,
            open: Boolean,
            tag: String,
            set: (Boolean) -> Unit,
        ) {
            if (want == open) return
            markup.append(if (want) "<$tag>" else "</$tag>")
            set(want)
        }

        fun toVerseText(): VerseText {
            if (italicOpen) markup.append("</${BibleMarkup.ADDED}>")
            if (wocOpen) markup.append("</${BibleMarkup.WORDS_OF_CHRIST}>")
            return VerseText(
                canonicalId = canonicalId,
                nativeLabel = nativeLabel,
                isTitle = isTitle,
                markup = markup.toString().trim(),
                heading = heading,
            )
        }
    }

    private fun JsonObject.items(): List<JsonElement> = this["items"]?.jsonArray ?: emptyList()

    private fun JsonObject.attr(name: String): String? =
        (this["attrs"] as? JsonObject)?.stringOrNull(name)

    private fun JsonObject.stringOrNull(name: String): String? =
        runCatching { this[name]?.jsonPrimitive?.content }.getOrNull()

    /** All descendant text, ignoring markup — used for headings and superscriptions. */
    private fun JsonObject.plainText(): String =
        buildString {
            fun walk(node: JsonElement) {
                val obj = node as? JsonObject ?: return
                if (obj.stringOrNull("type") == "text") append(obj.stringOrNull("text").orEmpty())
                obj.items().forEach { walk(it) }
            }
            walk(this@plainText)
        }
}

/**
 * [UsxTransformer.transform] output. [unmappedStyles] is empty on healthy input; a non-empty set
 * means a USX style was encountered that D-OT-11 does not map. Its text is still preserved (as
 * plain text), but the gate asserts this is empty so the mapping table is provably complete
 * against real payloads.
 */
data class UsxParseResult(
    val verses: List<VerseText>,
    val unmappedStyles: Set<String>,
)
